# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Compose
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.palmnote.**$$serializer { *; }
-keepclassmembers class com.palmnote.** { *** Companion; }
-keepclasseswithmembers class com.palmnote.** { kotlinx.serialization.KSerializer serializer(...); }

# Coil 3.x
-dontwarn coil3.*

# Widget
-keep class com.palmnote.ui.widget.BillWidgetProvider

# SQLCipher
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Hilt（库自带 consumer rules，显式保留安全网）
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room DAO（KSP 生成代码）
-keep class * extends androidx.room.RoomDatabase
-keep class * { *; }  # Room DAO 接口

# OpenCV（PaddleOCR 依赖，R8 下需保留完整类）
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# 移除调试日志（release 混淆时生效）；保留 w/e 错误级日志
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
