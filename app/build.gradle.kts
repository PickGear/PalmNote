plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.baselineProfiles)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

val localProps =
    run {
        val map = mutableMapOf<String, String>()
        val f = rootProject.file("local.properties")
        if (f.exists()) {
            f.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val eq = trimmed.indexOf('=')
                    if (eq > 0) map[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                }
            }
        }
        map
    }

// 密钥文件不存在时（如 CI 检出）退化为 unsigned 冒烟构建
val releaseStoreFile = file("../${localProps["RELEASE_STORE_FILE"] ?: "release.jks"}")

android {
    namespace = "com.palmnote.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.palmnote"
        minSdk = 26
        // targetSdk 34：自用侧载，禁用 Android 15+ 强制 predictive back，恢复传统返回动画；
        // compileSdk 保持 36 不损失编译能力。上 Play 时需升回 35+。
        targetSdk = 34
        // versionCode 单调递增：1.4.1 取 7（1.4.0 正式发布为 6，覆盖安装需更大）
        versionCode = 7
        versionName = libs.versions.palmnote.get()
        // 版本号的单一事实来源就是 `libs.versions.toml` 的 palmnote 键：
        // 界面取 `BuildConfig.VERSION_NAME`（AboutScreen 就是这么读的）。
        // 原先这里还 `resValue` 生成过一个 `app_version` 字符串资源，注释说给 About/AppLock 用，
        // 但两处实际都用 BuildConfig —— 全仓无人引用，已删（lint 的 UnusedResources 也报过它）。

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // README 截图专用：`-PhideDemoBanner=true` 打出的包会隐藏「示例模式」状态条
        // （说明见 AppNavigation）。默认 false —— 发布包必须带状态条。
        buildConfigField(
            "boolean",
            "HIDE_DEMO_BANNER",
            (project.findProperty("hideDemoBanner") == "true").toString()
        )
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // release 仅 arm64-v8a：手机/平板已全面 64 位，省去 ~30MB 32 位 native 库体积
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            // 密钥文件不存在时（如 CI 检出）退化为 unsigned 冒烟构建
            if (releaseStoreFile.exists()) {
                storeFile = releaseStoreFile
                storePassword = localProps["RELEASE_STORE_PASSWORD"] ?: ""
                keyAlias = localProps["RELEASE_KEY_ALIAS"] ?: ""
                keyPassword = localProps["RELEASE_KEY_PASSWORD"] ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // CI 无密钥时保持 unsigned 冒烟构建
            if (releaseStoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // debug 补充 x86_64 以便本机模拟器安装（release 仅 arm64-v8a 精简体积）
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
    }

    lint {
        abortOnError = true
    }

    testOptions {
        unitTests {
            // Robolectric 需要访问 Android 资源（如 manifest/asset）
            isIncludeAndroidResources = true
        }
    }

    baselineProfile {
        // 关闭自动生成：已提交静态 baseline-prof.txt，本地 release 构建不再依赖模拟器。
        // 需要更新 profile 时手动执行 generateBaselineProfile 任务。
        automaticGenerationDuringBuild = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-Xjvm-default=all",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.generateKotlin", "true")
    }
    sourceSets {
        // Room 2.7 MigrationTestHelper 从 assets 读取导出的 schema，
        // 直接把 schemas 目录挂为 assets，保持单一来源、随 ksp 自动更新。
        // androidTest：instrumentation 测试直接读 APK assets。
        // debug：Robolectric 单测读取的 merged assets 只包含 debug sourceSet，
        //   所以把 schemas 挂到 debug（而非 test），避免打进 release APK。
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas", "$rootDir/core/schemas")
        }
        getByName("debug") {
            assets.srcDirs("$projectDir/schemas", "$rootDir/core/schemas")
        }
    }
    // 仅保留中英文本地化资源：剥离 AppCompat/Compose 等库携带的其余 ~80 个语言包
    androidResources {
        localeFilters += listOf("zh", "en")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    allRules = false
}

// 输出文件名：PalmNote-<version>.apk
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = "PalmNote-${output.versionName.get()}.apk"
            }
        }
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.window.size)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SQLCipher 数据库加密
    implementation(libs.sqlcipher.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil 3.x
    implementation(libs.coil.compose)

    // PaddleOCR OCR - PP-OCRv6 via ONNX Runtime (ppocr-sdk)
    implementation(project(":core"))
    implementation(project(":ppocr-sdk"))

    // Paging3
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Lunar calendar
    implementation(libs.lunar.java)

    // Core
    implementation(libs.core.ktx)
    // 侧载安装也能在运行期把 ART profile 写进 ReferenceProfile（安装期 dexopt 只吃 APK 内嵌 profile）
    implementation(libs.profileinstaller)
    implementation(libs.appcompat)
    implementation(libs.documentfile)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)

    // Biometric
    implementation(libs.biometric)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
    // createComposeRule 在 Robolectric 单测里需要一个 ComponentActivity —— 由 ui-test-manifest 提供
    debugImplementation(libs.compose.ui.test.manifest)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.monitor)
    // Compose 测量测试：详情页规格是"逐项像素"的，需要能真的量出来（首个用例：英雄区 110dp）
    testImplementation(libs.compose.ui.test.junit4)

    // Android instrumentation tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.test.core)
}
