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

// 接口地址在构建期固化：CI 用 PATNEST_API_BASE 注入，本地构建用生产地址兜底。
// 下面这行 jvmArgs 会被 jpackage 写进启动器的 java-options，
// 安装包/绿色版因此不再需要 .env 或环境变量就能直接运行。
val desktopApiBase: String = System.getenv("PATNEST_API_BASE")
    ?.trim()?.takeIf { it.isNotBlank() }
    ?: "https://www.linkzdsada.dpdns.org:9200/api/v1"

compose.desktop {
    application {
        mainClass = "team.wd.atnestapp.MainKt"
        jvmArgs("-Dpatnest.api.base=$desktopApiBase")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            // 面向用户的产品名（不再用 applicationId 当安装包名）；
            // CI 会把最终分发文件再统一重命名成 patnest-<版本>-<平台>-<架构>.<格式>
            packageName = "patnest"
            // 与发布 tag 对齐；本地构建默认 2.0.5，CI 用 PATNEST_VERSION 覆盖
            packageVersion = System.getenv("PATNEST_VERSION")?.takeIf { it.isNotBlank() } ?: "2.0.5"
            // DataStore 落盘时用到 java.base 之外的内部 API。jpackage 产物默认只带最小模块集，
            // 不显式声明的话，装完运行会抛异常（IDE 里跑得起来，打包后才炸）。
            modules("jdk.unsupported", "jdk.unsupported.desktop")
        }
    }
}
