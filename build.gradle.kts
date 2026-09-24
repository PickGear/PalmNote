// Hilt Gradle 插件声明 javapoet 1.10.0，但其 AggregateDeps 需要 canonicalName()（1.13 才有）。
// 强制 buildscript classpath 使用 1.13.0（冲突解析取最高版本）。
buildscript {
    dependencies {
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

// Spotless + ktlint：代码风格统一。
// ratchetFrom("origin/main") —— 只检查/格式化「相对 main 变更的文件」，存量代码不重排（仓库有
// 100 个文件用通配符 import、953 行超 140 字符，全量重排会产生巨量噪声 diff）。
// 本地用法：`./gradlew spotlessApply` 自动格式化改动文件；`./gradlew spotlessCheck` 校验。
subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        ratchetFrom("origin/main")
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get())
                .editorConfigOverride(
                    mapOf(
                        // 与仓库既有约定对齐：保留通配符 import（如 kotlinx.coroutines.flow.*）
                        "ktlint_standard_no-wildcard-imports" to "disabled",
                        // 既有长行（含中文注释/字串）较多，交给 detekt MaxLineLength 把关
                        "ktlint_standard_max-line-length" to "disabled",
                        // 仓库既有风格无尾逗号；且尾逗号会改变 detekt baseline 的签名，故关闭
                        "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
                        "ktlint_standard_trailing-comma-on-call-site" to "disabled"
                    )
                )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }
}
