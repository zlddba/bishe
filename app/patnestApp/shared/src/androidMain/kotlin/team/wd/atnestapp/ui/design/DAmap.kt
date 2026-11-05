package team.wd.atnestapp.ui.design

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 高德 Key 与隐私合规的唯一入口。
 *
 * 两个坑都在这里兜住：
 * 1. Key 只从 Application 的 meta-data 读（构建期由 .env 注入）。Web 服务 Key 不能用在这里，
 *    用了只会得到 INVALID_USER_SCODE 和一张白图。
 * 2. 高德 8.1.0 之后必须先声明隐私合规再构造 MapView，否则地图直接白屏——
 *    这是接入高德最常见的"代码没错但就是不出来"的原因。
 *
 * 这里直接声明"已同意"：应用内目前没有独立的隐私弹窗，协议文本在 ㊼ 协议页。
 * 若后续要严格合规，应把这两个 true 换成用户实际的选择结果（拒绝则不要创建地图）。
 */
internal object AmapBridge {
    private const val KEY_META = "com.amap.api.v2.apikey"

    /** 构建脚本在没配 Key 时写入的占位值，见 androidApp/build.gradle.kts 的 manifestPlaceholders。 */
    private const val UNSET = "unset"

    @Volatile
    private var prepared = false

    /** 读 meta-data 里的 Android Key；空串或占位符 "unset" 表示还没申请，调用方应退回后端瓦片底图。 */
    fun key(context: Context): String = runCatching {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        info.metaData?.getString(KEY_META).orEmpty().trim()
    }.getOrDefault("").let { if (it == UNSET) "" else it }

    fun hasKey(context: Context): Boolean = key(context).isNotEmpty()

    @Synchronized
    fun prepare(context: Context) {
        if (prepared) return
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        prepared = true
    }
}

/**
 * 高德 SDK 版地图：矢量渲染，缩放/拖动跟手，能看到路名和 POI。
 *
 * 只在配了 Android Key 时使用；没配时上层 [DMapView] 会退回瓦片底图。
 * 导航不在应用内做——SDK 只负责"看清位置"，点导航由 [team.wd.atnestapp.ui.app.MapNavigation] 交给手机上已装的地图 App。
 */
@Composable
internal fun DAmapView(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    showMarker: Boolean = true,
    onCenterChange: ((Double, Double) -> Unit)? = null,
    onZoomChange: ((Int) -> Unit)? = null,
    onLoadFailed: (() -> Unit)? = null,
    // 标记位置与相机中心分开：全屏页把镜头拖走后，标记仍要钉在原坐标
    markerLatitude: Double = latitude,
    markerLongitude: Double = longitude,
    // 「我的位置」：系统定位拿到的一个点，画成蓝点；null 表示还没定位到/没权限
    myLocation: Pair<Double, Double>? = null,
) {
    val context = LocalContext.current
    AmapBridge.prepare(context)

    // TextureMapView 而不是 MapView：底层是 TextureView，才能被 Compose 的圆角裁切（GLSurfaceView 会溢出直角）。
    // 它是原生 View，必须自行托管生命周期：Compose 不会替它调 onCreate/onDestroy
    val mapView = remember { TextureMapView(context).apply { onCreate(null) } }
    var aMap by remember { mutableStateOf<AMap?>(null) }

    // Key 与包名/签名不匹配时，高德不会抛异常，只会给一块白屏。
    // 用一个"加载成功"标记 + 超时兜底，把这种情况转成可用的瓦片底图，而不是让用户盯着空白。
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(aMap) {
        val map = aMap ?: return@LaunchedEffect
        map.setOnMapLoadedListener { loaded = true }
        delay(6000)
        if (!loaded) onLoadFailed?.invoke()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 观察者只收后续事件，页面此刻可能已经是 RESUMED，补一次
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // 中心与级别必须分两次下发。
    //
    // 合成成 newLatLngZoom(lat, lng, zoom) 会出事：双指缩放时中心也会跟着变，
    // onCameraChangeFinish 把新中心写回上层 → 这个 effect 重启 → 又拿 state 里那个
    // 「手势之前的旧级别」整体重设相机，用户刚缩放的级别当场被弹回去。
    // 级别越大回弹越明显，所以现象是「放大之后手势缩放像失灵」。
    LaunchedEffect(aMap, latitude, longitude) {
        val map = aMap ?: return@LaunchedEffect
        val current = map.cameraPosition.target
        // 已经在这个点上了就别再动相机，否则"回调→下发→再回调"会互相踢。
        // 1e-6 度约 0.1 米，远小于人的操作精度，但能吸掉 float 换算带回来的尾数
        if (abs(current.latitude - latitude) < 1e-6 &&
            abs(current.longitude - longitude) < 1e-6
        ) {
            return@LaunchedEffect
        }
        // 只改中心，不带级别：zoom 由下面那条通道单独负责
        map.moveCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)))
    }

    LaunchedEffect(aMap, zoom) {
        val map = aMap ?: return@LaunchedEffect
        val target = zoom.coerceIn(MAP_ZOOM_MIN, MAP_ZOOM_MAX).toFloat()
        // 手势缩放刚把级别回写成整数，这里再 zoomTo 精度更低的同一个值只会打断惯性。
        // 用 0.51 是因为四舍五入最多差 0.5，正好全被吞掉；而按钮一档是 1，仍会执行
        if (abs(map.cameraPosition.zoom - target) < 0.51f) return@LaunchedEffect
        map.moveCamera(CameraUpdateFactory.zoomTo(target))
    }

    val myIcon = remember(context) { buildMyLocationIcon(context) }

    LaunchedEffect(aMap, markerLatitude, markerLongitude, showMarker, myLocation) {
        val map = aMap ?: return@LaunchedEffect
        map.clear()
        if (showMarker) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(markerLatitude, markerLongitude))
                    .title("位置"),
            )
        }
        // 蓝点压在最上层（zIndex），避免和机构图钉重叠时被盖住
        myLocation?.let { (myLat, myLng) ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(myLat, myLng))
                    .icon(myIcon)
                    .zIndex(20f)
                    .title("我的位置"),
            )
        }
    }

    val latestOnCenterChange by rememberUpdatedState(onCenterChange)
    val latestOnZoomChange by rememberUpdatedState(onZoomChange)
    val latestLat by rememberUpdatedState(latitude)
    val latestLng by rememberUpdatedState(longitude)
    val latestZoom by rememberUpdatedState(zoom)

    LaunchedEffect(aMap, interactive) {
        val map = aMap ?: return@LaunchedEffect
        // 手势开关显式给值：默认虽然是 true，但"只展示"的小地图（interactive = false）
        // 不关掉的话，用户手指一滑就把卡片里那张图拖走了，还会把中心写回业务状态
        map.uiSettings.isZoomGesturesEnabled = interactive
        map.uiSettings.isScrollGesturesEnabled = interactive
        // 只展示的页面不需要监听相机变化；地图随页面销毁，不必手动清监听
        if (!interactive) return@LaunchedEffect
        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) = Unit

            override fun onCameraChangeFinish(position: CameraPosition?) {
                val camera = position ?: return
                // 手势缩放的结果也要回写。不回写的话上层还拿着手势之前的级别，
                // 之后任何一次相机下发都会把用户缩放好的视角弹回去。
                val level = camera.zoom.roundToInt().coerceIn(MAP_ZOOM_MIN, MAP_ZOOM_MAX)
                if (level != latestZoom) latestOnZoomChange?.invoke(level)

                val target = camera.target ?: return
                // moveCamera 自己也会触发一次回调，跟上一次的外部坐标相同就忽略，
                // 否则"外部改→移相机→回调→再改"会来回抖
                if (abs(target.latitude - latestLat) < 1e-7 &&
                    abs(target.longitude - latestLng) < 1e-7
                ) {
                    return
                }
                latestOnCenterChange?.invoke(target.latitude, target.longitude)
            }
        })
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            if (aMap == null) {
                val map = view.map
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isScaleControlsEnabled = true
                map.uiSettings.isCompassEnabled = false
                // 只看位置，别让单指误触把地图转歪
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.mapType = AMap.MAP_TYPE_NORMAL
                aMap = map
            }
        },
    )
}

/**
 * 画一个「我的位置」蓝点：半透明光晕 + 实心圆 + 白色内核。
 *
 * 不用 `BitmapDescriptorFactory.defaultMarker(HUE_AZURE)`：那是水滴形图钉，
 * 和机构标记长得一样，两个点叠在一起时分不清哪个是我；而且它带阴影、尺寸固定，
 * 放在地图上比常见的定位蓝点大一倍。自己画一个 26dp 的位图更贴合地图习惯。
 */
private fun buildMyLocationIcon(context: Context): BitmapDescriptor {
    val size = (26 * context.resources.displayMetrics.density).toInt().coerceAtLeast(26)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f
    paint.color = AndroidColor.argb(56, 26, 115, 232)
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = AndroidColor.rgb(26, 115, 232)
    canvas.drawCircle(radius, radius, radius * 0.56f, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(radius, radius, radius * 0.22f, paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
