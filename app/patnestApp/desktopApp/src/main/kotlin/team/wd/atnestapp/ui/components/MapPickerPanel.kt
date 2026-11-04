package team.wd.atnestapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.util.TileSlot
import team.wd.atnestapp.ui.util.openInAmapWeb
import team.wd.atnestapp.ui.util.panCenter
import team.wd.atnestapp.ui.util.visibleTiles
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// 没有已存坐标时的默认视野：呼和浩特市中心（GCJ-02）
private const val DEFAULT_LAT = 40.821365
private const val DEFAULT_LNG = 111.772564
private const val MAP_HEIGHT = 340

private data class TileKey(val zoom: Int, val x: Int, val y: Int)

/**
 * 应用内地图选点面板。红点所在的画面中心就是选中位置，拖动地图把它对准门店即可。
 *
 * 底图用瓦片而不是整张静态地图：拖动全程本地渲染（只改视觉偏移），
 * 松手后才把新露出的那一两列瓦片请求补齐，所以拖动是连续的、也不会每帧打一次接口。
 */
@Composable
fun MapPickerPanel(
    app: AppState,
    initialLat: Double?,
    initialLng: Double?,
    onConfirm: (lat: Double, lng: Double, address: String) -> Unit,
) {
    var lat by remember { mutableStateOf(initialLat ?: DEFAULT_LAT) }
    var lng by remember { mutableStateOf(initialLng ?: DEFAULT_LNG) }
    var zoom by remember { mutableIntStateOf(if (initialLat != null && initialLng != null) 15 else 11) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    val tiles = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    // 只用于去重在途请求，不需要触发重组，所以不是 state
    val pending = remember { mutableSetOf<TileKey>() }
    var loadingCount by remember { mutableIntStateOf(0) }
    // 瓦片一条都没拉到时（CDN 被拦、后端没出包）退回整张静态底图
    var needStatic by remember { mutableStateOf(false) }
    var staticBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // 中心/缩放/尺寸变化 → 补瓦片。拖动过程中只改 dragOffset，不会走到这里
    LaunchedEffect(lat, lng, zoom, mapSize) {
        if (mapSize.width < 60 || mapSize.height < 60) return@LaunchedEffect
        val slots = visibleTiles(lat, lng, zoom, mapSize.width.toDouble(), mapSize.height.toDouble())
        slots.forEach { slot ->
            val key = TileKey(slot.zoom, slot.x, slot.y)
            if (tiles.containsKey(key) || !pending.add(key)) return@forEach
            loadingCount++
            app.scope.launch {
                val outcome = app.loadTile(key.zoom, key.x, key.y)
                pending.remove(key)
                val bytes = outcome.getOrNull()
                if (bytes != null) {
                    runCatching { bytes.decodeToImageBitmap() }.getOrNull()?.let { tiles[key] = it }
                } else if (tiles.isEmpty()) {
                    // 一张都没拉到才有必要打扰用户，个别瓦片失败留着空白即可
                    note = "瓦片加载失败：" + (outcome.exceptionOrNull()?.message ?: "未知错误")
                    needStatic = true
                }
                loadingCount--
            }
        }
    }

    // 静态底图兜底：尺寸按视口来，失败就不再重复尝试
    LaunchedEffect(needStatic, lat, lng, zoom, mapSize) {
        if (!needStatic || mapSize.width < 60) return@LaunchedEffect
        delay(200)
        val outcome = app.loadMapImage(lat, lng, zoom, mapSize.width.coerceIn(60, 1000), mapSize.height.coerceIn(60, 1000))
        val bytes = outcome.getOrNull()
        if (bytes != null) {
            staticBitmap = runCatching { bytes.decodeToImageBitmap() }.getOrNull()
            note = "瓦片不可用，已改用静态底图"
        }
    }

    // 中心稳定下来后再问一次地址，避免拖动中反复请求
    LaunchedEffect(lat, lng) {
        delay(400)
        app.reverseLocation(lat, lng)?.let {
            if (it.formattedAddress.isNotBlank()) address = it.formattedAddress
        }
    }

    Column(Modifier.fillMaxWidth()) {
        FieldLabel("先按地址找到大致位置（选填）")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftTextField(city, { city = it }, Modifier.width(120.dp), placeholder = "城市")
            SoftTextField(keyword, { keyword = it }, Modifier.weight(1f), placeholder = "区/街道/门店名")
            GhostButton(
                "移到该地址",
                {
                    app.scope.launch {
                        val geo = app.resolveShopAddress(keyword, city) ?: return@launch
                        dragOffset = Offset.Zero
                        lat = geo.latitude
                        lng = geo.longitude
                        zoom = 15
                        if (geo.formattedAddress.isNotBlank()) address = geo.formattedAddress
                    }
                },
                height = 40.dp,
            )
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT.dp)
                .clip(PatNestShape.Md)
                .border(1.dp, PatNestColors.Line, PatNestShape.Md)
                .background(PatNestColors.PanelSoft)
                .onSizeChanged { mapSize = it }
                // 手势容器不能随中心坐标重启，否则拖动会被自己取消；
                // lat/lng 是状态读取，回调里拿到的始终是最新值
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragOffset = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                        },
                        onDragEnd = {
                            if (dragOffset.getDistance() > 2f) {
                                // 拖动地图等于把中心往反方向挪，红点始终指着选中的点
                                val moved = panCenter(
                                    lat, lng, zoom,
                                    -dragOffset.x.toDouble(), -dragOffset.y.toDouble(),
                                )
                                lat = moved.first
                                lng = moved.second
                            }
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = { dragOffset = Offset.Zero },
                    )
                },
        ) {
            val fallback = staticBitmap
            if (tiles.isEmpty() && fallback != null) {
                Image(
                    bitmap = fallback,
                    contentDescription = "机构位置地图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val slots = visibleTiles(lat, lng, zoom, size.width.toDouble(), size.height.toDouble())
                    slots.forEach { slot: TileSlot ->
                        val image = tiles[TileKey(slot.zoom, slot.x, slot.y)] ?: return@forEach
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(
                                (slot.leftPx + dragOffset.x).roundToInt(),
                                (slot.topPx + dragOffset.y).roundToInt(),
                            ),
                            dstSize = IntSize(image.width, image.height),
                        )
                    }
                }
            }

            // 中心红点 = 当前选中坐标，画在瓦片之上
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color.White, Offset(cx, cy - 18f), Offset(cx, cy + 18f), strokeWidth = 2f)
                drawLine(Color.White, Offset(cx - 18f, cy), Offset(cx + 18f, cy), strokeWidth = 2f)
                drawCircle(Color.White, radius = 9f, center = Offset(cx, cy))
                drawCircle(PatNestColors.Brand, radius = 6.5f, center = Offset(cx, cy))
            }

            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 按钮自带左右留白，宽度要给文字留出空间
                GhostButton("-", { zoom = (zoom - 1).coerceAtLeast(3) }, Modifier.width(56.dp), height = 30.dp)
                GhostButton("+", { zoom = (zoom + 1).coerceAtMost(18) }, Modifier.width(56.dp), height = 30.dp)
            }
            if (loadingCount > 0) {
                Text(
                    "加载瓦片 $loadingCount",
                    color = PatNestColors.Ink2,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "选中坐标 GCJ-02：${coordText(lat)}, ${coordText(lng)} · 缩放 z$zoom",
            color = PatNestColors.Ink2,
            fontSize = 12.5.sp,
        )
        if (address.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(address, color = PatNestColors.Ink3, fontSize = 12.sp)
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(note, color = PatNestColors.Warn, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "拖动地图把红点对准门店位置；需要 POI 检索时可以用浏览器里的完整地图选点",
            color = PatNestColors.Ink3,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                "在浏览器打开高德",
                {
                    if (!openInAmapWeb(lng, lat, address.ifBlank { "机构位置" })) {
                        app.toast("当前系统无法打开浏览器")
                    }
                },
                height = 36.dp,
            )
            GhostButton(
                if (app.mapPicking) "等待浏览器选点…" else "用浏览器选点",
                {
                    app.scope.launch {
                        val geo = app.pickLocationOnMap("机构位置", lat, lng) ?: return@launch
                        dragOffset = Offset.Zero
                        lat = geo.latitude
                        lng = geo.longitude
                        zoom = 15
                        if (geo.formattedAddress.isNotBlank()) address = geo.formattedAddress
                    }
                },
                height = 36.dp,
            )
            Spacer(Modifier.weight(1f))
            GhostButton("取消", { app.modal = null })
            PrimaryButton("确定选点", { onConfirm(lat, lng, address) })
        }
    }
}

/** 保留 6 位小数并去掉浮点尾巴；不用 String.format，避免跟随系统 locale 把小数点变成逗号。 */
private fun coordText(v: Double): String = ((v * 1e6).roundToLong() / 1e6).toString()
