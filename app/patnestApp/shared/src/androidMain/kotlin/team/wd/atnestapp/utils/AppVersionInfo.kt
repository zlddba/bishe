package team.wd.atnestapp.utils

import android.content.Context

internal object AppContextHolder {
    var application: Context?=null
}

fun initAppVersionHelper(context: Context){
    AppContextHolder.application=context.applicationContext
}

actual fun getAppVersionInfo(): AppVersionInfo {
    val ctx= AppContextHolder.application?:return AppVersionInfo.Unknown
    return runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code= pi.longVersionCode
        AppVersionInfo(
            versionName =pi.versionName?:"",
            buildNumber = code.toString()
        )
    }.getOrElse { AppVersionInfo.Unknown }
}