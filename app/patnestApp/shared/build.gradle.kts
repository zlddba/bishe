import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * 高德 3D 地图 SDK 的来源，两种取其一：
 *
 * 1. 官网下载的 aar 放进 shared/amap-repo/（本地 Maven 仓库布局，见该目录 README）
 *    → 用本地包，官网版本比 Maven Central 新。
 * 2. amap-repo 里没有 aar → 退回 Maven 上的 com.amap.api:3dmap（版本旧，但能直接跑）。
 *
 * 为什么不写成 implementation(files("libs/xxx.aar"))：
 * shared 用的是 AGP 的 KMP 库插件（com.android.kotlin.multiplatform.library），
 * 它必须把 shared 打成 AAR 交给 androidApp。AGP 9 明确禁止 AAR 里再引用"本地 .aar 文件"：
 *
 *   Direct local .aar file dependencies are not supported when building an AAR.
 *
 * 因为这样的 AAR 不会把那个本地 aar 的类与资源打进去，下游拿到的是残缺产物。
 * 换成 Maven 坐标后 AGP 走标准的 AAR 依赖管线，限制就不存在了。
 *
 * 为什么必须在 shared 而不是 androidApp：DAmap.kt 在 shared 的 androidMain 里
 * 直接 import com.amap.api.maps.*，SDK 得进 shared 的编译类路径，
 * 只给 androidApp 加依赖是编不过的。
 *
 * 两种来源绝不能同时存在：同一个类被 aar 和 Maven 包各提供一次，
 * 会在 dex 阶段报 Duplicate class com.amap.api.maps.AMap，直接构建失败。
 * 所以这里用 if/else 而不是"两个都加"。
 *
 * 注意：这个判断在配置阶段执行。换版本 / 删了 aar 之后要重新 Sync 一次项目
 * （命令行加 --rerun-tasks，或直接删 .gradle 缓存），否则会沿用上一次的结论。
 */
val amapLocalVersion = "11.3.100"
val amapLocalAar = file(
    "amap-repo/com/amap/api/amap-bundle/$amapLocalVersion/amap-bundle-$amapLocalVersion.aar"
)
val useLocalAmapAar: Boolean = amapLocalAar.isFile

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
            if (useLocalAmapAar) {
                implementation("com.amap.api:amap-bundle:$amapLocalVersion")
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
