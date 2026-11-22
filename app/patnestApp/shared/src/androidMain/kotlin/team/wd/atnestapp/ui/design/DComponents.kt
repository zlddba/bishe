package team.wd.atnestapp.ui.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.app.MobileImage

/* ------------------------------------------------------------------ *
 * 屏骨架：状态栏 / 顶栏 / 可滚动内容 / 固定底栏 / 底部 Tab
 * ------------------------------------------------------------------ */

/**
 * 让状态栏图标临时切成浅色（白色），用于首页粉色 hero 顶到状态栏的场景；
 * 离开该页时自动还原成当前主题要求的颜色（浅色主题深图标 / 深色主题浅图标）。
 */
@Composable
fun DPaleStatusBar() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 宽屏下把内容限宽到 [DWindow.contentMaxWidth]，窄屏原样放行（顶栏/骨架共用）。 */
internal fun Modifier.dPageWidth(win: DWindow): Modifier =
    if (win.hasContentLimit) this.widthIn(max = win.contentMaxWidth) else this

@Composable
fun DBottomTabs(active: DTab, nav: DNav) {
    // 两套底栏：主人端「首页 / 找机构 / 寄养动态 / 我的」，
    // 机构端「工作台 / 订单 / 动态 / 我的」。同一个位置，两边是完全不同的页面。
    // 玻璃 Tab 栏悬浮在内容之上（骨架已为正文预留底部空间），内容从玻璃下滚过。
    val isSitter = DLocalStore.current.isSitterMode
    val tabs = DTab.entries.filter { it.sitter == isSitter }
    Box(Modifier.fillMaxWidth()) {
        DGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            // 与玻璃面相接的只有上缘：顶部两个圆角，下缘贴屏幕底
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            // 透明导航栏：只留 8% 底色 + 55% 玻璃，45% 的真实内容直接透出来；
            // 底部再叠一层很淡的渐变压住文字，避免透明后小字号发飘
            tint = DColor.Bg.copy(alpha = 0.08f),
            blurRadius = 22f,
            cornerDp = 18.dp,
            surfaceAlpha = 0.55f,
        ) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, DColor.Bg.copy(alpha = 0.30f)),
                    ),
                ),
            )
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(top = 8.dp),
            ) {
        tabs.forEach { tab ->
            val on = tab == active
            val icon = tab.dIcon
            val cardShape = RoundedCornerShape(14.dp)
            Column(
                Modifier.weight(1f)
                    .padding(horizontal = 3.dp)
                    // 当前页：浅色圆角透明玻璃卡；未选中保持透明
                    .then(
                        if (on) {
                            Modifier
                                .dGlassSurface(
                                    shape = cardShape,
                                    // 选中卡用主题品牌色，透明浅色：浅色下是淡粉玻璃，
                                    // 深色下自动变成深酒红玻璃，BrandInk 在两边都可读
                                    tint = DColor.Brand.copy(alpha = 0.20f),
                                    blurRadius = 14f,
                                    cornerDp = 14.dp,
                                    edgeDp = 6.dp,
                                    surfaceAlpha = 0.42f,
                                    fallbackColor = DColor.BrandSoft.copy(alpha = 0.55f),
                                )
                                .dGlassSheen(strength = 0.75f)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { nav.tab(tab) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 选中态用颜色 + 轻微放大过渡，切换 Tab 时不会「啪」地跳一下。
                val tint by animateColorAsState(
                    targetValue = if (on) DColor.BrandInk else DColor.Ink2,
                    animationSpec = tween(180),
                )
                val scale by animateFloatAsState(
                    targetValue = if (on) 1.08f else 1f,
                    animationSpec = tween(180),
                )
                AppIcon(
                    icon,
                    Modifier.size(21.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    tint,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    fontSize = 10.5.sp,
                    color = tint,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    // 选中项已有浅色玻璃卡托底，不再加阴影；未选中仍在透明栏上，保留轻阴影
                    style = if (on) {
                        TextStyle()
                    } else {
                        TextStyle(
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.38f), blurRadius = 6f),
                        )
                    },
                )
            }
        }
        }
    }
}
}

/**
 * Tab 图标。底部导航栏和左侧导航栏共用一份映射，
 * 否则改图标要改两处，迟早会漏一个。
 */
internal val DTab.dIcon: AppIcon
    get() = when (this) {
        DTab.Home, DTab.SitterHome -> AppIcon.Home
        DTab.Sitters -> AppIcon.Search
        DTab.Dynamics, DTab.SitterMoments -> AppIcon.Image
        DTab.Profile, DTab.SitterProfile -> AppIcon.User
        DTab.SitterOrders -> AppIcon.Orders
    }

/**
 * 宽屏左侧导航栏。与底部 [DBottomTabs] 是同一套 [DTab]（按身份过滤），只是形态不同：
 * 手机竖屏横向铺 4 个 Tab 刚好在拇指区，宽屏下铺开会把图标拉到离手指很远的位置，
 * 左栏则符合「导航在左、内容在右」的大屏习惯，也腾出了整块底部空间。
 *
 * [active] 为 null（二级页）时不高亮任何一项，但导航常驻：
 * 平板上从详情页直接跳去别的 Tab 是常见操作，不该逼用户先返回。
 */
@Composable
fun DNavigationRail(active: DTab?, nav: DNav) {
    val win = LocalDWindow.current
    val isSitter = DLocalStore.current.isSitterMode
    val tabs = DTab.entries.filter { it.sitter == isSitter }
    // 宽屏左栏也走环境玻璃：整条栏只采窗口底层的品牌光斑，不采页面内容，
    // 所以不会因为页面滚动而闪烁，也没有记录层反馈风险。
    Row(
        Modifier.width(win.railWidth).fillMaxHeight()
            .dGlassSurface(
                shape = RectangleShape,
                // 透明导航栏：只留一层薄玻璃，露出页面底色
                tint = DColor.Card.copy(alpha = 0.16f),
                blurRadius = 20f,
                cornerDp = 0.dp,
                surfaceAlpha = 0.60f,
            ),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部品牌位：没有它，第一项 Tab 会紧贴状态栏，看起来像被裁掉一截
            Box(
                Modifier.size(38.dp).clip(DShape.Lg).background(DColor.BrandSoft),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Paw, Modifier.size(21.dp), DColor.BrandInk)
            }
            Spacer(Modifier.height(18.dp))
            tabs.forEach { tab ->
                DNavRailItem(tab, tab == active) { nav.tab(tab) }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        // 白底栏与浅灰页面底之间需要一条边界，否则两列会糊在一起
        Box(Modifier.width(1.dp).fillMaxHeight().background(DColor.Line))
    }
}

@Composable
private fun DNavRailItem(tab: DTab, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (selected) DColor.BrandInk else DColor.Ink3,
        animationSpec = tween(180),
    )
    // 选中项做成品牌色小玻璃片；未选中保持透明，不额外增加层
    val glass = selected && isGlassSupported && LocalDGlassAmbient.current != null
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            .then(
                if (glass) {
                    Modifier.dGlassSurface(
                        shape = DShape.Lg,
                        tint = DColor.BrandSoft.copy(alpha = 0.78f),
                        blurRadius = 12f,
                        cornerDp = 8.dp,
                        edgeDp = 5.dp,
                        fallbackColor = DColor.BrandSoft,
                    )
                } else {
                    Modifier.background(if (selected) DColor.BrandSoft else Color.Transparent)
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(tab.dIcon, Modifier.size(22.dp), tint)
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            fontSize = 11.sp,
            // 选中态图标保留品牌色，文字改用 Ink：BrandInk 压在 BrandSoft 上只有约 3.9:1
            color = if (selected) DColor.Ink else tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/* ------------------------------------------------------------------ *
 * 基础块
 * ------------------------------------------------------------------ */

/** 页面左右安全边距。窄屏是设计稿的 18dp，宽屏逐档放大到 24 / 32dp。 */
@Composable
fun DPad(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = LocalDWindow.current.gutter),
        content = content,
    )
}

@Composable
fun DCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 内容卡片统一液态玻璃：默认采样窗口底层的环境光斑（不是页面记录层），
    // 因此可以安全地放在滚动内容里。API < 33 或无环境记录时退回 Miuix 实体卡。
    val glass = isGlassSupported && LocalDGlassAmbient.current != null
    if (glass) {
        Box(
            modifier
                .fillMaxWidth()
                .dGlassSurface(
                    shape = DShape.Lg,
                    // 浅粉底上卡片要显白，但不能回到实心卡：68% 白底 + 反光
                    tint = DColor.Card.copy(alpha = 0.68f),
                    blurRadius = 16f,
                    cornerDp = 12.dp,
                )
                // 卡片统一反光：左上 bloom + 斜向光带 + 顶部亮边
                .dGlassSheen(strength = 0.95f)
                .border(1.dp, DColor.White.copy(alpha = 0.20f), DShape.Lg),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(padding),
                content = content,
            )
        }
    } else {
        // 底层已换 Miuix Card（HyperOS 卡片观感）：调用点签名不变（padding → insideMargin）。
        // Miuix Card 无描边参数，1dp 描边保留在 modifier 上。
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, DColor.Line, DShape.Lg),
            cornerRadius = 12.dp,
            insideMargin = PaddingValues(padding),
            colors = CardDefaults.defaultColors(color = DColor.Card, contentColor = DColor.Ink),
            content = content,
        )
    }
}

@Composable
fun DSection(text: String, more: String? = null, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = LocalDWindow.current.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
        Spacer(Modifier.weight(1f))
        if (more != null) {
            Text(
                more,
                fontSize = 12.sp,
                color = DColor.Ink3,
                modifier = Modifier.clickable(enabled = onMore != null) { onMore?.invoke() },
            )
        }
    }
}

@Composable
fun DDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DColor.Line))
}

/**
 * 首页 / 个人中心的粉色头部。
 * insetTop = true 时渐变会一直铺到状态栏下面（沉浸式），文字内容自动让开状态栏。
 *
 * @param trailing 标题行右侧的插图位（首页放通知铃铛）；标题占满剩余宽度，
 *   所以插图始终贴在标题行的最右边，不会把标题挤窄。
 */
@Composable
fun DHero(
    title: String,
    sub: String,
    insetTop: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            // 环境玻璃 + 68% 品牌渐变：hero 在滚动内容里，只能采环境层
            .dGlassHero()
            .then(if (insetTop) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = LocalDWindow.current.gutter, vertical = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DColor.White,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
        Spacer(Modifier.height(5.dp))
        Text(sub, fontSize = 12.5.sp, color = DColor.White.copy(alpha = 0.9f))
        if (content != null) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/* ------------------------------------------------------------------ *
 * 标签 / 按钮 / 选择
 * ------------------------------------------------------------------ */

enum class DTagTone { Brand, Ok, Warn, Line, Info, Danger }

@Composable
fun DTag(text: String, tone: DTagTone = DTagTone.Brand) {
    val (bg, _) = when (tone) {
        DTagTone.Brand -> DColor.BrandSoft to DColor.BrandInk
        DTagTone.Ok -> DColor.OkSoft to DColor.Ok
        DTagTone.Warn -> DColor.WarnSoft to DColor.Warn
        DTagTone.Line -> DColor.NeutralSoft to DColor.Ink2
        DTagTone.Info -> DColor.InfoSoft to DColor.Info
        DTagTone.Danger -> DColor.DangerSoft to DColor.Danger
    }
    Box(
        Modifier
            .dGlassSurface(
                shape = DShape.Pill,
                tint = bg.copy(alpha = 0.72f),
                blurRadius = 8f,
                // tag 高度约 20dp，SDF 半径取一半，避免半径大于半高
                cornerDp = 10.dp,
                edgeDp = 4.dp,
                fallbackColor = bg,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        // 小字号 tag 不再用低对比的 tone 前景色：底色已经表达语义，
        // 文字统一用 Ink，浅色/深色下都能读清
        Text(text, fontSize = 10.5.sp, color = DColor.Ink)
    }
}

enum class DButtonStyle { Primary, Ghost, Outline }

@Composable
fun DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    style: DButtonStyle = DButtonStyle.Primary,
    height: Dp = 44.dp,
    enabled: Boolean = true,
) {
    val (bg, fg, border) = when (style) {
        DButtonStyle.Primary -> Triple(DColor.Brand, DColor.White, DColor.Brand)
        DButtonStyle.Ghost -> Triple(DColor.BrandSoft, DColor.Ink, DColor.BrandSoft)
        DButtonStyle.Outline -> Triple(DColor.Card, DColor.Ink2, DColor.Line2)
    }
    // Ghost / Outline 做玻璃底；Primary 保持品牌实色，不抢主操作
    val glass = enabled && style != DButtonStyle.Primary &&
        isGlassSupported && LocalDGlassAmbient.current != null
    Box(
        modifier
            .height(height)
            .clip(DShape.Md)
            .then(
                if (glass) {
                    Modifier.dGlassSurface(
                        shape = DShape.Md,
                        tint = if (style == DButtonStyle.Ghost) {
                            DColor.BrandSoft.copy(alpha = 0.72f)
                        } else {
                            DColor.Card.copy(alpha = 0.72f)
                        },
                        blurRadius = 14f,
                        cornerDp = 8.dp,
                        fallbackColor = bg,
                    )
                } else {
                    Modifier.background(if (enabled) bg else DColor.Line2)
                },
            )
            .border(1.dp, if (glass) DColor.White.copy(alpha = 0.20f) else border, DShape.Md)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) fg else DColor.Ink3,
        )
    }
}

/** 行内小按钮（编辑 / 删除 / 去支付等）。 */
@Composable
fun DSmallButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    val glass = !filled && isGlassSupported && LocalDGlassAmbient.current != null
    Box(
        Modifier
            .clip(DShape.Pill)
            .then(
                if (glass) {
                    Modifier.dGlassSurface(
                        shape = DShape.Pill,
                        tint = DColor.Card.copy(alpha = 0.72f),
                        blurRadius = 12f,
                        cornerDp = 20.dp,
                    )
                } else {
                    Modifier.background(if (filled) DColor.Brand else DColor.Card)
                },
            )
            .border(
                1.dp,
                when {
                    filled -> DColor.Brand
                    glass -> DColor.White.copy(alpha = 0.18f)
                    else -> DColor.Line2
                },
                DShape.Pill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            color = if (filled) DColor.White else DColor.Ink2,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun DChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 未选中的 chip 也做成小玻璃片；选中态保持实色品牌填充，保证白字对比度
    val glass = !selected && isGlassSupported && LocalDGlassAmbient.current != null
    Box(
        Modifier
            .clip(DShape.Pill)
            // 选中态是「填充」，用 BrandStrong 而不是 BrandInk：深色主题下 BrandInk 会提亮成浅粉，
            // 再配白字就看不清了，填充色必须自己保持深一档。
            .then(
                if (glass) {
                    Modifier.dGlassSurface(
                        shape = DShape.Pill,
                        tint = DColor.Card.copy(alpha = 0.72f),
                        blurRadius = 12f,
                        cornerDp = 20.dp,
                    )
                } else {
                    Modifier.background(if (selected) DColor.BrandStrong else DColor.Card)
                },
            )
            .border(
                1.dp,
                when {
                    selected -> DColor.BrandStrong
                    glass -> DColor.White.copy(alpha = 0.18f)
                    else -> DColor.Line
                },
                DShape.Pill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = if (selected) DColor.White else DColor.Ink2)
    }
}

@Composable
fun DTabLine(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = LocalDWindow.current.gutter)) {
        items.forEachIndexed { i, text ->
            Column(
                Modifier.padding(end = 20.dp).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text,
                    fontSize = 13.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == selected) DColor.BrandInk else DColor.Ink2,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.width(24.dp).height(3.dp).clip(DShape.Pill)
                        .background(if (i == selected) DColor.Brand else Color.Transparent),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 表单 / 列表行
 * ------------------------------------------------------------------ */

@Composable
fun DField(label: String, value: String, hint: String = "", onClick: (() -> Unit)? = null) {
    val glass = isGlassSupported && LocalDGlassAmbient.current != null
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth()
                .dGlassSurface(
                    shape = DShape.Md,
                    tint = DColor.Card.copy(alpha = 0.75f),
                    blurRadius = 14f,
                    cornerDp = 8.dp,
                )
                .border(1.dp, if (glass) DColor.White.copy(alpha = 0.18f) else DColor.Line, DShape.Md)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                value.ifBlank { hint },
                fontSize = 14.sp,
                color = if (value.isBlank()) DColor.Ink2 else DColor.Ink,
            )
        }
    }
}

@Composable
fun DKeyValue(label: String, value: String, valueColor: Color = DColor.Ink) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, color = DColor.Ink2)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DLabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 12.5.sp, color = DColor.Ink2, modifier = Modifier.width(88.dp))
        Text(value, fontSize = 12.5.sp, color = DColor.Ink)
    }
}

@Composable
fun DMenuRow(
    text: String,
    value: String = "",
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.5.sp, color = DColor.Ink)
        Spacer(Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, fontSize = 11.5.sp, color = DColor.Ink3)
        Spacer(Modifier.width(6.dp))
        Text("›", fontSize = 16.sp, color = DColor.Ink3)
    }
}

@Composable
fun DInfoRow(text: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.5.sp, color = DColor.Ink)
        Spacer(Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, fontSize = 11.5.sp, color = DColor.Ink3)
    }
}

@Composable
fun DOptionRow(
    title: String,
    sub: String = "",
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .dGlassSurface(
                shape = DShape.Md,
                tint = if (selected) DColor.BrandSoft.copy(alpha = 0.80f) else DColor.Card.copy(alpha = 0.74f),
                blurRadius = 12f,
                cornerDp = 8.dp,
                edgeDp = 6.dp,
                fallbackColor = if (selected) DColor.BrandSoft else DColor.Card,
            )
            .border(1.5.dp, if (selected) DColor.Brand else DColor.Line, DShape.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .border(2.dp, if (selected) DColor.Brand else DColor.Line2, CircleShape)
                .background(if (selected) DColor.Brand else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DColor.Ink)
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 11.sp, color = DColor.Ink3)
            }
        }
    }
}

@Composable
fun DStepHeader(no: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LocalDWindow.current.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(DColor.Brand),
            contentAlignment = Alignment.Center,
        ) {
            Text(no.toString(), fontSize = 12.sp, color = DColor.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.5.sp, color = DColor.Ink2)
    }
}

@Composable
fun DFeeLine(label: String, value: String, total: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = if (total) 13.sp else 12.sp,
            color = DColor.BrandInk,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = if (total) 15.sp else 12.sp,
            color = DColor.BrandInk,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/* ------------------------------------------------------------------ *
 * 展示块：星级 / 头像 / 封面 / 图片 / 时间线 / 空状态
 * ------------------------------------------------------------------ */

@Composable
fun DStars(rating: Double, size: androidx.compose.ui.unit.TextUnit = 12.sp) {
    val full = rating.toInt().coerceIn(0, 5)
    Text(
        "★".repeat(full) + "☆".repeat(5 - full),
        fontSize = size,
        color = DColor.Star,
    )
}

@Composable
fun DAvatar(text: String, size: Dp = 40.dp, rounded: Boolean = true) {
    Box(
        Modifier.size(size).clip(if (rounded) DShape.Md else CircleShape)
            .background(DColor.CoverGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DColor.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DCover(text: String, width: Dp = 86.dp, height: Dp = 86.dp) {
    Box(
        Modifier.width(width).height(height).clip(DShape.Md).background(DColor.CoverGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DColor.White, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun DImageBlock(text: String, height: Dp = 130.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(height).clip(DShape.Md).background(DColor.ImageGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DColor.White, fontSize = 12.sp)
    }
}

enum class DNodeState { Done, Current, Todo }

@Composable
fun DTimelineNode(
    title: String,
    sub: String = "",
    state: DNodeState = DNodeState.Done,
    last: Boolean = false,
) {
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(12.dp).clip(CircleShape).background(
                    when (state) {
                        DNodeState.Done -> DColor.Ok
                        DNodeState.Current -> DColor.Brand
                        DNodeState.Todo -> DColor.Line2
                    },
                ),
            )
            if (!last) Box(Modifier.width(1.5.dp).height(38.dp).background(DColor.Line2))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (state == DNodeState.Todo) DColor.Ink3 else DColor.Ink,
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(sub, fontSize = 11.sp, color = DColor.Ink3)
            }
        }
    }
}

@Composable
fun DEmpty(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    // 空状态本身也做成一块薄玻璃面板，而不是只留一个图标圆
    DGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = DShape.Lg,
        tint = DColor.Card.copy(alpha = 0.55f),
        blurRadius = 18f,
        cornerDp = 12.dp,
        edgeDp = 10.dp,
        sheen = true,
        // 空状态在页面内容层里，必须采环境层；默认会采页面记录层而自采样
        backdrop = LocalDGlassAmbient.current,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp)
                    .dGlassSurface(
                        shape = CircleShape,
                        tint = DColor.BrandSoft.copy(alpha = 0.70f),
                        blurRadius = 16f,
                        cornerDp = 32.dp,
                        fallbackColor = DColor.BrandSoft,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Paw, Modifier.size(30.dp), DColor.BrandInk)
            }
            Spacer(Modifier.height(12.dp))
            Text(text, fontSize = 13.sp, color = DColor.Ink3)
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                DSmallButton(action, { onAction?.invoke() }, filled = true)
            }
        }
    }
}

@Composable
fun DNotice(text: String) {
    val glass = isGlassSupported && LocalDGlassAmbient.current != null
    Box(
        Modifier.fillMaxWidth()
            .dGlassSurface(
                shape = DShape.Md,
                tint = DColor.BrandSoft.copy(alpha = 0.78f),
                blurRadius = 14f,
                cornerDp = 8.dp,
                fallbackColor = DColor.BrandSoft,
            )
            .border(1.dp, if (glass) DColor.White.copy(alpha = 0.16f) else Color.Transparent, DShape.Md)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 12.sp, color = DColor.Ink)
    }
}

@Composable
fun DSearchBar(hint: String, onClick: () -> Unit = {}) {
    val glass = isGlassSupported && LocalDGlassAmbient.current != null
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = LocalDWindow.current.gutter, vertical = 8.dp)
            .dGlassSurface(
                shape = DShape.Md,
                tint = DColor.Card.copy(alpha = 0.75f),
                blurRadius = 14f,
                cornerDp = 8.dp,
            )
            .border(1.dp, if (glass) DColor.White.copy(alpha = 0.18f) else DColor.Line, DShape.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcon.Search, Modifier.size(17.dp), DColor.Ink2)
        Spacer(Modifier.width(8.dp))
        Text(hint, fontSize = 13.sp, color = DColor.Ink2)
    }
}

/**
 * 设计稿里常见的「底部结算栏」——玻璃化后的悬浮胶囊条。
 *
 * 悬浮在骨架内容之上（骨架已为正文预留底部空间），内容从玻璃下滚过；
 * 玻璃背景取 LocalDGlassBackdrop 记录的页面内容，Android 13 以下退化实体卡。
 */
@Composable
fun DFixBar(amountLabel: String, amount: String, button: String, onAction: () -> Unit) {
    val gutter = LocalDWindow.current.gutter
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = gutter, vertical = 10.dp)
            .navigationBarsPadding(),
    ) {
        DGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = DShape.Lg,
            // 结算栏也保持通透：30% 主题底 + 45% 玻璃，金额文字靠柔光阴影保读
            tint = DColor.Bg.copy(alpha = 0.30f),
            blurRadius = 20f,
            surfaceAlpha = 0.45f,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        amountLabel,
                        fontSize = 11.5.sp,
                        color = DColor.Ink2,
                        style = TextStyle(shadow = dGlassTextShadow()),
                    )
                    Text(
                        amount,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = DColor.BrandInk,
                        style = TextStyle(shadow = dGlassTextShadow()),
                    )
                }
                Spacer(Modifier.width(14.dp))
                DButton(button, onAction, modifier = Modifier.weight(1f), height = 42.dp)
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 联网版新增：真实输入框、网络图片、加载 / 空 / 错误态
 * ------------------------------------------------------------------ */



/** 机构封面：有真实 URL 就加载，没有就退回设计稿的渐变占位。 */
@Composable
fun DCoverImage(url: String, fallback: String, width: Dp = 86.dp, height: Dp = 86.dp) {
    if (url.isNotBlank()) {
        MobileImage(
            url = url,
            modifier = Modifier.width(width).height(height),
            shape = DShape.Md,
            placeholderSize = 20.dp,
        )
    } else {
        DCover(fallback, width, height)
    }
}

/** 详情页大图：同样按 URL 有无切换。 */
@Composable
fun DImageUrl(url: String, fallback: String, height: Dp = 130.dp, modifier: Modifier = Modifier) {
    if (url.isNotBlank()) {
        MobileImage(
            url = url,
            modifier = modifier.fillMaxWidth().height(height),
            shape = DShape.Md,
            placeholderSize = 26.dp,
        )
    } else {
        DImageBlock(fallback, height, modifier)
    }
}

/** 统一加载态：不占位整屏，只在内容区给出轻提示。 */
@Composable
fun DLoading(text: String = "加载中…") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .dGlassSurface(
                    shape = DShape.Pill,
                    tint = DColor.Card.copy(alpha = 0.75f),
                    blurRadius = 14f,
                    cornerDp = 20.dp,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(text, fontSize = 12.5.sp, color = DColor.Ink2)
        }
    }
}

/** 统一错误态：带重试按钮，避免接口挂了页面一片空白。 */
@Composable
fun DErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth()
            .dGlassSurface(
                shape = DShape.Lg,
                tint = DColor.Card.copy(alpha = 0.78f),
                blurRadius = 16f,
                cornerDp = 12.dp,
                edgeDp = 8.dp,
            )
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = 12.5.sp, color = DColor.Ink2)
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                DSmallButton("重试", onRetry)
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 下拉选择：多个选项点开菜单挑一个
 * ------------------------------------------------------------------ */

/**
 * 点触发器弹出菜单，选中后菜单自己收起。
 *
 * 这里刻意不做「点一下换成下一个值」：带着 ▾ 却不弹菜单是个伪下拉，
 * 选项一多没人猜得到下一个是谁，也回不去上一个。
 *
 * 用 Popup 而不是 Material 的 DropdownMenu —— 后者容器底色、圆角、内边距全跟着
 * Material 主题走，会在自绘皮肤里露出一块不协调的浅灰。focusable = true 保证
 * 点空白处和按返回键都能关掉菜单。
 *
 * [trigger] 拿到的是「菜单是否展开」，用来把箭头翻过来。
 */
@Composable
fun DDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trigger: @Composable (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0) }
    var anchorHeight by remember { mutableStateOf(0) }
    var anchorBottom by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val view = LocalView.current

    Box(modifier) {
        Box(
            Modifier
                .onGloballyPositioned {
                    anchorWidth = it.size.width
                    anchorHeight = it.size.height
                    // Compose 1.13 起 LayoutCoordinates 不再有 positionInWindow()，
                    // 用 localToWindow(原点) 拿同一个窗口坐标
                    anchorBottom = it.localToWindow(Offset.Zero).y.toInt() + it.size.height
                }
                .clickable(enabled = enabled) { open = true },
        ) {
            trigger(open)
        }

        if (open && enabled && anchorWidth > 0) {
            val gap = with(density) { 6.dp.roundToPx() }
            // 估算菜单高度（一行 46dp，封顶 292dp）：下方放不下就往上翻，
            // 免得列表底部的选择器弹出一个被屏幕裁掉一半的菜单。
            val rowPx = with(density) { 46.dp.roundToPx() }
            val maxPx = with(density) { 292.dp.roundToPx() }
            val menuPx = minOf(options.size * rowPx, maxPx)
            val fitsBelow = anchorBottom + gap + menuPx <= view.height
            val anchorDp = with(density) { anchorWidth.toDp() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, if (fitsBelow) anchorHeight + gap else -(menuPx + gap)),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .widthIn(min = if (anchorDp < 132.dp) 132.dp else anchorDp)
                        .heightIn(max = 292.dp)
                        .clip(DShape.Md)
                        // Popup 是独立窗口，拿不到父页面的 backdrop，做不了真实模糊；
                        // 用「高不透明白 + 品牌柔光 + 亮描边」模拟玻璃层，不误导为实心卡
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    DColor.Card.copy(alpha = 0.94f),
                                    DColor.Card.copy(alpha = 0.88f),
                                    DColor.BrandSoft.copy(alpha = 0.72f),
                                ),
                            ),
                        )
                        .border(1.dp, DColor.White.copy(alpha = 0.26f), DShape.Md)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    options.forEachIndexed { index, text ->
                        val selected = index == selectedIndex
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    open = false
                                    if (!selected) onSelect(index)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) DColor.BrandInk else DColor.Ink,
                            )
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                AppIcon(AppIcon.Check, Modifier.size(14.dp), DColor.Brand)
                            }
                        }
                    }
                }
            }
        }
    }
}
