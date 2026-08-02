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
}
