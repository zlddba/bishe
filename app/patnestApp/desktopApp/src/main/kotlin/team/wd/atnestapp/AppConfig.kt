package team.wd.atnestapp

import java.io.File

/**
 * 配置只读一处：KMP 项目根目录下的 .env（app/patnestApp/.env）。
 * 从 desktopApp 启动时会向上找到根目录这份文件，所有接口共用同一个地址。
 */
object AppConfig {
    private const val KEY = "PATNEST_API_BASE"

    val apiBaseUrl: String by lazy {
        System.getenv(KEY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: dotEnv()[KEY]?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("未配置 $KEY，请在桌面程序目录的 .env 中填写接口地址")
    }

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
