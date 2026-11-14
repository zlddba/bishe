package team.wd.atnestapp

import java.io.File

/**
 * 桌面端接口地址配置。
 *
 * 打包产物必须开箱即用：接口地址在**构建期**由 Gradle 写成
 * `-Dpatnest.api.base=...` 写进 jpackage 启动器，随安装包一起分发。
 * 运行时不再强制要求 .env 或环境变量；它们只作为「想临时换服务器」的覆盖手段。
 *
 * 优先级（从高到低）：
 * 1. 环境变量 PATNEST_API_BASE —— 显式覆盖，便于运维指向别的后端
 * 2. .env —— 本地开发 / 从源码目录启动时沿用旧习惯
 * 3. 系统属性 patnest.api.base —— 构建期固化在启动器里（安装包走这条）
 * 4. DEFAULT_API_BASE —— IDE 直接 run 时的兜底，保证任何情况下都不会抛异常
 */
object AppConfig {
    private const val KEY = "PATNEST_API_BASE"
    private const val PROPERTY_KEY = "patnest.api.base"

    /** 与 desktopApp/build.gradle.kts 的默认值保持一致 */
    const val DEFAULT_API_BASE = "https://www.linkzdsada.dpdns.org:9200/api/v1"

    val apiBaseUrl: String by lazy {
        System.getenv(KEY)?.clean()
            ?: dotEnv()[KEY]?.clean()
            ?: System.getProperty(PROPERTY_KEY)?.clean()
            ?: DEFAULT_API_BASE
    }

    private fun String.clean(): String? = trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .takeIf { it.isNotEmpty() }

    private fun dotEnv(): Map<String, String> {
        // 从当前工作目录向上找最近的 .env：Gradle 运行 desktopApp 时能定位到 KMP 项目根目录。
        val file = generateSequence(File(".").absoluteFile) { it.parentFile }
            .take(4)
            .map { File(it, ".env") }
            .firstOrNull { it.isFile } ?: return emptyMap()
        return runCatching {
            file.readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) {
                        null
                    } else {
                        val key = line.substring(0, index).trim()
                        val value = line.substring(index + 1).trim()
                            .removeSurrounding("\"")
                            .removeSurrounding("'")
                        key to value
                    }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}
