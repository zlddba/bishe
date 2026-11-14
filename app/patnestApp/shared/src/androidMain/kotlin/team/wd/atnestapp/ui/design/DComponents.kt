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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.PullToRefresh
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.app.MobileImage

/* ------------------------------------------------------------------ *
 * 屏骨架：状态栏 / 顶栏 / 可滚动内容 / 固定底栏 / 底部 Tab
 * ------------------------------------------------------------------ */

@Composable
fun DFrame(
    nav: DNav,
    title: String? = null,
    activeTab: DTab? = null,
    backable: Boolean = true,
    // true = 内容自带沉浸头部（首页 hero），DFrame 不再补状态栏高度。
    immersiveTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    // 下拉刷新（Android）：传 onRefresh 即启用，refreshing 传当前加载标志
    refreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val win = LocalDWindow.current
    Column(Modifier.fillMaxSize().background(DColor.Bg)) {
        if (title != null) {
            DTopBar(title = title, nav = nav, backable = backable, actions = actions)
        } else if (!immersiveTop) {
            // 无顶栏的页面（登录、找机构、支付成功等）自己也要让开状态栏，
            // 否则第一行内容会被状态栏压住。
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }
        // 宽屏把正文限制在可读宽度并居中。手机下 dPageWidth 是空操作，
        // 版式与改造前逐像素一致。
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // 进场动效交给 NavHost 的转场统一处理，这里不再给整屏内容加 alpha 动画：
            // 整屏 alpha < 1 会让这一页整体离屏合成（整块 RenderNode 每帧重绘），
            // 页面里再有封面图时掉帧非常明显。转场本身已经足够顺，不需要二次淡入。
            //
            // 没有底栏 / Tab 的页面，滚动内容要自己让开系统导航条。
            val needBottomInset = bottomBar == null && activeTab == null
            val colModifier = Modifier
                .dPageWidth(win)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 清单声明 adjustResize + EdgeToEdge 时，键盘高度不会自动变成内容留白，
                // 不加 imePadding 页尾输入框会被键盘直接盖住
                .imePadding()
                .then(if (needBottomInset) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = 14.dp)
            if (onRefresh != null) {
                // Miuix 的下拉刷新：指示器的拉出、驻留、收回全部由组件按 isRefreshing
                // 状态驱动，刷新结束 isRefreshing=false 即走完成动画收回；即使刷新请求
                // 被忽略（store 的「加载中直接 return」守卫）或极速完成，也不会卡住。
                // contentAlignment 虽是 Miuix 组件没有的参数，但该容器是全宽的——
                // 正文限宽居中靠内容自己的布局完成，这里无需额外处理。
                PullToRefresh(
                    isRefreshing = refreshing == true,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    refreshTexts = listOf("下拉刷新", "松手刷新", "正在刷新…", "刷新成功"),
                ) {
                    Column(colModifier, content = content)
                }
            } else {
                Column(colModifier, content = content)
            }
        }
        if (bottomBar != null) {
            // 底部结算栏同样限宽：否则宽屏下按钮会拉成一米长，和居中的正文脱节。
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.dPageWidth(win)) { bottomBar() }
            }
        }
        // 宽屏的 Tab 由左侧常驻导航栏承担（见 DesignApp），底部不再重复画一条。
        if (activeTab != null && win.isCompact) DBottomTabs(activeTab, nav)
    }
}

/** 正文限宽：只在 Medium / Expanded 生效，手机返回原 Modifier。 */
internal fun Modifier.dPageWidth(win: DWindow): Modifier =
    if (win.hasContentLimit) this.widthIn(max = win.contentMaxWidth) else this

/**
 * 与 [DFrame] 同构，但正文用 LazyColumn。
 *
 * 机构 / 订单这类一次可能返回几十条的页面走这个骨架：LazyColumn 只组合可见项，
 * 首屏不用一次建满 50 张卡片，内存和首帧耗时都会明显下降。
 * 用法差别只有一处：正文是 [LazyListScope]，所以每块内容要自己包 `item { }`。
 */
@Composable
fun DLazyFrame(
    nav: DNav,
    title: String? = null,
    activeTab: DTab? = null,
    backable: Boolean = true,
    immersiveTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    // 下拉刷新（Android）：传 onRefresh 即启用，refreshing 传当前加载标志
    refreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val win = LocalDWindow.current
    Column(Modifier.fillMaxSize().background(DColor.Bg)) {
        if (title != null) {
            DTopBar(title = title, nav = nav, backable = backable, actions = actions)
        } else if (!immersiveTop) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val needBottomInset = bottomBar == null && activeTab == null
            val scrollModifier = Modifier
                .dPageWidth(win)
                .fillMaxSize()
                .imePadding()
                .then(if (needBottomInset) Modifier.navigationBarsPadding() else Modifier)
            if (onRefresh != null) {
                PullToRefresh(
                    isRefreshing = refreshing == true,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    refreshTexts = listOf("下拉刷新", "松手刷新", "正在刷新…", "刷新成功"),
                ) {
                    LazyColumn(
                        scrollModifier,
                        contentPadding = PaddingValues(bottom = 14.dp),
                        content = content,
                    )
                }
            } else {
                LazyColumn(
                    scrollModifier,
                    contentPadding = PaddingValues(bottom = 14.dp),
                    content = content,
                )
            }
        }
        if (bottomBar != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.dPageWidth(win)) { bottomBar() }
            }
        }
        if (activeTab != null && win.isCompact) DBottomTabs(activeTab, nav)
    }
}

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

@Composable
fun DTopBar(
    title: String,
    nav: DNav,
    backable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val win = LocalDWindow.current
    // 外层只负责让开状态栏，内层 Row 与正文共用同一套限宽和边距：
    // 宽屏下标题才不会贴在左边、正文却居中。
    Box(
        Modifier.fillMaxWidth().statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            // statusBarsPadding 由外层处理，这里只管 48dp 的内容高度，
            // 顶栏总占位仍是「状态栏 + 48dp」，与改造前一致。
            Modifier.dPageWidth(win).fillMaxWidth().height(48.dp)
                .padding(horizontal = win.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (backable) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable { nav.back() },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Back, Modifier.size(18.dp), DColor.Ink)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

@Composable
fun DBottomTabs(active: DTab, nav: DNav) {
    // 两套底栏：主人端「首页 / 找机构 / 寄养动态 / 我的」，
    // 机构端「工作台 / 订单 / 动态 / 我的」。同一个位置，两边是完全不同的页面。
    val isSitter = DLocalStore.current.isSitterMode
    val tabs = DTab.entries.filter { it.sitter == isSitter }
    Row(
        // 白底一直铺到屏幕最底部（手势条下面），按钮内容再用 navigationBarsPadding 顶上来。
        Modifier.fillMaxWidth().background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Sm)
            .navigationBarsPadding()
            .height(64.dp)
            .padding(top = 8.dp),
    ) {
        tabs.forEach { tab ->
            val on = tab == active
            val icon = tab.dIcon
            Column(
                Modifier.weight(1f).clickable { nav.tab(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 选中态用颜色 + 轻微放大过渡，切换 Tab 时不会「啪」地跳一下。
                val tint by animateColorAsState(
                    targetValue = if (on) DColor.BrandInk else DColor.Ink3,
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
                )
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
    Row(Modifier.width(win.railWidth).fillMaxHeight().background(DColor.Card)) {
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
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            .clip(DShape.Lg)
            .background(if (selected) DColor.BrandSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(tab.dIcon, Modifier.size(22.dp), tint)
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            fontSize = 11.sp,
            color = tint,
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
    Column(
        modifier
            .fillMaxWidth()
            .clip(DShape.Lg)
            .background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Lg)
            .padding(padding),
        content = content,
    )
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
        Modifier.fillMaxWidth().background(DColor.HeroGradient)
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
    val (bg, fg) = when (tone) {
        DTagTone.Brand -> DColor.BrandSoft to DColor.BrandInk
        DTagTone.Ok -> DColor.OkSoft to DColor.Ok
        DTagTone.Warn -> DColor.WarnSoft to DColor.Warn
        DTagTone.Line -> DColor.NeutralSoft to DColor.Ink2
        DTagTone.Info -> DColor.InfoSoft to DColor.Info
        DTagTone.Danger -> DColor.DangerSoft to DColor.Danger
    }
    Box(
        Modifier.clip(DShape.Pill).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.5.sp, color = fg)
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
        DButtonStyle.Ghost -> Triple(DColor.BrandSoft, DColor.BrandInk, DColor.BrandSoft)
        DButtonStyle.Outline -> Triple(DColor.Card, DColor.Ink2, DColor.Line2)
    }
    Box(
        modifier
            .height(height)
            .clip(DShape.Md)
            .background(if (enabled) bg else DColor.Line2)
            .border(1.dp, border, DShape.Md)
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
    Box(
        Modifier
            .clip(DShape.Pill)
            .background(if (filled) DColor.Brand else DColor.Card)
            .border(1.dp, if (filled) DColor.Brand else DColor.Line2, DShape.Pill)
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
    Box(
        Modifier
            .clip(DShape.Pill)
            // 选中态是「填充」，用 BrandStrong 而不是 BrandInk：深色主题下 BrandInk 会提亮成浅粉，
            // 再配白字就看不清了，填充色必须自己保持深一档。
            .background(if (selected) DColor.BrandStrong else DColor.Card)
            .border(1.dp, if (selected) DColor.BrandStrong else DColor.Line, DShape.Pill)
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
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.Card)
                .border(1.dp, DColor.Line, DShape.Md)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                value.ifBlank { hint },
                fontSize = 14.sp,
                color = if (value.isBlank()) DColor.Ink3 else DColor.Ink,
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
            .clip(DShape.Md)
            .background(if (selected) DColor.BrandSoft else DColor.Card)
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
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(DColor.BrandSoft),
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

@Composable
fun DNotice(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.BrandSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 12.sp, color = DColor.BrandInk)
    }
}

@Composable
fun DSearchBar(hint: String, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = LocalDWindow.current.gutter, vertical = 8.dp)
            .clip(DShape.Md).background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcon.Search, Modifier.size(17.dp), DColor.Ink3)
        Spacer(Modifier.width(8.dp))
        Text(hint, fontSize = 13.sp, color = DColor.Ink3)
    }
}

/** 设计稿里常见的「底部结算栏」。 */
@Composable
fun DFixBar(amountLabel: String, amount: String, button: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Sm)
            .navigationBarsPadding()
            .padding(horizontal = LocalDWindow.current.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(amountLabel, fontSize = 11.5.sp, color = DColor.Ink3)
            Text(amount, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DColor.BrandInk)
        }
        Spacer(Modifier.width(14.dp))
        DButton(button, onAction, modifier = Modifier.weight(1f), height = 42.dp)
    }
}

/* ------------------------------------------------------------------ *
 * 联网版新增：真实输入框、网络图片、加载 / 空 / 错误态
 * ------------------------------------------------------------------ */

/**
 * 可编辑输入框（设计层样式）。
 *
 * 用新版状态式 BasicTextField(state: TextFieldState, ...)，对外仍是 value / onValueChange：
 * 外部值变化同步进内部 state，内部输入再经 snapshotFlow 回传。
 * secret 走官方 BasicSecureTextField（系统级混淆渲染）——不要用 OutputTransformation
 * 全量打码：退格时删除区域会被整段掩码映射错，一次删除就清空整框（登录页 bug）。
 */
@Composable
fun DFieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    secret: Boolean = false,
    multiline: Boolean = false,
    enabled: Boolean = true,
    onSubmit: (() -> Unit)? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = value)
    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.edit { replace(0, length, value) }
        }
    }
    // 回调用 rememberUpdatedState 取最新值：否则调用方每次重组换了 lambda 身份，
    // 这个收集协程就要重启一次，输入过程中会白跑很多次。
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(Unit) {
        snapshotFlow { state.text.toString() }.collect { currentOnValueChange(it) }
    }
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        if (secret) {
            // 密码框走官方 BasicSecureTextField（系统级混淆渲染）——不要用
            // OutputTransformation 全量打码：删除字符时 transformed 坐标系的删除
            // 区域会被整段掩码映射错，一次退格就清空整框（登录页 v2.0.6 前 bug）。
            BasicSecureTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = TextStyle(color = DColor.Ink, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
                ),
                onKeyboardAction = onSubmit?.let { submit ->
                    KeyboardActionHandler { performDefaultAction ->
                        submit()
                        performDefaultAction()
                    }
                },
                cursorBrush = SolidColor(DColor.Brand),
                textObfuscationMode = if (reveal) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                decorator = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .clip(DShape.Md)
                            .background(DColor.Card)
                            .border(1.dp, DColor.Line, DShape.Md)
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                                Text(placeholder, fontSize = 14.sp, color = DColor.Ink3)
                            }
                            inner()
                        }
                        Text(
                            if (reveal) "隐藏" else "显示",
                            fontSize = 12.sp,
                            color = DColor.BrandInk,
                            modifier = Modifier.clickable { reveal = !reveal }.padding(start = 8.dp),
                        )
                    }
                },
            )
        } else {
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = TextStyle(color = DColor.Ink, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
                ),
                onKeyboardAction = onSubmit?.let { submit ->
                    KeyboardActionHandler { performDefaultAction ->
                        submit()
                        performDefaultAction()
                    }
                },
                lineLimits = if (multiline) {
                    TextFieldLineLimits.MultiLine(1, Int.MAX_VALUE)
                } else {
                    TextFieldLineLimits.SingleLine
                },
                cursorBrush = SolidColor(DColor.Brand),
                decorator = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (multiline) 88.dp else 46.dp)
                            .clip(DShape.Md)
                            .background(DColor.Card)
                            .border(1.dp, DColor.Line, DShape.Md)
                            .padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = if (multiline) 12.dp else 0.dp,
                                bottom = if (multiline) 12.dp else 0.dp,
                            ),
                        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                                Text(placeholder, fontSize = 14.sp, color = DColor.Ink3)
                            }
                            inner()
                        }
                    }
                },
            )
        }
    }
}


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
        Text(text, fontSize = 12.5.sp, color = DColor.Ink3)
    }
}

/** 统一错误态：带重试按钮，避免接口挂了页面一片空白。 */
@Composable
fun DErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, fontSize = 12.5.sp, color = DColor.Ink3)
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            DSmallButton("重试", onRetry)
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
                        .background(DColor.Card)
                        .border(1.dp, DColor.Line, DShape.Md)
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
