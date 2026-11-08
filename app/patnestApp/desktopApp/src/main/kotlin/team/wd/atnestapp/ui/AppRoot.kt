package team.wd.atnestapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.ApiClient
import team.wd.atnestapp.data.api.ApplySitterPayload
import team.wd.atnestapp.data.api.AuthResponseDto
import team.wd.atnestapp.data.api.CreateOrderPayload
import team.wd.atnestapp.data.api.CreateOrderPetPayload
import team.wd.atnestapp.data.api.CreatePetPayload
import team.wd.atnestapp.data.session.desktopSessionStore
import team.wd.atnestapp.data.api.CouponListDto
import team.wd.atnestapp.data.api.DynamicDto
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.EarningsDto
import team.wd.atnestapp.data.api.GeocodeDto
import team.wd.atnestapp.data.api.GeocodePayload
import team.wd.atnestapp.data.api.GeoPickPayload
import team.wd.atnestapp.data.api.ReversePayload
import team.wd.atnestapp.data.api.EstimateDto
import team.wd.atnestapp.data.api.PointsEntryDto
import team.wd.atnestapp.data.api.RefundDto
import team.wd.atnestapp.data.api.PetStayDto
import team.wd.atnestapp.data.api.TicketDto
import team.wd.atnestapp.data.api.ReceivedOrderDto
import team.wd.atnestapp.data.api.WalletDto
import team.wd.atnestapp.data.api.WalletTransactionDto
import team.wd.atnestapp.data.api.SitterDto
import team.wd.atnestapp.data.api.SitterReviewDto
import team.wd.atnestapp.data.api.SitterCouponDto
import team.wd.atnestapp.data.api.SitterCouponPayload
import team.wd.atnestapp.data.api.UpdateShopPayload
import team.wd.atnestapp.data.api.UpdatePetPayload
import team.wd.atnestapp.data.api.PublicReviewDto
import team.wd.atnestapp.data.api.SettlementDto
import team.wd.atnestapp.data.api.AddonDto
import team.wd.atnestapp.data.api.AddonPayload
import team.wd.atnestapp.data.api.AddonUpdatePayload
import team.wd.atnestapp.data.api.SitterStatsDto
import team.wd.atnestapp.data.api.PriceRuleDto
import team.wd.atnestapp.data.api.PriceRulePayload
import team.wd.atnestapp.ui.data.SbOrder
import team.wd.atnestapp.data.api.NotificationDto
import team.wd.atnestapp.data.NotificationCenter
import team.wd.atnestapp.data.api.ReviewPayload
import team.wd.atnestapp.data.api.UpdateProfilePayload
import team.wd.atnestapp.data.api.UserDto
import team.wd.atnestapp.AppConfig
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.DangerLinkText
import team.wd.atnestapp.ui.components.DashedUploadBox
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.KeyValueRow
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.PhotoStrip
import team.wd.atnestapp.ui.components.MapPickerPanel
import team.wd.atnestapp.ui.components.RemoteImage
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.launch.DesktopSplash
import team.wd.atnestapp.ui.pages.owner.LoginPage
import team.wd.atnestapp.ui.shell.MainShell
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patShadow
import team.wd.atnestapp.ui.theme.patShadowSoft
import team.wd.atnestapp.ui.util.openUrl
import kotlin.math.roundToInt

enum class AppRole { Owner, Sitter }

enum class AppPage(val title: String) {
    Home("首页"),
    Sitters("找机构"),
    SitterDetail("机构详情"),
    Pets("我的宠物"),
    PetEdit("新增宠物"),
    Booking("预约下单"),
    Pay("收银台"),
    Orders("我的订单"),
    Dynamics("寄养动态"),
    Review("四维评价"),
    Profile("个人中心"),
    Favorites("我的收藏"),
    Coupons("优惠券"),
    Messages("消息通知"),
    Wallet("我的钱包"),
    SbDash("经营概览"),
    SbOrders("接单管理"),
    SbCapacity("容量日历"),
    SbPublish("寄养动态发布"),
    SbReviews("评价管理"),
    SbIncome("收益结算"),
    SbProfile("机构资料"),
    SbCoupons("优惠券"),
}

private enum class Screen { Launch, Main }

/** 桌面端自动拉数据的间隔：一开就是很久，新订单和新消息不该等用户手动刷新 */
private const val AUTO_REFRESH_MILLIS = 5_000L

sealed interface DrawerKind {
    data class PetDetail(val index: Int) : DrawerKind
    data class OrderDetail(val index: Int) : DrawerKind
    data class DynDetail(val index: Int) : DrawerKind
    data class SbOrderDetail(val index: Int) : DrawerKind
}

sealed interface ModalKind {
    data class Confirm(val title: String, val message: String, val onOk: () -> Unit) : ModalKind
    data object Publish : ModalKind
    data object ChangePassword : ModalKind
    data object ApplySitter : ModalKind
    data object PriceRule : ModalKind
    /** 机构优惠券表单：coupon 为 null 时新建，否则编辑 */
    data class CouponForm(val coupon: SitterCouponDto? = null) : ModalKind
    /** 机构调整指定日期（YYYY-MM-DD）的接待容量 */
    data class AdjustCapacity(val date: String) : ModalKind
    /** 地图选点：应用内弹框，红点即选中位置；lat/lng 为已有坐标时以其为中心 */
    data class MapPicker(
        val latitude: Double?,
        val longitude: Double?,
        val onPicked: (lat: Double, lng: Double, address: String) -> Unit,
    ) : ModalKind
    /** 图片放大预览 */
    data class Photo(val url: String, val caption: String = "") : ModalKind
    /** 取消订单（后端会释放已占容量） */
    data class CancelOrder(val orderId: Long, val orderNo: String) : ModalKind
    /** 改期：待支付 / 待确认 / 已接单可改 */
    data class Reschedule(
        val orderId: Long,
        val orderNo: String,
        val startDate: String,
        val endDate: String,
    ) : ModalKind
    /** 续住：只能往后延，且订单必须已支付 */
    data class Renew(val orderId: Long, val orderNo: String, val endDate: String, val amount: Int) : ModalKind
    /** 申请退款：进入平台审核 */
    data class Refund(val orderId: Long, val orderNo: String, val amount: Int) : ModalKind
    /** 联系客服：提交售后工单 */
    data object Ticket : ModalKind
}

class AppState {
    /**
     * 会话持久化（`~/.patnest/patnest_session.preferences_pb`）。
     * 构造时同步读一次本地文件，冷启动就能知道「登录过没有」，不必等异步回调。
     */
    val sessionStore = desktopSessionStore()

    val api = ApiClient(runCatching { AppConfig.apiBaseUrl }.getOrDefault("")).also { client ->
        // 任意接口返回"用户不存在"（账号已被删除）→ 强制退出并清空本地会话数据
        client.onSessionInvalid = { forceLogoutOnMissingUser() }
        // 静默续期也换不到令牌（刷新令牌过期/被吊销）→ 退出登录，避免停在"看着已登录、请求全失败"的状态
        client.onAuthExpired = { forceLogoutExpired() }
        // 续期成功 → 轮换后的访问/刷新令牌写回本地，否则下次启动会拿着作废的那张
        client.onAuthRefreshed = { access, refresh -> sessionStore.setTokens(access, refresh) }
    }
    lateinit var scope: CoroutineScope

    var role by mutableStateOf(AppRole.Owner)
    var page by mutableStateOf(AppPage.Home)
    var loggedIn by mutableStateOf(false)
    var isSitterAccount by mutableStateOf(false)
    var accountRole by mutableStateOf("owner")
    var loginVisible by mutableStateOf(false)
    var authBusy by mutableStateOf(false)
    var account by mutableStateOf("")
    var nickname by mutableStateOf("")
    private var pendingPage by mutableStateOf<AppPage?>(null)
    var sitterIndex by mutableStateOf(0)
    var petIndex by mutableStateOf(0)
    var orderIndex by mutableStateOf(0)
    var dynIndex by mutableStateOf(0)
    var sbOrderIndex by mutableStateOf(0)
    var toastMessage by mutableStateOf<String?>(null)
    var drawer by mutableStateOf<DrawerKind?>(null)
    var modal by mutableStateOf<ModalKind?>(null)

    var sitters by mutableStateOf<List<Sitter>>(emptyList())
    var sitterSearch by mutableStateOf("")
    var sittersLoading by mutableStateOf(false)
    var sittersError by mutableStateOf<String?>(null)

    var pets by mutableStateOf<List<Pet>>(emptyList())
    var petsLoading by mutableStateOf(false)
    var petsError by mutableStateOf<String?>(null)

    var orders by mutableStateOf<List<Order>>(emptyList())
    var ordersLoading by mutableStateOf(false)
    var ordersError by mutableStateOf<String?>(null)

    var favorites by mutableStateOf<List<Sitter>>(emptyList())
    var favoritesLoading by mutableStateOf(false)
    var favoritesError by mutableStateOf<String?>(null)

    // 通知状态统一收敛到 shared 的 NotificationCenter，避免与 Android 端重复实现。
    // toast 以回调注入（桌面 toast 机制见下方 toast() 定义）。
    val notificationsCenter = NotificationCenter(api) { toast(it) }

    var profile by mutableStateOf<UserDto?>(null)

    var ownerDynamics by mutableStateOf<List<DynamicDto>>(emptyList())
    var dynamicsLoading by mutableStateOf(false)
    var dynamicsError by mutableStateOf<String?>(null)
    // 列表接口不返回"我点过没有"，所以点赞状态只在前端乐观维护
    val likedDynamics = mutableStateListOf<Long>()

    var coupons by mutableStateOf<CouponListDto?>(null)
    var couponsLoading by mutableStateOf(false)
    var couponsError by mutableStateOf<String?>(null)

    var myShop by mutableStateOf<SitterDto?>(null)
    var shopLoading by mutableStateOf(false)
    var shopError by mutableStateOf<String?>(null)
    var shopGeoResolving by mutableStateOf(false)
    var mapPicking by mutableStateOf(false)

    var receivedOrders by mutableStateOf<List<SbOrder>>(emptyList())
    var receivedOrdersLoading by mutableStateOf(false)
    var receivedOrdersError by mutableStateOf<String?>(null)

    var capacityItems by mutableStateOf<List<CapacityItemDto>>(emptyList())
    var capacityLoading by mutableStateOf(false)
    var capacityError by mutableStateOf<String?>(null)

    var earnings by mutableStateOf<EarningsDto?>(null)
    var earningsLoading by mutableStateOf(false)
    var earningsError by mutableStateOf<String?>(null)

    var sitterReviews by mutableStateOf<List<SitterReviewDto>>(emptyList())
    var sitterReviewsLoading by mutableStateOf(false)
    var sitterReviewsError by mutableStateOf<String?>(null)

    var sitterDynamics by mutableStateOf<List<DynamicDto>>(emptyList())
    var sitterDynamicsLoading by mutableStateOf(false)
    var sitterDynamicsError by mutableStateOf<String?>(null)

    var detailCapacity by mutableStateOf<List<CapacityItemDto>>(emptyList())
    var detailReviews by mutableStateOf<List<PublicReviewDto>>(emptyList())
    var detailAddons by mutableStateOf<List<AddonDto>>(emptyList())
    var detailExtrasLoading by mutableStateOf(false)

    var settlements by mutableStateOf<List<SettlementDto>>(emptyList())
    var settlementsLoading by mutableStateOf(false)
    var settlementsError by mutableStateOf<String?>(null)

    var sitterStats by mutableStateOf<SitterStatsDto?>(null)
    var sitterStatsLoading by mutableStateOf(false)

    var priceRules by mutableStateOf<List<PriceRuleDto>>(emptyList())
    var priceRulesLoading by mutableStateOf(false)

    var sitterCoupons by mutableStateOf<List<SitterCouponDto>>(emptyList())
    var sitterCouponsLoading by mutableStateOf(false)

    // 机构自己的加购服务（含已下架）
    var myAddons by mutableStateOf<List<AddonDto>>(emptyList())
    var addonsLoading by mutableStateOf(false)

    // 宠物详情抽屉里的历史寄养记录（列表接口不返回，按需单独取）
    var petStays by mutableStateOf<List<PetStayDto>>(emptyList())

    var selectedCouponId by mutableStateOf(0L)
    var selectedCouponTitle by mutableStateOf("")
    var selectedCouponType by mutableStateOf("")
    var selectedCouponValue by mutableStateOf(0.0)
    var selectedPoints by mutableStateOf(0)

    // 下单页估价、积分、退款与工单
    var pointsBalance by mutableStateOf(0)
    var pointsHistory by mutableStateOf<List<PointsEntryDto>>(emptyList())

    // 钱包（真实货币）：概览、余额、流水、加载/错误态
    var wallet by mutableStateOf<WalletDto?>(null)
    var walletBalance by mutableStateOf(0.0)
    var walletTransactions by mutableStateOf<List<WalletTransactionDto>>(emptyList())
    var walletLoading by mutableStateOf(false)
    var walletError by mutableStateOf<String?>(null)

    var refunds by mutableStateOf<List<RefundDto>>(emptyList())
    var tickets by mutableStateOf<List<TicketDto>>(emptyList())
    var estimate by mutableStateOf<EstimateDto?>(null)
    var estimating by mutableStateOf(false)
    // >0 表示 PetEdit 处于编辑模式
    var editingPetId by mutableStateOf(0L)

    fun go(target: AppPage) {
        if (target in sitterPages && !isSitterAccount) {
            toast("当前账号没有机构权限")
            return
        }
        if (!loggedIn && target in gatedPages) {
            requestLogin(target)
            return
        }
        page = target
    }

    /** 弹出登录页；pending 为登录成功后要回到的页面。 */
    fun requestLogin(pending: AppPage? = null) {
        pendingPage = pending
        loginVisible = true
        drawer = null
        modal = null
        toast("请先登录后再继续")
    }

    fun cancelLogin() {
        loginVisible = false
        pendingPage = null
    }

    suspend fun login(loginAccount: String, password: String) {
        authBusy = true
        try {
            applyAuth(api.login(loginAccount, password))
        } catch (e: Exception) {
            toast(e.message ?: "登录失败")
        } finally {
            authBusy = false
        }
    }

    suspend fun register(loginAccount: String, password: String, phone: String) {
        authBusy = true
        try {
            // 昵称默认沿用账号名，登录后可在"个人中心 → 我的资料"修改
            applyAuth(api.register(loginAccount, password, loginAccount, phone))
        } catch (e: Exception) {
            toast(e.message ?: "注册失败")
        } finally {
            authBusy = false
        }
    }

    private suspend fun applyAuth(authResponse: AuthResponseDto) {
        // 同时记住刷新令牌：访问令牌过期后客户端会自动续期，用户不会被中途踢下线
        api.applyAuth(authResponse.accessToken, authResponse.refreshToken, authResponse.expiresIn)
        // 登录后查询一次用户信息，角色以服务端 /auth/me 为准
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
        account = me.username.ifBlank { authResponse.user.username }
        nickname = me.nickname?.takeIf { it.isNotBlank() } ?: account
        accountRole = me.role
        isSitterAccount = me.role == "sitter"
        role = AppRole.Owner
        loggedIn = true
        loginVisible = false
        // 落盘：下次启动直接进登录态（令牌 + 账号快照，昵称/角色变了也会被下次 restore 校正）
        sessionStore.saveAuth(
            token = api.token.orEmpty(),
            refreshToken = authResponse.refreshToken,
            account = account,
            nickname = nickname,
            role = accountRole,
        )
        val target = pendingPage
        pendingPage = null
        if (target != null) {
            page = target
        }
        toast(
            if (authResponse.cancelWithdrawn) {
                "已撤销注销申请，账号已恢复，欢迎 $nickname"
            } else {
                "登录成功，欢迎 $nickname"
            },
        )
        if (isSitterAccount) loadMyShop()
        loadPets(force = true)
        loadOrders(force = true)
        notificationsCenter.loadNotifications(force = true)
        loadFavorites(force = true)
    }

    /**
     * 冷启动恢复登录态。
     *
     * 先用本地快照把界面点亮（避免每次启动闪一下未登录），再用 `/auth/me` 校正角色与昵称。
     * 令牌真失效时 ApiClient 会先尝试静默续期，续不上会走 [forceLogoutExpired] 清盘退出，
     * 因此这里失败只管「保留登录态」，不做额外判定。
     */
    fun restoreSession() {
        val saved = sessionStore.current
        if (!saved.isLoggedIn) return
        api.applyAuth(saved.token, saved.refreshToken, 3600)
        account = saved.account
        nickname = saved.nickname.ifBlank { saved.account }
        accountRole = saved.role
        isSitterAccount = saved.role == "sitter"
        loggedIn = true
        scope.launch {
            try {
                val me = api.me()
                if (me.role == "admin") {
                    clearSession()
                    toast("管理员账号请使用后台管理端登录")
                    return@launch
                }
                account = me.username.ifBlank { account }
                nickname = me.nickname?.takeIf { it.isNotBlank() } ?: account
                accountRole = me.role
                isSitterAccount = me.role == "sitter"
                // 昵称 / 角色可能在服务端改过，把校正后的这一份写回本地
                sessionStore.saveAuth(
                    token = api.token.orEmpty(),
                    refreshToken = saved.refreshToken,
                    account = account,
                    nickname = nickname,
                    role = accountRole,
                )
            } catch (_: Exception) {
                // 网络异常等临时问题：保留本地登录态，页面里各自的请求会提示失败
            }
            if (isSitterAccount) loadMyShop()
            loadPets(force = true)
            loadOrders(force = true)
            notificationsCenter.loadNotifications(force = true)
            loadFavorites(force = true)
        }
    }

    fun logout() {
        val currentToken = api.token
        if (!currentToken.isNullOrBlank()) {
            scope.launch { runCatching { api.logout(currentToken) } }
        }
        clearSession()
        toast("已退出登录")
    }

    /**
     * 申请注销：account=整个账户，sitter=仅注销机构。
     * 提交成功后立即退出登录（服务端同时吊销全部刷新令牌），冷静期 3 天内重新登录自动撤销注销申请。
     */
    suspend fun requestAccountCancel(mode: String) {
        try {
            api.requestAccountCancel(mode)
            // 直接退出登录：清空本地会话并 best-effort 调用登出接口
            val currentToken = api.token
            if (!currentToken.isNullOrBlank()) {
                runCatching { api.logout(currentToken) }
            }
            clearSession()
            toast(
                if (mode == "sitter") {
                    "机构注销申请已提交，已退出登录；3 天内重新登录可撤销"
                } else {
                    "注销申请已提交，已退出登录；3 天内重新登录可撤销"
                },
            )
        } catch (e: Exception) {
            toast(e.message ?: "提交注销申请失败")
        }
    }

    /**
     * 服务端确认当前账号已不存在（如被后台删除）：
     * 静默强制退出——不回调服务端注销接口（用户行已不存在），本地令牌与全部缓存一并清空。
     */
    private fun forceLogoutOnMissingUser() {
        if (!loggedIn) return
        clearSession()
        toast("账号不存在，已退出登录")
    }

    /**
     * 登录态在服务端已经作废：清掉本地会话，直接把登录框弹出来，
     * 用户重新登录后可以继续刚才的操作（挂起页由 pendingPage 记录）。
     */
    private fun forceLogoutExpired() {
        if (!loggedIn) return
        val current = page
        clearSession()
        pendingPage = current
        loginVisible = true
        toast("登录已过期，请重新登录")
    }

    /**
     * 清空登录态与所有按用户缓存的数据。
     * 磁盘上的会话（~/.patnest）必须一并清掉，否则下次启动会被 restore 又恢复回来。
     */
    private fun clearSession() {
        // 登录态与令牌
        loggedIn = false
        isSitterAccount = false
        accountRole = "owner"
        account = ""
        nickname = ""
        pendingPage = null
        api.clearAuth()
        sessionStore.clear()
        authBusy = false

        // 主人端缓存
        pets = emptyList()
        petsLoading = false
        petsError = null
        orders = emptyList()
        ordersLoading = false
        ordersError = null
        favorites = emptyList()
        favoritesLoading = false
        favoritesError = null
        notificationsCenter.reset()
        profile = null
        ownerDynamics = emptyList()
        dynamicsLoading = false
        dynamicsError = null
        likedDynamics.clear()
        // 换账号后计数基线要重来，否则会把上一任账号的未读数当成"新消息"提示
        lastUnreadCount = -1
        lastPendingCount = -1
        coupons = null
        couponsLoading = false
        couponsError = null
        detailCapacity = emptyList()
        detailReviews = emptyList()
        detailAddons = emptyList()
        detailExtrasLoading = false
        selectedCouponId = 0L
        selectedCouponTitle = ""
        selectedCouponType = ""
        selectedCouponValue = 0.0
        selectedPoints = 0
        pointsBalance = 0
        pointsHistory = emptyList()
        wallet = null
        walletBalance = 0.0
        walletTransactions = emptyList()
        walletError = null
        refunds = emptyList()
        tickets = emptyList()
        estimate = null
        editingPetId = 0L

        // 机构端缓存
        myShop = null
        shopLoading = false
        shopError = null
        receivedOrders = emptyList()
        receivedOrdersLoading = false
        receivedOrdersError = null
        capacityItems = emptyList()
        capacityLoading = false
        capacityError = null
        earnings = null
        earningsLoading = false
        earningsError = null
        sitterReviews = emptyList()
        sitterReviewsLoading = false
        sitterReviewsError = null
        sitterDynamics = emptyList()
        sitterDynamicsLoading = false
        sitterDynamicsError = null
        settlements = emptyList()
        settlementsLoading = false
        settlementsError = null
        sitterStats = null
        sitterStatsLoading = false
        priceRules = emptyList()
        priceRulesLoading = false
        sitterCoupons = emptyList()
        sitterCouponsLoading = false

        // 视图状态
        sitterIndex = 0
        petIndex = 0
        orderIndex = 0
        dynIndex = 0
        sbOrderIndex = 0
        role = AppRole.Owner
        page = AppPage.Home
        drawer = null
        modal = null
    }

    suspend fun loadSitters(force: Boolean = false) {
        if (sittersLoading) return
        if (!force && sitters.isNotEmpty()) return
        sittersLoading = true
        sittersError = null
        try {
            sitters = api.listSitters()
        } catch (e: Exception) {
            sittersError = e.message ?: "加载机构失败"
        } finally {
            sittersLoading = false
        }
    }

    suspend fun loadPets(force: Boolean = false) {
        if (!loggedIn || petsLoading) return
        if (!force && pets.isNotEmpty()) return
        petsLoading = true
        petsError = null
        try {
            pets = api.listPets()
        } catch (e: Exception) {
            petsError = e.message ?: "加载宠物失败"
        } finally {
            petsLoading = false
        }
    }

    suspend fun createPet(
        name: String,
        typeLabel: String,
        sizeLabel: String,
        ageText: String,
        vaccinated: Boolean,
        note: String,
        avatarUrl: String = "",
    ) {
        val species = speciesCode(typeLabel)
        val bodySize = bodySizeCode(sizeLabel)
        try {
            api.createPet(
                CreatePetPayload(
                    name = name.trim(),
                    species = species,
                    bodySize = bodySize,
                    ageMonth = parseAgeMonth(ageText),
                    vaccinated = vaccinated,
                    notes = note.trim(),
                    avatarUrl = avatarUrl,
                    photoUrls = if (avatarUrl.isNotBlank()) listOf(avatarUrl) else emptyList(),
                ),
            )
            toast("宠物档案已创建")
            loadPets(force = true)
            go(AppPage.Pets)
        } catch (e: Exception) {
            toast(e.message ?: "创建宠物失败")
        }
    }

    /** 编辑已有宠物档案 */
    suspend fun updatePet(
        petId: Long,
        name: String,
        typeLabel: String,
        sizeLabel: String,
        ageText: String,
        vaccinated: Boolean,
        note: String,
        avatarUrl: String = "",
    ) {
        try {
            api.updatePet(
                petId,
                UpdatePetPayload(
                    name = name.trim(),
                    species = speciesCode(typeLabel),
                    bodySize = bodySizeCode(sizeLabel).ifBlank { null },
                    ageMonth = parseAgeMonth(ageText),
                    vaccinated = vaccinated,
                    notes = note.trim(),
                    avatarUrl = avatarUrl,
                    photoUrls = if (avatarUrl.isNotBlank()) listOf(avatarUrl) else null,
                ),
            )
            toast("宠物档案已更新")
            loadPets(force = true)
            go(AppPage.Pets)
        } catch (e: Exception) {
            toast(e.message ?: "更新宠物失败")
        }
    }

    private fun speciesCode(typeLabel: String) = when (typeLabel) {
        "猫" -> "cat"
        "狗" -> "dog"
        else -> "other"
    }

    private fun bodySizeCode(sizeLabel: String) = when (sizeLabel) {
        "迷你" -> "tiny"
        "小型" -> "small"
        "中型" -> "medium"
        "大型" -> "large"
        "超大型" -> "xlarge"
        else -> ""
    }

    suspend fun loadOrders(force: Boolean = false) {
        if (!loggedIn || ordersLoading) return
        if (!force && orders.isNotEmpty()) return
        ordersLoading = true
        ordersError = null
        try {
            orders = api.listOrders()
        } catch (e: Exception) {
            ordersError = e.message ?: "加载订单失败"
        } finally {
            ordersLoading = false
        }
    }

    /**
     * 创建订单并返回订单 id（不负责导航）。
     * 失败静默提示并返回 null；下单成功后清空已选优惠券/积分并刷新订单列表。
     */
    internal suspend fun createOrderRaw(sitterId: Long, startDate: String, endDate: String, petId: Long): Long? = try {
        val result = api.createOrder(
            CreateOrderPayload(
                sitterId = sitterId,
                startDate = startDate,
                endDate = endDate,
                pets = listOf(CreateOrderPetPayload(petId)),
                userCouponId = selectedCouponId,
                pointsUsed = selectedPoints,
            ),
        )
        selectedCouponId = 0L
        selectedCouponTitle = ""
        selectedCouponType = ""
        selectedCouponValue = 0.0
        selectedPoints = 0
        estimate = null
        loadOrders(force = true)
        val index = orders.indexOfFirst { it.id == result.orderId }
        if (index >= 0) orderIndex = index
        result.orderId
    } catch (e: Exception) {
        toast(e.message ?: "下单失败")
        null
    }

    suspend fun createOrder(sitterId: Long, startDate: String, endDate: String, petId: Long) {
        val orderId = createOrderRaw(sitterId, startDate, endDate, petId) ?: return
        toast("订单已创建，请在 30 分钟内完成支付")
        go(AppPage.Pay)
    }

    suspend fun payOrder(orderId: Long, method: String = "mock"): Boolean = try {
        api.payOrder(orderId, method)
        loadOrders(force = true)
        toast("支付成功")
        true
    } catch (e: Exception) {
        toast(e.message ?: "支付失败")
        false
    }

    /** 取消订单：后端会释放已占容量，成功后刷新列表与日历 */
    suspend fun cancelOrder(orderId: Long, reason: String): Boolean = try {
        api.cancelOrder(orderId, reason.ifBlank { "个人安排变化" })
        loadOrders(force = true)
        toast("订单已取消，容量已释放")
        true
    } catch (e: Exception) {
        toast(e.message ?: "取消失败")
        false
    }

    /** 改期：成功后费用会按新周期重算，返回值里带着新金额 */
    suspend fun rescheduleOrder(orderId: Long, startDate: String, endDate: String): Boolean = try {
        val result = api.rescheduleOrder(orderId, startDate, endDate)
        loadOrders(force = true)
        toast("已改到 ${result.startDate} ~ ${result.endDate}，费用 ¥${result.totalAmount}")
        true
    } catch (e: Exception) {
        toast(e.message ?: "改期失败")
        false
    }

    /** 续住：延长结束日期 */
    suspend fun renewOrder(orderId: Long, endDate: String): Boolean = try {
        val result = api.renewOrder(orderId, endDate)
        loadOrders(force = true)
        val paid = if (result.extraAmount > 0.0) "，已补差价 ¥${result.extraAmount}" else ""
        toast("已续住 ${result.extraDays} 天$paid，订单合计 ¥${result.totalAmount}")
        true
    } catch (e: Exception) {
        toast(e.message ?: "续住失败")
        false
    }

    /** 申请退款：进入平台审核，不是直接退钱 */
    suspend fun requestRefund(orderId: Long, reason: String): Boolean = try {
        api.requestRefund(orderId, reason)
        loadRefunds(force = true)
        toast("退款申请已提交，等待平台处理")
        true
    } catch (e: Exception) {
        toast(e.message ?: "提交失败")
        false
    }

    suspend fun loadRefunds(force: Boolean = false) {
        if (!loggedIn) return
        if (!force && refunds.isNotEmpty()) return
        try {
            refunds = api.listRefundRequests()
        } catch (e: Exception) {
            // 列表为空不是致命问题，保留旧数据即可
        }
    }

    suspend fun loadTickets(force: Boolean = false) {
        if (!loggedIn) return
        if (!force && tickets.isNotEmpty()) return
        try {
            tickets = api.listTickets()
        } catch (e: Exception) {
            // 忽略，页面上显示空列表
        }
    }

    suspend fun submitTicket(title: String, content: String, category: String = "other"): Boolean {
        if (title.isBlank() || content.isBlank()) {
            toast("请填写问题标题和描述")
            return false
        }
        return try {
            api.createTicket(category, title.trim(), content.trim())
            loadTickets(force = true)
            toast("工单已提交，平台会尽快回复")
            true
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
            false
        }
    }

    /** 积分余额；失败时静默归零，避免下单页被拖住 */
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

    /** 钱包概览与流水；失败时静默归零，避免页面被拖住（与积分一致） */
    suspend fun loadWallet(force: Boolean = false) {
        if (!loggedIn) return
        if (!force && wallet != null && walletTransactions.isNotEmpty()) return
        walletLoading = true
        walletError = null
        try {
            val overview = api.walletOverview()
            wallet = overview
            walletBalance = overview.balance
            val list = api.walletTransactions(page = 1)
            walletTransactions = list.items
        } catch (e: Exception) {
            // 拉不到不阻塞：余额归零，保留错误文案供页面提示
            walletError = e.message ?: "加载钱包失败"
            walletBalance = 0.0
        } finally {
            walletLoading = false
        }
    }

    /**
     * 发起充值：先创建充值单，再模拟确认到账，最后刷新余额与流水。
     * 返回是否成功，UI 据以关闭弹窗。
     */
    suspend fun recharge(amount: Double): Boolean {
        if (amount <= 0.0) {
            toast("请输入大于 0 的充值金额")
            return false
        }
        return try {
            val created = api.createRecharge(amount, "mock")
            if (created.rechargeId <= 0L) {
                toast("充值单创建失败")
                false
            } else {
                confirmRecharge(created.rechargeId)
            }
        } catch (e: Exception) {
            toast(e.message ?: "充值失败")
            false
        }
    }

    /** 确认一笔充值到账（演示阶段直接确认），成功后刷新钱包概览与流水 */
    suspend fun confirmRecharge(rechargeId: Long): Boolean = try {
        val result = api.confirmRecharge(rechargeId)
        walletBalance = result.balance
        wallet = (wallet ?: WalletDto()).copy(balance = result.balance)
        loadWallet(force = true)
        toast("充值成功，余额 ¥${money(result.balance)}")
        true
    } catch (e: Exception) {
        toast(e.message ?: "确认充值失败")
        false
    }

    /** 下单前实时估价。参数不全或后端报错时清空，页面退回本地估算 */
    suspend fun refreshEstimate(
        sitterId: Long,
        startDate: String,
        endDate: String,
        petIds: List<Long>,
    ) {
        if (sitterId <= 0 || petIds.isEmpty() || startDate.isBlank() || endDate.isBlank()) {
            estimate = null
            return
        }
        estimating = true
        try {
            estimate = api.estimateOrder(
                sitterId = sitterId,
                startDate = startDate,
                endDate = endDate,
                petIds = petIds,
                userCouponId = selectedCouponId,
                pointsUsed = selectedPoints,
            )
        } catch (_: Exception) {
            // 日期还在改、还没选宠物都会失败，这里不弹提示，页面自己显示"本地估算"
            estimate = null
        } finally {
            estimating = false
        }
    }

    suspend fun submitReview(
        orderId: Long,
        overall: Int,
        service: Int,
        environment: Int,
        hygiene: Int,
        comment: String,
    ) {
        try {
            api.submitReview(
                orderId,
                ReviewPayload(
                    overall, service, environment, hygiene,
                    comment.ifBlank { "服务满意，感谢照料。" }.take(1000),
                ),
            )
            loadOrders(force = true)
            toast("评价已提交")
            go(AppPage.Orders)
        } catch (e: Exception) {
            toast(e.message ?: "评价提交失败")
        }
    }

    // 轮询判断"是否真有新东西"的基线计数；-1 表示还没建立基线，那一轮不提示
    private var lastUnreadCount = -1
    private var lastPendingCount = -1

    /**
     * 每 5 秒一次的静默刷新。桌面端经常一开就是很久，新订单、新动态、新消息要自己冒出来，
     * 不该等用户去点"刷新"。
     *
     * 抽屉和弹框都是按列表下标取数据的，中途刷新会让内容错位，所以它们打开时只刷通知；
     * 列表已有内容时各页面不闪加载骨架，用户基本无感。
     */
    suspend fun autoRefresh() {
        if (!loggedIn) return
        notificationsCenter.loadNotifications(force = true)
        if (drawer != null || modal != null || loginVisible) return

        when (role) {
            AppRole.Sitter -> {
                loadReceivedOrders(force = true)
                loadCapacity(force = true)
                loadSitterStats(force = true)
            }
            else -> {
                loadOrders(force = true)
                loadOwnerDynamics(force = true)
            }
        }
        // 只刷当前这一屏会变的业务数据，切页后下一轮自然换成另一套；
        // 找机构这类浏览列表变化不频繁，不值得每 5 秒重拉一次
        when (page) {
            AppPage.Pets -> loadPets(force = true)
            AppPage.Coupons -> loadCoupons(force = true)
            AppPage.Wallet -> loadWallet(force = true)
            AppPage.SbDash -> loadEarnings(force = true)
            AppPage.SbReviews -> loadSitterReviews(force = true)
            AppPage.SbCoupons -> loadSitterCoupons(force = true)
            AppPage.SbPublish -> loadSitterDynamics(force = true)
            AppPage.SbIncome -> {
                loadEarnings(force = true)
                loadSettlements(force = true)
            }
            else -> Unit
        }
        announceNewArrivals()
    }

    /** 只有数量真的变多才提示，且不打断正在显示的提示 */
    private fun announceNewArrivals() {
        val unread = notificationsCenter.notifications.count { !it.isRead }
        if (lastUnreadCount >= 0 && unread > lastUnreadCount && toastMessage == null) {
            toast("有 ${unread - lastUnreadCount} 条新消息")
        }
        lastUnreadCount = unread

        if (role == AppRole.Sitter) {
            val pending = receivedOrders.count { it.status == "待确认" }
            if (lastPendingCount >= 0 && pending > lastPendingCount && toastMessage == null) {
                toast("有 ${pending - lastPendingCount} 个新订单待确认")
            }
            lastPendingCount = pending
        }
    }

    suspend fun loadFavorites(force: Boolean = false) {
        if (!loggedIn || favoritesLoading) return
        if (!force && favorites.isNotEmpty()) return
        favoritesLoading = true
        favoritesError = null
        try {
            favorites = api.listFavorites()
        } catch (e: Exception) {
            favoritesError = e.message ?: "加载收藏失败"
        } finally {
            favoritesLoading = false
        }
    }

    suspend fun addFavorite(sitterId: Long) {
        try {
            api.addFavorite(sitterId)
            toast("已收藏")
            loadFavorites(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "收藏失败")
        }
    }

    suspend fun removeFavorite(sitterId: Long) {
        try {
            api.removeFavorite(sitterId)
            favorites = favorites.filterNot { it.id == sitterId }
            toast("已取消收藏")
        } catch (e: Exception) {
            toast(e.message ?: "取消收藏失败")
        }
    }

    suspend fun loadProfile() {
        if (!loggedIn) return
        try {
            profile = api.me()
            profile?.nickname?.takeIf { it.isNotBlank() }?.let { nickname = it }
        } catch (e: Exception) {
            toast(e.message ?: "加载资料失败")
        }
    }

    suspend fun saveProfile(
        nicknameInput: String,
        phone: String,
        address: String,
        contactName: String,
        contactPhone: String,
    ) {
        try {
            api.updateProfile(UpdateProfilePayload(nicknameInput, phone, address, contactName, contactPhone))
            nickname = nicknameInput
            profile = api.me()
            toast("资料已保存")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        try {
            api.changePassword(oldPassword, newPassword)
            modal = null
            toast("密码已修改")
        } catch (e: Exception) {
            toast(e.message ?: "修改失败")
        }
    }

    /** 机构入驻申请：提交营业执照 / 身份证等资质材料；审核期间账号保持主人角色，审核通过后升级为机构。 */
    suspend fun applySitter(
        name: String,
        typeLabel: String,
        contactPerson: String,
        address: String,
        licensePhotoUrl: String,
        idCardPhotoUrl: String,
        description: String,
    ) {
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
                    licensePhotoUrl = licensePhotoUrl,
                    idCardPhotoUrl = idCardPhotoUrl,
                    address = address.trim(),
                    description = description.trim(),
                ),
            )
            modal = null
            toast("机构申请已提交，等待平台审核")
            loadProfile()
        } catch (e: Exception) {
            toast(e.message ?: "申请失败")
        }
    }

    suspend fun loadPriceRules(force: Boolean = false) {
        if (!loggedIn || priceRulesLoading) return
        if (!force && priceRules.isNotEmpty()) return
        priceRulesLoading = true
        try {
            priceRules = api.listPriceRules()
        } catch (_: Exception) {
        } finally {
            priceRulesLoading = false
        }
    }

    suspend fun createPriceRule(name: String, startDate: String, endDate: String, multiplier: Double) {
        try {
            api.createPriceRule(PriceRulePayload(name.trim(), startDate, endDate, multiplier))
            toast("定价规则已保存")
            loadPriceRules(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    /* ---------- 机构优惠券管理 ---------- */

    suspend fun loadSitterCoupons(force: Boolean = false) {
        if (!loggedIn || sitterCouponsLoading) return
        if (!force && sitterCoupons.isNotEmpty()) return
        sitterCouponsLoading = true
        try {
            sitterCoupons = api.listSitterCoupons().items
        } catch (e: Exception) {
            toast(e.message ?: "加载优惠券失败")
        } finally {
            sitterCouponsLoading = false
        }
    }

    /** 新建或编辑机构优惠券（coupon 为 null 时新建），成功后刷新列表并关闭弹窗。 */
    suspend fun saveSitterCoupon(coupon: SitterCouponDto?, payload: SitterCouponPayload) {
        try {
            if (coupon == null) api.createSitterCoupon(payload) else api.updateSitterCoupon(coupon.id, payload)
            modal = null
            toast(if (coupon == null) "优惠券已发布" else "优惠券已保存")
            loadSitterCoupons(force = true)
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
            loadSitterCoupons(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun deleteSitterCoupon(coupon: SitterCouponDto) {
        try {
            api.deleteSitterCoupon(coupon.id)
            toast("优惠券已删除")
            loadSitterCoupons(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }
    suspend fun uploadImage(fileName: String, bytes: ByteArray): String? = try {
        api.uploadImage(bytes, fileName)
    } catch (e: Exception) {
        toast(e.message ?: "图片上传失败")
        null
    }


    suspend fun updatePetVaccine(petId: Long, urls: List<String>) {
        try {
            api.updatePetVaccine(petId, urls)
            toast("免疫证明已提交，等待核验")
            loadPets(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }

    suspend fun deletePet(petId: Long) {
        try {
            api.deletePet(petId)
            drawer = null
            toast("宠物档案已删除")
            loadPets(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun loadOwnerDynamics(force: Boolean = false) {
        if (!loggedIn || dynamicsLoading) return
        if (!force && ownerDynamics.isNotEmpty()) return
        dynamicsLoading = true
        dynamicsError = null
        try {
            ownerDynamics = api.listOwnerDynamics()
        } catch (e: Exception) {
            dynamicsError = e.message ?: "加载寄养动态失败"
        } finally {
            dynamicsLoading = false
        }
    }

    /** 点赞 / 取消点赞。之前这里只弹一句"已点赞"，其实后端接口早就有了，现在真的写进库。 */
    suspend fun toggleDynamicLike(dynamicId: Long) {
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
                val result = api.likeDynamic(dynamicId)
                if (!likedDynamics.contains(dynamicId)) likedDynamics.add(dynamicId)
                toast(if (result.liked) "已点赞" else "点赞已记录")
            }
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun loadCoupons(force: Boolean = false) {
        if (!loggedIn || couponsLoading) return
        if (!force && coupons != null) return
        couponsLoading = true
        couponsError = null
        try {
            coupons = api.listCoupons()
        } catch (e: Exception) {
            couponsError = e.message ?: "加载优惠券失败"
        } finally {
            couponsLoading = false
        }
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

    suspend fun loadMyShop(force: Boolean = false) {
        if (!loggedIn || shopLoading) return
        if (!force && myShop != null) return
        shopLoading = true
        shopError = null
        try {
            myShop = api.getMyShop()
            loadMyAddons()
        } catch (e: Exception) {
            shopError = e.message ?: "加载机构资料失败"
        } finally {
            shopLoading = false
        }
    }

    suspend fun loadMyAddons() {
        addonsLoading = true
        try {
            myAddons = api.myAddons()
        } catch (e: Exception) {
            // 服务项不是主流程，失败时留空列表即可
        } finally {
            addonsLoading = false
        }
    }

    /** 某只宠物的历史寄养记录；换宠物查看时先清空，避免显示上一只的记录 */
    suspend fun loadPetStays(petId: Long) {
        if (petId <= 0L) return
        petStays = emptyList()
        try {
            petStays = api.listPetStays(petId)
        } catch (e: Exception) {
            // 没有记录时留空即可，不打断查看档案
        }
    }

    suspend fun createAddon(name: String, price: Double): Boolean {
        if (name.isBlank()) {
            toast("请填写服务名称")
            return false
        }
        if (price <= 0.0) {
            toast("价格需大于 0")
            return false
        }
        return try {
            api.createAddon(AddonPayload(name.trim(), price))
            loadMyAddons()
            toast("加购服务已上架")
            true
        } catch (e: Exception) {
            toast(e.message ?: "新增失败")
            false
        }
    }

    /** 上架 / 下架：后端用 enabled 标记，主人端只看得见 enabled 的项 */
    suspend fun toggleAddon(addonId: Long, enabled: Boolean) {
        try {
            api.updateAddon(addonId, AddonUpdatePayload(enabled = enabled))
            loadMyAddons()
            toast(if (enabled) "已重新上架" else "已下架")
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun removePriceRule(ruleId: Long) {
        try {
            api.deletePriceRule(ruleId)
            loadPriceRules(force = true)
            toast("节假日规则已删除")
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
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
        coverPhotoUrl: String = "",
        detailPhotos: List<String> = emptyList(),
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        try {
            myShop = api.updateMyShop(
                UpdateShopPayload(
                    name = name,
                    type = type,
                    description = description,
                    dailyPrice = dailyPrice,
                    visitPrice = visitPrice,
                    priceMode = priceMode,
                    capacity = capacity,
                    address = address,
                    coverPhotoUrl = coverPhotoUrl,
                    detailPhotos = detailPhotos,
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
            toast("机构资料已保存")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
    }

    /**
     * 地址 → 坐标。桌面端没有系统定位，靠后端调高德地理编码；
     * 解析成功只更新页面状态，仍需点"保存资料"才落库。
     */
    suspend fun resolveShopAddress(address: String, city: String): GeocodeDto? {
        if (address.isBlank()) {
            toast("请先填写所在地址")
            return null
        }
        if (shopGeoResolving) return null
        shopGeoResolving = true
        return try {
            val result = api.geocodeAddress(GeocodePayload(address.trim(), city.trim()))
            toast(if (result.fromCache) "坐标已解析（读自缓存）" else "坐标已解析，记得保存资料")
            result
        } catch (e: Exception) {
            toast(e.message ?: "地址解析失败")
            null
        } finally {
            shopGeoResolving = false
        }
    }

    /**
     * 地图选点。Compose Desktop 没有内嵌浏览器，也不值得为选点引入 JCEF：
     * 改成让后端发一张一次性选点页，用系统浏览器打开，用户选完点后这里轮询取回坐标。
     * 令牌 5 分钟过期、取走即销毁；轮询超过有效期再多等一会儿就放弃。
     */
    suspend fun pickLocationOnMap(
        placeName: String,
        latitude: Double?,
        longitude: Double?,
    ): GeocodeDto? {
        if (mapPicking) return null
        mapPicking = true
        try {
            val session = runCatching {
                api.createGeoPick(GeoPickPayload(latitude, longitude, placeName))
            }.getOrElse {
                toast(it.message ?: "地图选点暂时不可用")
                return null
            }
            if (!openUrl(api.baseUrl + session.path)) {
                toast("无法打开系统浏览器，请改用“按地址解析坐标”")
                return null
            }
            toast("已在浏览器打开选点页，选完位置即可回来")

            val deadline = System.currentTimeMillis() + (session.expiresIn.coerceAtLeast(30) + 20) * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(800)
                // 单次轮询失败不中断：网络抖一下不该让整个选点作废
                val result = runCatching { api.pollGeoPick(session.token) }.getOrNull() ?: continue
                when (result.status) {
                    "done" -> return GeocodeDto(
                        latitude = result.latitude,
                        longitude = result.longitude,
                        formattedAddress = result.formattedAddress,
                    )
                    "expired" -> {
                        toast("选点链接已过期，请重新发起")
                        return null
                    }
                }
            }
            toast("没有等到选点结果，可重新发起或改用地址解析")
            return null
        } finally {
            mapPicking = false
        }
    }

    /**
     * 取一张以该点为中心的静态地图 PNG。
     * 失败时把后端原文带出来（404=还没出包、501=没配 AMAP_KEY、其它=高德或网络），
     * 别在这里把原因吞成一句"配额或网络问题"。
     */
    suspend fun loadMapImage(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        width: Int,
        height: Int,
    ): Result<ByteArray> = runCatching {
        api.fetchStaticMap(latitude, longitude, zoom, width, height)
    }

    /** 坐标 → 地址文本，用于选点时实时显示位置说明。 */
    suspend fun reverseLocation(latitude: Double, longitude: Double): GeocodeDto? = runCatching {
        api.reverseGeocode(ReversePayload(latitude, longitude))
    }.getOrNull()

    /**
     * 取一张地图瓦片。单张失败不该打断整幅图，所以这里只回 Result，
     * 由弹框决定留空还是重试。
     */
    suspend fun loadTile(zoom: Int, x: Int, y: Int): Result<ByteArray> = runCatching {
        api.fetchTile(zoom, x, y)
    }

    suspend fun loadReceivedOrders(force: Boolean = false) {
        if (!loggedIn || receivedOrdersLoading) return
        if (!force && receivedOrders.isNotEmpty()) return
        receivedOrdersLoading = true
        receivedOrdersError = null
        try {
            receivedOrders = api.listReceivedOrders().map { it.toUiOrder() }
        } catch (e: Exception) {
            receivedOrdersError = e.message ?: "加载订单失败"
        } finally {
            receivedOrdersLoading = false
        }
    }

    suspend fun acceptReceivedOrder(id: Long) {
        try {
            api.acceptOrder(id)
            toast("已确认接单")
            loadReceivedOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun rejectReceivedOrder(id: Long) {
        try {
            api.rejectOrder(id, "机构暂时无法接待")
            toast("已拒绝该订单")
            loadReceivedOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun completeReceivedOrder(id: Long) {
        try {
            api.completeOrder(id)
            toast("订单已完成")
            loadReceivedOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "操作失败")
        }
    }

    suspend fun loadCapacity(force: Boolean = false) {
        if (!loggedIn || capacityLoading) return
        if (!force && capacityItems.isNotEmpty()) return
        capacityLoading = true
        capacityError = null
        try {
            val today = java.time.LocalDate.now()
            capacityItems = api.myCapacity(today.toString(), today.plusDays(6).toString()).items
        } catch (e: Exception) {
            capacityError = e.message ?: "加载容量失败"
        } finally {
            capacityLoading = false
        }
    }

    /** 机构调整指定日期的接待容量（按日覆盖店铺默认容量）。 */
    suspend fun setCapacity(date: String, capacity: Int) {
        try {
            api.setMyCapacity(date, capacity)
            modal = null
            toast("已更新 $date 的接待容量")
            loadCapacity(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "容量调整失败")
        }
    }
    suspend fun loadEarnings(force: Boolean = false) {
        if (!loggedIn || earningsLoading) return
        if (!force && earnings != null) return
        earningsLoading = true
        earningsError = null
        try {
            val today = java.time.LocalDate.now()
            earnings = api.sitterEarnings(today.minusDays(29).toString(), today.toString())
        } catch (e: Exception) {
            earningsError = e.message ?: "加载收益失败"
        } finally {
            earningsLoading = false
        }
    }

    suspend fun loadSitterReviews(force: Boolean = false) {
        if (!loggedIn || sitterReviewsLoading) return
        if (!force && sitterReviews.isNotEmpty()) return
        sitterReviewsLoading = true
        sitterReviewsError = null
        try {
            sitterReviews = api.listSitterReviews()
        } catch (e: Exception) {
            sitterReviewsError = e.message ?: "加载评价失败"
        } finally {
            sitterReviewsLoading = false
        }
    }

    suspend fun replyReview(reviewId: Long, text: String) {
        try {
            api.replyReview(reviewId, text.take(500))
            toast("回复已发送")
            loadSitterReviews(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "回复失败")
        }
    }

    suspend fun loadSitterDynamics(force: Boolean = false) {
        if (!loggedIn || sitterDynamicsLoading) return
        if (!force && sitterDynamics.isNotEmpty()) return
        sitterDynamicsLoading = true
        sitterDynamicsError = null
        try {
            sitterDynamics = api.listSitterDynamics()
        } catch (e: Exception) {
            sitterDynamicsError = e.message ?: "加载动态失败"
        } finally {
            sitterDynamicsLoading = false
        }
    }

    fun toast(message: String) {
        toastMessage = message
    }

    fun openSitterDetail(index: Int) {
        sitterIndex = index
        go(AppPage.SitterDetail)
    }

    fun openBooking(sitterIdx: Int = sitterIndex) {
        sitterIndex = sitterIdx
        go(AppPage.Booking)
    }

    fun openPay(orderIdx: Int = orderIndex) {
        orderIndex = orderIdx
        go(AppPage.Pay)
    }

    fun openReview(orderIdx: Int = orderIndex) {
        orderIndex = orderIdx
        go(AppPage.Review)
    }

    fun switchRole(target: AppRole) {
        if (!loggedIn) {
            requestLogin()
            return
        }
        if (!isSitterAccount) {
            toast("当前账号不是机构账号")
            return
        }
        role = target
        page = if (target == AppRole.Owner) AppPage.Home else AppPage.SbDash
        if (target == AppRole.Sitter) {
            scope.launch { loadReceivedOrders() }
        }
    }

    suspend fun submitDynamic(orderId: Long, text: String, photos: List<String> = emptyList()) {
        if (orderId == 0L) {
            toast("暂无可发布动态的寄养订单")
            return
        }
        try {
            api.publishDynamic(orderId, text.ifBlank { "宠物状态良好，按时投喂。" }.take(2000), photos)
            toast("动态已提交")
            loadSitterDynamics(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "发布失败")
        }
    }

    suspend fun loadSitterDetailExtras(sitterId: Long) {
        if (sitterId == 0L || detailExtrasLoading) return
        detailExtrasLoading = true
        try {
            val today = java.time.LocalDate.now()
            detailCapacity = runCatching {
                api.sitterCapacityPublic(sitterId, today.toString(), today.plusDays(6).toString()).items
            }.getOrDefault(emptyList())
            detailReviews = runCatching { api.sitterReviewsPublic(sitterId) }.getOrDefault(emptyList())
            detailAddons = runCatching { api.sitterAddons(sitterId) }.getOrDefault(emptyList())
        } finally {
            detailExtrasLoading = false
        }
    }

    suspend fun loadSettlements(force: Boolean = false) {
        if (!loggedIn || settlementsLoading) return
        if (!force && settlements.isNotEmpty()) return
        settlementsLoading = true
        settlementsError = null
        try {
            settlements = api.listSettlements()
        } catch (e: Exception) {
            settlementsError = e.message ?: "加载结算失败"
        } finally {
            settlementsLoading = false
        }
    }

    suspend fun applySettlement() {
        val today = java.time.LocalDate.now()
        try {
            api.applySettlement(today.withDayOfMonth(1).toString(), today.toString())
            toast("结算申请已提交")
            loadSettlements(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "申请失败")
        }
    }

    suspend fun loadSitterStats(force: Boolean = false) {
        if (!loggedIn || sitterStatsLoading) return
        if (!force && sitterStats != null) return
        sitterStatsLoading = true
        try {
            sitterStats = api.sitterStats()
        } catch (_: Exception) {
            // 统计失败不影响页面其他数据
        } finally {
            sitterStatsLoading = false
        }
    }

    suspend fun deleteSitterDynamic(dynamicId: Long) {
        try {
            api.deleteSitterDynamic(dynamicId)
            toast("动态已删除")
            loadSitterDynamics(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "删除失败")
        }
    }

    suspend fun contactSitter(sitterId: Long) {
        try {
            api.contactSitter(sitterId, "您好，我想咨询一下寄养服务。")
            toast("已发送联系请求")
        } catch (e: Exception) {
            toast(e.message ?: "发送失败")
        }
    }

    companion object {
        /** 游客可浏览首页、找机构与机构详情，其余页面需要登录。 */
        private val gatedPages = setOf(
            AppPage.Pets, AppPage.PetEdit, AppPage.Booking, AppPage.Pay,
            AppPage.Orders, AppPage.Dynamics, AppPage.Review, AppPage.Profile,
            AppPage.Favorites, AppPage.Coupons, AppPage.Messages, AppPage.Wallet,
            AppPage.SbDash, AppPage.SbOrders, AppPage.SbCapacity, AppPage.SbPublish,
            AppPage.SbReviews, AppPage.SbIncome, AppPage.SbProfile,
        )

        /** 只有机构账号才能进入的页面。 */
        private val sitterPages = setOf(
            AppPage.SbDash, AppPage.SbOrders, AppPage.SbCapacity, AppPage.SbPublish,
            AppPage.SbReviews, AppPage.SbIncome, AppPage.SbProfile, AppPage.SbCoupons,
        )
    }
}

/**
 * 应用根：启动页 → 登录页 → 主界面。titleBar 由桌面端注入（窗口拖拽与最小化/关闭）。
 */
@Composable
fun AppRoot(
    titleBar: @Composable () -> Unit = {},
    /** 窗口是否最小化到任务栏；由 main.kt 传入，最小化时暂停轮询 */
    isMinimized: () -> Boolean = { false },
) {
    var screen by remember { mutableStateOf(Screen.Launch) }
    val scope = rememberCoroutineScope()
    val app = remember { AppState().also { it.scope = scope } }

    // 冷启动先恢复会话：本地有令牌就直接进登录态，不用每次重新输密码
    LaunchedEffect(Unit) { app.restoreSession() }

    LaunchedEffect(screen) {
        if (screen == Screen.Launch) {
            delay(1500)
            screen = Screen.Main
            app.loadSitters()
        }
    }

    // 每 5 秒拉一次当前视角需要的数据；最小化到任务栏时暂停，回到前台下一轮自然补上。
    LaunchedEffect(app.loggedIn) {
        if (!app.loggedIn) return@LaunchedEffect
        while (true) {
            // 后台循环不能因为一次意外异常就把整个应用带崩
            if (!isMinimized()) runCatching { app.autoRefresh() }
            delay(AUTO_REFRESH_MILLIS)
        }
    }

    val toast = app.toastMessage
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            app.toastMessage = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(Modifier.fillMaxSize()) {
            titleBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    Screen.Launch -> DesktopSplash()
                    Screen.Main -> if (app.loginVisible) {
                        LoginPage(
                            busy = app.authBusy,
                            onLogin = { account, password ->
                                scope.launch { app.login(account, password) }
                            },
                            onRegister = { account, password, phone ->
                                scope.launch { app.register(account, password, phone) }
                            },
                            onCancel = { app.cancelLogin() },
                            onError = { app.toast(it) },
                        )
                    } else {
                        MainShell(app)
                    }
                }
            }
        }

        ToastOverlay(app.toastMessage)
        DrawerOverlay(app)
        ModalOverlay(app)
    }
}

@Composable
private fun BoxScope.ToastOverlay(message: String?) {
    if (message != null) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .patShadowSoft(14.dp, PatNestShape.Md)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF241A1E))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(message, color = Color.White, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun BoxScope.DrawerOverlay(app: AppState) {
    val kind = app.drawer ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66241A1E))
            .clickable { app.drawer = null },
    )
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(430.dp)
            .patShadow(24.dp, RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
            .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(drawerTitle(kind), color = PatNestColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(26.dp).clickable { app.drawer = null }, contentAlignment = Alignment.Center) {
                AppIcon(AppIcon.Close, Modifier.size(16.dp), PatNestColors.Ink3)
            }
        }
        ThinDivider()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            DrawerBody(app, kind)
        }
        ThinDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            DrawerFooter(app, kind)
        }
    }
}

private fun drawerTitle(kind: DrawerKind): String = when (kind) {
    is DrawerKind.PetDetail -> "宠物档案"
    is DrawerKind.OrderDetail -> "订单详情"
    is DrawerKind.DynDetail -> "动态详情"
    is DrawerKind.SbOrderDetail -> "机构订单详情"
}

@Composable
private fun DrawerBody(app: AppState, kind: DrawerKind) {
    when (kind) {
        is DrawerKind.PetDetail -> {
            val p = app.pets.getOrNull(kind.index) ?: return
            LaunchedEffect(p.id) { app.loadPetStays(p.id) }
            val stays = app.petStays
            listOf(
                "宠物名" to p.name,
                "品种" to p.breed,
                "类型" to p.type,
                "体型" to p.size,
                "年龄" to p.age,
                "疫苗情况" to p.vaccine,
                "免疫证明" to p.cert,
                "累计寄养" to "${stays.size} 单",
                "健康备注" to p.note,
            ).forEach { (k, v) -> KeyValueRow(k, v) }
            if (stays.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FieldLabel("寄养记录")
                stays.take(6).forEach { stay ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stay.sitterName.ifBlank { "机构已注销" },
                                color = PatNestColors.Ink, fontSize = 12.5.sp,
                            )
                            Text(
                                "${stay.startDate} ~ ${stay.endDate} · ${stay.totalDays} 天",
                                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                            )
                        }
                        Text(
                            orderStatusLabelOf(stay.status),
                            color = PatNestColors.Ink2, fontSize = 12.sp,
                        )
                    }
                    ThinDivider()
                }
            }
            if (p.photos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FieldLabel("宠物照片")
                PhotoStrip(p.photos) { url -> app.modal = ModalKind.Photo(url, p.name) }
            }
            if (p.vaccinePhotos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FieldLabel("免疫证明")
                PhotoStrip(p.vaccinePhotos) { url -> app.modal = ModalKind.Photo(url, "${p.name} 免疫证明") }
                if (p.vaccineNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("审核备注：${p.vaccineNote}", color = PatNestColors.Ink3, fontSize = 12.sp)
                }
            }
        }
        is DrawerKind.OrderDetail -> {
            val o = app.orders.getOrNull(kind.index) ?: return
            listOf(
                "订单号" to o.no,
                "机构" to o.org,
                "宠物" to o.pet,
                "寄养区间" to o.range,
                "天数 / 次数" to o.days,
                "费用构成" to o.fee,
                "应付金额" to "¥${o.amount}",
                "下单时间" to o.made,
                "订单状态" to o.status,
            ).forEach { (k, v) -> KeyValueRow(k, v) }
            // 退款申请进度：只有下过单才有意义，加载失败就当没有
            LaunchedEffect(o.id) {
                if (o.id > 0) app.loadRefunds()
            }
            val mine = app.refunds.filter { it.orderId == o.id }
            if (mine.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FieldLabel("退款申请")
                mine.forEach { r ->
                    KeyValueRow(
                        refundStatusLabel(r.status),
                        "¥${money(r.amount)} · ${r.reason.ifBlank { "未填写原因" }}",
                    )
                }
            }
        }
        is DrawerKind.DynDetail -> {
            val d = app.ownerDynamics.getOrNull(kind.index) ?: return
            listOf(
                "发布时间" to d.createdAt.replace('T', ' ').take(16),
                "动态内容" to d.content,
            ).forEach { (k, v) -> KeyValueRow(k, v) }
            if (d.photos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FieldLabel("照片 ${d.photos.size} 张")
                PhotoStrip(d.photos) { url -> app.modal = ModalKind.Photo(url, "寄养动态") }
            }
        }
        is DrawerKind.SbOrderDetail -> {
            val o = app.receivedOrders.getOrNull(kind.index) ?: return
            listOf(
                "订单号" to o.no,
                "主人" to o.owner,
                "宠物" to o.pet,
                "寄养区间" to o.range,
                "天数" to o.days,
                "费用构成" to "¥${o.amount}",
                "下单时间" to o.made,
                "订单状态" to o.status,
            ).forEach { (k, v) -> KeyValueRow(k, v) }
        }
    }
}

@Composable
private fun DrawerFooter(app: AppState, kind: DrawerKind) {
    when (kind) {
        is DrawerKind.PetDetail -> {
            app.pets.getOrNull(kind.index)?.let { p ->
                GhostButton(
                    "删除",
                    {
                        app.modal = ModalKind.Confirm(
                            "删除宠物档案",
                            "确定删除「${p.name}」吗？删除后该宠物不会再出现在预约列表里。",
                        ) {
                            app.scope.launch { app.deletePet(p.id) }
                        }
                    },
                    textColor = PatNestColors.Danger,
                )
            }
            GhostButton("关闭", { app.drawer = null })
            PrimaryButton("预约此宠物", {
                app.drawer = null
                app.go(AppPage.Booking)
            })
        }
        is DrawerKind.OrderDetail -> {
            val o = app.orders.getOrNull(kind.index) ?: return
            GhostButton("关闭", { app.drawer = null })
            when (o.status) {
                "待支付" -> PrimaryButton("去支付", {
                    app.drawer = null
                    app.openPay(kind.index)
                })
                "已结束" -> PrimaryButton("去评价", {
                    app.drawer = null
                    app.openReview(kind.index)
                })
                else -> PrimaryButton("查看动态", {
                    app.drawer = null
                    app.go(AppPage.Dynamics)
                })
            }
        }
        is DrawerKind.DynDetail -> {
            GhostButton("关闭", { app.drawer = null })
            val d = app.ownerDynamics.getOrNull(kind.index)
            if (d != null) {
                PrimaryButton(
                    if (app.likedDynamics.contains(d.id)) "已点赞" else "点赞",
                    { app.scope.launch { app.toggleDynamicLike(d.id) } },
                )
            }
        }
        is DrawerKind.SbOrderDetail -> {
            val o = app.receivedOrders.getOrNull(kind.index) ?: return
            GhostButton("关闭", { app.drawer = null })
            when (o.status) {
                "待确认" -> {
                    DangerLinkText("拒绝") {
                        app.drawer = null
                        app.scope.launch { app.rejectReceivedOrder(o.id) }
                    }
                    PrimaryButton("确认接单", {
                        app.drawer = null
                        app.scope.launch { app.acceptReceivedOrder(o.id) }
                    })
                }
                "寄养中", "已接单" -> PrimaryButton("标记完成", {
                    app.drawer = null
                    app.scope.launch { app.completeReceivedOrder(o.id) }
                })
            }
        }
    }
}

@Composable
private fun BoxScope.ModalOverlay(app: AppState) {
    val modal = app.modal ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66241A1E))
            .clickable { app.modal = null },
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .width(if (modal is ModalKind.MapPicker || modal is ModalKind.Photo) 760.dp else 420.dp)
            .patShadow(30.dp, PatNestShape.Xl)
            .clip(PatNestShape.Xl)
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (modal) {
                    is ModalKind.Confirm -> modal.title
                    ModalKind.Publish -> "发布寄养动态"
                    ModalKind.ChangePassword -> "修改密码"
                    ModalKind.ApplySitter -> "机构入驻申请"
                    ModalKind.PriceRule -> "节假日定价"
                    is ModalKind.CouponForm -> if (modal.coupon == null) "发布优惠券" else "编辑优惠券"
                    is ModalKind.AdjustCapacity -> "调整容量 · ${modal.date}"
                    is ModalKind.MapPicker -> "地图选点"
                    is ModalKind.Photo -> "图片预览"
                    is ModalKind.CancelOrder -> "取消订单"
                    is ModalKind.Reschedule -> "申请改期"
                    is ModalKind.Renew -> "续住"
                    is ModalKind.Refund -> "申请退款"
                    ModalKind.Ticket -> "联系客服"
                },
                color = PatNestColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(26.dp).clickable { app.modal = null }, contentAlignment = Alignment.Center) {
                AppIcon(AppIcon.Close, Modifier.size(16.dp), PatNestColors.Ink3)
            }
        }
        ThinDivider()
        // 地图弹框不能套 verticalScroll：拖动地图的手势会被滚动容器吃掉
        val bareModal = modal is ModalKind.MapPicker || modal is ModalKind.Photo
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (bareModal) Modifier
                    else Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            ModalBody(app, modal)
        }
        if (modal is ModalKind.Confirm) {
            ThinDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                GhostButton("取消", { app.modal = null })
                PrimaryButton("确定", {
                    app.modal = null
                    modal.onOk()
                })
            }
        }
    }
}

@Composable
private fun ModalBody(app: AppState, modal: ModalKind) {
    when (modal) {
        is ModalKind.Confirm -> Text(
            modal.message,
            color = PatNestColors.Ink2, fontSize = 14.sp, lineHeight = 22.sp,
        )
        ModalKind.Publish -> {
            val activeOrders = app.receivedOrders.filter {
                it.status == "寄养中" || it.status == "已接单" || it.status == "待确认"
            }
            fun orderLabel(order: SbOrder) = "${order.no} · ${order.owner}"
            var selectedOrderId by remember { mutableStateOf(activeOrders.firstOrNull()?.id ?: 0L) }
            var text by remember { mutableStateOf("") }
            var photoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
            var uploadingPhoto by remember { mutableStateOf(false) }
            FieldLabel("寄养订单")
            if (activeOrders.isEmpty()) {
                Text("暂无可发布动态的寄养订单", color = PatNestColors.Ink2, fontSize = 13.sp)
            } else {
                ChipRow(
                    options = activeOrders.map { orderLabel(it) },
                    selected = activeOrders.firstOrNull { it.id == selectedOrderId }?.let { orderLabel(it) } ?: "",
                    onSelect = { label ->
                        activeOrders.firstOrNull { orderLabel(it) == label }?.let { selectedOrderId = it.id }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            FieldLabel("动态内容")
            SoftTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "记录宠物近况、喂食、遛宠情况……",
                singleLine = false,
                minHeight = 84.dp,
            )
            Spacer(Modifier.height(12.dp))
            FieldLabel("照片")
            DashedUploadBox(
                if (photoUrls.isEmpty()) "点击上传照片（最多 9 张）" else "已上传 ${photoUrls.size} 张，点击继续添加",
            ) {
                if (uploadingPhoto) return@DashedUploadBox
                if (photoUrls.size >= 9) {
                    app.toast("最多上传 9 张照片")
                    return@DashedUploadBox
                }
                val file = team.wd.atnestapp.ui.util.chooseImageFile() ?: return@DashedUploadBox
                app.scope.launch {
                    uploadingPhoto = true
                    try {
                        val url = app.uploadImage(file.name, file.readBytes())
                        if (url != null) photoUrls = photoUrls + url
                    } finally {
                        uploadingPhoto = false
                    }
                }
            }
            if (photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photoUrls.forEach { url ->
                        Box {
                            RemoteImage(
                                url = url,
                                modifier = Modifier.size(76.dp, 56.dp),
                                contentDescription = "动态配图",
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { photoUrls = photoUrls - url },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("×", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (uploadingPhoto) {
                Text("正在上传…", color = PatNestColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                "提交后经平台审核，通过后对主人可见",
                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("提交审核", {
                    app.modal = null
                    app.scope.launch {
                        app.submitDynamic(orderId = selectedOrderId, text = text, photos = photoUrls)
                    }
                }, Modifier.weight(1f))
            }
        }
        ModalKind.ChangePassword -> {
            var oldPassword by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var confirmPassword by remember { mutableStateOf("") }
            FieldLabel("原密码")
            SoftTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "请输入当前密码",
                password = true,
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel("新密码")
            SoftTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "8-128 位",
                password = true,
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel("确认新密码")
            SoftTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "再次输入新密码",
                password = true,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("确认修改", {
                    when {
                        oldPassword.isBlank() -> app.toast("请输入原密码")
                        newPassword.length < 8 -> app.toast("新密码至少 8 位")
                        newPassword.length > 128 -> app.toast("新密码不能超过 128 位")
                        newPassword != confirmPassword -> app.toast("两次输入的新密码不一致")
                        else -> app.scope.launch { app.changePassword(oldPassword, newPassword) }
                    }
                }, Modifier.weight(1f))
            }
        }
        ModalKind.ApplySitter -> {
            var name by remember { mutableStateOf("") }
            var type by remember { mutableStateOf("家庭寄养") }
            var address by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            var contactPerson by remember { mutableStateOf("") }
            var licenseUrl by remember { mutableStateOf("") }
            var idCardUrl by remember { mutableStateOf("") }
            var uploadingLicense by remember { mutableStateOf(false) }
            var uploadingIdCard by remember { mutableStateOf(false) }
            FieldLabel("机构名称")
            SoftTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "如：萌宠之家",
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel("机构类型")
            ChipRow(listOf("家庭寄养", "门店寄养", "上门喂养", "其他"), type) { type = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("联系人姓名")
                    SoftTextField(contactPerson, { contactPerson = it }, Modifier.fillMaxWidth(), placeholder = "如：张三")
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("所在地址")
                    SoftTextField(address, { address = it }, Modifier.fillMaxWidth(), placeholder = "如：呼和浩特 · 赛罕区")
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldLabel("营业执照照片")
            DashedUploadBox(
                if (licenseUrl.isBlank()) "点击上传营业执照" else "营业执照已上传，点击可更换",
                if (uploadingLicense) "正在上传…" else "支持 JPG / PNG，≤ 5MB",
                Modifier.fillMaxWidth().height(104.dp),
            ) {
                if (uploadingLicense) return@DashedUploadBox
                val file = team.wd.atnestapp.ui.util.chooseImageFile() ?: return@DashedUploadBox
                app.scope.launch {
                    uploadingLicense = true
                    try {
                        val url = app.uploadImage(file.name, file.readBytes())
                        if (url != null) {
                            licenseUrl = url
                            app.toast("营业执照已上传")
                        }
                    } finally {
                        uploadingLicense = false
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldLabel("联系人身份证照片（选填）")
            DashedUploadBox(
                if (idCardUrl.isBlank()) "点击上传身份证照片" else "身份证已上传，点击可更换",
                if (uploadingIdCard) "正在上传…" else "支持 JPG / PNG，≤ 5MB",
                Modifier.fillMaxWidth().height(104.dp),
            ) {
                if (uploadingIdCard) return@DashedUploadBox
                val file = team.wd.atnestapp.ui.util.chooseImageFile() ?: return@DashedUploadBox
                app.scope.launch {
                    uploadingIdCard = true
                    try {
                        val url = app.uploadImage(file.name, file.readBytes())
                        if (url != null) {
                            idCardUrl = url
                            app.toast("身份证照片已上传")
                        }
                    } finally {
                        uploadingIdCard = false
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldLabel("机构简介（选填）")
            SoftTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "介绍寄养环境、照料方式等",
                singleLine = false,
                minHeight = 60.dp,
            )
            Text(
                "价格与接待容量在审核通过后于机构端设置",
                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("提交申请", {
                    when {
                        name.isBlank() -> app.toast("请输入机构名称")
                        contactPerson.isBlank() -> app.toast("请输入联系人姓名")
                        licenseUrl.isBlank() -> app.toast("请上传营业执照照片")
                        else -> app.scope.launch {
                            app.applySitter(
                                name = name,
                                typeLabel = type,
                                contactPerson = contactPerson,
                                address = address,
                                licensePhotoUrl = licenseUrl,
                                idCardPhotoUrl = idCardUrl,
                                description = description,
                            )
                        }
                    }
                }, Modifier.weight(1f))
            }
        }
        ModalKind.PriceRule -> {
            var name by remember { mutableStateOf("节假日上浮") }
            var startDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(7).toString()) }
            var endDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(10).toString()) }
            var multiplierText by remember { mutableStateOf("1.2") }
            FieldLabel("规则名称")
            SoftTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "如：国庆上浮")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("开始日期")
                    SoftTextField(startDate, { startDate = it }, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("结束日期")
                    SoftTextField(endDate, { endDate = it }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldLabel("价格倍率（0.5 - 5）")
            SoftTextField(multiplierText, { multiplierText = it }, Modifier.fillMaxWidth(), placeholder = "如 1.2")
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("保存规则", {
                    val start = runCatching { java.time.LocalDate.parse(startDate) }.getOrNull()
                    val end = runCatching { java.time.LocalDate.parse(endDate) }.getOrNull()
                    val multiplier = multiplierText.toDoubleOrNull()
                    when {
                        name.isBlank() -> app.toast("请输入规则名称")
                        start == null || end == null -> app.toast("日期格式应为 YYYY-MM-DD")
                        end.isBefore(start) -> app.toast("结束日期不能早于开始日期")
                        multiplier == null || multiplier < 0.5 || multiplier > 5 ->
                            app.toast("价格倍率需在 0.5 到 5 之间")
                        else -> {
                            app.modal = null
                            app.scope.launch {
                                app.createPriceRule(name, startDate, endDate, multiplier)
                            }
                        }
                    }
                }, Modifier.weight(1f))
            }
        }
        is ModalKind.CouponForm -> {
            val editing = modal.coupon
            var title by remember { mutableStateOf(editing?.title.orEmpty()) }
            var typeLabel by remember {
                mutableStateOf(
                    when (editing?.type) {
                        "percent" -> "折扣券"
                        null -> "满减券"
                        else -> "满减券"
                    },
                )
            }
            var valueText by remember { mutableStateOf(editing?.value?.toString().orEmpty().ifBlank { "10" }) }
            var minAmountText by remember { mutableStateOf(editing?.minAmount?.toInt()?.toString() ?: "0") }
            var totalQtyText by remember { mutableStateOf(editing?.totalQty?.toString() ?: "100") }
            var perUserText by remember { mutableStateOf(editing?.perUser?.toString() ?: "1") }
            var startDate by remember { mutableStateOf(editing?.startsAt.orEmpty().ifBlank { java.time.LocalDate.now().toString() }) }
            var endDate by remember { mutableStateOf(editing?.endsAt.orEmpty()) }

            FieldLabel("券名称")
            SoftTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = "如：本店寄养立减 20")
            Spacer(Modifier.height(10.dp))
            FieldLabel("券类型")
            ChipRow(listOf("满减券", "折扣券"), typeLabel) { typeLabel = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(if (typeLabel == "满减券") "立减金额（元）" else "折扣比例（%）")
                    SoftTextField(valueText, { valueText = it }, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("使用门槛（元）")
                    SoftTextField(minAmountText, { minAmountText = it }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("发放总量（0 不限）")
                    SoftTextField(totalQtyText, { totalQtyText = it }, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("每人限领")
                    SoftTextField(perUserText, { perUserText = it }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("开始日期")
                    SoftTextField(startDate, { startDate = it }, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("截止日期（空=长期）")
                    SoftTextField(endDate, { endDate = it }, Modifier.fillMaxWidth())
                }
            }
            Text(
                "机构优惠券仅限主人领取后在本机构下单时抵扣",
                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton(if (editing == null) "发布" else "保存", {
                    val value = valueText.toDoubleOrNull()
                    val minAmount = minAmountText.toDoubleOrNull() ?: 0.0
                    val totalQty = totalQtyText.toIntOrNull() ?: 0
                    val perUser = perUserText.toIntOrNull() ?: 1
                    val type = if (typeLabel == "折扣券") "percent" else "fixed"
                    when {
                        title.isBlank() -> app.toast("请输入券名称")
                        value == null || value <= 0 -> app.toast("面值需大于 0")
                        type == "percent" && value > 100 -> app.toast("折扣券面值应为 1-100 的百分比")
                        minAmount < 0 || totalQty < 0 || perUser < 1 -> app.toast("门槛/数量/限领不能为负")
                        endDate.isNotBlank() && endDate < startDate -> app.toast("截止日期不能早于开始日期")
                        else -> app.scope.launch {
                            app.saveSitterCoupon(
                                coupon = editing,
                                payload = SitterCouponPayload(
                                    title = title.trim(),
                                    type = type,
                                    value = value,
                                    minAmount = minAmount,
                                    totalQty = totalQty,
                                    perUser = perUser,
                                    enabled = editing?.enabled ?: true,
                                    startsAt = startDate.trim(),
                                    endsAt = endDate.trim(),
                                ),
                            )
                        }
                    }
                }, Modifier.weight(1f))
            }
        }
        is ModalKind.AdjustCapacity -> {
            val item = app.capacityItems.firstOrNull { it.date == modal.date }
            var capacityText by remember(modal.date) { mutableStateOf((item?.capacity ?: 1).toString()) }
            FieldLabel("当日接待容量（只）")
            SoftTextField(capacityText, { capacityText = it }, Modifier.fillMaxWidth())
            Text(
                "当日已在住 ${item?.used ?: 0} 只，调整后容量不能低于该数；未单独设置的日期沿用机构资料里的默认容量。",
                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("保存", {
                    val n = capacityText.filter { it.isDigit() }.toIntOrNull()
                    when {
                        n == null || n < 1 -> app.toast("容量至少为 1")
                        item != null && n < item.used -> app.toast("不能低于已在住的 ${item.used} 只")
                        else -> app.scope.launch { app.setCapacity(modal.date, n) }
                    }
                }, Modifier.weight(1f))
            }
        }
        is ModalKind.MapPicker -> MapPickerPanel(
            app = app,
            initialLat = modal.latitude,
            initialLng = modal.longitude,
            onConfirm = { lat, lng, addr ->
                app.modal = null
                modal.onPicked(lat, lng, addr)
            },
        )
        is ModalKind.Photo -> Column(Modifier.fillMaxWidth()) {
            RemoteImage(
                url = modal.url,
                modifier = Modifier.fillMaxWidth().height(420.dp),
                contentDescription = "图片预览",
                contentScale = ContentScale.Fit,
                shape = PatNestShape.Md,
            )
            if (modal.caption.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(modal.caption, color = PatNestColors.Ink3, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                GhostButton("关闭", { app.modal = null })
            }
        }
        is ModalKind.CancelOrder -> {
            var reason by remember(modal.orderId) { mutableStateOf("") }
            Text(
                "取消后 ${modal.orderNo} 占用的当日容量会立即释放，已支付的订单需另行申请退款。",
                color = PatNestColors.Ink2, fontSize = 13.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            FieldLabel("取消原因（选填）")
            SoftTextField(reason, { reason = it }, Modifier.fillMaxWidth(), placeholder = "如：临时出差，改天再约")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("再想想", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("确认取消", {
                    app.modal = null
                    app.scope.launch { app.cancelOrder(modal.orderId, reason.trim()) }
                }, Modifier.weight(1f))
            }
        }
        is ModalKind.Reschedule -> {
            var startText by remember(modal.orderId) { mutableStateOf(modal.startDate) }
            var endText by remember(modal.orderId) { mutableStateOf(modal.endDate) }
            FieldLabel("新的开始日期")
            SoftTextField(startText, { startText = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD")
            Spacer(Modifier.height(10.dp))
            FieldLabel("新的结束日期")
            SoftTextField(endText, { endText = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD")
            Spacer(Modifier.height(8.dp))
            Text("改期会按新周期重算费用，日期格式如 2026-11-08", color = PatNestColors.Ink3, fontSize = 11.5.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("提交改期", {
                    if (!isPlainDate(startText) || !isPlainDate(endText) || endText <= startText) {
                        app.toast("日期格式应为 YYYY-MM-DD，且结束晚于开始")
                    } else {
                        app.modal = null
                        app.scope.launch { app.rescheduleOrder(modal.orderId, startText, endText) }
                    }
                }, Modifier.weight(1f))
            }
        }
        is ModalKind.Renew -> {
            var endText by remember(modal.orderId) { mutableStateOf(nextDay(modal.endDate)) }
            FieldLabel("续住到（含当天之前）")
            SoftTextField(endText, { endText = it }, Modifier.fillMaxWidth(), placeholder = "YYYY-MM-DD")
            Spacer(Modifier.height(8.dp))
            Text(
                "订单 ${modal.orderNo} 实付 ¥${modal.amount}，当前结束日期 ${modal.endDate}，新日期必须晚于它。",
                color = PatNestColors.Ink3, fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "续住会占用新增夜间的容量，并按机构当前排期价立即补收差价后生效。",
                color = PatNestColors.Ink2, fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("确认续住并补差价", {
                    if (!isPlainDate(endText) || endText <= modal.endDate) {
                        app.toast("请填写晚于 ${modal.endDate} 的日期，格式 YYYY-MM-DD")
                    } else {
                        app.modal = null
                        app.scope.launch { app.renewOrder(modal.orderId, endText) }
                    }
                }, Modifier.weight(1f))
            }
        }
        is ModalKind.Refund -> {
            var reason by remember(modal.orderId) { mutableStateOf("") }
            Text(
                "订单 ${modal.orderNo} 实付 ¥${modal.amount}，提交后由平台审核，审核通过才退款。",
                color = PatNestColors.Ink2, fontSize = 13.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            FieldLabel("退款原因")
            SoftTextField(reason, { reason = it }, Modifier.fillMaxWidth(), placeholder = "如：机构临时无法接待")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("提交申请", {
                    if (reason.trim().length < 2) {
                        app.toast("请至少简单写两句原因")
                    } else {
                        app.modal = null
                        app.scope.launch { app.requestRefund(modal.orderId, reason.trim()) }
                    }
                }, Modifier.weight(1f))
            }
        }
        ModalKind.Ticket -> {
            var category by remember { mutableStateOf("订单问题") }
            var title by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }
            FieldLabel("问题类型")
            ChipRow(listOf("订单问题", "机构投诉", "支付退款", "账号其他"), category) { category = it }
            Spacer(Modifier.height(12.dp))
            FieldLabel("标题")
            SoftTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = "一句话说清问题")
            Spacer(Modifier.height(10.dp))
            FieldLabel("详细描述")
            SoftTextField(content, { content = it }, Modifier.fillMaxWidth(), singleLine = false, minHeight = 84.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("提交工单", {
                    app.scope.launch {
                        if (app.submitTicket(title, content, ticketCategoryCode(category))) {
                            app.modal = null
                        }
                    }
                }, Modifier.weight(1f))
            }
        }
    }
}

/** 后端日期参数一律 YYYY-MM-DD，这里只做最朴素的形状校验，业务规则交给服务端 */
private fun isPlainDate(text: String): Boolean {
    if (text.length != 10) return false
    if (text[4] != '-' || text[7] != '-') return false
    val y = text.substring(0, 4).toIntOrNull() ?: return false
    val m = text.substring(5, 7).toIntOrNull() ?: return false
    val d = text.substring(8, 10).toIntOrNull() ?: return false
    return y in 2000..2999 && m in 1..12 && d in 1..31
}

/** 在 YYYY-MM-DD 上加一天，跨月跨年都靠"当月天数"表兜住 */
private fun nextDay(date: String): String {
    if (!isPlainDate(date)) return date
    val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var y = date.substring(0, 4).toInt()
    var m = date.substring(5, 7).toInt()
    var d = date.substring(8, 10).toInt()
    val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    val last = daysInMonth[m - 1] + if (m == 2 && leap) 1 else 0
    d += 1
    if (d > last) {
        d = 1
        m += 1
        if (m > 12) {
            m = 1
            y += 1
        }
    }
    return "%04d-%02d-%02d".format(y, m, d)
}

private fun ticketCategoryCode(label: String) = when (label) {
    "订单问题" -> "order"
    "机构投诉" -> "sitter"
    "支付退款" -> "refund"
    else -> "other"
}

private fun refundStatusLabel(status: String) = when (status) {
    "pending" -> "审核中"
    "approved" -> "已通过"
    "rejected" -> "已驳回"
    "cancelled" -> "已撤销"
    else -> status
}

/** 寄养记录里的订单状态：与订单列表用同一套中文标签 */
private fun orderStatusLabelOf(status: String) = when (status) {
    "pending_pay" -> "待支付"
    "confirmed" -> "待确认"
    "accepted" -> "已接单"
    "staying" -> "寄养中"
    "completed" -> "已结束"
    "evaluated" -> "已评价"
    "cancelled" -> "已取消"
    "rejected" -> "已拒绝"
    else -> status
}

/** 金额显示：整钱不带小数，避免 ¥156.0 这种观感；不用 String.format 以防跟随系统 locale */
private fun money(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private fun ReceivedOrderDto.toUiOrder(): SbOrder = SbOrder(
    no = orderNo,
    owner = ownerName.ifBlank { "—" },
    pet = if (petCount > 0) "$petCount 只" else "—",
    range = "${shortDay(startDate)} - ${shortDay(endDate)}",
    days = "$totalDays 天",
    amount = totalAmount.roundToInt(),
    status = sitterStatusLabel(status),
    made = formatCreated(createdAt),
    id = id,
    createdAt = createdAt,
)

private fun sitterStatusLabel(status: String): String = when (status) {
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

private fun shortDay(raw: String): String =
    if (raw.length >= 10) raw.substring(5, 10).replace('-', '/') else raw

private fun formatCreated(raw: String): String {
    if (raw.length < 16) return raw
    return "${shortDay(raw)} ${raw.substring(11, 16)}"
}

private fun parseAgeMonth(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val number = text.filter { it.isDigit() }.toIntOrNull() ?: return null
    val months = if (text.contains("岁")) number * 12 else number
    return months.coerceIn(0, 360)
}
