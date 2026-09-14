plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.owo233.fuckmarketads"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.owo233.fuckmarketads"
        minSdk = 29
        targetSdk = 36
        versionCode = 27
        versionName = "1.3.0"
        buildConfigField("String", "APP_NAME", "\"Fuck Market Ads\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        packaging {
            resources {
                excludes += "**"
                merges += "META-INF/xposed/*"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // Jetpack Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    // MiuiX（小米 HyperOS 风格的 Compose 组件库）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)

    // Xposed / Hook
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.ezxhelper.core)
}
