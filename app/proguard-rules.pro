# Xposed
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation,allowoptimization public class * extends com.owo233.fuckmarketads.init.EasyXposedInit {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

# libxposed service（模块 App 侧通过它写入远程偏好，需保留类名）
-keep class io.github.libxposed.service.** { *; }
-keep class com.owo233.fuckmarketads.App { *; }
-keep class com.owo233.fuckmarketads.MainActivity { *; }

# Compose / MiuiX
# 本项目开启了 -repackageclasses 与 -overloadaggressively，
# 显式保留 @Composable 成员与 Compose 编译器生成的 ComposableSingletons，避免 Release 包崩。
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class **$ComposableSingletons {
    public <fields>;
}
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**
-dontwarn top.yukonga.miuix.kmp.**
-dontwarn com.materialkolor.**
-dontwarn org.jetbrains.skiko.**

# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# Strip debug log
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Obfuscation
-repackageclasses ''
-allowaccessmodification
-dontpreverify
-overloadaggressively
-renamesourcefileattribute *

-classobfuscationdictionary obf-dict.txt
-obfuscationdictionary obf-dict.txt
