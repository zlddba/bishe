package team.wd.atnestapp.ui.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Web Mercator 换算（与高德瓦片、在线地图同一套规则）：
 * 每个级别整幅世界图是 `256 * 2^zoom` 像素，图片正中就是中心坐标。
 * 坐标始终是 GCJ-02，这里只做投影，不做坐标系转换。
 *
 * 放在 commonMain 是因为手机端与桌面端共用同一份瓦片算法：
 * 两边都从后端 `/geo/tile` 取图，只有绘制方式不同。
 */
const val TILE_EDGE = 256.0

fun worldSizePx(zoom: Int): Double = TILE_EDGE * 2.0.pow(zoom.toDouble())

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
    val rad = safeLat * PI / 180.0
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
}

/** 归一化墨卡托 Y → 纬度 */
private fun mercatorYToLat(yNorm: Double): Double {
    val n = PI * (1.0 - 2.0 * yNorm)
    return atan(sinh(n)) * 180.0 / PI
}

private const val EARTH_RADIUS_M = 6_371_008.8

/**
 * 两点间的球面距离（米）。
 *
 * 用 haversine 而不是平面勾股：经纬度一度在不同纬度上的实际长度差很多，
 * 平面近似在北京还行，到高纬度就会偏出百分之几十。这里既要显示"距我多远"，
 * 也要据此决定要不要把两点一起框进屏幕，所以必须准。
 */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val rad = PI / 180.0
    val dLat = (lat2 - lat1) * rad
    val dLng = (lng2 - lng1) * rad
    val a = sin(dLat / 2).pow(2) +
        cos(lat1 * rad) * cos(lat2 * rad) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * 让两点同时落在屏幕内的缩放级别。
 *
 * `paddingRatio` 是可用屏幕比例：0.62 表示两点最多占视口的 62%，
 * 四周留出余量，免得标记贴在屏幕边缘被浮层挡住。
 *
 * 只用于「我的位置」与机构在同城的情况；两点相距太远时调用方应先判断距离，
 * 否则会缩到整张中国地图，两个点都成像素点，反而什么也看不出。
 */
fun zoomToFit(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
    viewW: Double,
    viewH: Double,
    paddingRatio: Double = 0.62,
    minZoom: Int = 3,
    maxZoom: Int = 17,
): Int {
    if (viewW < 32 || viewH < 32) return maxZoom
    // 在 zoom = 0（世界 256px）下算像素差，再按 2^z 反推级别
    val (x1, y1) = lonLatToWorldPixel(lat1, lng1, 0)
    val (x2, y2) = lonLatToWorldPixel(lat2, lng2, 0)
    var dx = abs(x2 - x1)
    // 经度是环形的，跨 180° 时取短边
    if (dx > TILE_EDGE / 2) dx = TILE_EDGE - dx
    val dy = abs(y2 - y1)
    val span = hypot(dx, dy)
    if (span < 1e-9) return maxZoom
    val scale = min(viewW, viewH) * paddingRatio / span
    if (scale <= 1.0) return minZoom
    return (ln(scale) / ln(2.0)).toInt().coerceIn(minZoom, maxZoom)
}
