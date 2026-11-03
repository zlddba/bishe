package team.wd.atnestapp.ui.util

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

/**
 * 桌面端不嵌地图 SDK，看位置统一用系统浏览器打开高德网页版。
 * 坐标是 GCJ-02，所以带 coordinate=gaode；callnative=1 让装了高德 App 的场景有机会直接跳过去。
 */
fun openInAmapWeb(longitude: Double, latitude: Double, name: String): Boolean {
    val position = "${coordForUri(longitude)},${coordForUri(latitude)}"
    val label = URLEncoder.encode(name.ifBlank { "机构位置" }, "UTF-8")
    val uri = URI.create("https://uri.amap.com/marker?position=$position&name=$label&coordinate=gaode&callnative=1")

    return runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
        } else {
            return false
        }
    }.isSuccess
}

/** 去掉浮点尾部的 0，链接里 116.4074 比 116.40740000000001 更好看也更短。 */
private fun coordForUri(v: Double): String {
    val rounded = ((v * 1_000_000).toLong() / 1_000_000.0)
    return rounded.toString()
}

/** 用系统浏览器打开任意链接（地图选点页由后端出，同源，不需要额外密钥下发）。 */
fun openUrl(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        return false
    }
    Desktop.getDesktop().browse(URI(url))
}.isSuccess
