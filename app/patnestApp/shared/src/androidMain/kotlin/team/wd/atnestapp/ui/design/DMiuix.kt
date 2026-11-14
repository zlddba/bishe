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
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * Miuix 逐屏重写的表单页桥接助手。
 *
 * 设计目标：参数与旧设计系统组件（DFieldInput / DButton / DFrame）保持同构，
 * 屏内替换即改函数名，业务逻辑零改动；实现全部落在 Miuix 组件上。
 */

/**
 * 表单页骨架：Scaffold + TopAppBar（返回键）+ 滚动正文（gutter 边距）。
 * 替代 `DFrame(nav, title) { DPad { ... } }` 结构。
 */
@Composable
fun DMiuixFormScaffold(
    nav: DNav,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { nav.back() },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Back, Modifier.size(18.dp), DColor.Ink)
                    }
                },
            )
        },
        containerColor = DColor.Bg,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDWindow.current.gutter, vertical = 14.dp),
            content = content,
        )
    }
}

/**
 * Miuix 版输入框：参数与旧 [DFieldInput] 完全一致，屏内替换即改函数名。
 *
 * - label 常驻在输入框上方（沿用旧版式）；
 * - placeholder 走 Miuix 浮动 label 机制（空态显示提示文案，有内容后浮成小标签）；
 * - 密码掩码复用 [DPasswordMask]，显隐开关用文字按钮（AppIcon 暂无眼睛图标）；
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
        TextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            label = placeholder.ifBlank { label },
            useLabelAsPlaceholder = true,
            enabled = enabled,
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
            outputTransformation = if (secret && !reveal) DPasswordMask else null,
            trailingIcon = if (secret) {
                {
                    Text(
                        if (reveal) "隐藏" else "显示",
                        fontSize = 12.sp,
                        color = DColor.BrandInk,
                        modifier = Modifier.clickable { reveal = !reveal }.padding(horizontal = 6.dp),
                    )
                }
            } else null,
        )
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
 * 结构与 DFrame 同构：TopAppBar / 状态栏 spacer → PullToRefresh(滚动列) → 底部 Tab。
 * content 直接是 ColumnScope，页内继续用 DPad / DCard 等内容组件。
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
    Column(Modifier.fillMaxSize().background(DColor.Bg)) {
        if (title != null) {
            TopAppBar(
                title = title,
                color = DColor.Bg,
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
            )
        } else if (!immersiveTop) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val needBottomInset = bottomBar == null && activeTab == null
            val colModifier = Modifier
                .dPageWidth(win)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .then(if (needBottomInset) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = 14.dp)
            if (onRefresh != null) {
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
            // 底部结算栏同样限宽：宽屏下按钮不会拉成通栏长条
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.dPageWidth(win)) { bottomBar() }
            }
        }
        if (activeTab != null && win.isCompact) DBottomTabs(activeTab, nav)
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
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    refreshing: Boolean? = null,
    onRefresh: (() -> Unit)? = null,
    // LazyColumn 的 DSL 是非 Composable 的接收者 lambda（item 内容才是 @Composable），
    // 与 DLazyFrame 保持一致；写成 @Composable 反而传不进 LazyColumn。
    content: LazyListScope.() -> Unit,
) {
    val win = LocalDWindow.current
    Column(Modifier.fillMaxSize().background(DColor.Bg)) {
        if (title != null) {
            TopAppBar(
                title = title,
                color = DColor.Bg,
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
            )
        } else if (win.isCompact) {
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
