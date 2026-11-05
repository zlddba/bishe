import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(project(":shared"))

    // JetBrains 图标资源
    implementation(libs.jetbrains.icons)

    // 商家直播：VLCJ 播放 HLS。运行机需要安装 VLC（libvlc），否则页面会显示"缺少 VLC"提示。
    implementation(libs.vlcj)

    // 商家直播：软件内开播——JavaCV 调 FFmpeg 采集摄像头/麦克风并推 RTMP。
    // 这个依赖自带各平台原生库，体积较大（首次构建要下载几百 MB）；只用播放可不加。
    implementation(libs.javacv)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.compose.material3)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "team.wd.atnestapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "team.wd.atnestapp"
            packageVersion = "1.0.0"
            // DataStore 落盘时用到 java.base 之外的内部 API。jpackage 产物默认只带最小模块集，
            // 不显式声明的话，装完运行会抛异常（IDE 里跑得起来，打包后才炸）。
            modules("jdk.unsupported", "jdk.unsupported.desktop")
        }
    }
}
