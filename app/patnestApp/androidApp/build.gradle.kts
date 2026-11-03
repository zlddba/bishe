import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile =
                file("D:\\schoollearn\\biyezuopin\\app\\patnestApp\\buildkeys\\patnestapp_debug.jks")
            storePassword = "patnestapp_debug"
            keyPassword = "patnestapp_debug"
            keyAlias = "key0"
        }
        create("release") {
            storeFile =
                file("D:\\schoollearn\\biyezuopin\\app\\patnestApp\\buildkeys\\patnestapp_release.jks")
            storePassword = "patnestapp_release"
            keyPassword = "patnestapp_release"
            keyAlias = "key0"
        }
    }
    namespace = "team.wd.atnestapp"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "team.wd.atnestapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            // 不指定 signingConfig 时 assembleRelease 产出的是未签名 APK
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}
