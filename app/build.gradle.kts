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

val localProps = run {
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
    namespace = "com.palmnote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.palmnote"
        minSdk = 26
        // targetSdk 34锛氳嚜鐢?渚ц浇锛岀鐢?Android 15+ 寮哄埗 predictive back锛屾仮澶嶄紶缁熻繑鍥炲姩鐢伙紱
        // compileSdk 淇濇寔 36 涓嶆崯澶辩紪璇戣兘鍔涖€備笂 Play 鏃堕渶鍗囧洖 35+銆?
        targetSdk = 34
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // 瀵嗛挜鏂囦欢涓嶅瓨鍦ㄦ椂锛堝 CI 妫€鍑猴級閫€鍖栦负 unsigned 鍐掔儫鏋勫缓
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
                "proguard-rules.pro"
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

    baselineProfile {
        automaticGenerationDuringBuild = true
    }

    android.applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                this.outputFileName = "PalmNote-${versionName}.apk"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-Xjvm-default=all"
        )
    }
    buildFeatures {
        compose = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.generateKotlin", "true")
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

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation("androidx.compose.material3:material3-window-size-class")
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
    implementation(project(":ppocr-sdk"))

    // Paging3
    implementation("androidx.paging:paging-runtime-ktx:3.3.4")
    implementation("androidx.paging:paging-compose:3.3.4")

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Lunar calendar
    implementation(libs.lunar.java)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)

    // Android instrumentation tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.3.1")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.sqlite:sqlite-framework:2.4.0")
    androidTestImplementation("androidx.test:core:1.6.1")
}
