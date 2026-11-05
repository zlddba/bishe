package team.wd.atnestapp.ui.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 调起手机里的导航软件。
 *
 * 这里刻意不做「内置导航」：路径规划、实时路况、语音播报都是地图厂商的强项，
 * 应用自己画一条线只会更难用。做法是把目的地坐标交给用户已装的 App——
 * 按高德 → 百度 → 腾讯 → 系统 `geo:` 选择器 的顺序尝试，谁装了就交给谁。
 *
 * 不做 `queryIntentActivities` 预判：Android 11 起包可见性受限，查不到不等于没装
 * （除非在 Manifest 里把每个包都声明一遍）。直接 `startActivity` 再捕获
 * [ActivityNotFoundException] 更准，也不需要用户额外授权。
 *
 * 坐标口径是 GCJ-02（后端地理编码的结果），高德/腾讯直接可用；
 * 百度通过 `coord_type=gcj02` 声明由它自己换算，避免偏移几百米。
 */
object MapNavigation {

    /** 返回是否成功叫起了某个 App / 浏览器；false 表示这台机器上没有任何可用的目的地应用。 */
    fun navigate(context: Context, latitude: Double, longitude: Double, name: String): Boolean {
        val label = Uri.encode(name.ifBlank { "目的地" })
        val coord = "$latitude,$longitude"

        val deepLinks = listOf(
            // 高德：dev=0 表示起点用当前位置，t=0 驾车
            "com.autonavi.minimap" to
                "androidamap://route/plan/?sourceApplication=patnest" +
                "&dlat=$latitude&dlon=$longitude&dname=$label&dev=0&t=0",
            // 百度：destination 的格式是 latlng:纬度,经度|name:名称
            "com.baidu.BaiduMap" to
                "baidumap://map/direction?destination=latlng:$coord|name:$label" +
                "&coord_type=gcj02&mode=driving&src=android.team.wd.atnestapp",
            // 腾讯：referer 是它要求的调用方标识，随便填一个非空值即可
            "com.tencent.map" to
                "qqmap://map/routeplan?type=drive&fromcoord=CurrentLocation" +
                "&to=$label&tocoord=$coord&referer=patnest",
        )
        deepLinks.forEach { (pkg, uri) -> if (start(context, uri, pkg)) return true }

        // 标准 geo: 协议：系统会弹出"用哪个应用打开"，用户选过的会记住
        if (start(context, "geo:$coord?q=$coord($label)", null)) return true

        // 一个地图 App 都没有（模拟器、纯净平板）：退到浏览器打开高德网页版
        return start(
            context,
            "https://uri.amap.com/navigation?to=$longitude,$latitude,$label&mode=car&callnative=1",
            null,
        )
    }

    private fun start(context: Context, uri: String, pkg: String?): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            if (pkg != null) setPackage(pkg)
            // 非 Activity 的 Context 起 Activity 必须带这个 flag，否则直接抛异常
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        // 少数 ROM 会拦截 URI 唤起，当作"这个 App 不可用"，继续试下一个
        false
    }
}
