package team.wd.atnestapp.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import team.wd.atnestapp.ui.app.MobileSession
import team.wd.atnestapp.ui.app.MobileState

/**
 * 按 docs/KMP-Android端UI设计稿.html 还原的手机端 UI，已经接上真实接口。
 *
 * 数据层复用 `ui/app/MobileState`（它只做状态与接口调用，不依赖界面层），
 * 61 屏通过 [DLocalStore] 读同一份 store，不再各写一遍联网逻辑。
 *
 * - 首页 / 找机构 / 机构详情是公开页，未登录也能浏览；
 * - 其余页面由 [DNav] 统一拦到登录页。
 */
@Composable
fun DesignApp(apiBase: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { MobileSession(context, apiBase) }
    val store = remember { MobileState(session).also { it.scope = scope } }

    // 主题跟随系统深浅色。
    val dark = isSystemInDarkTheme()
    val navController = rememberNavController()
    val nav = remember(navController, store) {
        DNav(
            controller = navController,
            // 统一走 store 的提示通道：提示条自己订阅，不需要在根部持有状态，
            // 否则每弹一次提示都会让整个 NavHost 重组一遍。
            showToast = { store.toast(it) },
            store = store,
            isLoggedIn = { store.loggedIn },
        )
    }

    // 根页按身份定：机构端是工作台，主人端是首页。
    // MobileState 的 activeIdentity 初值直接读本地会话快照，所以第一次组合就是对的，
    // 不会出现「先闪一下主人端首页再跳进工作台」。
    val startRoute = remember { DScreen.homeOfSitter(store.isSitterMode).name }

    // 冷启动：本地有令牌就恢复登录态（内部会校准角色并拉一轮数据）
    LaunchedEffect(Unit) { store.restoreSession() }

    // 身份和「当前站在哪一套界面」不一致时换根。
    // 只在站在另一套的根页时才纠正，避免把用户从容量日历这类二级页拽回来。
    LaunchedEffect(store.loggedIn, store.activeIdentity) {
        if (!store.loggedIn) return@LaunchedEffect
        val atOwnerRoot = nav.currentRoute() == DScreen.Home.name
        val atSitterRoot = nav.currentRoute() == DScreen.SitterWorkspace.name
        when {
            store.isSitterMode && atOwnerRoot -> nav.resetStack(DScreen.SitterWorkspace)
            !store.isSitterMode && atSitterRoot -> nav.resetStack(DScreen.Home)
        }
    }

    // 登录态变化后补齐首页这一轮数据；未登录也要能看机构列表（公开接口）
    LaunchedEffect(store.loggedIn) {
        if (store.loggedIn) {
            store.loadSitters()
            store.loadPets()
            store.loadOrders()
            store.loadNotifications()
            store.loadCoupons()
            store.loadPoints()
        } else {
            store.loadSitters(force = true)
        }
    }

    BackHandler(enabled = navController.previousBackStackEntry != null) { nav.back() }

    // 路由图只建一次。
    //
    // NavHost 内部用 remember(route, startDestination, builder) 建图，
    // 而 builder 一旦在每次重组时都是新 lambda（它捕获了 nav），整张图就会被重建，
    // 61 个目的地全部重新注册——这是导航卡顿最典型的原因之一。
    val graphBuilder: NavGraphBuilder.() -> Unit = remember(nav) {
        {
            DScreen.entries.forEach { screen ->
                composable(screen.name) {
                    DDispatcher(screen, nav)
                }
            }
        }
    }

    CompositionLocalProvider(DLocalStore provides store) {
        DTheme(darkTheme = dark) {
            // 窗口尺寸在这里实测，而不是读 Configuration：
            // 分屏拖拽、折叠屏开合、自由窗口缩放都会即时改变 maxWidth，
            // 页面按新档位重排，不依赖 Activity 重建，导航栈也不会丢。
            //
            // 不再整块 safeContentPadding：背景铺满全屏（状态栏 / 导航条下方也是页面底色），
            // 各屏由 DFrame 自己用 statusBarsPadding / navigationBarsPadding 避让系统栏。
            BoxWithConstraints(Modifier.fillMaxSize().background(DColor.Bg)) {
                val window = remember(maxWidth, maxHeight) { DWindow.of(maxWidth, maxHeight) }
                // 根级玻璃背景：Toast 这类窗口级浮层模糊「整个界面」；
                // 页内骨架会 provide 自己的 backdrop 遮蔽这份，各浮层总模糊自己正下方的内容
                val rootBackdrop = rememberDGlassBackdrop()
                CompositionLocalProvider(LocalDWindow provides window) {
                    // 宽屏导航栏常驻，需要知道该高亮哪一项：二级页的 tab 为 null，
                    // 栏内不高亮任何一项，但仍可一键跳到别的 Tab。
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val activeTab = remember(backStackEntry) {
                        backStackEntry?.destination?.route
                            ?.let { route -> DScreen.entries.firstOrNull { it.name == route }?.tab }
                    }
                    Row(
                        Modifier.fillMaxSize()
                            .then(if (isGlassSupported) Modifier.dGlassBackdropSource(rootBackdrop) else Modifier),
                    ) {
                        if (window.showRail) DNavigationRail(activeTab, nav)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            NavHost(
                                navController = navController,
                                startDestination = startRoute,
                                modifier = Modifier.fillMaxSize(),
                                // 二级页：新页从右侧推入、旧页向左退出 1/4（视差），像原生页面栈。
                                // 例外：无论 Tab 切回还是返回，首页（导航栏第一页）本身不做进入动画，直接出现。
                                enterTransition = {
                                    if (screenOf(targetState) == DScreen.Home) EnterTransition.None
                                    else if (isTabSwitch()) fadeIn(tween(220)) else pushIn()
                                },
                                exitTransition = { if (isTabSwitch()) fadeOut(tween(220)) else pushOut() },
                                popEnterTransition = {
                                    if (screenOf(targetState) == DScreen.Home) EnterTransition.None else popIn()
                                },
                                popExitTransition = { popOut() },
                                // 各页高度一致，关掉尺寸过渡，避免切换瞬间的高度插值抖动。
                                sizeTransform = null,
                                builder = graphBuilder,
                            )
                        }
                    }
                    // 提示条按窗口居中，而不是内容区居中：宽屏下它要落在大屏的视觉中心，
                    // 而不是缩在限宽后的正文中间。
                    DToastHost(store, rootBackdrop, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/**
 * 底部提示条。
 *
 * 单独成一个 composable 有两个原因：
 * 1. 提示相关的状态只在这里读，弹提示不会让整页重组；
 * 2. 收起由 [collectLatest] 负责，来新消息时上一段的 1.8 秒计时自动作废，
 *    不需要额外维护 Job，也不会出现「第二条提示被第一条的计时提前收掉」。
 */
@Composable
private fun DToastHost(store: MobileState, backdrop: LayerBackdrop?, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        snapshotFlow { store.toastMessage }
            .filterNotNull()
            .collectLatest { text ->
                store.toastMessage = null
                message = text
                visible = true
                delay(1800)
                visible = false
            }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = modifier,
    ) {
        // 玻璃 Toast：悬浮在页面之上，模糊身后滚过的内容；
        // 无背景记录（或 API<33）时退化为原实体深色气泡，观感不变
        Box(
            // 手机要避开底部 Tab；宽屏没有 Tab，贴近底部即可
            Modifier.padding(bottom = LocalDWindow.current.toastBottom)
                .navigationBarsPadding(),
        ) {
            DGlassSurface(
                shape = DShape.Pill,
                tint = DColor.Toast,
                blurRadius = 28f,
                border = Color.White.copy(alpha = 0.22f),
                fallbackColor = DColor.Toast,
                fallbackBorderColor = Color.Transparent,
            ) {
                Text(
                    message,
                    color = DColor.White,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 转场：底部 Tab 之间纯淡入淡出；二级页走左右推入/推出
 * ------------------------------------------------------------------ */

private fun screenOf(entry: NavBackStackEntry): DScreen? =
    entry.destination.route?.let { route -> DScreen.entries.firstOrNull { it.name == route } }

/** 两个 Tab 根页面之间切换（首页↔找机构↔动态↔我的），用淡入淡出而不是左右推。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = screenOf(initialState) ?: return false
    val to = screenOf(targetState) ?: return false
    return from != to && from.tab != null && to.tab != null
}

/**
 * 二级页的推入 / 推出。
 *
 * 只用位移、不叠加淡入淡出：整屏 alpha 过渡会强制这一页走离屏合成，
 * 页面上有封面图或长列表时，转场期间几乎必然掉帧。位移本身已经足够表达层级关系。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushIn(): EnterTransition =
    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushOut(): ExitTransition =
    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 4 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popIn(): EnterTransition =
    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 4 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popOut(): ExitTransition =
    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }

/** 61 屏的统一分发入口。新增设计稿页面时只改这里 + DNav 枚举。 */
@Composable
private fun DDispatcher(screen: DScreen, nav: DNav) {
    when (screen) {
        DScreen.Home -> D01Home(nav)
        DScreen.FindSitters -> D02FindSitters(nav)
        DScreen.SitterDetail -> D03SitterDetail(nav)
        DScreen.SitterMap -> DSitterMap(nav)
        DScreen.Booking -> D04Booking(nav)
        DScreen.Orders -> D05Orders(nav)
        DScreen.Dynamics -> D06Dynamics(nav)
        DScreen.Profile -> D07Profile(nav)
        DScreen.Pets -> D08Pets(nav)
        DScreen.Login -> D09Login(nav)
        DScreen.Checkout -> D10Checkout(nav)
        DScreen.ReviewWrite -> D11ReviewWrite(nav)
        DScreen.PetAdd -> D12PetAdd(nav)
        DScreen.Favorites -> D13Favorites(nav)
        DScreen.Coupons -> D14Coupons(nav)
        DScreen.Messages -> D15Messages(nav)
        DScreen.Addresses -> D16Addresses(nav)
        DScreen.Settings -> D17Settings(nav)
        DScreen.SitterWorkspace -> D18SitterWorkspace(nav)
        DScreen.SitterOrders -> DSitterOrders(nav)
        DScreen.SitterMoments -> DSitterMoments(nav)
        DScreen.SitterProfile -> DSitterProfile(nav)
        DScreen.OrderDetail -> D19OrderDetail(nav)
        DScreen.Tickets -> D20Tickets(nav)
        DScreen.TicketChat -> DTicketChat(nav)
        DScreen.Points -> D21Points(nav)
        DScreen.PaySuccess -> D22PaySuccess(nav)
        DScreen.SitterCompare -> D23SitterCompare(nav)
        DScreen.MapPicker -> D24MapPicker(nav)
        DScreen.RoleSwitch -> D25RoleSwitch(nav)
        DScreen.ShopProfile -> D26ShopProfile(nav)
        DScreen.Capacity -> D27Capacity(nav)
        DScreen.PublishDynamic -> D28PublishDynamic(nav)
        DScreen.SitterIncome -> D29SitterIncome(nav)
        DScreen.SitterCoupons -> D30SitterCoupons(nav)
        DScreen.SitterReviews -> D31SitterReviews(nav)
        DScreen.DynamicDetail -> D32DynamicDetail(nav)
        DScreen.PetHistory -> D33PetHistory(nav)
        DScreen.RefundProgress -> D34RefundProgress(nav)
        DScreen.Addons -> D35Addons(nav)
        DScreen.HolidayRules -> D36HolidayRules(nav)
        DScreen.SitterOrderDetail -> D37SitterOrderDetail(nav)
        DScreen.SearchResults -> D38SearchResults(nav)
        DScreen.Announcements -> D39Announcements(nav)
        DScreen.Register -> D40Register(nav)
        DScreen.ProfileEdit -> D41ProfileEdit(nav)
        DScreen.PetDetail -> D42PetDetail(nav)
        DScreen.ClaimCenter -> D43ClaimCenter(nav)
        DScreen.AddressAdd -> D44AddressAdd(nav)
        DScreen.Support -> D45Support(nav)
        DScreen.SitterOnboarding -> D46SitterOnboarding(nav)
        DScreen.Agreements -> D47Agreements(nav)
        DScreen.OrderCancel -> D48OrderCancel(nav)
        DScreen.ReviewAppend -> D49ReviewAppend(nav)
        DScreen.Invite -> D50Invite(nav)
        DScreen.SitterDashboard -> D51SitterDashboard(nav)
        DScreen.SearchHistory -> D52SearchHistory(nav)
        DScreen.EmptyStates -> D53EmptyStates(nav)
        DScreen.PointsLedger -> D54PointsLedger(nav)
        DScreen.CouponDetail -> D55CouponDetail(nav)
        DScreen.OrderReschedule -> D56OrderReschedule(nav)
        DScreen.OrderExtend -> D57OrderExtend(nav)
        DScreen.SitterGallery -> D58SitterGallery(nav)
        DScreen.ReviewDetail -> D59ReviewDetail(nav)
        DScreen.Wallet -> D60Wallet(nav)
        DScreen.SettingsDetail -> D61SettingsDetail(nav)
    }
}
