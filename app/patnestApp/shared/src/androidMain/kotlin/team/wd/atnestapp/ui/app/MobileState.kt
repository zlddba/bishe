package team.wd.atnestapp.ui.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.AddonDto
import team.wd.atnestapp.data.api.AddonPayload
import team.wd.atnestapp.data.api.AddonUpdatePayload
import team.wd.atnestapp.data.api.ApiClient
import team.wd.atnestapp.data.api.ApplySitterPayload
import team.wd.atnestapp.data.api.AuthResponseDto
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.CouponListDto
import team.wd.atnestapp.data.api.CreateOrderPayload
import team.wd.atnestapp.data.api.CreateOrderPetPayload
import team.wd.atnestapp.data.api.CreatePetPayload
import team.wd.atnestapp.data.api.DynamicDto
import team.wd.atnestapp.data.api.EarningsDto
import team.wd.atnestapp.data.api.EstimateDto
import team.wd.atnestapp.data.api.GeocodeDto
import team.wd.atnestapp.data.api.GeocodePayload
import team.wd.atnestapp.data.api.NotificationDto
import team.wd.atnestapp.data.api.OrderDto
import team.wd.atnestapp.data.api.PetStayDto
import team.wd.atnestapp.data.api.PointsEntryDto
import team.wd.atnestapp.data.api.PriceRuleDto
import team.wd.atnestapp.data.api.PriceRulePayload
import team.wd.atnestapp.data.api.PublicReviewDto
import team.wd.atnestapp.data.api.ReceivedOrderDto
import team.wd.atnestapp.data.api.RefundDto
import team.wd.atnestapp.data.api.ReviewPayload
import team.wd.atnestapp.data.api.SettlementDto
import team.wd.atnestapp.data.api.SitterCouponDto
import team.wd.atnestapp.data.api.SitterCouponPayload
import team.wd.atnestapp.data.api.SitterDto
import team.wd.atnestapp.data.api.SitterReviewDto
import team.wd.atnestapp.data.api.SitterStatsDto
import team.wd.atnestapp.data.api.TicketDto
import team.wd.atnestapp.data.api.UpdatePetPayload
import team.wd.atnestapp.data.api.UpdateProfilePayload
import team.wd.atnestapp.data.api.UpdateShopPayload
import team.wd.atnestapp.data.api.UserCouponDto
import team.wd.atnestapp.data.api.UserDto
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import java.time.LocalDate
import kotlin.math.roundToInt

/** 手机端路由表：单 Activity + 手写返回栈，不为几个页面引入 navigation 依赖。 */
enum class MobileScreen {
    Home, Sitters, SitterDetail, Booking, Orders, OrderDetail, Pay,
    Pets, PetEdit, Coupons, Messages, Favorites, Dynamics, Profile,
    Points, Tickets, Stays,
    SbOverview, SbOrders, SbCapacity, SbShop, SbReviews, SbDynamics, SbCoupons, SbIncome;

    val needsLogin: Boolean
        get() = this != Home && this != Sitters && this != SitterDetail
}

/** 底部弹层内容。移动端把所有确认和短表单都做成从底部滑出的面板。 */
sealed interface MobileSheet {
    data class Message(
        val title: String,
        val body: String,
        val actionLabel: String = "知道了",
        val onAction: (() -> Unit)? = null,
    ) : MobileSheet

    data class Reason(
        val title: String,
        val hint: String,
        val submitLabel: String,
        val initial: String = "",
        val onSubmit: (String) -> Unit,
    ) : MobileSheet

    data class DateRange(
        val title: String,
        val startDate: String,
        val endDate: String,
        val minStart: String,
        val submitLabel: String,
        val note: String = "",
        val onSubmit: (String, String) -> Unit,
    ) : MobileSheet

    data class DatePick(
        val title: String,
        val value: String,
        val minValue: String,
        val submitLabel: String,
        val note: String = "",
        val onSubmit: (String) -> Unit,
    ) : MobileSheet

    data class Review(val orderNo: String, val onSubmit: (Int, Int, Int, Int, String) -> Unit) : MobileSheet

    data class Form(val title: String, val fields: List<FormField>, val submitLabel: String, val onSubmit: (List<String>) -> Unit) : MobileSheet

    data class CouponForm(val coupon: SitterCouponDto?) : MobileSheet
    data class AddonForm(val addon: AddonDto?) : MobileSheet
    data class PriceRuleForm(val rules: List<PriceRuleDto>) : MobileSheet
    data class CapacityForm(val date: String, val capacity: Int) : MobileSheet
    /** 下单时从券包里挑一张 */
    object CouponPicker : MobileSheet

    object ApplySitter : MobileSheet
    object Ticket : MobileSheet
    data class Photo(val url: String, val caption: String) : MobileSheet
}

/** 通用表单里的一个输入项， keyboardType 只用来切换数字键盘。 */
data class FormField(
    val label: String,
    val placeholder: String = "",
    val initial: String = "",
    val multiline: Boolean = false,
    val numeric: Boolean = false,
    val secret: Boolean = false,
)

/**
 * 手机端状态中枢。数据层完全复用 shared 里的 ApiClient，
 * 页面只读这里的字段、只调这里的方法，业务口径与桌面端保持一致。
 */
class MobileState(private val session: MobileSession) {
    var api: ApiClient = ApiClient(session.baseUrl)
        private set

    lateinit var scope: CoroutineScope

    init {
        bindClientCallbacks()
    }

    private fun bindClientCallbacks() {
        // 服务端确认账号已不存在（后台删除/注销完成）：静默退出，不回调登出接口
        api.onSessionInvalid = { forceLogout("账号不存在，已退出登录") }
        // 刷新令牌也换不到新令牌：清会话并把用户送回登录页
        api.onAuthExpired = { forceLogout("登录已过期，请重新登录") }
    }

    /** 更换接口地址后重建客户端，同时把本地令牌带过去 */
    fun updateServerAddress(url: String) {
        val cleaned = url.trim().removeSuffix("/")
        if (cleaned.isBlank()) {
            toast("请填写接口地址")
            return
        }
        val oldToken = api.token
        val oldRefresh = session.refreshToken
        session.baseUrl = cleaned
        serverAddress = cleaned
        api = ApiClient(cleaned)
        bindClientCallbacks()
        if (!oldToken.isNullOrBlank()) api.applyAuth(oldToken, oldRefresh, 3600)
        toast("接口地址已更新")
    }

    /* ---------- 会话 ---------- */
    var loggedIn by mutableStateOf(false)
        private set
    var account by mutableStateOf("")
        private set
    var nickname by mutableStateOf("")
        private set
    var userRole by mutableStateOf("owner")
        private set
    var isSitterAccount by mutableStateOf(false)
        private set
    var profile by mutableStateOf<UserDto?>(null)
        private set
    var serverAddress by mutableStateOf(session.baseUrl)
        private set

    /* ---------- 导航与浮层 ---------- */
    var screen by mutableStateOf(MobileScreen.Home)
        private set
    /** 返回栈里只存"当前页之下"的页面，空栈表示已经在 Tab 首页，此时系统返回键直接退出 */
    private var stack by mutableStateOf(emptyList<MobileScreen>())
    var sheet by mutableStateOf<MobileSheet?>(null)
    var toastMessage by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var selectedSitter by mutableStateOf<Sitter?>(null)
    var selectedOrder by mutableStateOf<Order?>(null)
    var editingPet by mutableStateOf<Pet?>(null)
    var viewingPet by mutableStateOf<Pet?>(null)

    /* ---------- 主人端数据 ---------- */
    var sitters by mutableStateOf(listOf<Sitter>())
        private set
    var sitterQuery by mutableStateOf("")
    var sitterType by mutableStateOf("全部")
    var sittersLoading by mutableStateOf(false)
        private set
    var sittersError by mutableStateOf<String?>(null)
        private set

    var pets by mutableStateOf(listOf<Pet>())
        private set
    var petsLoading by mutableStateOf(false)
        private set
    var petsError by mutableStateOf<String?>(null)
        private set

    var orders by mutableStateOf(listOf<Order>())
        private set
    var ordersLoading by mutableStateOf(false)
        private set
    var ordersError by mutableStateOf<String?>(null)
        private set

    var favorites by mutableStateOf(listOf<Sitter>())
        private set
    var favoritesLoading by mutableStateOf(false)
        private set

    var notifications by mutableStateOf(listOf<NotificationDto>())
        private set
    var notificationsLoading by mutableStateOf(false)
        private set

    // 消息页按归属视角分开拉取；notifications（全部）只用于未读角标
    var notificationScope by mutableStateOf("personal")
    var personalNotifications by mutableStateOf(listOf<NotificationDto>())
        private set
    var businessNotifications by mutableStateOf(listOf<NotificationDto>())
        private set
    var scopedNotificationsLoading by mutableStateOf(false)
        private set

    var ownerDynamics by mutableStateOf(listOf<DynamicDto>())
        private set
    var dynamicsLoading by mutableStateOf(false)
        private set
    val likedDynamics = mutableStateListOf<Long>()

    var orderDynamics by mutableStateOf(listOf<DynamicDto>())
        private set
    var orderDynamicsLoading by mutableStateOf(false)
        private set

    var coupons by mutableStateOf<CouponListDto?>(null)
        private set
    var couponsLoading by mutableStateOf(false)
        private set

    var pointsBalance by mutableStateOf(0)
        private set
    var pointsHistory by mutableStateOf(listOf<PointsEntryDto>())
        private set

    var refunds by mutableStateOf(listOf<RefundDto>())
        private set
    var tickets by mutableStateOf(listOf<TicketDto>())
        private set
    var petStays by mutableStateOf(listOf<PetStayDto>())
        private set

    /* ---------- 下单页 ---------- */
    var bookingStart by mutableStateOf(LocalDate.now().plusDays(1).toString())
    var bookingEnd by mutableStateOf(LocalDate.now().plusDays(4).toString())
    var bookingPetIds by mutableStateOf(listOf<Long>())
    var bookingRemark by mutableStateOf("")
    var bookingCoupon by mutableStateOf<UserCouponDto?>(null)
    var bookingPoints by mutableStateOf(0)
    var bookingAddons by mutableStateOf(listOf<String>())
    var estimate by mutableStateOf<EstimateDto?>(null)
        private set
    var estimating by mutableStateOf(false)
        private set
    var detailCapacity by mutableStateOf(listOf<CapacityItemDto>())
        private set
    var detailReviews by mutableStateOf(listOf<PublicReviewDto>())
        private set
    var detailAddons by mutableStateOf(listOf<AddonDto>())
        private set

    /* ---------- 机构端数据 ---------- */
    var myShop by mutableStateOf<SitterDto?>(null)
        private set
    var shopLoading by mutableStateOf(false)
        private set
    var shopError by mutableStateOf<String?>(null)
        private set
    var receivedOrders by mutableStateOf(listOf<ReceivedOrderDto>())
        private set
    var receivedOrdersLoading by mutableStateOf(false)
        private set
    var capacityItems by mutableStateOf(listOf<CapacityItemDto>())
        private set
    var capacityLoading by mutableStateOf(false)
        private set
    var earnings by mutableStateOf<EarningsDto?>(null)
        private set
    var earningsLoading by mutableStateOf(false)
        private set
    var sitterReviews by mutableStateOf(listOf<SitterReviewDto>())
        private set
    var sitterDynamics by mutableStateOf(listOf<DynamicDto>())
        private set
    var sitterCoupons by mutableStateOf(listOf<SitterCouponDto>())
        private set
    var settlements by mutableStateOf(listOf<SettlementDto>())
        private set
    var myAddons by mutableStateOf(listOf<AddonDto>())
        private set
    var priceRules by mutableStateOf(listOf<PriceRuleDto>())
        private set
    var sitterStats by mutableStateOf<SitterStatsDto?>(null)
        private set

    val unreadCount: Int get() = notifications.count { !it.isRead }
    val pendingOrderCount: Int get() = orders.count { it.rawStatus == "pending_pay" }
    val pendingReceiveCount: Int get() = receivedOrders.count { it.status == "confirmed" }
    /* ================= 导航 ================= */

    fun toast(message: String) {
        toastMessage = message
    }

    fun go(target: MobileScreen, keepCurrent: Boolean = true) {
        if (target.needsLogin && !loggedIn) {
            toast("请先登录")
            return
        }
        stack = if (keepCurrent) stack + screen else stack
        screen = target
    }

    /** 底部导航直接切主 Tab，返回栈清空，避免按返回键在 Tab 之间反复横跳 */
    fun switchTab(target: MobileScreen) {
        if (target.needsLogin && !loggedIn) {
            toast("请先登录")
            return
        }
        stack = emptyList()
        screen = target
    }

    fun back(): Boolean {
        if (sheet != null) {
            sheet = null
            return true
        }
        if (stack.isEmpty()) return false
        screen = stack.last()
        stack = stack.dropLast(1)
        return true
    }

    /** 返回键是否还有内容可以撤销：有弹层先关弹层，否则退一页 */
    val canGoBack: Boolean
        get() = sheet != null || stack.isNotEmpty()

    /**
     * 页面级按需加载。loadAll 只装首页那一轮，剩下的进页面再取，
     * 每个 loader 内部有"已有数据就跳过"的护栏，重复调用不会打爆接口。
     */
    fun loadFor(target: MobileScreen) {
        if (!loggedIn) return
        scope.launch {
            when (target) {
                MobileScreen.Booking -> { loadCoupons(); loadAddonsForBooking() }
                MobileScreen.SitterDetail -> selectedSitter?.let { loadSitterExtras(it.id) }
                MobileScreen.OrderDetail -> selectedOrder?.let { loadOrderDynamics(it) }
                MobileScreen.Stays -> viewingPet?.let { loadPetStays(it) }
                MobileScreen.Coupons -> loadCoupons()
                MobileScreen.Points -> loadPoints()
                MobileScreen.Tickets -> { loadTickets(); loadRefunds() }
                MobileScreen.Dynamics -> loadOwnerDynamics()
                MobileScreen.Messages -> { loadNotifications(); loadScopedNotifications() }
                MobileScreen.Favorites -> loadFavorites()
                MobileScreen.Pets -> loadPets()
                MobileScreen.Profile -> loadProfile()
                MobileScreen.SbOverview -> { loadSitterStats(); loadMyShop() }
                MobileScreen.SbOrders -> loadReceivedOrders()
                MobileScreen.SbCapacity -> loadCapacity()
                MobileScreen.SbIncome -> { loadEarnings(); loadSettlements() }
                MobileScreen.SbReviews -> loadSitterReviews()
                MobileScreen.SbDynamics -> loadSitterDynamics()
                MobileScreen.SbCoupons -> loadSitterCoupons()
                MobileScreen.SbShop -> { loadMyShop(); loadMyAddons(); loadPriceRules() }
                else -> Unit
            }
        }
    }

    /** 下单页要展示机构的加购项，detailAddons 是随详情一起拉的，这里兜底补一次 */
    private suspend fun loadAddonsForBooking() {
        val sitter = selectedSitter ?: return
        if (detailAddons.isEmpty()) loadSitterExtras(sitter.id)
    }

    fun openSitter(sitter: Sitter) {
        selectedSitter = sitter
        go(MobileScreen.SitterDetail)
        scope.launch { loadSitterExtras(sitter.id) }
    }

    fun openOrder(order: Order) {
        selectedOrder = order
        go(MobileScreen.OrderDetail)
    }

    /** 去收银台：先记住这张订单，支付页直接读它 */
    fun openPay(order: Order) {
        selectedOrder = order
        go(MobileScreen.Pay)
    }

    fun startBooking(sitter: Sitter) {
        if (!loggedIn) {
            toast("登录后即可下单")
            return
        }
        if (pets.isEmpty()) {
            toast("请先添加宠物档案")
            go(MobileScreen.PetEdit)
            return
        }
        selectedSitter = sitter
        bookingPetIds = listOf(pets.first().id)
        bookingCoupon = null
        bookingPoints = 0
        bookingAddons = emptyList()
        go(MobileScreen.Booking)
        scope.launch {
            loadPets()
            loadCoupons()
            loadPoints()
            refreshEstimate()
        }
    }

    val visibleSitters: List<Sitter>
        get() {
            val keyword = sitterQuery.trim()
            return sitters.filter { sitter ->
                (keyword.isBlank() || sitter.name.contains(keyword, true) || sitter.addr.contains(keyword, true))
            }
        }

    private fun sitterTypeCode(): String? = when (sitterType) {
        "家庭寄养" -> "home"
        "门店寄养" -> "shop"
        "上门喂养" -> "visit"
        "其他" -> "other"
        else -> null
    }

    /* ================= 登录与会话 ================= */

    /** 冷启动：本地还留着令牌就直接恢复登录态，再由 me() 校准角色 */
    fun restoreSession() {
        if (!session.hasSession) return
        api.applyAuth(session.token, session.refreshToken, 3600)
        account = session.account
        nickname = session.nickname.ifBlank { session.account }
        userRole = session.role
        isSitterAccount = session.role == "sitter"
        loggedIn = true
        scope.launch {
            try {
                applyMe(api.me())
            } catch (_: Exception) {
                // 令牌可能只是临时不可用，先保留本地登录态，页面里请求失败会各自提示
            }
            loadAll()
        }
    }

    suspend fun login(inputAccount: String, password: String) {
        if (busy) return
        busy = true
        try {
            applyAuth(api.login(inputAccount, password))
        } catch (e: Exception) {
            toast(e.message ?: "登录失败")
        } finally {
            busy = false
        }
    }

    suspend fun register(inputAccount: String, password: String, phone: String) {
        if (busy) return
        busy = true
        try {
            // 昵称沿用账号名，注册后可在个人中心修改
            applyAuth(api.register(inputAccount, password, inputAccount.trim(), phone.trim()))
        } catch (e: Exception) {
            toast(e.message ?: "注册失败")
        } finally {
            busy = false
        }
    }

    private suspend fun applyAuth(auth: AuthResponseDto) {
        api.applyAuth(auth.accessToken, auth.refreshToken, auth.expiresIn)
        val me = try {
            api.me()
        } catch (e: Exception) {
            api.clearAuth()
            toast(e.message ?: "获取用户信息失败")
            return
        }
        if (me.role == "admin") {
            api.clearAuth()
            toast("管理员账号请使用后台管理端登录")
            return
        }
        session.token = api.token.orEmpty()
        session.refreshToken = auth.refreshToken
        applyMe(me)
        toast("登录成功，欢迎 " + nickname)
        loadAll()
    }

    private fun applyMe(me: UserDto) {
        account = me.username.ifBlank { account }
        nickname = me.nickname?.takeIf { it.isNotBlank() } ?: account
        userRole = me.role
        isSitterAccount = me.role == "sitter"
        profile = me
        loggedIn = true
        session.account = account
        session.nickname = nickname
        session.role = me.role
    }

    fun logout() {
        val current = api.token
        if (!current.isNullOrBlank()) scope.launch { runCatching { api.logout(current) } }
        clearSession()
        toast("已退出登录")
    }

    /** 申请注销：服务端会吊销全部令牌，这里直接退出登录，冷静期内重新登录即可撤销 */
    suspend fun requestAccountCancel(mode: String) {
        try {
            api.requestAccountCancel(mode)
            val current = api.token
            if (!current.isNullOrBlank()) runCatching { api.logout(current) }
            clearSession()
            sheet = null
            toast("注销申请已提交，3 天内重新登录可撤销")
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }

    private fun forceLogout(message: String) {
        if (!loggedIn) return
        clearSession()
        toast(message)
    }

    private fun clearSession() {
        loggedIn = false
        isSitterAccount = false
        userRole = "owner"
        account = ""
        nickname = ""
        profile = null
        api.clearAuth()
        session.clearAuth()
        sheet = null
        pets = emptyList()
        orders = emptyList()
        favorites = emptyList()
        notifications = emptyList()
        personalNotifications = emptyList()
        businessNotifications = emptyList()
        scopedNotificationsLoading = false
        notificationScope = "personal"
        ownerDynamics = emptyList()
        coupons = null
        refunds = emptyList()
        tickets = emptyList()
        pointsHistory = emptyList()
        pointsBalance = 0
        estimate = null
        myShop = null
        receivedOrders = emptyList()
        capacityItems = emptyList()
        earnings = null
        sitterReviews = emptyList()
        sitterDynamics = emptyList()
        sitterCoupons = emptyList()
        settlements = emptyList()
        myAddons = emptyList()
        priceRules = emptyList()
        sitterStats = null
        stack = emptyList()
        screen = MobileScreen.Home
    }

    private suspend fun loadAll() {
        loadSitters()
        if (isSitterAccount) {
            loadMyShop()
            loadReceivedOrders()
            switchTab(MobileScreen.SbOverview)
        } else {
            loadPets()
            loadOrders()
            loadNotifications()
            loadFavorites()
            switchTab(MobileScreen.Home)
        }
    }
    /** 机构账号在主人与机构两种工作台之间切换 */
    fun switchToSitter() {
        if (!isSitterAccount) {
            toast("当前账号不是机构账号")
            return
        }
        screen = MobileScreen.SbOverview
        stack = emptyList()
        scope.launch {
            loadMyShop()
            loadReceivedOrders()
        }
    }

    fun switchToOwner() {
        screen = MobileScreen.Home
        stack = emptyList()
        scope.launch { loadAll() }
    }

    /* ================= 主人端：加载 ================= */

    suspend fun loadSitters(force: Boolean = false) {
        if (sittersLoading) return
        if (!force && sitters.isNotEmpty()) return
        sittersLoading = true
        sittersError = null
        try {
            sitters = api.listSitters(sitterTypeCode())
        } catch (e: Exception) {
            sittersError = e.message ?: "机构列表加载失败"
        } finally {
            sittersLoading = false
        }
    }

    fun reloadSittersByType() {
        sitters = emptyList()
        scope.launch { loadSitters(force = true) }
    }

    suspend fun loadPets(force: Boolean = false) {
        if (!loggedIn || petsLoading) return
        if (!force && pets.isNotEmpty()) return
        petsLoading = true
        petsError = null
        try {
            pets = api.listPets()
        } catch (e: Exception) {
            petsError = e.message ?: "宠物档案加载失败"
        } finally {
            petsLoading = false
        }
    }

    suspend fun loadOrders(force: Boolean = false) {
        if (!loggedIn || ordersLoading) return
        if (!force && orders.isNotEmpty()) return
        ordersLoading = true
        ordersError = null
        try {
            orders = api.listOrders()
        } catch (e: Exception) {
            ordersError = e.message ?: "订单加载失败"
        } finally {
            ordersLoading = false
        }
    }

    suspend fun loadNotifications(force: Boolean = false) {
        if (!loggedIn || notificationsLoading) return
        if (!force && notifications.isNotEmpty()) return
        notificationsLoading = true
        try {
            notifications = api.listNotifications()
        } catch (_: Exception) {
            // 消息不是主流程，失败留空即可
        } finally {
            notificationsLoading = false
        }
    }

    /** 消息页：一次拉取两个视角；商家消息只有机构账号才有 */
    suspend fun loadScopedNotifications(force: Boolean = false) {
        if (!loggedIn || scopedNotificationsLoading) return
        if (!force && (personalNotifications.isNotEmpty() || businessNotifications.isNotEmpty())) return
        scopedNotificationsLoading = true
        try {
            personalNotifications = api.listPersonalNotifications()
            businessNotifications = if (isSitterAccount) api.listBusinessNotifications() else emptyList()
        } catch (_: Exception) {
            // 消息不是主流程，失败留空即可
        } finally {
            scopedNotificationsLoading = false
        }
    }

    /** 消息页当前展示的列表 */
    fun visibleNotifications(): List<NotificationDto> =
        if (notificationScope == "business") businessNotifications else personalNotifications

    suspend fun markScopedRead(id: Long) {
        runCatching {
            if (notificationScope == "business") api.markBusinessNotificationRead(id)
            else api.markPersonalNotificationRead(id)
        }
        if (notificationScope == "business") {
            businessNotifications = businessNotifications.map { if (it.id == id) it.copy(isRead = true) else it }
        } else {
            personalNotifications = personalNotifications.map { if (it.id == id) it.copy(isRead = true) else it }
        }
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    /** 一键已读只清当前视角，另一类消息的未读数不受影响 */
    suspend fun markAllScopedRead() {
        val current = visibleNotifications()
        if (current.none { !it.isRead }) {
            toast("没有未读消息")
            return
        }
        runCatching {
            if (notificationScope == "business") api.markAllBusinessNotificationsRead()
            else api.markAllPersonalNotificationsRead()
        }
        val ids = current.map { it.id }.toSet()
        if (notificationScope == "business") {
            businessNotifications = businessNotifications.map { it.copy(isRead = true) }
        } else {
            personalNotifications = personalNotifications.map { it.copy(isRead = true) }
        }
        notifications = notifications.map { if (it.id in ids) it.copy(isRead = true) else it }
        toast("已全部标为已读")
    }

    suspend fun loadFavorites(force: Boolean = false) {
        if (!loggedIn || favoritesLoading) return
        if (!force && favorites.isNotEmpty()) return
        favoritesLoading = true
        try {
            favorites = api.listFavorites()
        } catch (e: Exception) {
            toast(e.message ?: "收藏加载失败")
        } finally {
            favoritesLoading = false
        }
    }

    suspend fun loadOwnerDynamics(force: Boolean = false) {
        if (!loggedIn || dynamicsLoading) return
        if (!force && ownerDynamics.isNotEmpty()) return
        dynamicsLoading = true
        try {
            ownerDynamics = api.listOwnerDynamics()
        } catch (e: Exception) {
            toast(e.message ?: "动态加载失败")
        } finally {
            dynamicsLoading = false
        }
    }

    /** 某只宠物的寄养记录；换宠物看之前先清空，避免串上一只的数据 */
    suspend fun loadPetStays(pet: Pet) {
        viewingPet = pet
        petStays = emptyList()
        if (pet.id <= 0L) return
        try {
            petStays = api.listPetStays(pet.id)
        } catch (e: Exception) {
            toast(e.message ?: "寄养记录加载失败")
        }
    }

    suspend fun loadOrderDynamics(order: Order) {
        selectedOrder = order
        orderDynamics = emptyList()
        if (orderDynamicsLoading) return
        orderDynamicsLoading = true
        try {
            orderDynamics = api.listDynamics(order.id)
        } catch (e: Exception) {
            toast(e.message ?: "动态加载失败")
        } finally {
            orderDynamicsLoading = false
        }
    }

    suspend fun loadCoupons(force: Boolean = false) {
        if (!loggedIn || couponsLoading) return
        if (!force && coupons != null) return
        couponsLoading = true
        try {
            coupons = api.listCoupons()
        } catch (e: Exception) {
            toast(e.message ?: "优惠券加载失败")
        } finally {
            couponsLoading = false
        }
    }

    /** 积分余额拉不到时静默归零，别把下单页拖住 */
    suspend fun loadPoints(force: Boolean = false) {
        if (!loggedIn) return
        if (!force && pointsHistory.isNotEmpty()) return
        try {
            val data = api.myPoints()
            pointsBalance = data.balance
            pointsHistory = data.items
        } catch (_: Exception) {
            pointsBalance = 0
        }
    }

    suspend fun loadRefunds() {
        if (!loggedIn) return
        refunds = runCatching { api.listRefundRequests() }.getOrDefault(refunds)
    }

    suspend fun loadTickets() {
        if (!loggedIn) return
        tickets = runCatching { api.listTickets() }.getOrDefault(tickets)
    }

    suspend fun loadProfile() {
        if (!loggedIn) return
        try {
            applyMe(api.me())
        } catch (e: Exception) {
            toast(e.message ?: "资料加载失败")
        }
    }

    /** 详情页的容量、评价、加购三项都是可选信息，失败哪一项就留空哪一项 */
    suspend fun loadSitterExtras(sitterId: Long) {
        if (sitterId <= 0L) return
        val from = LocalDate.now().toString()
        val to = LocalDate.now().plusDays(13).toString()
        detailCapacity = runCatching { api.sitterCapacityPublic(sitterId, from, to).items }.getOrDefault(emptyList())
        detailReviews = runCatching { api.sitterReviewsPublic(sitterId) }.getOrDefault(emptyList())
        detailAddons = runCatching { api.sitterAddons(sitterId) }.getOrDefault(emptyList())
    }

    /* ================= 主人端：操作 ================= */

    suspend fun savePet(
        name: String,
        typeLabel: String,
        sizeLabel: String,
        ageText: String,
        vaccinated: Boolean,
        note: String,
        avatarUrl: String,
    ): Boolean {
        if (name.isBlank()) {
            toast("请填写宠物名字")
            return false
        }
        val age = ageText.trim().toIntOrNull()
        return try {
            val target = editingPet
            if (target == null) {
                api.createPet(
                    CreatePetPayload(
                        name = name.trim(),
                        species = speciesCode(typeLabel),
                        bodySize = bodySizeCode(sizeLabel),
                        ageMonth = age,
                        vaccinated = vaccinated,
                        notes = note.trim(),
                        avatarUrl = avatarUrl,
                        photoUrls = if (avatarUrl.isNotBlank()) listOf(avatarUrl) else emptyList(),
                    ),
                )
                toast("宠物档案已创建")
            } else {
                api.updatePet(
                    target.id,
                    UpdatePetPayload(
                        name = name.trim(),
                        species = speciesCode(typeLabel),
                        bodySize = bodySizeCode(sizeLabel).ifBlank { null },
                        ageMonth = age,
                        vaccinated = vaccinated,
                        notes = note.trim(),
                        avatarUrl = avatarUrl,
                        photoUrls = if (avatarUrl.isNotBlank()) listOf(avatarUrl) else null,
                    ),
                )
                toast("宠物档案已更新")
            }
            editingPet = null
            loadPets(force = true)
            go(MobileScreen.Pets, keepCurrent = false)
            true
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
            false
        }
    }

    private fun speciesCode(label: String) = when (label) {
        "猫" -> "cat"
        "狗" -> "dog"
        else -> "other"
    }

    private fun bodySizeCode(label: String) = when (label) {
        "迷你" -> "tiny"
        "小型" -> "small"
        "中型" -> "medium"
        "大型" -> "large"
        "超大型" -> "xlarge"
        else -> ""
    }

    fun sizeLabelOf(code: String): String = when (code) {
        "tiny" -> "迷你"
        "small" -> "小型"
        "medium" -> "中型"
        "large" -> "大型"
        "xlarge" -> "超大型"
        else -> "中型"
    }


    suspend fun deletePet(pet: Pet) {
        try {
            api.deletePet(pet.id)
            pets = pets.filterNot { it.id == pet.id }
            sheet = null
            toast("宠物档案已删除")
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun submitVaccine(pet: Pet, urls: List<String>) {
        try {
            api.updatePetVaccine(pet.id, urls)
            toast("免疫证明已提交，等待核验")
            loadPets(force = true)
            sheet = null
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }
    /* ================= 下单与订单 ================= */

    val bookingDays: Int
        get() {
            val s = runCatching { LocalDate.parse(bookingStart) }.getOrNull() ?: return 0
            val e = runCatching { LocalDate.parse(bookingEnd) }.getOrNull() ?: return 0
            return e.toEpochDay().minus(s.toEpochDay()).toInt().coerceAtLeast(0)
        }

    /** 让结束日期始终晚于开始日期，最少 1 天 */
    fun shiftStart(days: Long) {
        val next = runCatching { LocalDate.parse(bookingStart) }.getOrNull()?.plusDays(days) ?: return
        bookingStart = next.toString()
        val end = runCatching { LocalDate.parse(bookingEnd) }.getOrNull()
        if (end == null || !end.isAfter(next)) bookingEnd = next.plusDays(1).toString()
        scope.launch { refreshEstimate() }
    }

    fun shiftEnd(days: Long) {
        val start = runCatching { LocalDate.parse(bookingStart) }.getOrNull() ?: return
        val next = (runCatching { LocalDate.parse(bookingEnd) }.getOrNull() ?: start.plusDays(1)).plusDays(days)
        if (!next.isAfter(start)) return
        bookingEnd = next.toString()
        scope.launch { refreshEstimate() }
    }

    fun pickBookingStart(day: String) {
        val start = runCatching { LocalDate.parse(day) }.getOrNull() ?: return
        bookingStart = day
        val end = runCatching { LocalDate.parse(bookingEnd) }.getOrNull()
        if (end == null || !end.isAfter(start)) bookingEnd = start.plusDays(1).toString()
        scope.launch { refreshEstimate() }
    }

    fun pickBookingEnd(day: String) {
        val start = runCatching { LocalDate.parse(bookingStart) }.getOrNull() ?: return
        val next = runCatching { LocalDate.parse(day) }.getOrNull() ?: return
        if (!next.isAfter(start)) {
            toast("结束日期要晚于开始日期")
            return
        }
        bookingEnd = day
        scope.launch { refreshEstimate() }
    }

    fun toggleBookingPet(petId: Long) {
        bookingPetIds = if (bookingPetIds.contains(petId)) {
            bookingPetIds.filterNot { it == petId }
        } else {
            bookingPetIds + petId
        }
        scope.launch { refreshEstimate() }
    }

    fun selectBookingCoupon(coupon: UserCouponDto?) {
        bookingCoupon = coupon
        scope.launch { refreshEstimate() }
    }

    fun selectBookingPoints(points: Int) {
        bookingPoints = points.coerceIn(0, pointsBalance)
        scope.launch { refreshEstimate() }
    }

    fun toggleBookingAddon(name: String) {
        bookingAddons = if (bookingAddons.contains(name)) bookingAddons.filterNot { it == name } else bookingAddons + name
    }

    /** 下单前实时估价，参数不齐或后端报错时清空，页面退回本地估算 */
    suspend fun refreshEstimate() {
        val sitter = selectedSitter
        if (sitter == null || bookingPetIds.isEmpty() || bookingDays <= 0) {
            estimate = null
            return
        }
        estimating = true
        estimate = runCatching {
            api.estimateOrder(
                sitterId = sitter.id,
                startDate = bookingStart,
                endDate = bookingEnd,
                petIds = bookingPetIds,
                userCouponId = bookingCoupon?.id ?: 0L,
                pointsUsed = bookingPoints,
            )
        }.getOrNull()
        estimating = false
    }

    suspend fun createOrder(): Boolean {
        val sitter = selectedSitter ?: return false
        if (bookingPetIds.isEmpty()) {
            toast("请至少选择一只宠物")
            return false
        }
        if (bookingDays <= 0) {
            toast("结束日期要晚于开始日期")
            return false
        }
        busy = true
        try {
            api.createOrder(
                CreateOrderPayload(
                    sitterId = sitter.id,
                    startDate = bookingStart,
                    endDate = bookingEnd,
                    pets = bookingPetIds.map { CreateOrderPetPayload(it) },
                    remark = bookingRemark.trim(),
                    userCouponId = bookingCoupon?.id ?: 0L,
                    pointsUsed = bookingPoints,
                ),
            )
        } catch (e: Exception) {
            toast(e.message ?: "下单失败")
            busy = false
            return false
        }
        busy = false
        bookingCoupon = null
        bookingPoints = 0
        bookingAddons = emptyList()
        estimate = null
        loadOrders(force = true)
        toast("订单已创建，请在 30 分钟内完成支付")
        go(MobileScreen.Pay)
        return true
    }

    suspend fun payOrder(order: Order): Boolean {
        busy = true
        val ok = try {
            api.payOrder(order.id)
            true
        } catch (e: Exception) {
            toast(e.message ?: "支付失败")
            false
        }
        busy = false
        if (!ok) return false
        loadOrders(force = true)
        selectedOrder = orders.firstOrNull { it.id == order.id } ?: order
        toast("支付成功")
        go(MobileScreen.Orders, keepCurrent = false)
        return true
    }

    suspend fun cancelOrder(order: Order, reason: String) {
        try {
            api.cancelOrder(order.id, reason.ifBlank { "个人安排变化" })
            sheet = null
            toast("订单已取消，容量已释放")
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "取消失败")
        }
    }

    suspend fun rescheduleOrder(order: Order, start: String, end: String) {
        try {
            val r = api.rescheduleOrder(order.id, start, end)
            sheet = null
            toast("已改到 " + r.startDate + " 至 " + r.endDate + "，费用 " + money(r.totalAmount))
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "改期失败")
        }
    }

    /** 续住：后端按新天数补差价并立即入账，这里把实付金额读回来给用户看 */
    suspend fun renewOrder(order: Order, end: String) {
        try {
            val r = api.renewOrder(order.id, end)
            sheet = null
            val paid = if (r.extraAmount > 0.0) "，已补差价 " + money(r.extraAmount) else ""
            toast("已续住 " + r.extraDays + " 天" + paid + "，订单合计 " + money(r.totalAmount))
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "续住失败")
        }
    }

    suspend fun requestRefund(order: Order, reason: String) {
        try {
            api.requestRefund(order.id, reason)
            sheet = null
            toast("退款申请已提交，等待平台处理")
            loadRefunds()
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }

    suspend fun submitReview(order: Order, overall: Int, service: Int, environment: Int, hygiene: Int, comment: String) {
        try {
            api.submitReview(
                order.id,
                ReviewPayload(overall, service, environment, hygiene, comment.ifBlank { "服务满意，感谢照料。" }.take(1000)),
            )
            sheet = null
            toast("评价已提交")
            loadOrders(force = true)
            go(MobileScreen.Orders, keepCurrent = false)
        } catch (e: Exception) {
            toast(e.message ?: "评价提交失败")
        }
    }

    /* ================= 收藏 / 消息 / 券 / 工单 ================= */

    fun isFavorite(sitterId: Long): Boolean = favorites.any { it.id == sitterId }

    suspend fun toggleFavorite(sitter: Sitter) {
        if (!loggedIn) {
            toast("登录后才能收藏")
            return
        }
        try {
            if (isFavorite(sitter.id)) {
                api.removeFavorite(sitter.id)
                favorites = favorites.filterNot { it.id == sitter.id }
                toast("已取消收藏")
            } else {
                api.addFavorite(sitter.id)
                loadFavorites(force = true)
                toast("已收藏")
            }
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun markAllRead() {
        runCatching { api.markAllNotificationsRead() }
        notifications = notifications.map { it.copy(isRead = true) }
        toast("已全部标为已读")
    }

    suspend fun markRead(id: Long) {
        runCatching { api.markNotificationRead(id) }
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    suspend fun claimCoupon(couponId: Long) {
        try {
            api.claimCoupon(couponId)
            toast("优惠券领取成功")
            loadCoupons(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "领取失败")
        }
    }

    /** 删除券包里的券，已使用的会被服务端拒绝，这里直接显示后端原文 */
    suspend fun deleteMyCoupon(coupon: UserCouponDto) {
        try {
            api.deleteMyCoupon(coupon.id)
            toast("优惠券已删除")
            loadCoupons(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun toggleLike(dynamicId: Long) {
        if (!loggedIn) {
            toast("登录后才能点赞")
            return
        }
        val liked = likedDynamics.contains(dynamicId)
        try {
            if (liked) {
                api.unlikeDynamic(dynamicId)
                likedDynamics.remove(dynamicId)
                toast("已取消点赞")
            } else {
                api.likeDynamic(dynamicId)
                if (!likedDynamics.contains(dynamicId)) likedDynamics.add(dynamicId)
                toast("已点赞")
            }
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun submitTicket(title: String, content: String, category: String) {
        if (title.isBlank() || content.isBlank()) {
            toast("请填写问题标题和描述")
            return
        }
        try {
            api.createTicket(category, title.trim(), content.trim())
            sheet = null
            toast("工单已提交，平台会尽快回复")
            loadTickets()
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }

    suspend fun contactSitter(sitter: Sitter, message: String) {
        if (message.isBlank()) {
            toast("请先写点内容")
            return
        }
        try {
            api.contactSitter(sitter.id, message.trim())
            sheet = null
            toast("已发送，机构会在消息里回复")
        } catch (e: Exception) {
            toast(e.message ?: "发送失败")
        }
    }

    suspend fun saveProfile(nicknameInput: String, phone: String, address: String, contactName: String, contactPhone: String) {
        try {
            api.updateProfile(UpdateProfilePayload(nicknameInput.trim(), phone.trim(), address.trim(), contactName.trim(), contactPhone.trim()))
            sheet = null
            nickname = nicknameInput.trim()
            session.nickname = nickname
            loadProfile()
            toast("资料已保存")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        try {
            api.changePassword(oldPassword, newPassword)
            sheet = null
            toast("密码已修改")
        } catch (e: Exception) {
            toast(e.message ?: "修改失败")
        }
    }

    suspend fun applySitter(name: String, typeLabel: String, contactPerson: String, address: String, licenseUrl: String, idCardUrl: String, description: String) {
        if (name.isBlank() || contactPerson.isBlank()) {
            toast("机构名称与联系人必填")
            return
        }
        if (licenseUrl.isBlank()) {
            toast("请上传营业执照照片")
            return
        }
        val type = when (typeLabel) {
            "家庭寄养" -> "home"
            "门店寄养" -> "shop"
            "上门喂养" -> "visit"
            else -> "other"
        }
        try {
            api.applySitter(
                ApplySitterPayload(
                    name = name.trim(),
                    type = type,
                    contactPerson = contactPerson.trim(),
                    licensePhotoUrl = licenseUrl,
                    idCardPhotoUrl = idCardUrl,
                    address = address.trim(),
                    description = description.trim(),
                ),
            )
            sheet = null
            toast("入驻申请已提交，等待平台审核")
            loadProfile()
        } catch (e: Exception) {
            toast(e.message ?: "申请失败")
        }
    }

    /* ================= 机构端 ================= */

    suspend fun loadMyShop(force: Boolean = false) {
        if (!loggedIn || shopLoading) return
        if (!force && myShop != null) return
        shopLoading = true
        shopError = null
        try {
            myShop = api.getMyShop()
            loadMyAddons()
        } catch (e: Exception) {
            shopError = e.message ?: "机构资料加载失败"
        } finally {
            shopLoading = false
        }
    }
    suspend fun loadMyAddons() {
        myAddons = runCatching { api.myAddons() }.getOrDefault(myAddons)
    }

    suspend fun saveMyShop(
        name: String,
        type: String,
        description: String,
        dailyPrice: Double,
        visitPrice: Double,
        priceMode: String,
        capacity: Int,
        address: String,
        coverPhotoUrl: String,
        detailPhotos: List<String>,
        latitude: Double?,
        longitude: Double?,
    ) {
        if (name.isBlank()) {
            toast("机构名称不能为空")
            return
        }
        if (capacity <= 0) {
            toast("默认容量至少为 1")
            return
        }
        busy = true
        try {
            myShop = api.updateMyShop(
                UpdateShopPayload(
                    name = name.trim(),
                    type = type,
                    description = description.trim(),
                    dailyPrice = dailyPrice,
                    visitPrice = visitPrice,
                    priceMode = priceMode,
                    capacity = capacity,
                    address = address.trim(),
                    coverPhotoUrl = coverPhotoUrl,
                    detailPhotos = detailPhotos,
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
            sheet = null
            toast("机构资料已保存")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        } finally {
            busy = false
        }
    }

    /** 地址换坐标：手机端没有内嵌地图，用后端地理编码补上经纬度 */
    suspend fun resolveAddress(address: String, city: String): GeocodeDto? {
        if (address.isBlank()) {
            toast("请先填写所在地址")
            return null
        }
        return try {
            val result = api.geocodeAddress(GeocodePayload(address.trim(), city.trim()))
            toast(if (result.fromCache) "坐标已解析（读自缓存）" else "坐标已解析，记得保存资料")
            result
        } catch (e: Exception) {
            toast(e.message ?: "地址解析失败")
            null
        }
    }

    suspend fun loadSitterStats() {
        if (!loggedIn) return
        sitterStats = runCatching { api.sitterStats() }.getOrNull() ?: sitterStats
    }

    suspend fun loadReceivedOrders(force: Boolean = false) {
        if (!loggedIn || receivedOrdersLoading) return
        if (!force && receivedOrders.isNotEmpty()) return
        receivedOrdersLoading = true
        try {
            receivedOrders = api.listReceivedOrders()
        } catch (e: Exception) {
            toast(e.message ?: "订单加载失败")
        } finally {
            receivedOrdersLoading = false
        }
    }

    suspend fun acceptOrder(order: ReceivedOrderDto) {
        try {
            api.acceptOrder(order.id)
            toast("已确认接单")
            loadReceivedOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun rejectOrder(order: ReceivedOrderDto, reason: String) {
        try {
            api.rejectOrder(order.id, reason.ifBlank { "机构暂时无法接待" })
            sheet = null
            toast("已拒绝该订单")
            loadReceivedOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun completeOrder(order: ReceivedOrderDto) {
        try {
            api.completeOrder(order.id)
            toast("订单已完成")
            loadReceivedOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    /** 机构端订单状态中文标签，与桌面端同一套口径 */
    fun sitterStatusText(status: String): String = when (status) {
        "pending_pay" -> "待支付"
        "confirmed" -> "待确认"
        "accepted" -> "已接单"
        "staying" -> "寄养中"
        "completed" -> "已完成"
        "evaluated" -> "已评价"
        "cancelled" -> "已取消"
        "rejected" -> "已拒绝"
        else -> status
    }

    suspend fun loadCapacity(force: Boolean = false) {
        if (!loggedIn || capacityLoading) return
        if (!force && capacityItems.isNotEmpty()) return
        capacityLoading = true
        try {
            val today = LocalDate.now()
            capacityItems = api.myCapacity(today.toString(), today.plusDays(13).toString()).items
        } catch (e: Exception) {
            toast(e.message ?: "容量加载失败")
        } finally {
            capacityLoading = false
        }
    }

    /** 按日覆盖默认容量，1-200，且不能低于当日已在住数量 */
    suspend fun setCapacity(date: String, capacity: Int) {
        try {
            api.setMyCapacity(date, capacity)
            sheet = null
            toast("已更新 " + date + " 的接待容量")
            loadCapacity(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "容量调整失败")
        }
    }

    suspend fun loadEarnings(force: Boolean = false) {
        if (!loggedIn || earningsLoading) return
        if (!force && earnings != null) return
        earningsLoading = true
        try {
            val today = LocalDate.now()
            earnings = api.sitterEarnings(today.minusDays(29).toString(), today.toString())
        } catch (e: Exception) {
            toast(e.message ?: "收益加载失败")
        } finally {
            earningsLoading = false
        }
    }

    suspend fun loadSitterReviews() {
        if (!loggedIn) return
        sitterReviews = runCatching { api.listSitterReviews() }.getOrDefault(sitterReviews)
    }

    suspend fun replyReview(reviewId: Long, text: String) {
        if (text.isBlank()) {
            toast("回复内容不能为空")
            return
        }
        try {
            api.replyReview(reviewId, text.take(500))
            sheet = null
            toast("回复已发送")
            loadSitterReviews()
        } catch (e: Exception) {
            toast(e.message ?: "回复失败")
        }
    }

    suspend fun loadSitterDynamics() {
        if (!loggedIn) return
        sitterDynamics = runCatching { api.listSitterDynamics() }.getOrDefault(sitterDynamics)
    }

    suspend fun publishDynamic(orderId: Long, content: String, photos: List<String>) {
        if (orderId <= 0L) {
            toast("请选择对应的寄养订单")
            return
        }
        try {
            api.publishDynamic(orderId, content.ifBlank { "宠物状态良好，按时投喂。" }.take(2000), photos)
            sheet = null
            toast("动态已发布")
            loadSitterDynamics()
        } catch (e: Exception) {
            toast(e.message ?: "发布失败")
        }
    }

    suspend fun deleteDynamic(dynamic: DynamicDto) {
        try {
            api.deleteSitterDynamic(dynamic.id)
            sitterDynamics = sitterDynamics.filterNot { it.id == dynamic.id }
            toast("动态已删除")
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun loadSitterCoupons() {
        if (!loggedIn) return
        try {
            sitterCoupons = api.listSitterCoupons().items
        } catch (e: Exception) {
            toast(e.message ?: "优惠券加载失败")
        }
    }

    suspend fun saveSitterCoupon(coupon: SitterCouponDto?, payload: SitterCouponPayload) {
        try {
            if (coupon == null) api.createSitterCoupon(payload) else api.updateSitterCoupon(coupon.id, payload)
            sheet = null
            toast(if (coupon == null) "优惠券已发布" else "优惠券已保存")
            loadSitterCoupons()
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    suspend fun toggleSitterCoupon(coupon: SitterCouponDto) {
        val payload = SitterCouponPayload(
            title = coupon.title,
            type = coupon.type,
            value = coupon.value,
            minAmount = coupon.minAmount,
            totalQty = coupon.totalQty,
            perUser = coupon.perUser,
            enabled = !coupon.enabled,
            startsAt = coupon.startsAt,
            endsAt = coupon.endsAt,
        )
        try {
            api.updateSitterCoupon(coupon.id, payload)
            toast(if (coupon.enabled) "已停用该优惠券" else "已启用该优惠券")
            loadSitterCoupons()
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun deleteSitterCoupon(coupon: SitterCouponDto) {
        try {
            api.deleteSitterCoupon(coupon.id)
            toast("优惠券已删除")
            loadSitterCoupons()
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun createAddon(name: String, price: Double, unit: String) {
        if (name.isBlank() || price <= 0.0) {
            toast("服务名称必填，价格要大于 0")
            return
        }
        try {
            api.createAddon(AddonPayload(name.trim(), price, unit))
            sheet = null
            loadMyAddons()
            toast("加购服务已上架")
        } catch (e: Exception) {
            toast(e.message ?: "新增失败")
        }
    }

    suspend fun updateAddon(addon: AddonDto, name: String, price: Double, unit: String) {
        if (name.isBlank() || price <= 0.0) {
            toast("服务名称必填，价格要大于 0")
            return
        }
        try {
            api.updateAddon(addon.id, AddonUpdatePayload(name = name.trim(), price = price, unit = unit))
            sheet = null
            loadMyAddons()
            toast("加购服务已更新")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    suspend fun toggleAddon(addon: AddonDto) {
        try {
            api.updateAddon(addon.id, AddonUpdatePayload(enabled = !addon.enabled))
            loadMyAddons()
            toast(if (addon.enabled) "已下架" else "已重新上架")
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun loadPriceRules() {
        if (!loggedIn) return
        priceRules = runCatching { api.listPriceRules() }.getOrDefault(priceRules)
    }

    suspend fun createPriceRule(name: String, start: String, end: String, multiplier: Double) {
        if (name.isBlank() || multiplier <= 0.0) {
            toast("规则名称必填，倍率要大于 0")
            return
        }
        try {
            api.createPriceRule(PriceRulePayload(name.trim(), start, end, multiplier))
            sheet = null
            loadPriceRules()
            toast("定价规则已保存")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    suspend fun removePriceRule(rule: PriceRuleDto) {
        try {
            api.deletePriceRule(rule.id)
            loadPriceRules()
            toast("节假日规则已删除")
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun loadSettlements() {
        if (!loggedIn) return
        settlements = runCatching { api.listSettlements() }.getOrDefault(settlements)
    }

    suspend fun applySettlement() {
        val today = LocalDate.now()
        try {
            api.applySettlement(today.withDayOfMonth(1).toString(), today.toString())
            toast("结算申请已提交")
            loadSettlements()
        } catch (e: Exception) {
            toast(e.message ?: "申请失败")
        }
    }

    /* ================= 杂项 ================= */

    /** 选图后直接上传，返回服务端图片地址；失败时把后端原文带出来 */
    suspend fun uploadPhoto(fileName: String, bytes: ByteArray): String? = try {
        api.uploadImage(bytes, fileName)
    } catch (e: Exception) {
        toast(e.message ?: "图片上传失败")
        null
    }

    fun money(amount: Double): String = "¥" + ((amount * 100).roundToInt() / 100.0).let { formatted ->
        if (formatted % 1.0 == 0.0) formatted.toLong().toString() else formatted.toString()
    }

    fun shortDay(raw: String): String = if (raw.length >= 10) raw.substring(5, 10).replace("-", "/") else raw

    fun timeText(raw: String): String {
        if (raw.length < 16) return raw
        val month = raw.substring(5, 7) + "/" + raw.substring(8, 10)
        return month + " " + raw.substring(11, 16)
    }

    fun weekday(date: String): String = when (runCatching { LocalDate.parse(date).dayOfWeek.value }.getOrDefault(0)) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> ""
    }

    /**
     * 前台轮询。手机不像桌面端一直开着，间隔取 15 秒，且只在没有任何弹层遮挡时刷，
     * 免得用户正在看某个订单时被换掉内容。
     */
    suspend fun autoRefresh() {
        if (!loggedIn) return
        loadNotifications(force = true)
        if (sheet != null) return
        if (isSitterAccount) {
            loadReceivedOrders(force = true)
            when (screen) {
                MobileScreen.SbCapacity -> loadCapacity(force = true)
                MobileScreen.SbIncome -> loadEarnings(force = true)
                MobileScreen.SbReviews -> loadSitterReviews()
                MobileScreen.SbCoupons -> loadSitterCoupons()
                MobileScreen.SbDynamics -> loadSitterDynamics()
                else -> return
            }
            return
        }
        when (screen) {
            MobileScreen.Orders, MobileScreen.OrderDetail, MobileScreen.Pay -> loadOrders(force = true)
            MobileScreen.Pets -> loadPets(force = true)
            MobileScreen.Coupons -> loadCoupons(force = true)
            MobileScreen.Dynamics -> loadOwnerDynamics(force = true)
            else -> Unit
        }
    }
}