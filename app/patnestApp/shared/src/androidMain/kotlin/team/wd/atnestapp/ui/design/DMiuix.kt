package team.wd.atnestapp.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.components.AppIcon
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * Miuix 逐屏重写的表单页桥接助手。
 *
 * 设计目标：参数与旧设计系统组件（DFieldInput / DButton / DFrame）保持同构，
 * 屏内替换即改函数名，业务逻辑零改动；实现全部落在 Miuix 组件上。
 */

/**
 * 三个骨架（Form / Scroll / List）共用的统一顶栏——液态玻璃悬浮形态。
 *
 * 骨架把顶栏悬浮在正文之上，正文从玻璃顶栏下滚过（官方 GlassTopAppBar 的
 * 标准形态）。统一三件事，平板和手机都成立：
 * - 限宽居中：顶栏包在 fillMaxWidth + dPageWidth 里，玻璃带与限宽后的正文对齐；
 * - 标题边距用 win.gutter（手机 18dp / 平板 24 / 32dp），与 DPad 正文边距一致；
 * - 状态栏避让由 TopAppBar 自己处理（defaultWindowInsetsPadding 默认开），
 *   玻璃带连状态栏区域一起覆盖，滚上来的内容在状态栏后面也能透出。
 *
 * 顶栏玻璃 tint 故意比浮层高（40%）：大标题文字要直接压在玻璃上，
 * 通用 3% 的极限档会让标题和滚过的正文文字互相打架。
 */
@Composable
internal fun DMiuixTopBar(
    nav: DNav,
    title: String,
    backable: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    val win = LocalDWindow.current
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxWidth().dPageWidth(win)) {
            DGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                // 透明顶栏：只留 10% 底色 + 55% 玻璃，滚动内容直接透出来；
                // 标题区叠一层向下淡出的页面底色，保证大标题不糊
                tint = DColor.Bg.copy(alpha = 0.10f),
                blurRadius = 24f,
                cornerDp = 0.dp,
                surfaceAlpha = 0.55f,
            ) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(DColor.Bg.copy(alpha = 0.50f), Color.Transparent),
                        ),
                    ),
                )
                TopAppBar(
                    title = title,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                    navigationIcon = if (backable) {
                        {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable { nav.back() },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(AppIcon.Back, Modifier.size(18.dp), DColor.Ink)
                            }
                        }
                    } else {
                        {}
                    },
                    actions = actions,
                    titlePadding = win.gutter,
                    navigationIconPadding = win.gutter,
                    actionIconPadding = win.gutter,
                )
            }
        }
    }
}

/**
 * 表单页骨架：液态玻璃顶栏（悬浮）+ 滚动正文（gutter 边距）。
 * 替代 `DFrame(nav, title) { DPad { ... } }` 结构。
 * 正文与 Scroll/List 骨架同款限宽：平板上表单不会拉通整个窗口。
 */
@Composable
fun DMiuixFormScaffold(
    nav: DNav,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val win = LocalDWindow.current
    val density = LocalDensity.current
    val backdrop = rememberDGlassBackdrop()
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalDGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(DColor.Bg)) {
            // 被记录的内容层自带不透明底色（官方背景采样要求）；
            // 顶栏悬浮其上，表单从玻璃下滚过
            Column(
                Modifier
                    .fillMaxSize()
                    .dGlassBackdropSource(backdrop)
                    .background(DColor.Bg),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .dPageWidth(win)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .navigationBarsPadding()
                            // 顶栏垫片随内容滚动：初始位置在玻璃顶栏下方，
                            // 上滚时表单真正穿进玻璃
                            .padding(top = with(density) { topBarHeightPx.toDp() })
                            .padding(horizontal = win.gutter, vertical = 14.dp),
                        content = content,
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height },
            ) {
                DMiuixTopBar(nav, title, backable = true, actions = {})
            }
        }
    }
}

/**
 * 全局唯一输入框（Miuix 重写后的统一入口）：参数与旧 [DFieldInput] 完全一致，
 * 屏内替换即改函数名。普通与密码分支共用同一套自绘外观（卡片底 + 描边 +
 * 上方常驻 label + 占位文案叠加），保证全项目观感一致：
 * - 密码走官方 BasicSecureTextField（系统级混淆渲染）——不要用 OutputTransformation
 *   全量打码：退格时删除区域会被整段掩码映射错，一次删除就清空整框；
 * - 普通分支走 BasicTextField(state)，与密码分支同款 decorator；
 * - value/onValueChange 通过 TextFieldState + snapshotFlow 桥接，校验逻辑不用动。
 */
@Composable
fun DMiuixField(
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
    // 回调取最新值：调用方每次重组换 lambda 身份时，收集协程不重启
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(Unit) {
        snapshotFlow { state.text.toString() }.collect { currentOnValueChange(it) }
    }
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        val keyboardOptions = KeyboardOptions(
            keyboardType = when {
                secret -> KeyboardType.Password
                numeric -> KeyboardType.Number
                else -> KeyboardType.Text
            },
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
        )
        val keyboardAction = onSubmit?.let { submit ->
            KeyboardActionHandler { performDefaultAction ->
                submit()
                performDefaultAction()
            }
        }
        val fieldDecorator = TextFieldDecorator { inner ->
            DMiuixFieldBox(
                placeholder = placeholder,
                isEmpty = state.text.isEmpty(),
                multiline = multiline,
                showRevealToggle = secret,
                reveal = reveal,
                onToggleReveal = { reveal = !reveal },
                inner = inner,
            )
        }
        if (secret) {
            BasicSecureTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = TextStyle(color = DColor.Ink, fontSize = 14.sp),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = keyboardAction,
                cursorBrush = SolidColor(DColor.Brand),
                textObfuscationMode = if (reveal) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                decorator = fieldDecorator,
            )
        } else {
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = TextStyle(color = DColor.Ink, fontSize = 14.sp),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = keyboardAction,
                lineLimits = if (multiline) {
                    TextFieldLineLimits.MultiLine(1, Int.MAX_VALUE)
                } else {
                    TextFieldLineLimits.SingleLine
                },
                cursorBrush = SolidColor(DColor.Brand),
                decorator = fieldDecorator,
            )
        }
    }
}

/** 输入框统一外观：卡片底 + 描边 + 占位文案叠加 + （密码框）显隐开关。 */
@Composable
private fun DMiuixFieldBox(
    placeholder: String,
    isEmpty: Boolean,
    multiline: Boolean,
    showRevealToggle: Boolean,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
    inner: @Composable () -> Unit,
) {
    val glass = isGlassSupported && LocalDGlassAmbient.current != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (multiline) 88.dp else 46.dp)
            // 输入框也走环境玻璃：文字压在 62% 卡片底上，边框与折射给出玻璃层次
            .dGlassSurface(
                shape = DShape.Md,
                tint = DColor.Card.copy(alpha = 0.75f),
                blurRadius = 14f,
                cornerDp = 8.dp,
            )
            .border(1.dp, if (glass) DColor.White.copy(alpha = 0.18f) else DColor.Line, DShape.Md)
            .padding(
                start = 14.dp,
                end = if (showRevealToggle) 8.dp else 14.dp,
                top = if (multiline) 12.dp else 0.dp,
                bottom = if (multiline) 12.dp else 0.dp,
            ),
        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (isEmpty && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 14.sp, color = DColor.Ink2)
            }
            inner()
        }
        if (showRevealToggle) {
            Text(
                if (reveal) "隐藏" else "显示",
                fontSize = 12.sp,
                color = DColor.BrandInk,
                modifier = Modifier.clickable(onClick = onToggleReveal).padding(start = 8.dp),
            )
        }
    }
}

/** Miuix 主操作按钮（品牌色填充、通栏）。替代 `DButton(text, onClick)`。 */
@Composable
fun DMiuixPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(text)
    }
}

/**
 * 滚动列骨架：Miuix 版 DFrame（title=null + immersiveTop 时可做沉浸 hero 页）。
 *
 * 结构：顶栏 → 内容层（整层记录进玻璃 backdrop）→ 底部浮层（结算栏 / Tab）。
 * 浮层不再占位在内容下方，而是悬浮在内容之上——内容从玻璃结算栏 / Tab 下滚过，
 * 玻璃模糊的才是真实滚动的页面。内容底部预留了浮层高度的呼吸空间，末尾卡片不会被遮死。
 */
@Composable
fun DMiuixScrollScaffold(
    nav: DNav,
    title: String? = null,
    activeTab: DTab? = null,
    backable: Boolean = true,
    immersiveTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    refreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val win = LocalDWindow.current
    val density = LocalDensity.current
    val backdrop = rememberDGlassBackdrop()
    // 玻璃顶栏实测高度：正文内容垫同等顶部内边距（内边距随内容滚动——
    // 初始位置在顶栏下方，向上滚动时正文才真正从玻璃顶栏下穿过去）
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalDGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(DColor.Bg)) {
            // 内容层：整层绘制都记录进 backdrop（顶栏底色 + 滚动正文），
            // 玻璃浮层模糊的就是这一层。
            // 官方 blur 指南明确警告：layerBackdrop 只记录自身绘制，不含父级背景——
            // 页面底色必须画在这一层内部，否则卡片之间是透明像素，
            // 模糊把透明黑扩散开，玻璃浮层会出现发灰发黑的「脏带」。
            Column(
                Modifier
                    .fillMaxSize()
                    .dGlassBackdropSource(backdrop)
                    .background(DColor.Bg),
            ) {
                if (title == null && !immersiveTop) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                }
                // 视口通顶：有顶栏时正文从屏幕顶部开始，才能滚进玻璃顶栏下面
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val needBottomInset = bottomBar == null && activeTab == null
                    // 浮层悬在内容上方，正文尾部给足空间滚过玻璃
                    val bottomSlack = when {
                        bottomBar != null -> 112.dp
                        activeTab != null && win.isCompact -> 92.dp
                        else -> 0.dp
                    }
                    val colModifier = Modifier
                        .dPageWidth(win)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .then(if (needBottomInset) Modifier.navigationBarsPadding() else Modifier)
                        .padding(bottom = 14.dp + bottomSlack)
                        .then(
                            // 顶栏垫片放在 verticalScroll 之后：随内容滚动（内容内边距），
                            // 而不是视口 inset——后者会把正文裁在顶栏下方，永远穿不进玻璃
                            if (title != null)
                                Modifier.padding(top = with(density) { topBarHeightPx.toDp() })
                            else Modifier,
                        )
                    if (onRefresh != null) {
                        PullToRefresh(
                            isRefreshing = refreshing == true,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                            refreshTexts = listOf("下拉刷新", "松手刷新", "正在刷新…", "刷新成功"),
                        ) {
                            // PullToRefresh 内部是 TopStart 的 Box：限宽正文必须在这里再居中一次，
                            // 否则带刷新的页面在平板上靠左、不带刷新的页面居中，口径分裂
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                Column(colModifier, content = content)
                            }
                        }
                    } else {
                        Column(colModifier, content = content)
                    }
                }
            }
            // 顶部玻璃顶栏：悬浮在正文之上，正文从玻璃下滚过；
            // 实测高度回传给正文做滚动内边距
            if (title != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topBarHeightPx = it.height },
                ) {
                    DMiuixTopBar(nav, title, backable, actions)
                }
            }
            // 底部浮层：悬浮在内容之上（限宽居中），玻璃质感由各浮层组件自取 backdrop
            if (bottomBar != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.fillMaxWidth().dPageWidth(win)) { bottomBar() }
                }
            }
            if (activeTab != null && win.isCompact) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    DBottomTabs(activeTab, nav)
                }
            }
        }
    }
}

/**
 * 惰性列表骨架：Miuix 版 DLazyFrame。长列表（机构 / 订单 / 优惠券）用这个，
 * content 是 LazyListScope，页内 item { } 组织。
 */
@Composable
fun DMiuixListScaffold(
    nav: DNav,
    title: String? = null,
    activeTab: DTab? = null,
    backable: Boolean = true,
    immersiveTop: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    refreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    // LazyColumn 的 DSL 是非 Composable 的接收者 lambda（item 内容才是 @Composable），
    // 与 DLazyFrame 保持一致；写成 @Composable 反而传不进 LazyColumn。
    content: LazyListScope.() -> Unit,
) {
    val win = LocalDWindow.current
    val backdrop = rememberDGlassBackdrop()
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(DColor.Bg)) {
            // 同 ScrollScaffold：被记录的层必须自带不透明底色（官方背景采样要求）
            Column(
                Modifier
                    .fillMaxSize()
                    .dGlassBackdropSource(backdrop)
                    .background(DColor.Bg),
            ) {
                if (title == null && !immersiveTop) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                }
                // 视口通顶：正文才能滚进玻璃顶栏下面
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val needBottomInset = bottomBar == null && activeTab == null
                    val bottomSlack = when {
                        bottomBar != null -> 112.dp
                        activeTab != null && win.isCompact -> 92.dp
                        else -> 0.dp
                    }
                    val scrollModifier = Modifier
                        .dPageWidth(win)
                        .fillMaxSize()
                        .imePadding()
                        .then(if (needBottomInset) Modifier.navigationBarsPadding() else Modifier)
                    val listPadding = PaddingValues(
                        top = if (title != null) with(density) { topBarHeightPx.toDp() } else 0.dp,
                        bottom = 14.dp + bottomSlack,
                    )
                    if (onRefresh != null) {
                        PullToRefresh(
                            isRefreshing = refreshing == true,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                            refreshTexts = listOf("下拉刷新", "松手刷新", "正在刷新…", "刷新成功"),
                        ) {
                            // 同 ScrollScaffold：PullToRefresh 是 TopStart 排布，限宽列表要再居中
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                LazyColumn(
                                    scrollModifier,
                                    contentPadding = listPadding,
                                    content = content,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            scrollModifier,
                            contentPadding = listPadding,
                            content = content,
                        )
                    }
                }
            }
            // 顶部玻璃顶栏：悬浮在正文之上，实测高度回传给正文做滚动内边距
            if (title != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topBarHeightPx = it.height },
                ) {
                    DMiuixTopBar(nav, title, backable, actions)
                }
            }
            if (bottomBar != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.fillMaxWidth().dPageWidth(win)) { bottomBar() }
                }
            }
            if (activeTab != null && win.isCompact) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    DBottomTabs(activeTab, nav)
                }
            }
        }
    }
}
