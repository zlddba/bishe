package team.wd.atnestapp.utils

data class AppVersionInfo(
    val versionName: String,
    val buildNumber: String,
) {
    val versionCode: Long get() = buildNumber.toLongOrNull() ?: 0L

    val displayVersion: String get() = if (buildNumber.isBlank()) versionName
    else "$versionName ($buildNumber)"

    companion object {
        val Unknown = AppVersionInfo("unknown", "")
    }
}

expect fun getAppVersionInfo(): AppVersionInfo