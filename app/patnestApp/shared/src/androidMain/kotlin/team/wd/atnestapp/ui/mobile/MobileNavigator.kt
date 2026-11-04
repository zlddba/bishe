package team.wd.atnestapp.ui.mobile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 商家导航：优先叫起高德地图 App，没装就退回系统地图（geo:），再不行提示用户。
 * 只发一个 ACTION_VIEW，不涉及定位权限，也不需要新的依赖。
 */
fun openNavigation(context: Context, latitude: Double, longitude: Double, name: String) {
    val lat = latitude.toString()
    val lng = longitude.toString()
    val query = Uri.encode(name.ifBlank { "宠栖机构" })
    val candidates = listOf(
        "amapuri://route/plan/?sid=0&dlat=" + lat + "&dlon=" + lng + "&dname=" + query + "&dev=0&t=0",
        "androidamap://viewMap?sourceApplication=PetNest&poiname=" + query + "&lat=" + lat + "&lon=" + lng + "&dev=0",
        "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + query + ")",
    )
    for (raw in candidates) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // 继续尝试下一个
        } catch (_: SecurityException) {
            continue
        }
    }
}
