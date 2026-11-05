package team.wd.atnestapp.ui.design

import androidx.navigation.NavHostController
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.ui.app.MobileState

/**
 * 底部 Tab。**主人端和机构端各有一套**，切换身份时整套换掉：
 * 主人端是「首页 / 找机构 / 寄养动态 / 我的」，机构端是「工作台 / 订单 / 动态 / 我的」。
 *
 * [sitter] 标出这个 Tab 属于哪一边，渲染时按当前身份过滤，
 * 所以同一个位置在两套界面里可以是完全不同的页面。
 */
enum class DTab(val label: String, val sitter: Boolean = false) {
    // 主人端（设计稿 ① ② ⑥ ⑦）
    Home("首页"),
    Sitters("找机构"),
    Dynamics("寄养动态"),
    Profile("我的"),

    // 机构端（商家工作台，设计稿里没有，按真实商家版的信息结构补的）
    SitterHome("工作台", sitter = true),
    SitterOrders("订单", sitter = true),
    SitterMoments("动态", sitter = true),
    SitterProfile("我的", sitter = true),
}

/**
 * 设计稿 61 屏，另加一页 [SitterMap]（全屏机构位置，设计稿里没有，
 * 由机构详情那块 170dp 的小地图点开）。
 * tab 非 null 的页面才显示底部导航；其余为二级页。
 */
enum class DScreen(
    val title: String,
    val group: String,
    val tab: DTab? = null,
) {
    Home("首页", "主人端 · 主流程", DTab.Home),
    FindSitters("找机构", "主人端 · 主流程", DTab.Sitters),
    SitterDetail("机构详情", "主人端 · 主流程"),
    SitterMap("机构位置", "主人端 · 主流程"),
    Booking("预约下单", "主人端 · 主流程"),
    Orders("我的订单", "主人端 · 主流程"),
    Dynamics("寄养动态", "主人端 · 主流程", DTab.Dynamics),
    Profile("我的", "主人端 · 主流程", DTab.Profile),
    Pets("我的宠物", "主人端 · 主流程"),

    Login("登录", "账号与支付"),
    Checkout("收银台", "账号与支付"),
    ReviewWrite("写评价", "账号与支付"),
    PetAdd("新增宠物", "账号与支付"),
    Favorites("我的收藏", "账号与支付"),
    Coupons("优惠券", "账号与支付"),
    Messages("消息通知", "账号与支付"),
    Addresses("地址管理", "账号与支付"),
    Settings("设置", "账号与支付"),

    /*
     * 机构端主流程：这四屏是机构身份下的底部导航一级页，
     * 和主人端那四个 Tab 一一对应但内容完全不同（工作台 / 接单 / 发布动态 / 店铺管理）。
     */
    SitterWorkspace("机构工作台", "机构端 · 主流程", DTab.SitterHome),
    SitterOrders("机构订单", "机构端 · 主流程", DTab.SitterOrders),
    SitterMoments("机构动态", "机构端 · 主流程", DTab.SitterMoments),
    SitterProfile("机构我的", "机构端 · 主流程", DTab.SitterProfile),
    OrderDetail("订单详情", "订单与售后"),
    Tickets("售后工单", "订单与售后"),
    // 设计稿里没有独立一屏：工单列表点进去就是这张会话，返回键回列表
    TicketChat("工单会话", "订单与售后"),
    Points("积分中心", "订单与售后"),
    PaySuccess("支付成功", "订单与售后"),
    SitterCompare("机构对比", "订单与售后"),

    MapPicker("地图选点", "更多功能"),
    RoleSwitch("角色切换", "更多功能"),
    ShopProfile("店铺资料", "更多功能"),
    Capacity("容量日历", "更多功能"),
    PublishDynamic("发布动态", "更多功能"),
    SitterIncome("机构收益与结算", "更多功能"),
    SitterCoupons("机构优惠券管理", "更多功能"),
    SitterReviews("机构评价", "更多功能"),
    DynamicDetail("动态详情", "更多功能"),
    PetHistory("宠物寄养历史", "更多功能"),
    RefundProgress("退款进度", "更多功能"),
    Addons("加购服务管理", "更多功能"),
    HolidayRules("节假日定价规则", "更多功能"),
    SitterOrderDetail("机构订单详情", "更多功能"),
    SearchResults("搜索结果", "更多功能"),
    Announcements("平台公告", "更多功能"),
    Register("注册", "更多功能"),
    ProfileEdit("个人资料编辑", "更多功能"),
    PetDetail("宠物详情", "更多功能"),
    ClaimCenter("领券中心", "更多功能"),
    AddressAdd("新增地址", "更多功能"),
    Support("联系客服", "更多功能"),
    SitterOnboarding("机构入驻", "更多功能"),
    Agreements("用户协议 / 隐私政策", "更多功能"),
    OrderCancel("取消订单", "更多功能"),
    ReviewAppend("追评", "更多功能"),
    Invite("邀请有礼", "更多功能"),
    SitterDashboard("机构数据看板", "更多功能"),
    SearchHistory("搜索历史", "更多功能"),
    EmptyStates("空状态合集", "更多功能"),
    PointsLedger("积分明细", "更多功能"),
    CouponDetail("优惠券详情", "更多功能"),
    OrderReschedule("订单改期", "更多功能"),
    OrderExtend("订单续费", "更多功能"),
    SitterGallery("机构相册", "更多功能"),
    ReviewDetail("评价详情", "更多功能"),
    Wallet("钱包余额", "更多功能"),
    SettingsDetail("账号与安全", "账号与支付"),
    ;

    /**
     * 未登录也能访问的公开页：首页、找机构、机构详情、登录/注册、协议。
     * 其余页面在 [DNav.go] 里统一拦到登录页，避免 61 屏各写一遍判断。
     */
    val needsLogin: Boolean
        get() = when (this) {
            Home, FindSitters, SitterDetail, SitterMap, Login, Register, Agreements -> false
            else -> true
        }

    /** 该 Tab 对应的根页面，切换底部导航时直接回到栈底。 */
    companion object {
        fun rootOf(tab: DTab): DScreen = when (tab) {
            DTab.Home -> Home
            DTab.Sitters -> FindSitters
            DTab.Dynamics -> Dynamics
            DTab.Profile -> Profile
            DTab.SitterHome -> SitterWorkspace
            DTab.SitterOrders -> SitterOrders
            DTab.SitterMoments -> SitterMoments
            DTab.SitterProfile -> SitterProfile
        }

        /** 当前身份进入 App 后的落地页：机构端是工作台，主人端是首页。 */
        fun homeOfSitter(sitter: Boolean): DScreen =
            if (sitter) SitterWorkspace else Home
    }
}

/**
 * 设计稿导航契约，底层用 Jetpack Navigation Compose 的 NavHostController 实现：
 * - go(screen)：navigate 压栈进入新页面（需要登录的页面会先被拦到登录页）；
 * - tab(tab)：切底部导航，清掉栈里的二级页，落在该 Tab 的首页；
 * - replace(screen)：用新页换掉当前页（切换身份、支付成功这类「动作型」跳转）；
 * - back()：popBackStack 出栈；
 * - afterLogin()：登录 / 注册成功后回到「本来想去的那一页」；
 * - toast(msg)：轻提示，不属于导航行为；
 * - openSitter / openOrder / openPet / startBooking：把「选中对象」写进数据层再跳页，
 *   下游页面直接读 `store.selectedSitter` 等，不再靠死数据。
 *
 * 所有屏只依赖本类与 [MobileState]；路由名即 DScreen 的枚举名。
 */
class DNav(
    private val controller: NavHostController,
    private val showToast: (String) -> Unit,
    private val store: MobileState? = null,
    private val isLoggedIn: () -> Boolean = { true },
) {
    /**
     * 被登录拦下来的那一页。
     *
     * 未登录点「我的」「订单」时并不真的压栈，而是弹登录页；登录成功如果只做 back()，
     * 会退回拦截前的页面（首页），用户会觉得刚点的那一下丢了 —— 记住目标好补跳。
     */
    private var pendingTarget: DScreen? = null

    fun go(screen: DScreen, guard: Boolean = true) {
        if (guard && screen.needsLogin && !isLoggedIn()) {
            pendingTarget = screen
            showToast("请先登录")
            controller.navigate(DScreen.Login.name) { launchSingleTop = true }
            return
        }
        controller.navigate(screen.name) {
            // 连点同一个入口时不再叠一层，避免转场动画重放。
            launchSingleTop = true
        }
    }

    /**
     * 切底部导航：回到该 Tab 的首页。
     *
     * 刻意不用 saveState / restoreState。那套 API 会把该 Tab 之前压着的二级页一起存下来，
     * 再点回来直接落在那张二级页上（「我的 → 收银台 → 支付成功」点首页，再点「我的」
     * 又回到支付成功页），栈一层层累积，表现就是「页面栈不对劲」。
     */
    fun tab(tab: DTab) {
        // 两套 Tab 各自有效：机构端身份下点不到主人端的 Tab，反过来也一样。
        // 少了这道闸，身份与页面就会错位（机构身份停在主人端首页）。
        if (tab.sitter != (store?.isSitterMode ?: false)) return
        val target = DScreen.rootOf(tab)
        if (target.needsLogin && !isLoggedIn()) {
            pendingTarget = target
            showToast("请先登录")
            controller.navigate(DScreen.Login.name) { launchSingleTop = true }
            return
        }
        // 已经站在这个 Tab 的首页上就别再 pop + push 一次，否则页面会被重建、转场重放
        if (controller.currentDestination?.route == target.name) return
        // 同一身份的 Tab 之间互斥：只清到本身份自己的栈底。
        // 机构端锚在工作台、主人端锚在首页，切换 Tab 不会串到另一套界面里去。
        val anchor = DScreen.rootOf(if (tab.sitter) DTab.SitterHome else DTab.Home).name
        controller.navigate(target.name) {
            if (anchor != target.name) popUpTo(anchor) { inclusive = false }
            launchSingleTop = true
        }
    }

    /**
     * 把整个返回栈换成 [screen]。
     *
     * 给「切换身份」用：另一套页面不该留在栈里，否则从机构工作台按返回会掉回主人端首页，
     * 用户会以为自己没切成功。换完之后返回键就是退出 App，和两个独立 App 的观感一致。
     */
    fun resetStack(screen: DScreen, guard: Boolean = true) {
        if (guard && screen.needsLogin && !isLoggedIn()) {
            pendingTarget = screen
            showToast("请先登录")
            controller.navigate(DScreen.Login.name) { launchSingleTop = true }
            return
        }
        if (controller.currentDestination?.route == screen.name) return
        controller.navigate(screen.name) {
            popUpTo(controller.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    /**
     * 用目标页替换当前页，而不是压在它上面。
     *
     * 给「切换身份」这类动作型跳转用：go() 会把切换页留在栈里，
     * 从机构工作台按返回又回到切换页，看起来像没切成功。
     */
    fun replace(screen: DScreen, guard: Boolean = true) {
        if (guard && screen.needsLogin && !isLoggedIn()) {
            pendingTarget = screen
            showToast("请先登录")
            controller.navigate(DScreen.Login.name) { launchSingleTop = true }
            return
        }
        val current = controller.currentDestination?.route
        controller.navigate(screen.name) {
            if (current != null) popUpTo(current) { inclusive = true }
            launchSingleTop = true
        }
    }

    /**
     * 清空栈，只留栈底的 Tab 容器，再把 [screen] 压上去。
     *
     * 给「流程终点」用（支付成功）：直接 go() 的话，从成功页返回会回到收银台，
     * 而那笔钱已经付过、页面上的按钮还能再点一次 —— 这就是最典型的「栈不对劲」。
     */
    fun resetTo(screen: DScreen, guard: Boolean = true) {
        if (guard && screen.needsLogin && !isLoggedIn()) {
            pendingTarget = screen
            showToast("请先登录")
            controller.navigate(DScreen.Login.name) { launchSingleTop = true }
            return
        }
        controller.navigate(screen.name) {
            popUpTo(controller.graph.startDestinationId)
            launchSingleTop = true
        }
    }

    fun back() {
        // 从登录页返回 = 放弃这次操作，清掉待跳目标，免得下次登录莫名跳页
        if (controller.currentDestination?.route == DScreen.Login.name) pendingTarget = null
        controller.popBackStack()
    }

    /** 当前所在路由（等于 DScreen 的枚举名）。给冷启动按身份落位这类判断用。 */
    fun currentRoute(): String? = controller.currentDestination?.route

    /**
     * 登录 / 注册成功后的收尾：能回到「原来想去的那一页」就去，否则退回上一页。
     *
     * 直接在登录页里 back() 会把用户丢回首页，而他本来点的是「我的」或「订单」。
     */
    fun afterLogin() {
        val target = pendingTarget
        pendingTarget = null
        if (target != null && target != DScreen.Login) {
            val tabOfTarget = target.tab
            if (tabOfTarget != null) tab(tabOfTarget) else replace(target)
            return
        }
        // 机构端账号登录后直接进工作台：否则先落在主人端首页，用户还得自己找回去
        if (store?.isSitterMode == true) {
            resetStack(DScreen.SitterWorkspace)
            return
        }
        back()
        // 从注册页点进来时栈是 [.., 登录, 注册]，只出栈一次会停在已经没用的登录页
        if (controller.currentDestination?.route == DScreen.Login.name) back()
    }

    fun toast(message: String) = showToast(message)

    /** 打开机构详情：先记住机构，详情页读 store.selectedSitter。 */
    fun openSitter(sitter: Sitter) {
        store?.selectedSitter = sitter
        go(DScreen.SitterDetail)
    }

    /** 打开订单详情，同时把订单交给数据层，详情/收银/改期都复用。 */
    fun openOrder(order: Order) {
        store?.selectedOrder = order
        go(DScreen.OrderDetail)
    }

    /** 打开宠物详情 / 寄养历史。 */
    fun openPet(pet: Pet, target: DScreen = DScreen.PetDetail) {
        store?.viewingPet = pet
        go(target)
    }

    /** 从机构详情进预约：校验登录与宠物档案，再带上默认参数。 */
    fun startBooking(sitter: Sitter) {
        val s = store ?: return
        if (!isLoggedIn()) {
            toast("登录后即可预约")
            // 登录完要退回机构详情让用户重新点预约（预约参数还没填），不能跳到别处
            pendingTarget = null
            go(DScreen.Login, guard = false)
            return
        }
        if (s.pets.isEmpty()) {
            toast("请先添加宠物档案")
            go(DScreen.PetAdd)
            return
        }
        s.selectedSitter = sitter
        s.bookingPetIds = listOf(s.pets.first().id)
        s.bookingCoupon = null
        s.bookingPoints = 0
        s.bookingAddons = emptyList()
        go(DScreen.Booking)
    }
}
