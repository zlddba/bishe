package team.wd.atnestapp.ui.mobile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.launch.Launch

/** 底部导航项：图标、文案、目标页面 */
private val OWNER_TABS = listOf(
    Triple(AppIcon.Home, "首页", MobileScreen.Home),
    Triple(AppIcon.Search, "找机构", MobileScreen.Sitters),
    Triple(AppIcon.Orders, "订单", MobileScreen.Orders),
    Triple(AppIcon.User, "我的", MobileScreen.Profile),
)

private val SITTER_TABS = listOf(
    Triple(AppIcon.Home, "概览", MobileScreen.SbOverview),
    Triple(AppIcon.Orders, "接单", MobileScreen.SbOrders),
    Triple(AppIcon.Calendar, "容量", MobileScreen.SbCapacity),
    Triple(AppIcon.Card, "收益", MobileScreen.SbIncome),
    Triple(AppIcon.Shop, "资料", MobileScreen.SbShop),
)

/**
 * 手机端根组件：一个 Activity 内完成启动页、登录、两套工作台和全部页面分发。
 * 导航用 state 里的返回栈，不用 navigation 依赖，页面数量和跳转路径都不值得再引一个库。
 */
@Composable
fun PatNestMobileApp(context: Context) {
    val session = remember { MobileSession(context) }
    val scope = rememberCoroutineScope()
    val state = remember { MobileState(session).also { it.scope = scope } }
    var splash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        state.restoreSession()
        delay(1200)
        splash = false
    }

    // 启动页结束后再把当前页缺的数据补上，避免和 restoreSession 的首轮加载抢同一批接口
    LaunchedEffect(splash, state.screen) {
        if (!splash) state.loadFor(state.screen)
    }

    LaunchedEffect(state.loggedIn) {
        if (!state.loggedIn) return@LaunchedEffect
        while (true) {
            delay(15000)
            state.autoRefresh()
        }
    }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        delay(2400)
        if (state.toastMessage == message) state.toastMessage = null
    }

    BackHandler(enabled = !splash && state.canGoBack) { state.back() }

    PatNestMobileTheme {
        if (splash) {
            Launch()
        } else {
            MobilePageBackground {
                val tabRoot = isTabRoot(state.screen)
                Column(Modifier.fillMaxSize().safeContentPadding()) {
                    if (!tabRoot) {
                        PageTopBar(title = titleFor(state.screen), onBack = { state.back() })
                    }
                    Box(Modifier.weight(1f)) {
                        ScreenBody(state)
                    }
                    if (state.loggedIn && tabRoot) {
                        MsDivider()
                        val tabs = if (isSitterWorkspace(state.screen)) SITTER_TABS else OWNER_TABS
                        BottomBar(
                            items = tabs.map { Pair(it.first, it.second) },
                            selected = tabs.indexOfFirst { it.third == state.screen }.coerceAtLeast(0),
                            badges = tabs.map { badgeFor(state, it.third) },
                            onSelect = { index -> state.switchTab(tabs[index].third) },
                        )
                    }
                }
                val toast = state.toastMessage
                if (toast != null) ToastOverlay(toast)
                if (state.sheet != null) SheetHost(state)
            }
        }
    }
}

@Composable
private fun ScreenBody(state: MobileState) {
    if (!state.loggedIn) {
        LoginScreen(state)
        return
    }
    when (state.screen) {
        MobileScreen.Home -> HomeScreen(
            state = state,
            onBell = { state.go(MobileScreen.Messages) },
            onQuick = { state.go(it) },
        )
        MobileScreen.Sitters -> SittersScreen(state)
        MobileScreen.SitterDetail -> SitterDetailScreen(state)
        MobileScreen.Booking -> BookingScreen(state)
        MobileScreen.Orders -> OrdersScreen(state)
        MobileScreen.OrderDetail -> OrderDetailScreen(state)
        MobileScreen.Pay -> PayScreen(state)
        MobileScreen.Pets -> PetsScreen(state)
        MobileScreen.PetEdit -> PetEditScreen(state)
        MobileScreen.Stays -> StaysScreen(state)
        MobileScreen.Coupons -> CouponsScreen(state)
        MobileScreen.Messages -> MessagesScreen(state)
        MobileScreen.Favorites -> FavoritesScreen(state)
        MobileScreen.Dynamics -> DynamicsScreen(state)
        MobileScreen.Profile -> ProfileScreen(state)
        MobileScreen.Points -> PointsScreen(state)
        MobileScreen.Tickets -> TicketsScreen(state)
        MobileScreen.SbOverview -> SbOverviewScreen(state, onQuick = { state.go(it) })
        MobileScreen.SbOrders -> SbOrdersScreen(state)
        MobileScreen.SbCapacity -> SbCapacityScreen(state)
        MobileScreen.SbIncome -> SbIncomeScreen(state)
        MobileScreen.SbReviews -> SbReviewsScreen(state)
        MobileScreen.SbDynamics -> SbDynamicsScreen(state)
        MobileScreen.SbCoupons -> SbCouponsScreen(state)
        MobileScreen.SbShop -> SbShopScreen(state)
    }
}

private fun isSitterWorkspace(target: MobileScreen): Boolean = target.name.startsWith("Sb")

/** 有独立顶栏的页面不再叠加底部导航，其余都算 Tab 首页 */
private fun isTabRoot(target: MobileScreen): Boolean = when (target) {
    MobileScreen.Home, MobileScreen.Sitters, MobileScreen.Orders, MobileScreen.Profile -> true
    MobileScreen.SbOverview, MobileScreen.SbOrders, MobileScreen.SbCapacity,
    MobileScreen.SbIncome, MobileScreen.SbShop -> true
    else -> !isSitterWorkspace(target) && !target.needsLogin
}

private fun titleFor(target: MobileScreen): String = when (target) {
    MobileScreen.SitterDetail -> "机构详情"
    MobileScreen.Booking -> "预约寄养"
    MobileScreen.OrderDetail -> "订单详情"
    MobileScreen.Pay -> "收银台"
    MobileScreen.Pets -> "我的宠物"
    MobileScreen.PetEdit -> "宠物档案"
    MobileScreen.Stays -> "寄养记录"
    MobileScreen.Coupons -> "优惠券"
    MobileScreen.Messages -> "消息通知"
    MobileScreen.Favorites -> "我的收藏"
    MobileScreen.Dynamics -> "寄养动态"
    MobileScreen.Points -> "积分明细"
    MobileScreen.Tickets -> "退款与工单"
    MobileScreen.SbReviews -> "评价管理"
    MobileScreen.SbDynamics -> "寄养动态"
    MobileScreen.SbCoupons -> "优惠券管理"
    else -> "宠栖"
}

private fun badgeFor(state: MobileState, target: MobileScreen): Int = when (target) {
    MobileScreen.Orders -> state.orders.count { it.rawStatus == "pending_pay" }
    MobileScreen.SbOverview -> state.receivedOrders.count { it.status == "confirmed" }
    MobileScreen.SbOrders -> state.receivedOrders.count { it.status == "confirmed" }
    else -> 0
}
