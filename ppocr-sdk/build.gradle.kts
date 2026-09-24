plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.paddle.ocr"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv.android)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
}

// detekt：与 app/core 同一套规则与配置。本模块为 PaddleOCR PP-OCRv6 的本地化移植，
// 存在大量第三方算法样板，故用独立 baseline 冻结存量问题，只对新增代码把关。
detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline-ppocr.xml")
    buildUponDefaultConfig = true
    allRules = false
}
