import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * 高德 3D 地图的依赖来源，两种取其一：
 *
 * 1. 官网下载的 aar 丢进 shared/libs/ → 用本地包（官网版本比 Maven Central 新）。
 *    文件名随便叫，放几个都行（3D 地图 / 定位 / 搜索可以一起放）。
 * 2. libs/ 是空的 → 退回 Maven 上的 com.amap.api:3dmap（版本旧，但能直接跑）。
 *
 * 为什么必须在 shared 而不是 androidApp：DAmap.kt 在 shared 的 androidMain 里
 * 直接 import com.amap.api.maps.*，SDK 得进 shared 的编译类路径，
 * 只给 androidApp 加依赖是编不过的。
 *
 * 两种来源绝不能同时存在：同一个类被 aar 和 Maven 包各提供一次，
 * 会在 dex 阶段报 Duplicate class com.amap.api.maps.AMap，直接构建失败。
 * 所以这里用 if/else 而不是"两个都加"。
 *
 * 注意：这个判断在配置阶段执行。放了 / 删了 aar 之后要重新 Sync 一次项目
 * （命令行加 --rerun-tasks，或直接删 .gradle 缓存），否则会沿用上一次的结论。
 */
val amapLocalLibs = fileTree("libs") { include("*.aar", "*.jar") }
val useLocalAmapAar: Boolean = amapLocalLibs.files.isNotEmpty()

kotlin {
    jvm()
    android {
       namespace = "team.wd.atnestapp.shared"
       compileSdk {
           version = release(libs.versions.android.compileSdk.get().toInt()) {
               minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
           }
       }
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_21
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.navigation.compose)
            // 高德 3D 地图：只用于「看位置」，导航交给手机里已装的地图 App。
            // 用 implementation 而不是 api：MainActivity 不需要直接引用 SDK 类型。
            //
            // 本地 aar 是"胖包"，classes.jar / jni 各架构 .so / assets 样式表 / 自带混淆规则
            // 都在里面，implementation(fileTree) 之后 AGP 会自动解包并合并，
            // 不像 jar 那样还要自己配 jniLibs.srcDirs。
            //
            // 万一 Kotlin 这边不接受 FileCollection 形式的依赖（报
            // "Unsupported dependency type"），改成按配置名加即可：
            //   add("androidMainImplementation", amapLocalLibs)
            if (useLocalAmapAar) {
                implementation(amapLocalLibs)
            } else {
                implementation(libs.amap.map3d)
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // 会话持久化：SessionStore 是 public API，DataStore 类型也跟着暴露给 androidApp / desktopApp
            api(libs.datastore.core)
            api(libs.datastore.preferences.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
