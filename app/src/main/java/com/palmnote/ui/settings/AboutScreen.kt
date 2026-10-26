package com.palmnote.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palmnote.app.BuildConfig
import com.palmnote.app.R
import com.palmnote.ui.components.*
import com.palmnote.ui.components.SettingsMenuItem
import com.palmnote.ui.theme.*

/** 反馈与项目主页地址（Intent 交给系统浏览器打开，本应用无需联网权限）。 */
private const val FEEDBACK_ISSUES_URL = "https://github.com/PickGear/PalmNote/issues"
private const val PROJECT_REPO_URL = "https://github.com/PickGear/PalmNote"

/**
 * 用户交流群（QQ）。与 GitHub Issues 同为反馈渠道：用法交流、反馈与建议都可以。
 */
private const val QQ_GROUP_NUMBER = "1036958236"

/** 唤起 QQ 加群页（QQ 未安装时回退为复制群号）。 */
private fun qqGroupJoinUri(): String =
    "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$QQ_GROUP_NUMBER&card_type=group&source=qrcode"

/**
 * 关于页：品牌块 + 四张卡片（项目信息 / 反馈交流 / 法律信息 / 开源致谢），无分区标题、
 * 全部一级条目（用户 2026-10-05 定：不分级；两个反馈渠道各占一行、点击直达）。
 *
 * 卡片内用 [SettingsMenuItem] 统一行样式；页尾保留开源致谢与技术栈一行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {},
    onNavigateToVersionHistory: () -> Unit = {}
) {
    val context = LocalContext.current

    fun openInBrowser(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.about_feedback_failed), Toast.LENGTH_SHORT).show()
        }
    }

    var showGroupSheet by remember { mutableStateOf(false) }

    /** 复制群号：QQ 未安装 / 用户手动添加时都靠它（复制比记住一串数字现实）。 */
    fun copyGroupNumber() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("QQ group", QQ_GROUP_NUMBER))
        Toast.makeText(context, context.getString(R.string.about_group_copied), Toast.LENGTH_SHORT).show()
    }

    /** 唤起 QQ 加群页；未装 QQ（或 scheme 被拦）退化为复制群号，别让用户点了没反应。 */
    fun openQqGroup() {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(qqGroupJoinUri()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) {
            copyGroupNumber()
            Toast.makeText(context, context.getString(R.string.about_group_open_failed), Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.about_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 项目身份：应用图标 + 名称 + 版本 + 一句话介绍 ──
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 应用图标：启动图标的图形（便签卡 + 三条线）+ 主题主色底（默认即启动图标的青）。
                    // 尺寸按「正常 app 图标」给：72dp 底框 + 36dp 图形（图形占一半）——
                    // 88dp/54dp 那版在 28sp 的应用名上方显得过重（用户指出不协调）。
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground_bw),
                            contentDescription = null,
                            // onPrimary 而不是写死白：深色模式下主题色是亮青，白图形会糊成一片
                            tint = MaterialTheme.colorScheme.onPrimary,
                            // 该 vector 的 viewport 是 108dp、图形只占居中的 36dp，故容器须取 108dp
                            // 才等于 36dp 视觉图形；大于底框必须用 requiredSize，
                            // 否则会被父级约束钳到 72dp（图形只剩 24dp）
                            modifier = Modifier.requiredSize(108.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.about_description_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // ── 项目信息：版本历史 / 项目源码（卡片之间的留白即分组，本页不设分区标题：
            // 条目只有 6 行且标题自解释，加标题只多三行噪声；见 2026-10-05 与用户的对齐）──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.about_version_history),
                        subtitle = stringResource(R.string.about_version_history_desc),
                        tint = DopamineSky,
                        onClick = onNavigateToVersionHistory
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(
                        icon = Icons.Outlined.Code,
                        title = stringResource(R.string.about_project_source),
                        subtitle = stringResource(R.string.about_project_source_desc),
                        tint = StatusActive,
                        onClick = { openInBrowser(PROJECT_REPO_URL) }
                    )
                }
            }

            // ── 反馈交流：两个渠道各占一行、**直接是动作**（用户 2026-10-05 定：
            // 关于页只要一级菜单，不套二级面板）；行标题即渠道名，副标题写各自适合什么。──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(
                        icon = Icons.Outlined.Feedback,
                        title = stringResource(R.string.about_feedback_github_title),
                        subtitle = stringResource(R.string.about_feedback_desc),
                        // 琥珀 + 反馈气泡：上一行「项目源码」已经是 Code + 绿，同图标会同行撞车
                        tint = DopamineAmber,
                        onClick = { openInBrowser(FEEDBACK_ISSUES_URL) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(
                        icon = Icons.Outlined.Groups,
                        title = stringResource(R.string.about_group_title),
                        subtitle = stringResource(R.string.about_group_desc),
                        tint = DopamineMint,
                        // 弹窗里给「跳转 / 复制」两个动作（用户定的），行本身不直接跳
                        onClick = { showGroupSheet = true }
                    )
                }
            }

            // ── 法律信息：隐私 / 开源许可 ──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SettingsMenuItem(
                        icon = Icons.Outlined.PrivacyTip,
                        title = stringResource(R.string.about_privacy_policy),
                        subtitle = stringResource(R.string.about_privacy_desc),
                        tint = ErrorLight,
                        onClick = onNavigateToPrivacy
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    SettingsMenuItem(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.about_open_source_license),
                        subtitle = stringResource(R.string.about_license_desc),
                        tint = StatusActive,
                        onClick = onNavigateToLicense
                    )
                }
            }

            // ── 开源与致谢：GPL-3.0 一句话说明 + 致谢脚注 ──
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Outlined.VolunteerActivism,
                            contentDescription = null,
                            tint = ModuleLife,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.about_open_source_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kotlin · Jetpack Compose · Room · SQLCipher",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_made_with),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showGroupSheet) {
        QqGroupSheet(
            onDismiss = { showGroupSheet = false },
            onCopy = { copyGroupNumber() },
            onOpenQq = { openQqGroup() }
        )
    }
}

/**
 * QQ 群面板：群号 + 「跳转 / 复制」两个动作（用户定的：弹窗直选）。
 * 不放隐私说明和「重要问题走 Issue」的引导 —— GitHub 行就在上面，说明文字用户已明确不需要。
 */
@Composable
private fun QqGroupSheet(
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onOpenQq: () -> Unit
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.about_group_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.about_group_sheet_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.about_group_number_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                QQ_GROUP_NUMBER,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenQq, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.about_group_open))
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.about_group_copy))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit = {}
) {
    val isZh = LocalConfiguration.current.locales[0].language == "zh"
    val lines = getPrivacyPolicyLines(isZh)
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.about_privacy_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    style = if (isPrivacyHeading(line, isZh))
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

private fun isPrivacyHeading(line: String, isZh: Boolean): Boolean {
    return if (isZh) {
        line.startsWith("一、") || line.startsWith("二、") || line.startsWith("三、") || line.startsWith("四、") || line.startsWith("五、") || line.startsWith("六、") || line.startsWith("七、") || line.startsWith("八、") || line.startsWith("九、") || line.endsWith("隐私政策")
    } else {
        line.matches(Regex("^\\d+\\..*")) || line == "Privacy Policy"
    }
}

fun getPrivacyPolicyLines(isZh: Boolean = true): List<String> {
    return if (isZh) privacyPolicyLinesZh else privacyPolicyLinesEn
}

internal val privacyPolicyLinesZh = """
隐私政策

应用名称：PalmNote（掌记）
最后更新：2026年10月18日

PalmNote 是一款纯本地应用。这份说明只想讲清楚一件事：你的数据不离开你的设备。

一、我们不收集数据

不收集。PalmNote 在清单中移除了网络权限（INTERNET），在系统层面就没有联网能力，因此不存在上传、回传或遥测。应用内没有广告、统计或崩溃上报 SDK，也不提供账号注册——没有服务器可言。

二、你主动使用功能时，在本机处理的数据

- 账单导入：通过系统文件选择器选中你下载的账单文件，解析全程在本机完成，文件不外传
- 应用锁：PIN 经加盐哈希后存储；指纹 / 面部仅用于本机校验，不存储、不上传
- 日历同步：仅在你开启后写入系统日历，不读取其它日历数据

三、数据存在哪里

全部存放在应用私有目录，受 Android 沙箱保护，其它应用无法读取：

- 数据库：SQLCipher 全库加密（AES-256），密钥仅存于本机
- 备份文件：可选 AES-GCM 加密，密钥由你设定
- 密码本：字段级加密，独立主密码

四、你的数据你做主

你可以随时导出备份、删除单条或全部数据。卸载应用即彻底删除本机全部数据，且不可恢复。

五、疑问与反馈

https://github.com/PickGear/PalmNote/issues

本应用基于 GPL-3.0 发布，按"现状"提供、不附带任何担保，完整的免责与责任条款见仓库中的 LICENSE。
""".trimIndent().split("\n").filter { it.isNotBlank() }

internal val privacyPolicyLinesEn = """
Privacy Policy

App name: PalmNote
Last updated: October 18, 2026

PalmNote is a fully local app. This document says one thing: your data never leaves your device.

1. We Do Not Collect Data

We collect nothing. PalmNote removes the network permission (INTERNET) in the manifest, so it has no network capability at the OS level - there is no upload, no telemetry, no data reporting. The app contains no advertising, analytics or crash-reporting SDKs, and provides no account registration, because there is no server.

2. Data Processed On-Device When You Use Certain Features

- Photos and bill recognition: uses the system camera / photo picker; recognition runs on-device with an offline model, and images never leave your device
- App lock: PINs are stored as salted hashes; fingerprint / face is used only for on-device verification and is never stored or transmitted
- Calendar sync: only writes to the system calendar when you enable it, and never reads other calendar data

3. Where Data Is Stored

Everything is stored in the app's private directory, protected by the Android sandbox and unreadable by other apps:

- Database: fully encrypted with SQLCipher (AES-256); keys are kept only on the device
- Backup files: optional AES-GCM encryption with a user-set key
- Password vault: field-level encryption with a separate master password

4. Your Data Is Yours

You can export a backup and delete any or all data at any time. Uninstalling the app wipes all local data permanently and irreversibly.

5. Questions and Feedback

https://github.com/PickGear/PalmNote/issues

The app is released under GPL-3.0 and provided "as is", without warranty of any kind. The complete disclaimer and liability terms are in the LICENSE file in the repository.
""".trimIndent().split("\n").filter { it.isNotBlank() }
