package team.wd.atnestapp.ui.util

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator 换算（与高德瓦片、在线地图同一套规则）：
 * 每个级别整幅世界图是 `256 * 2^zoom` 像素，图片正中就是中心坐标。
 * 坐标始终是 GCJ-02，这里只做投影，不做坐标系转换。
 */
const val TILE_EDGE = 256.0

fun worldSizePx(zoom: Int): Double = TILE_EDGE * Math.pow(2.0, zoom.toDouble())

/** 经纬度 → 世界像素坐标，返回 (x, y) */
fun lonLatToWorldPixel(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
    val world = worldSizePx(zoom)
    return ((lng + 180.0) / 360.0 * world) to (latToMercatorY(lat) * world)
}

/** 世界像素坐标 → 经纬度，返回 (lat, lng) */
fun worldPixelToLonLat(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
    val world = worldSizePx(zoom)
    val lng = x / world * 360.0 - 180.0
    return mercatorYToLat((y / world).coerceIn(0.0, 1.0)) to lng
}

/** 把视口平移 dx/dy 像素后的新中心，返回 (lat, lng) */
fun panCenter(
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    dxPx: Double,
    dyPx: Double,
): Pair<Double, Double> {
    val (x, y) = lonLatToWorldPixel(centerLat, centerLng, zoom)
    val world = worldSizePx(zoom)
    return worldPixelToLonLat(
        (x + dxPx).coerceIn(0.0, world),
        (y + dyPx).coerceIn(0.0, world),
        zoom,
    )
}

/** 一块待绘制的瓦片，leftPx/topPx 是相对视口左上角的屏幕像素位置 */
data class TileSlot(val zoom: Int, val x: Int, val y: Int, val leftPx: Double, val topPx: Double)

/**
 * 视口内可见的瓦片。`extra` 是外围多取的一圈，这样拖动时新露出来的位置已有缓存。
 * 经度方向按 2^z 取模（世界是环形的），纬度方向出界直接丢弃。
 */
fun visibleTiles(
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    viewW: Double,
    viewH: Double,
    extra: Int = 1,
): List<TileSlot> {
    val (cx, cy) = lonLatToWorldPixel(centerLat, centerLng, zoom)
    val left = cx - viewW / 2.0 - extra * TILE_EDGE
    val top = cy - viewH / 2.0 - extra * TILE_EDGE
    val cols = ceil(viewW / TILE_EDGE).toInt() + extra * 2 + 1
    val rows = ceil(viewH / TILE_EDGE).toInt() + extra * 2 + 1
    val firstX = floor(left / TILE_EDGE).toInt()
    val firstY = floor(top / TILE_EDGE).toInt()
    val max = 1 shl zoom

    val out = ArrayList<TileSlot>(cols * rows)
    for (i in 0 until cols) {
        val rawX = firstX + i
        val x = ((rawX % max) + max) % max
        for (j in 0 until rows) {
            val y = firstY + j
            if (y < 0 || y >= max) continue
            out.add(TileSlot(zoom, x, y, rawX * TILE_EDGE - left, y * TILE_EDGE - top))
        }
    }
    return out
}

/** 纬度 → 归一化墨卡托 Y（0 在最北，1 在最南） */
private fun latToMercatorY(latDeg: Double): Double {
    val safeLat = latDeg.coerceIn(-85.05112878, 85.05112878)
    val rad = Math.toRadians(safeLat)
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
}

/** 归一化墨卡托 Y → 纬度 */
private fun mercatorYToLat(yNorm: Double): Double {
    val n = PI * (1.0 - 2.0 * yNorm)
    return Math.toDegrees(atan(sinh(n)))
}
