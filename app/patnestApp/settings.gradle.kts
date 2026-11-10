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
        // 高德官网下载的 SDK 放在 shared/amap-repo，按 Maven 仓库布局存放。
        // 放在最前面：同坐标优先取本地这份，取不到才会去 google()/mavenCentral()。
        // 只接管 com.amap.api 组，不影响其他依赖的解析路径。
        maven {
            name = "localAmapRepo"
            url = rootDir.resolve("shared/amap-repo").toURI()
            mavenContent { includeGroup("com.amap.api") }
        }
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
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
