package team.wd.atnestapp.utils

import platform.Foundation.NSBundle

actual fun getAppVersionInfo(): AppVersionInfo {
    val bundle= NSBundle.mainBundle
    val versionName = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
    val buildNumber = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: ""
    return if(versionName.isBlank()&&buildNumber.isBlank()) AppVersionInfo.Unknown else AppVersionInfo(versionName.ifBlank { "unknown" },buildNumber)
}