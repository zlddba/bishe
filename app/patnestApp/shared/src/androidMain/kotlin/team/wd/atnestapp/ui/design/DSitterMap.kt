package team.wd.atnestapp.ui.design

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.app.DeviceLocation
import team.wd.atnestapp.ui.app.DevicePoint
import team.wd.atnestapp.ui.app.MapNavigation
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.util.distanceMeters
import team.wd.atnestapp.ui.util.zoomToFit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 机构位置（全屏地图）。
 *
 * 机构详情里那块 170dp 的地图只够看个大概，这一页把地图铺满整屏：
 * 能拖、能缩放，机构名浮在顶部，地址和导航按钮浮在底部，都不占地图面积。
 *
 * 仍然不做「应用内导航」——点导航交给手机上装的地图 App（[MapNavigation]）。
 */
@Composable
fun DSitterMap(nav: DNav) {
    val store = DLocalStore.current
    val context = LocalContext.current
    val sitter = store.selectedSitter
    val lat = sitter?.latitude
    val lng = sitter?.longitude

    // 没带机构（冷启动直落本页）或机构没填坐标：给一条能走回去的空状态，不留白屏
    if (sitter == null || lat == null || lng == null) {
        Column(Modifier.fillMaxSize().background(DColor.Bg)) {
            // DMiuixTopBar 自己就带状态栏避让，这里不要再套一层；
            // 顶栏与全项目三个骨架共用同一实现，样式统一。
            DMiuixTopBar(nav, "机构位置", backable = true, actions = {})
            DPad {
                Spacer(Modifier.height(30.dp))
                DEmpty("该机构还没有填写位置", "返回详情") { nav.back() }
            }
        }
        return
    }

    var zoom by remember(sitter.id) { mutableIntStateOf(16) }
    // 中心单独存一份：用户拖走之后不跟着机构坐标回弹
    var centerLat by remember(sitter.id) { mutableStateOf(lat) }
    var centerLng by remember(sitter.id) { mutableStateOf(lng) }
    // 视口尺寸：把「我的位置」和机构一起框进屏幕时要按它算缩放级别
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    // 「我的位置」：只取一次，不做持续跟随。看位置用不上轨迹，还能省电
    var myLoc by remember(sitter.id) { mutableStateOf<DevicePoint?>(null) }
    var locating by remember { mutableStateOf(false) }
    var pendingFit by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val moved = centerLat != lat || centerLng != lng
    val addrText = sitter.addr.ifBlank { "该机构未填写详细地址" }
    val myDistance = myLoc?.let { distanceMeters(it.latitude, it.longitude, lat, lng) }

    // 液态玻璃浮层：地图绘制层挂进 backdrop 记录（瓦片路径可完整采样）。
    // 高德 SDK 路径是 AndroidView 独立 View 层，采不到地图细节——
    // 此时模糊层只剩底色，靠 surfaceAlpha=0.55 让真实地图直接透出一半，
    // 玻璃质感（折射/边缘光/模糊底）两种模式下观感一致，不再分叉。
    val mapBackdrop = rememberDGlassBackdrop()

    // 定位成功后的收尾：画蓝点，必要时把两点一起框进屏幕
    suspend fun applyMyLocation(point: DevicePoint, fit: Boolean) {
        myLoc = point
        val meters = distanceMeters(point.latitude, point.longitude, lat, lng)
        // 超过 30 公里就不强行同框：缩到那么小，两个点都只剩一个像素，反而看不出在哪
        if (!fit || mapSize.width < 32 || meters > 30_000) return
        centerLat = (lat + point.latitude) / 2
        centerLng = (lng + point.longitude) / 2
        zoom = zoomToFit(
            lat, lng, point.latitude, point.longitude,
            mapSize.width.toDouble(), mapSize.height.toDouble(),
        )
    }

    suspend fun locate(fit: Boolean) {
        locating = true
        val point = DeviceLocation.current(context)
        locating = false
        if (point == null) {
            nav.toast("没拿到位置，检查系统定位是否开启")
            return
        }
        applyMyLocation(point, fit)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Android 12 起用户可能只给「大概位置」，粗定位也够画一个点
        if (grants.values.any { it }) {
            scope.launch { locate(pendingFit) }
        } else {
            nav.toast("没有定位权限，看不到自己的位置")
        }
    }

    // 有权限就静默定位一次；没权限不主动弹窗，等用户点右侧蓝点按钮再申请，
    // 免得只是想看看机构位置的人一进页面就被权限框拦住
    LaunchedEffect(sitter.id) {
        if (DeviceLocation.hasPermission(context)) locate(fit = true)
    }

    fun requestMyLocation() {
        if (DeviceLocation.hasPermission(context)) {
            scope.launch { locate(fit = true) }
        } else {
            pendingFit = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    CompositionLocalProvider(LocalDGlassBackdrop provides mapBackdrop) {
        Box(Modifier.fillMaxSize().background(DColor.Bg).onSizeChanged { mapSize = it }) {
            DMapView(
                latitude = centerLat,
                longitude = centerLng,
                // 地图绘制（瓦片/标记）记录进玻璃 backdrop：上下浮层模糊的就是地图本身
                modifier = Modifier.fillMaxSize().dGlassBackdropSource(mapBackdrop),
            zoom = zoom,
            interactive = true,
            rounded = false,
            // 标记钉在机构坐标上，拖动地图时它跟着瓦片走，而不是黏在屏幕中心
            markerLatitude = lat,
            markerLongitude = lng,
            // 我的位置画成蓝点；没定位到就是 null，图上只剩机构图钉
            myLocation = myLoc?.let { it.latitude to it.longitude },
            onCenterChange = { newLat, newLng ->
                centerLat = newLat
                centerLng = newLng
            },
            // 手势缩放后把级别同步回来：不回写的话上层还拿着旧级别，
            // 之后任何一次相机下发都会把用户缩放好的视野弹回去
            onZoomChange = { zoom = it },
        )

        // 右侧控件组：缩放 / 定位 / 回中（液态玻璃，浮在地图上）。
        // 必须画在记录层之外——corner 槽在 DMapView 内部会被记进 backdrop 自采样
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DMapGlassZoomPad(zoom = zoom, onZoomChange = { zoom = it })
            DMyLocationButton(locating = locating) { requestMyLocation() }
            DMapLocateButton(enabled = moved) {
                centerLat = lat
                centerLng = lng
                zoom = 16
            }
        }

        // 顶部浮层：返回 + 机构名与地址（液态玻璃浮在地图上，
        // 瓦片模式下能看见道路从玻璃下流过；SDK 模式退回实心卡片）
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 40dp 而不是 36dp：浮在地图上，太小的圆钮点击容错差，手指容易擦边点空
            DGlassSurface(
                modifier = Modifier.size(40.dp).clickable { nav.back() },
                shape = CircleShape,
                tint = DColor.Scrim.copy(alpha = 0.45f),
                blurRadius = 16f,
                cornerDp = 20.dp,
                surfaceAlpha = 0.55f,
                fallbackColor = DColor.Scrim,
                fallbackBorderColor = Color.Transparent,
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Back, Modifier.size(19.dp), DColor.White)
                }
            }
            Spacer(Modifier.width(10.dp))
            DGlassSurface(
                modifier = Modifier.weight(1f),
                shape = DShape.Md,
                // 信息栏要真的透出地图：只留 32% 主题底色 + 45% 玻璃，
                // 文字靠自适应柔光阴影保证可读
                tint = DColor.Bg.copy(alpha = 0.26f),
                blurRadius = 18f,
                cornerDp = 8.dp,
                surfaceAlpha = 0.42f,
                sheen = true,
                fallbackColor = DColor.Card,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        sitter.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DColor.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = dGlassTextShadow()),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        addrText,
                        fontSize = 11.sp,
                        color = DColor.Ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = dGlassTextShadow()),
                    )
                }
            }
        }

        // 底部浮层：地址 + 复制 + 导航（液态玻璃）
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding().padding(14.dp),
        ) {
            DGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = DShape.Lg,
                tint = DColor.Bg.copy(alpha = 0.26f),
                blurRadius = 18f,
                cornerDp = 12.dp,
                surfaceAlpha = 0.42f,
                sheen = true,
                fallbackColor = DColor.Card,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "机构地址",
                            fontSize = 11.sp,
                            color = DColor.Ink2,
                            style = TextStyle(shadow = dGlassTextShadow()),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            addrText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ink,
                            style = TextStyle(shadow = dGlassTextShadow()),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${D_mapCoord(lat)}, ${D_mapCoord(lng)} · GCJ-02",
                            fontSize = 10.5.sp,
                            color = DColor.Ink2,
                            style = TextStyle(shadow = dGlassTextShadow()),
                        )
                        Spacer(Modifier.height(3.dp))
                        // 有定位就报距离，没有就给一句引导——右侧那个蓝点按钮不写字，
                        // 不说一句用户未必知道它是干嘛的
                        Text(
                            when {
                                myDistance != null -> "距我 ${D_formatDistance(myDistance)}"
                                locating -> "正在定位…"
                                else -> "点右侧蓝点显示我的位置"
                            },
                            fontSize = 10.5.sp,
                            fontWeight = if (myDistance != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (myDistance != null) DColor.Brand else DColor.Ink3,
                            style = TextStyle(shadow = dGlassTextShadow()),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    DSmallButton("复制", {
                        val manager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        manager?.setPrimaryClip(ClipData.newPlainText("机构地址", addrText))
                        nav.toast("地址已复制")
                    })
                    Spacer(Modifier.width(6.dp))
                    DSmallButton("导航", {
                        if (!MapNavigation.navigate(context, lat, lng, sitter.name)) {
                            nav.toast("这台设备上没有可用的地图应用")
                        }
                    }, filled = true)
                }
            }
        }
    }
    }
}

/** 坐标保留 5 位小数（约 1 米精度），再多就是噪声。 */
private fun D_mapCoord(value: Double): String = "%.5f".format(value)

/** 距离文案：1 公里内报米，再远报公里，"0.4 公里"这种写法看不出量级。 */
private fun D_formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} 米" else "%.1f 公里".format(meters / 1000)

/** 与地图上蓝点同色，用户能把按钮和标记对上。 */
private val D_MyBlue = Color(0xFF1A73E8)

/**
 * 「我的位置」按钮：点了取一次定位，并把我和机构一起框进屏幕。
 *
 * 定位过程中置灰：重复点会并发注册两批位置监听，浪费电、也可能让相机来回跳。
 */
@Composable
private fun DMyLocationButton(locating: Boolean, onClick: () -> Unit) {
    val tint = if (locating) DColor.Ink3 else D_MyBlue
    DGlassSurface(
        modifier = Modifier.size(34.dp).clickable(enabled = !locating, onClick = onClick),
        shape = CircleShape,
        tint = DColor.Card.copy(alpha = 0.72f),
        blurRadius = 14f,
        cornerDp = 17.dp,
        surfaceAlpha = 0.55f,
        fallbackColor = DColor.Card,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {
            val r = size.minDimension / 2f
            drawCircle(tint.copy(alpha = 0.22f), radius = r)
            drawCircle(tint, radius = r * 0.56f)
            drawCircle(DColor.White, radius = r * 0.2f)
        }
        }
    }
}

/**
 * 「回到机构位置」按钮。
 *
 * 图标是自绘的准心：AppIcon 里没有定位类图标，而十字+圆环比随便借一个更像回事，
 * 也不用为了一个按钮往图标集里加路径。
 */
@Composable
private fun DMapLocateButton(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) DColor.Brand else DColor.Ink3
    DGlassSurface(
        modifier = Modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        tint = DColor.Card.copy(alpha = 0.72f),
        blurRadius = 14f,
        cornerDp = 17.dp,
        surfaceAlpha = 0.55f,
        fallbackColor = DColor.Card,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {
            val r = size.minDimension / 2f
            drawCircle(tint, radius = r * 0.62f, style = Stroke(width = 1.6f))
            drawCircle(tint, radius = r * 0.18f)
            // 四个方向各一小段，组成准心
            listOf(0.0, 90.0, 180.0, 270.0).forEach { deg ->
                val rad = deg * PI / 180.0
                val dirX = cos(rad).toFloat()
                val dirY = sin(rad).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(
                        center.x + r * 0.74f * dirX,
                        center.y + r * 0.74f * dirY,
                    ),
                    end = Offset(
                        center.x + r * 0.98f * dirX,
                        center.y + r * 0.98f * dirY,
                    ),
                    strokeWidth = 1.8f,
                )
            }
        }
        }
    }
}
