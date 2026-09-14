// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 起 Kotlin 编译已内置（不再需要 kotlin-android 插件），但内置版本可能低于
// MiuiX / Compose 运行时所需的 Kotlin 版本。这里显式声明更高版本的 KGP，
// Gradle 会用它替代内置版本；它同时也是 app 模块里 Compose 编译器插件的版本来源。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
