rootProject.name = "PatnestApp"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://cache-redirector.jetbrains.com/intellij-repository/releases") {
            mavenContent {
                includeGroupAndSubgroups("com.jetbrains.intellij.platform")
            }
        }
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
            mavenContent {
                includeGroupAndSubgroups("com.jetbrains.intellij.platform")
            }
        }
        // RootEncoder（Android 端摄像头/麦克风直推 RTMP）发布在 JitPack
        maven("https://jitpack.io")
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
