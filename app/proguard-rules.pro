# Xposed
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation,allowoptimization public class * extends com.mars.mimarketpurify.init.EasyXposedInit {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}

# libxposed service（模块 App 侧通过它写入远程偏好，需保留类名）
-keep class io.github.libxposed.service.** { *; }
-keep class com.mars.mimarketpurify.App { *; }
-keep class com.mars.mimarketpurify.MainActivity { *; }

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
