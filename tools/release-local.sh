#!/usr/bin/env bash
# 本地发布：构建正式签名包 → 覆盖上传 GitHub Release → 用服务端 digest 复验。
#
# 用法（Git Bash，仓库根）：
#   bash tools/release-local.sh              # 构建 + 本地核对 + 覆盖上传 + 复验
#   bash tools/release-local.sh --dry-run    # 只构建 + 本地核对，不上传
#   bash tools/release-local.sh --clean      # 构建前先 clean（干净环境出包）
#   bash tools/release-local.sh --install    # 构建后 adb install -r（真机回归用）
#
# 前置：local.properties 已按 local.properties.example 配好；gh 已登录。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DRY_RUN=0; DO_CLEAN=0; DO_INSTALL=0
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
        --clean)   DO_CLEAN=1 ;;
        --install) DO_INSTALL=1 ;;
        *) echo "未知参数：$arg（可用：--dry-run --clean --install）" >&2; exit 2 ;;
    esac
done

# 正式证书指纹：用于拦下「未签名 / debug 签名」的包被误发出去
EXPECT_CERT_SHA256="b6a3a0605ef558949f652793d09014726488039de1c003612d91c919a8001f9f"

# 从 version catalog 推导版本号与 tag（清单「一、版本号」要求两者一致）
VERSION="$(sed -n 's/^palmnote[[:space:]]*=[[:space:]]*"\(.*\)"/\1/p' gradle/libs.versions.toml)"
[ -n "$VERSION" ] || { echo "✗ 读不到 gradle/libs.versions.toml 里的 palmnote 版本号" >&2; exit 1; }
TAG="v$VERSION"
RELEASE_APK="PalmNote-$VERSION.apk"
APK="app/build/outputs/apk/release/$RELEASE_APK"
MAPPING="app/build/outputs/mapping/release/mapping.txt"
STAGE="app/build/release-assets"

# 真实发布必须从「可追溯的提交」出包：工作区有未提交改动时，发出去的包对应不到任何提交，
# 事后既无法复现也无法反查（--dry-run 不受此限，它的用途就是随时试构建）。
if [ "$DRY_RUN" != 1 ] && [ -n "$(git status --porcelain)" ]; then
    echo "✗ 工作区有未提交改动，发布会推出无法追溯到提交的包。请先提交（或 git stash）后再发。" >&2
    git status --short >&2
    exit 1
fi

HEAD_SHA="$(git rev-parse --short HEAD)"
echo "▶ 版本 $VERSION · tag $TAG · 构建提交 $HEAD_SHA（$(git log -1 --format=%s)）"
echo "  $([ "$DRY_RUN" = 1 ] && echo '试运行：只构建与本地核对，不上传' || echo '将覆盖上传发布资产')"

find_apksigner() {
    if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return; fi
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
    ls -1 "$sdk"/build-tools/*/apksigner "$sdk"/build-tools/*/apksigner.bat 2>/dev/null | sort -V | tail -1
}

find_adb() {
    if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
    ls -1 "$sdk"/platform-tools/adb "$sdk"/platform-tools/adb.exe 2>/dev/null | sort -V | tail -1
}

echo "▶ 1/5 构建正式包"
[ "$DO_CLEAN" = 1 ] && ./gradlew clean
./gradlew :app:assembleRelease

echo "▶ 2/5 本地核对产物"
[ -f "$APK" ] || { echo "✗ 没找到 $APK" >&2; exit 1; }
[ -f "$MAPPING" ] || { echo "✗ 没找到 mapping.txt（R8 没执行？）" >&2; exit 1; }
APKSIGNER="$(find_apksigner || true)"
if [ -n "$APKSIGNER" ]; then
    # 未签名/签名损坏时 apksigner 会非零退出：先吞掉退出码，否则 set -e 会在给出下面
    # 那句提示之前就终止——而「包没签名」恰恰是这段最该拦下的情况。
    VERIFY_OUT="$("$APKSIGNER" verify --print-certs "$APK" 2>&1 || true)"
    CERT="$(printf '%s\n' "$VERIFY_OUT" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
    [ "$CERT" = "$EXPECT_CERT_SHA256" ] || {
        echo "✗ 签名证书指纹不符：期望 $EXPECT_CERT_SHA256，实际 ${CERT:-（未签名）}" >&2
        echo "  多半是 local.properties 缺签名配置，或用了 debug 签名；已中止，不上传。" >&2
        exit 1
    }
    echo "  · 签名证书指纹一致 ✓"
else
    echo "✗ 找不到 apksigner，无法确认包是否用正式证书签名（不会盲发）" >&2
    echo "  可设 ANDROID_HOME，或把 build-tools 里的 apksigner 加进 PATH 后重试。" >&2
    exit 1
fi

echo "▶ 3/5 暂存资产并生成 SHA256SUMS"
rm -rf "$STAGE"; mkdir -p "$STAGE"
cp "$APK" "$STAGE/$RELEASE_APK"
cp "$MAPPING" "$STAGE/mapping.txt"
( cd "$STAGE" && sha256sum -b "$RELEASE_APK" mapping.txt > SHA256SUMS.txt )
LOCAL_APK_SUM="$(cd "$STAGE" && sha256sum -b "$RELEASE_APK" | cut -d' ' -f1)"
LOCAL_MAP_SUM="$(cd "$STAGE" && sha256sum -b mapping.txt | cut -d' ' -f1)"
echo "  · APK     sha256 $LOCAL_APK_SUM"
echo "  · mapping sha256 $LOCAL_MAP_SUM"

if [ "$DRY_RUN" = 1 ]; then
    echo "▶ 4/5 试运行：跳过上传（资产已在 $STAGE）"
else
    echo "▶ 4/5 上传到 Release $TAG"
    gh auth status >/dev/null 2>&1 || { echo "✗ gh 未登录" >&2; exit 1; }
    if gh release view "$TAG" >/dev/null 2>&1; then
        gh release upload "$TAG" "$STAGE/$RELEASE_APK" "$STAGE/mapping.txt" "$STAGE/SHA256SUMS.txt" --clobber
    else
        # 新版本还没有 Release：由本脚本创建（tag 不存在时会一并创建并触发 release.yml 的构建冒烟）
        gh release create "$TAG" "$STAGE/$RELEASE_APK" "$STAGE/mapping.txt" "$STAGE/SHA256SUMS.txt" \
            --title "PalmNote $TAG" --generate-notes
    fi
fi

echo "▶ 5/5 核对线上 digest"
if [ "$DRY_RUN" = 1 ]; then
    echo "  · 试运行：跳过（真跑时这里会用服务端 digest 与本地比对）"
else
    # 用 awk 按名字取值，而不是把变量拼进 jq 表达式（避免拼接进解释器程序）
    ASSETS="$(gh release view "$TAG" --json assets --jq '.assets[] | "\(.name)\t\(.digest)"')"
    SERVER_APK="$(printf '%s\n' "$ASSETS" | awk -F'\t' -v n="$RELEASE_APK" '$1==n {sub(/^sha256:/,"",$2); print $2}')"
    SERVER_MAP="$(printf '%s\n' "$ASSETS" | awk -F'\t' '$1=="mapping.txt" {sub(/^sha256:/,"",$2); print $2}')"
    [ "$SERVER_APK" = "$LOCAL_APK_SUM" ] || { echo "✗ 线上 APK digest 与本地不一致（$SERVER_APK）" >&2; exit 1; }
    [ "$SERVER_MAP" = "$LOCAL_MAP_SUM" ] || { echo "✗ 线上 mapping digest 与本地不一致（$SERVER_MAP）" >&2; exit 1; }
    echo "  · 服务端 digest 与本地一致 ✓"
    gh release view "$TAG" --json author,isDraft,isPrerelease --jq '"  · 发布者 \(.author.login) · 草稿 \(.isDraft) · 预发布 \(.isPrerelease)"'
fi

if [ "$DO_INSTALL" = 1 ]; then
    echo "▶ 真机安装（覆盖装、保数据）"
    ADB="$(find_adb || true)"
    [ -n "$ADB" ] || { echo "✗ 找不到 adb（可设 ANDROID_HOME 或把 platform-tools 加进 PATH）" >&2; exit 1; }
    "$ADB" install -r "$APK"
fi

echo "✅ 完成。真机回归清单见 docs/release-checklist.md 第八项（必须用 release 包走）。"
