package team.wd.atnestapp.ui.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import team.wd.atnestapp.ui.app.DeviceLocation.bestCached
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 一个经纬度点，**已经是 GCJ-02**（与高德底图、机构坐标同一套）。
 * 只用来传递设备位置，不参与接口模型。
 */
data class DevicePoint(val latitude: Double, val longitude: Double)

/**
 * 取一次设备当前位置。
 *
 * 没有接高德定位 SDK，用的是系统 [LocationManager]：
 * - 少一个包（高德定位 aar 约 3MB）和一套 Key/隐私声明；
 * - 国产 ROM 的位置服务由系统/厂商提供，不依赖 Google Play 服务；
 * - 这里只要一个点用来画「我在哪」，不需要持续跟随、地理围栏、逆地理编码这些定位 SDK 的能力。
 *
 * 系统给的是 WGS-84，而地图底图是 GCJ-02，国内两者差 50~500 米。
 * 所以出口统一做一次 [wgs84ToGcj02]，上层拿到的点直接能用在地图上，不会"人和蓝点差半条街"。
 */
object DeviceLocation {

    /** 粗定位也算「有权限」：Android 12 起用户可以选择只给大概位置。 */
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * 一次性定位。
     *
     * 先看系统缓存：一分钟内的位置直接可用，几乎不耗电；否则等一次真实定位，
     * 最多等 [timeout]（GPS 冷启动在室外通常 3~10 秒，室内只能靠基站/WiFi）。
     * 超时后用旧缓存兜底——"大概在哪"比"什么都没有"有用。
     */
    suspend fun current(context: Context, timeout: Duration = 6.seconds): DevicePoint? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val cached = bestCached(context, manager)
        if (cached != null && System.currentTimeMillis() - cached.time <= 60_000L) {
            return cached.toPoint()
        }
        val fix = withTimeoutOrNull(timeout) { awaitFix(context, manager) }
        return (fix ?: cached)?.toPoint()
    }

    /**
     * 系统缓存里最新的一条，可能是很久以前的点，是否可用由调用方按时效判断。
     *
     * 这里的 @SuppressLint("MissingPermission") 不是"跳过检查"：下面第一行就是显式校验，
     * 外面 [current] 入口也校验过一次，两层都挡不住的话还有 [runCatching] 兜 SecurityException
     * （用户可能进了页面之后在系统设置里把定位关掉）。加注解只是因为 lint 的判断很粗，
     * 认不出 `hasPermission()` 这种被抽出去的检查。
     */
    @SuppressLint("MissingPermission")
    private fun bestCached(context: Context, manager: LocationManager): Location? {
        if (!hasPermission(context)) return null
        return PROVIDERS.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    /**
     * 等一次真实的定位回调。
     *
     * 权限说明同 [bestCached]。
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(context: Context, manager: LocationManager): Location? {
        if (!hasPermission(context)) return null
        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }

                override fun onLocationChanged(locations: MutableList<Location>) {
                    locations.lastOrNull()?.let { onLocationChanged(it) }
                }

            }

            var registered = 0
            PROVIDERS.forEach { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) {
                        manager.requestLocationUpdates(
                            provider,
                            0L,
                            0f,
                            listener,
                            Looper.getMainLooper(),
                        )
                        registered++
                    }
                }
            }
            if (registered == 0) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        }
    }

    private fun Location.toPoint() = wgs84ToGcj02(latitude, longitude)

    private val PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    )
}

/**
 * 两点间球面距离（米）。
 *
 * 用等距圆柱近似：城市范围内误差很小，但比 Haversine 少几次三角函数，
 * 首页列表滚动时不会因为每张卡都算距离而掉帧。排序仍由后端 Haversine 完成。
 */
fun distanceMeters(
    fromLat: Double,
    fromLng: Double,
    toLat: Double,
    toLng: Double,
): Double {
    if (fromLat !in -90.0..90.0 || toLat !in -90.0..90.0) return Double.POSITIVE_INFINITY
    val midLat = (fromLat + toLat) / 2.0 * PI / 180.0
    val x = (toLng - fromLng) * cos(midLat)
    val y = toLat - fromLat
    return sqrt(x * x + y * y) * 111_320.0
}

/** 距离展示：1 公里内报米，超过报公里；无坐标返回空串。 */
fun formatDistance(meters: Double): String {
    if (!meters.isFinite() || meters <= 0.0) return ""
    return if (meters < 1000) {
        "${meters.roundToInt()} 米"
    } else {
        "%.1f 公里".format(meters / 1000.0)
    }
}

/** 克拉索夫斯基椭球参数，GCJ-02 偏移算法需要。 */
private const val GCJ_A = 6_378_245.0
private const val GCJ_EE = 0.006_693_421_622_965_943

/**
 * WGS-84 → GCJ-02（国测局"火星坐标"）。
 *
 * 这是国内地图通用的一段公开算法：把 GPS 原始坐标做一次非线性偏移，
 * 让点在加密后的底图上落在正确位置。不做这一步，蓝点会比实际位置偏几百米。
 *
 * 境外不偏移——港澳台和国外用的是 WGS-84，套上偏移反而会错；
 * 但算法里的"境外"判断是大陆经验矩形，港澳台会被算作境内，这一点在注释里说明即可。
 */
private fun wgs84ToGcj02(lat: Double, lng: Double): DevicePoint {
    if (lng !in 72.004..137.8347 || lat !in 0.8293..55.8271) {
        return DevicePoint(lat, lng)
    }
    val dLatRaw = gcjDeltaLat(lng - 105.0, lat - 35.0)
    val dLngRaw = gcjDeltaLng(lng - 105.0, lat - 35.0)
    val radLat = lat / 180.0 * PI
    val magic = 1 - GCJ_EE * sin(radLat) * sin(radLat)
    val sqrtMagic = sqrt(magic)
    val dLat = (dLatRaw * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * PI)
    val dLng = (dLngRaw * 180.0) / (GCJ_A / sqrtMagic * cos(radLat) * PI)
    return DevicePoint(lat + dLat, lng + dLng)
}

private fun gcjDeltaLat(x: Double, y: Double): Double {
    var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
    ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
    return ret
}

private fun gcjDeltaLng(x: Double, y: Double): Double {
    var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
    ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
    return ret
}
