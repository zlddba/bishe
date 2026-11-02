package team.wd.atnestapp.utils

import java.util.Properties
import kotlin.jvm.java

actual fun getAppVersionInfo(): AppVersionInfo {

    runCatching {
        val props = Properties()
        val loader = AppVersionInfo::class.java.classLoader
            ?: Thread.currentThread().contextClassLoader
            ?: ClassLoader.getSystemClassLoader()

        loader.getResourceAsStream("app.properties")?.use { props.load(it) }

        val name = props.getProperty("version.name")
        val build = props.getProperty("version.build")
        if (!name.isNullOrBlank()) {
            return AppVersionInfo(name, build.orEmpty())
        }
    }


    val pkg = AppVersionInfo::class.java.`package`
    val name = pkg?.implementationVersion.orEmpty()
    if (name.isNotBlank()) return AppVersionInfo(name, "")

    return AppVersionInfo.Unknown
}