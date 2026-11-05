# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------- 高德地图 SDK ----------------
# 当前 release 没开 isMinifyEnabled，下面几行是为将来开启混淆准备的：
# SDK 内部大量使用反射和 JNI 回调，类名被混淆后会出现"地图白屏 / 找不到 so"。
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.amap.api.trace.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**