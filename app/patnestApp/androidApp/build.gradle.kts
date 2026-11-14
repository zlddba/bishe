import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

/**
 * 构建配置只认一处：KMP 项目根目录的 .env（与 desktopApp 的 AppConfig 同一份）。
 *
 * 接口地址注入 BuildConfig.API_BASE，高德 Android Key 注入 manifest 的 meta-data，
 * App 里不再提供任何修改入口。
 *
 * 用 providers.fileContents 而不是 File.readText：前者会被 configuration cache 记为输入，
 * 改了 .env 重新构建就会生效；后者读的内容不在缓存追踪范围内，容易拿到上一次的旧地址。
 * .env 缺失（换了机器还没拷贝）时退回默认值，保证能编过。
 */
val envFile = rootProject.layout.projectDirectory.file(".env")
val envText: String = if (envFile.asFile.isFile) providers.fileContents(envFile).asText.get() else ""

fun envValue(name: String, fallback: String): String = envText
    .lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter("=")
    ?.trim()
    ?.removeSurrounding("\"")
    ?.removeSurrounding("'")
    ?.takeIf { it.isNotEmpty() }
    ?: fallback

val patnestApiBase: String = envValue("PATNEST_API_BASE", "127.0.0.1:9200/api/v1")

/**
 * 高德「Android 平台」Key。留空时 App 里的地图自动退回后端瓦片底图，
 * 不会出现"装了 SDK 却白屏"的情况。
 */
val patnestAmapKey: String = envValue("PATNEST_AMAP_ANDROID_KEY", "")

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
    /**
     * 签名文件定位：优先读环境变量（CI 会把 jks 从 Secrets 解码到 runner 临时目录），
     * 找不到再回落到项目内 buildkeys/。
     *
     * 之前的绝对路径 D:\schoollearn\... 只在本机能用：GitHub Actions 的 Linux runner
     * 在配置阶段就会因为文件不存在而直接失败。
     */
    fun keystore(envName: String, fileName: String): java.io.File {
        val fromEnv = providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }
        return if (fromEnv != null) {
            file(fromEnv)
        } else {
            rootProject.layout.projectDirectory.dir("buildkeys").file(fileName).asFile
        }
    }

    fun secret(envName: String, fallback: String): String =
        providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() } ?: fallback

    signingConfigs {
        getByName("debug") {
            storeFile = keystore("PATNEST_DEBUG_KEYSTORE", "patnestapp_debug.jks")
            storePassword = secret("PATNEST_DEBUG_STORE_PASSWORD", "patnestapp_debug")
            keyPassword = secret("PATNEST_DEBUG_KEY_PASSWORD", "patnestapp_debug")
            keyAlias = secret("PATNEST_DEBUG_KEY_ALIAS", "key0")
        }
        create("release") {
            storeFile = keystore("PATNEST_RELEASE_KEYSTORE", "patnestapp_release.jks")
            storePassword = secret("PATNEST_RELEASE_STORE_PASSWORD", "patnestapp_release")
            keyPassword = secret("PATNEST_RELEASE_KEY_PASSWORD", "patnestapp_release")
            keyAlias = secret("PATNEST_RELEASE_KEY_ALIAS", "key0")
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
        // 版本号随发布 tag 走：CI 构建时注入 PATNEST_VERSION / PATNEST_VERSION_CODE
        versionCode = System.getenv("PATNEST_VERSION_CODE")?.toIntOrNull() ?: 6
        versionName = System.getenv("PATNEST_VERSION")?.takeIf { it.isNotBlank() } ?: "2.0.4"

        // 接口根地址：构建期从 .env 的 PATNEST_API_BASE 注入，运行时不可修改
        buildConfigField("String", "API_BASE", "\"$patnestApiBase\"")

        // 高德地图 Key 走 Manifest 的 meta-data（SDK 只认这个位置）。
        // 没配时填 "unset" 而不是空串：空 android:value 在 manifest 合并时容易被当成资源引用报错，
        // App 读到 unset 就知道要走瓦片兜底。
        manifestPlaceholders["amapKey"] = patnestAmapKey.ifEmpty { "unset" }

        // 发行包只面向 arm64 设备：高德 AAR 自带 arm64-v8a / armeabi-v7a 两套 .so，
        // 这里只保留 64 位 ARM，产物名与 Release 附件也统一标 -android-arm64。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
        // 需要 BuildConfig.API_BASE，默认是关闭的
        buildConfig = true
    }
}
