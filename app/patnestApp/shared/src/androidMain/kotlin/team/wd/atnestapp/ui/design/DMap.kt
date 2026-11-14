package team.wd.atnestapp.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.app.MapTileKey
import team.wd.atnestapp.ui.app.MapTiles
import team.wd.atnestapp.ui.util.panCenter
import team.wd.atnestapp.ui.util.lonLatToWorldPixel
import team.wd.atnestapp.ui.util.visibleTiles
import team.wd.atnestapp.ui.util.worldSizePx
import kotlin.math.roundToInt

/** 缩放级别下限：再低整张图缩成一片。瓦片版与高德 SDK 版共用同一区间，切换底图时手感一致。 */
internal const val MAP_ZOOM_MIN = 4

/** 缩放级别上限：再高瓦片就糊了，SDK 版也超出了「看清位置」的用途。 */
internal const val MAP_ZOOM_MAX = 18

/** 「我的位置」蓝点配色。与高德 SDK 版自绘的位图保持同一组数值，两条底图看起来才一致。 */
private val MyLocationBlue = Color(0xFF1A73E8)
private val MyLocationGlow = Color(0x331A73E8)

/**
 * 应用内地图（只看位置）。
 *
 * 底图有两条路，优先用第一条：
 * 1. **高德 SDK**（配了 `PATNEST_AMAP_ANDROID_KEY` 时）——矢量渲染，缩放拖动跟手，有路名和 POI；
 * 2. **后端瓦片代理**（没配 Key 时）——走后端 `/geo/tile`，零 Key 零依赖，能看但交互弱。
 *
 * 两条都不在应用内做导航：点「导航」交给手机里装的高德/百度/腾讯，
 * 路径规划和实时路况本来就是它们的主场。
 *
 * 瓦片这条的拖动实现：拖动过程只改一个偏移量，画面靠已缓存的瓦片移动，
 * 松手才请求新露出的格子——这样跟手，也不会每一帧都打一次接口。
 */
@Composable
fun DMapView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Int = 15,
    interactive: Boolean = false,
    showMarker: Boolean = true,
    onCenterChange: ((Double, Double) -> Unit)? = null,
    // 手势缩放结束后把级别回传：上层要据此更新 +/- 按钮的基准，否则下一次点按钮会跳回旧级别
    onZoomChange: ((Int) -> Unit)? = null,
    corner: (@Composable BoxScope.() -> Unit)? = null,
    // 全屏页（铺满整屏）传 false：不要圆角和描边，否则四角会露出底色
    rounded: Boolean = true,
    // 标记默认就是中心；全屏页中心可拖走，标记仍要钉在机构位置上
    markerLatitude: Double = latitude,
    markerLongitude: Double = longitude,
    // 「我的位置」蓝点（系统定位结果）。null 表示没定位到或不显示
    myLocation: Pair<Double, Double>? = null,
) {
    val context = LocalContext.current
    // Key 是构建期注入的，运行期间不会变，读一次就够
    val useSdk = remember(context) { AmapBridge.hasKey(context) }
    // SDK 版 6 秒还没出图（多为 Key 与签名不匹配）就切瓦片，避免停在白屏上
    var sdkFailed by remember(context) { mutableStateOf(false) }
    // 卡片里的地图有圆角描边；全屏页只要底色，四角不能被裁
    val frame = if (rounded) {
        Modifier
            .clip(DShape.Lg)
            .background(DColor.MapWash)
            .border(1.dp, DColor.Line, DShape.Lg)
    } else {
        Modifier.background(DColor.MapWash)
    }
    if (useSdk && !sdkFailed) {
        Box(modifier.then(frame)) {
            DAmapView(
                latitude = latitude,
                longitude = longitude,
                zoom = zoom,
                modifier = Modifier.fillMaxSize(),
                interactive = interactive,
                showMarker = showMarker,
                markerLatitude = markerLatitude,
                markerLongitude = markerLongitude,
                myLocation = myLocation,
                onCenterChange = onCenterChange,
                onZoomChange = onZoomChange,
                onLoadFailed = { sdkFailed = true },
            )
            corner?.invoke(this)
        }
    } else {
        DTileMapView(
            latitude = latitude,
            longitude = longitude,
            modifier = modifier,
            zoom = zoom,
            interactive = interactive,
            showMarker = showMarker,
            onCenterChange = onCenterChange,
            corner = corner,
            rounded = rounded,
            markerLatitude = markerLatitude,
            markerLongitude = markerLongitude,
            myLocation = myLocation,
        )
    }
}

/**
 * 地图右下角/右上角的缩放按钮。
 *
 * 手机上没有鼠标滚轮，双指缩放又容易在拖动时误触，给一组明确的按钮更稳。
 * 级别范围 4~18：再低整张图缩成一片，再高瓦片就糊了。
 */
@Composable
fun DMapZoomPad(
    zoom: Int,
    onZoomChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DMapZoomButton("+") { onZoomChange((zoom + 1).coerceAtMost(MAP_ZOOM_MAX)) }
        DMapZoomButton("−") { onZoomChange((zoom - 1).coerceAtLeast(MAP_ZOOM_MIN)) }
    }
}

/**
 * 液态玻璃缩放按钮组：全屏地图页专用。
 * 只能画在记录层之外（浮层），玻璃模糊的就是脚下的地图瓦片。
 */
@Composable
fun DMapGlassZoomPad(
    zoom: Int,
    onZoomChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DMapGlassCircle(modifier = Modifier.clickable {
            onZoomChange((zoom + 1).coerceAtMost(MAP_ZOOM_MAX))
        }) {
            Text("+", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
        }
        DMapGlassCircle(modifier = Modifier.clickable {
            onZoomChange((zoom - 1).coerceAtLeast(MAP_ZOOM_MIN))
        }) {
            Text("−", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
        }
    }
}

@Composable
internal fun DMapGlassCircle(
    modifier: Modifier = Modifier,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    DGlassSurface(
        modifier = modifier.size(34.dp),
        shape = CircleShape,
        tint = DColor.Card.copy(alpha = 0.30f),
        blurRadius = 14f,
        cornerDp = 17.dp,
        surfaceAlpha = 0.55f,
        fallbackColor = DColor.Card,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content?.invoke(this)
        }
    }
}

@Composable
private fun DMapZoomButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(DColor.Card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DColor.Ink,
        )
    }
}

/**
 * 瓦片底图版（没配高德 Key 时的兜底）。
 *
 * 行为与 SDK 版一致：`interactive = true` 可拖动，松手回调新中心；默认只展示。
 */
@Composable
private fun DTileMapView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Int = 15,
    interactive: Boolean = false,
    showMarker: Boolean = true,
    onCenterChange: ((Double, Double) -> Unit)? = null,
    corner: (@Composable BoxScope.() -> Unit)? = null,
    rounded: Boolean = true,
    markerLatitude: Double = latitude,
    markerLongitude: Double = longitude,
    myLocation: Pair<Double, Double>? = null,
) {
    val store = DLocalStore.current
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val tiles = remember { mutableStateMapOf<MapTileKey, ImageBitmap>() }
    var loadingCount by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    // 中心、级别、尺寸任一变化都要重新算可见格子；拖动时不会走到这里（只改 drag）
    LaunchedEffect(latitude, longitude, zoom, mapSize) {
        if (mapSize.width < 32 || mapSize.height < 32) return@LaunchedEffect
        val slots = visibleTiles(latitude, longitude, zoom, mapSize.width.toDouble(), mapSize.height.toDouble())
        slots.forEach { slot ->
            val key = MapTileKey(slot.zoom, slot.x, slot.y)
            if (tiles.containsKey(key)) return@forEach
            val cached = MapTiles.cached(key)
            if (cached != null) {
                tiles[key] = cached
                return@forEach
            }
            loadingCount++
            launch {
                val image = MapTiles.load(store.api, key)
                loadingCount = (loadingCount - 1).coerceAtLeast(0)
                if (image != null) {
                    tiles[key] = image
                    note = null
                } else if (tiles.isEmpty()) {
                    // 一张都没拿到才提示；个别格子失败留白就行，别把整块地图变成错误页
                    note = "地图加载失败，检查网络或服务端 AMAP_KEY"
                }
            }
        }
    }

    val brand = DColor.Brand
    val white = DColor.White

    Box(
        modifier
            .then(
                if (rounded) {
                    Modifier
                        .clip(DShape.Lg)
                        .background(DColor.MapWash)
                        .border(1.dp, DColor.Line, DShape.Lg)
                } else {
                    Modifier.background(DColor.MapWash)
                },
            )
            .onSizeChanged { mapSize = it }
            // 手势容器不能跟着坐标重启，否则拖动会被自己取消；回调里读到的始终是最新值
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                    onDragEnd = {
                        if (drag.getDistance() > 2f) {
                            // 拖动地图 = 把中心往反方向挪，红点始终指着选中的点
                            val moved = panCenter(
                                latitude, longitude, zoom,
                                -drag.x.toDouble(), -drag.y.toDouble(),
                            )
                            onCenterChange?.invoke(moved.first, moved.second)
                        }
                        drag = Offset.Zero
                    },
                    onDragCancel = { drag = Offset.Zero },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val slots = visibleTiles(latitude, longitude, zoom, size.width.toDouble(), size.height.toDouble())
            slots.forEach { slot ->
                val image = tiles[MapTileKey(slot.zoom, slot.x, slot.y)] ?: return@forEach
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(
                        (slot.leftPx + drag.x).roundToInt(),
                        (slot.topPx + drag.y).roundToInt(),
                    ),
                    dstSize = IntSize(image.width, image.height),
                )
            }
        }

        if (showMarker || myLocation != null) {
            Canvas(Modifier.fillMaxSize()) {
                val (centerX, centerY) = lonLatToWorldPixel(latitude, longitude, zoom)
                val world = worldSizePx(zoom)

                if (showMarker) {
                    // 标记钉在地理位置上，而不是屏幕正中：中心被拖走时，标记要跟着瓦片一起移出去
                    val (markerX, markerY) = lonLatToWorldPixel(markerLatitude, markerLongitude, zoom)
                    var dxPx = markerX - centerX
                    // 经度是环形的，跨 180° 时取短边，否则标记会被画到屏幕另一侧
                    if (dxPx > world / 2) dxPx -= world
                    if (dxPx < -world / 2) dxPx += world
                    // 标记就是中心时（选点页），拖动过程中它要固定在屏幕中心等手势结束，
                    // 不能跟着 drag 一起飘；否则每拖一下红点都在抖，松手又弹回来。
                    val locked = markerLatitude == latitude && markerLongitude == longitude
                    val dragX = if (locked) 0f else drag.x
                    val dragY = if (locked) 0f else drag.y
                    val cx = size.width / 2f + dxPx.toFloat() + dragX
                    val cy = size.height / 2f + (markerY - centerY).toFloat() + dragY
                    drawLine(white, Offset(cx, cy - 16f), Offset(cx, cy + 16f), strokeWidth = 2f)
                    drawLine(white, Offset(cx - 16f, cy), Offset(cx + 16f, cy), strokeWidth = 2f)
                    drawCircle(white, radius = 8.5f, center = Offset(cx, cy))
                    drawCircle(brand, radius = 6f, center = Offset(cx, cy))
                }

                // 「我的位置」：始终跟着瓦片和拖动走，因为它表示的是地理点，不是视口中心
                myLocation?.let { (myLat, myLng) ->
                    val (myX, myY) = lonLatToWorldPixel(myLat, myLng, zoom)
                    var dxPx = myX - centerX
                    if (dxPx > world / 2) dxPx -= world
                    if (dxPx < -world / 2) dxPx += world
                    val cx = size.width / 2f + dxPx.toFloat() + drag.x
                    val cy = size.height / 2f + (myY - centerY).toFloat() + drag.y
                    drawCircle(MyLocationGlow, radius = 19f, center = Offset(cx, cy))
                    drawCircle(white, radius = 8.5f, center = Offset(cx, cy))
                    drawCircle(MyLocationBlue, radius = 6.5f, center = Offset(cx, cy))
                    drawCircle(white, radius = 2.2f, center = Offset(cx, cy))
                }
            }
        }

        if (loadingCount > 0 && tiles.isEmpty() && note == null) {
            Text(
                "地图加载中…",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        note?.let {
            Text(
                it,
                fontSize = 11.5.sp,
                color = DColor.Warn,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }

        corner?.invoke(this)
    }
}
