package team.wd.atnestapp.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.AddonDto
import team.wd.atnestapp.data.api.AddonPayload
import team.wd.atnestapp.data.api.AddonUpdatePayload
import team.wd.atnestapp.data.api.ApiClient
import team.wd.atnestapp.data.api.ApplySitterPayload
import team.wd.atnestapp.data.api.AuthResponseDto
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.CouponListDto
import team.wd.atnestapp.data.api.CreateOrderAddonPayload
import team.wd.atnestapp.data.api.CreateOrderPayload
import team.wd.atnestapp.data.api.CreateOrderPetPayload
import team.wd.atnestapp.data.api.CreatePetPayload
import team.wd.atnestapp.data.api.DynamicCommentDto
import team.wd.atnestapp.data.api.DynamicDto
import team.wd.atnestapp.data.api.EarningsDto
import team.wd.atnestapp.data.api.EstimateDto
import team.wd.atnestapp.data.api.InviteDto
import team.wd.atnestapp.data.api.GeocodeDto
import team.wd.atnestapp.data.api.GeocodePayload
import team.wd.atnestapp.data.api.NotificationDto
import team.wd.atnestapp.data.api.OrderPetDto
import team.wd.atnestapp.data.NotificationCenter
import team.wd.atnestapp.data.api.PetStayDto
import team.wd.atnestapp.data.api.PointsEntryDto
import team.wd.atnestapp.data.api.PriceRuleDto
import team.wd.atnestapp.data.api.PriceRulePayload
import team.wd.atnestapp.data.api.PublicReviewDto
import team.wd.atnestapp.data.api.ReceivedOrderDto
import team.wd.atnestapp.data.api.RefundDto
import team.wd.atnestapp.data.api.ReviewPayload
import team.wd.atnestapp.data.api.SendLoginCodeDto
import team.wd.atnestapp.data.api.ReversePayload
import team.wd.atnestapp.data.api.SettlementDto
import team.wd.atnestapp.data.api.SettlementWindowDto
import team.wd.atnestapp.data.api.SitterCouponDto
import team.wd.atnestapp.data.api.SitterCouponPayload
import team.wd.atnestapp.data.api.SitterDto
import team.wd.atnestapp.data.api.SitterReviewDto
import team.wd.atnestapp.data.api.SitterStatsDto
import team.wd.atnestapp.data.api.TicketDetailDto
import team.wd.atnestapp.data.api.TicketDto
import team.wd.atnestapp.data.api.UpdatePetPayload
import team.wd.atnestapp.data.api.UpdateProfilePayload
import team.wd.atnestapp.data.api.UpdateShopPayload
import team.wd.atnestapp.data.api.UserCouponDto
import team.wd.atnestapp.data.api.UserDto
import team.wd.atnestapp.data.api.toReceivedOrder
import team.wd.atnestapp.data.api.toUi
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 手机端状态中枢。数据层完全复用 shared 里的 ApiClient，
 * 页面只读这里的字段、只调这里的方法，业务口径与桌面端保持一致。
 */
class MobileState(private val session: MobileSession) {
    /**
     * 列表每页条数：取后端上限 50。
     * 首页统计（待支付角标等）是基于已加载列表算的，页太小会让统计偏低。
     */
    private val PAGE_SIZE = 50

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
        // 静默续期成功：把轮换后的令牌写回本地，否则下次冷启动拿着的是已作废的那张
        api.onAuthRefreshed = { access, refresh -> session.updateTokens(access, refresh) }
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
    /** 账号本身具备的机构能力（服务端 role == sitter）。只描述能力，不表示当前在用哪套。 */
    var isSitterAccount by mutableStateOf(false)
        private set

    /**
     * 当前正在使用的身份：owner / sitter。
     *
     * 和 [userRole] 必须分开：机构账号也是用户，切到主人端要能正常下单，
     * 而切过去之后整套页面（底部导航、首页、订单、消息）都要换成主人端那一套。
     * 只改一个高亮标记会让用户看到「机构账号 + 主人端页面 + 没有宠物和订单」的错位状态。
     *
     * 初值直接读本地会话快照（DataStore 构造时已同步读好），
     * 这样机构身份冷启动时 NavHost 的起始页就能直接是工作台，不会先闪一下主人端首页。
     */
    var activeIdentity by mutableStateOf(resolveIdentity(session.activeRole, session.role))
        private set
    val isSitterMode: Boolean get() = activeIdentity == "sitter"
    /** 是否可以用机构端（已入驻的机构账号才有） */
    val canUseSitter: Boolean get() = userRole == "sitter"
    /** 切换身份正在进行：页面据此禁用点击、显示进度 */
    var identitySwitching by mutableStateOf(false)
        private set
    /** 当前身份的中文名，设置、我的、身份页共用一份口径 */
    val identityLabel: String get() = if (isSitterMode) "机构端" else "宠物主人"

    /**
     * 由「上次选的」和「账号实际角色」推出当前身份。
     *
     * 三条规则：选过机构且账号确实是机构 → 机构；选过其它 → 主人端（尊重用户选择）；
     * 没选过 → 跟账号角色走。机构资格被收回时下次启动会自动落回主人端。
     */
    private fun resolveIdentity(saved: String, role: String): String = when {
        saved == "sitter" && role == "sitter" -> "sitter"
        saved.isNotBlank() -> "owner"
        role == "sitter" -> "sitter"
        else -> "owner"
    }
    var profile by mutableStateOf<UserDto?>(null)
        private set
    /**
     * 未绑定手机号（历史/后台导入账号）。
     *
     * 后端从 docs/16 起允许这类账号继续登录，但会回 `phone_bound=false`，
     * 客户端要提示去绑定 —— 否则用户不知道自己的手机号是空的，
     * 后续任何需要手机号的环节都会莫名失败（且没有可自查的地方）。
     */
    var needsPhoneBinding by mutableStateOf(false)
        private set

    /* ---------- 跨页选择与提示 ---------- */
    var toastMessage by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var selectedSitter by mutableStateOf<Sitter?>(null)
    /** 当前机构详情页的 pgvector 相似机构（机构 + 相似度） */
    var similarSitters by mutableStateOf<List<Pair<Sitter, Double>>>(emptyList())
        private set
    var similarSittersLoading by mutableStateOf(false)
        private set
    private var similarSitterId = 0L
    var selectedOrder by mutableStateOf<Order?>(null)
    /** 最近一次下单返回的订单 id：收银台据此精确定位订单，避免付错单 */
    var lastCreatedOrderId by mutableStateOf(0L)
        private set
    var editingPet by mutableStateOf<Pet?>(null)
    var viewingPet by mutableStateOf<Pet?>(null)
    /** 动态详情（设计层跳转用，避免把整条动态塞进路由参数） */
    var selectedDynamic by mutableStateOf<DynamicDto?>(null)
    /** 评价详情 */
    var selectedReview by mutableStateOf<PublicReviewDto?>(null)
    /** 优惠券详情 */
    var selectedCoupon by mutableStateOf<UserCouponDto?>(null)
    /** 地图选点回填：选点页解析出的坐标，机构资料保存时一并提交 */
    var pickedLatitude by mutableStateOf<Double?>(null)
    var pickedLongitude by mutableStateOf<Double?>(null)
    var pickedAddress by mutableStateOf("")
    /** 机构端「发布动态」要关联的订单 id，0 表示还没选 */
    var selectedOrderIdForDynamic by mutableStateOf(0L)
    /** 机构端订单详情当前查看的订单 */
    var selectedReceivedOrder by mutableStateOf<ReceivedOrderDto?>(null)
    /** 订单详情接口返回的入住宠物；列表 DTO 只有数量，没有名字 */
    var selectedOrderPets by mutableStateOf<List<OrderPetDto>>(emptyList())
    var selectedReceivedOrderPets by mutableStateOf<List<OrderPetDto>>(emptyList())
    /** 最近一次提交的评价 id，追评页据此提交 */
    var lastReviewId by mutableStateOf(0L)

    /* ---------- 主人端数据 ---------- */
    var sitters by mutableStateOf(listOf<Sitter>())
        private set
    /** 服务端搜索结果；null 表示还没搜/搜索失败，页面回退到本地过滤 */
    var sitterSearchResults by mutableStateOf<List<Sitter>?>(null)
    private var sitterPriceMin: Double? = null
    private var sitterPriceMax: Double? = null
    private var sitterSort: String? = null
    private var sitterLatitude: Double? = null
    private var sitterLongitude: Double? = null
    private var sittersRequestSeq = 0
    var sitterQuery by mutableStateOf("")
    var sitterType by mutableStateOf("全部")
    var sittersLoading by mutableStateOf(false)
        private set
    var sittersError by mutableStateOf<String?>(null)
        private set

    /* ---------- 首页定位推荐 ---------- */
    var recommendedSitters by mutableStateOf(listOf<Sitter>())
        private set
    var recommendedLoading by mutableStateOf(false)
        private set
    var recommendedError by mutableStateOf<String?>(null)
        private set
    /** 当前设备位置（GCJ-02）；null 表示还没定位/未授权 */
    var devicePoint by mutableStateOf<DevicePoint?>(null)
        private set
    /** 定位后的城市/区县文案，例如「呼和浩特市 · 回民区」 */
    var locationLabel by mutableStateOf("")
        private set
    var locating by mutableStateOf(false)
        private set
    private var recommendedRequestSeq = 0

    var pets by mutableStateOf(listOf<Pet>())
        private set
    /** 宠物列表分页：后端 per_page 上限 50，超过要翻页 */
    var petsHasMore by mutableStateOf(false)
        private set
    var petsLoadingMore by mutableStateOf(false)
        private set
    private var petsPage = 0
    private var petsRequestSeq = 0
    /** 当前宠物列表的物种筛选；null 表示全部 */
    private var petsSpecies: String? = null
    var petsLoading by mutableStateOf(false)
        private set
    var petsError by mutableStateOf<String?>(null)
        private set

    var orders by mutableStateOf(listOf<Order>())
        private set
    var ordersLoading by mutableStateOf(false)
        private set
    /** 订单分页状态：hasMore 由「已加载条数 < total」决定 */
    var ordersHasMore by mutableStateOf(false)
        private set
    var ordersLoadingMore by mutableStateOf(false)
        private set
    private var ordersPage = 0
    private var ordersRequestSeq = 0
    /** 当前列表的服务端状态筛选；null 表示全部 */
    private var ordersStatusFilter: String? = null
    var ordersError by mutableStateOf<String?>(null)
        private set

    var favorites by mutableStateOf(listOf<Sitter>())
        private set
    var favoritesLoading by mutableStateOf(false)
        private set

    // 通知状态统一收敛到 shared 的 NotificationCenter，避免与 Desktop 端重复实现。
    // toast 以回调注入（安卓 toast 机制见下方 toast() 定义）。
    val notificationsCenter = NotificationCenter(api) { toast(it) }

    var ownerDynamics by mutableStateOf(listOf<DynamicDto>())
        private set
    var dynamicsLoading by mutableStateOf(false)
        private set
    val likedDynamics = mutableStateListOf<Long>()

    var orderDynamics by mutableStateOf(listOf<DynamicDto>())
        private set
    var orderDynamicsLoading by mutableStateOf(false)
        private set

    /** 动态详情页的留言（后端一次全给，不翻页） */
    var dynamicComments by mutableStateOf(listOf<DynamicCommentDto>())
        private set
    var commentsLoading by mutableStateOf(false)
        private set
    /** 留言接口顺带回的点赞总数，比本地记录更准 */
    var dynamicLikes by mutableStateOf(0)
        private set
    var commentSending by mutableStateOf(false)
        private set

    /** 售后工单会话：列表点开时先记 id，会话页进页面再拉详情（避免点一下等一秒才跳页） */
    var selectedTicketId by mutableStateOf(0L)
    var ticketDetail by mutableStateOf<TicketDetailDto?>(null)
        private set
    var ticketLoading by mutableStateOf(false)
        private set
    var ticketReplying by mutableStateOf(false)
        private set

    var coupons by mutableStateOf<CouponListDto?>(null)
        private set
    var couponsLoading by mutableStateOf(false)
        private set

    var pointsBalance by mutableStateOf(0)
        private set
    var pointsHistory by mutableStateOf(listOf<PointsEntryDto>())
        private set

    /** 邀请码与邀请统计；未登录或接口失败时为 null */
    var invite by mutableStateOf<InviteDto?>(null)
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
    /** 加购服务 id → 数量（0 表示不选）。之前存的是名称且没有任何入口，等于摆设。 */
    var bookingAddonQty by mutableStateOf(mapOf<Long, Int>())
    /** 当前机构提供的加购服务 */
    var sitterAddons by mutableStateOf(listOf<AddonDto>())
        private set
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
    var receivedOrdersHasMore by mutableStateOf(false)
        private set
    var receivedOrdersLoadingMore by mutableStateOf(false)
        private set
    private var receivedOrdersPage = 0
    var capacityItems by mutableStateOf(listOf<CapacityItemDto>())
        private set
    var capacityLoading by mutableStateOf(false)
        private set
    var earnings by mutableStateOf<EarningsDto?>(null)
        private set
    var earningsLoading by mutableStateOf(false)
        private set
    /** 收益页选中的统计区间，空串表示用后端默认（近 30 天） */
    var earningsFrom by mutableStateOf("")
        private set
    var earningsTo by mutableStateOf("")
        private set
    /** 本次可申请结算的区间（上次结算之后 → 今天） */
    var settleWindow by mutableStateOf<SettlementWindowDto?>(null)
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

    val unreadCount: Int get() = notificationsCenter.notifications.count { !it.isRead }

    /* ================= 提示 ================= */

    fun toast(message: String) {
        toastMessage = message
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
        // 上次切到哪个身份就回哪个；没选过就按账号角色走。
        // 账号的机构资格被收回时（role 变回 owner）强制落回主人端，避免停在一个用不了的台子里。
        activeIdentity = resolveIdentity(session.activeRole, session.role)
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

    /** 请求演示验证码；返回体里带 demo_code 时由登录页弹本地通知。 */
    suspend fun sendLoginCode(phone: String): SendLoginCodeDto {
        return api.sendLoginCode(phone)
    }

    suspend fun loginWithCode(phone: String, code: String) {
        if (busy) return
        busy = true
        try {
            applyAuth(api.loginWithCode(phone, code))
        } catch (e: Exception) {
            toast(e.message ?: "验证码登录失败")
        } finally {
            busy = false
        }
    }

    suspend fun register(
        inputAccount: String,
        password: String,
        phone: String,
        inviteCode: String = "",
        nickname: String = "",
    ) {
        if (busy) return
        busy = true
        try {
            // 昵称：注册页填了就用手填的，留空才退回账号名（之前直接写死账号名，输入框形同虚设）
            val nick = nickname.trim().ifBlank { inputAccount.trim() }
            applyAuth(api.register(inputAccount, password, nick, phone.trim(), inviteCode.trim()))
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
        // 一条提示就够：未绑手机号的账号把「去绑定」放在前面的位置，
        // 否则「欢迎 XX」会把真正需要用户处理的事挤掉
        toast(if (needsPhoneBinding) "登录成功，请先绑定手机号" else "登录成功，欢迎 " + nickname)
        loadAll()
    }

    private fun applyMe(me: UserDto) {
        account = me.username.ifBlank { account }
        nickname = me.nickname?.takeIf { it.isNotBlank() } ?: account
        userRole = me.role
        isSitterAccount = me.role == "sitter"
        // 用服务端事实校正身份：资格被收回就退回主人端，并把落盘的偏好一起改掉，
        // 否则下次冷启动又会读到那个已经无效的 sitter。
        activeIdentity = resolveIdentity(session.activeRole, me.role)
        if (session.activeRole != activeIdentity) session.activeRole = activeIdentity
        profile = me
        needsPhoneBinding = me.phone.isNullOrBlank()
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
            val result = api.requestAccountCancel(mode)
            val current = api.token
            if (!current.isNullOrBlank()) runCatching { api.logout(current) }
            clearSession()
            // 账户注销时余额会被转成待退款单（不是没收），把金额说清楚，
            // 否则用户会以为钱没了
            val refund = result.walletRefundAmount ?: 0.0
            toast(
                buildString {
                    append("注销申请已提交，3 天内重新登录可撤销")
                    if (mode != "sitter" && refund > 0) {
                        append("。钱包余额 ").append(money(refund))
                            .append(" 将转为待退款单，客服会联系你确认收款方式")
                    }
                },
            )
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }

    /** 冷静期内撤销注销申请；撤销后账号恢复正常，不会进入清理。 */
    suspend fun withdrawAccountCancel() {
        try {
            api.withdrawAccountCancel()
            loadProfile()
            toast("已撤销注销申请")
        } catch (e: Exception) {
            toast(e.message ?: "撤销失败")
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
        activeIdentity = "owner"
        identitySwitching = false
        account = ""
        nickname = ""
        profile = null
        api.clearAuth()
        session.clearAuth()
        pets = emptyList()
        orders = emptyList()
        favorites = emptyList()
        notificationsCenter.reset()
        ownerDynamics = emptyList()
        coupons = null
        refunds = emptyList()
        tickets = emptyList()
        pointsHistory = emptyList()
        pointsBalance = 0
        invite = null
        estimate = null
        myShop = null
        receivedOrders = emptyList()
        capacityItems = emptyList()
        earnings = null
        earningsFrom = ""
        earningsTo = ""
        settleWindow = null
        sitterReviews = emptyList()
        sitterDynamics = emptyList()
        sitterCoupons = emptyList()
        settlements = emptyList()
        myAddons = emptyList()
        priceRules = emptyList()
        sitterStats = null
        clearDeviceLocation()
    }

    /**
     * 登录成功 / 冷启动恢复登录态后先拉这一轮数据，具体页面再按需补拉。
     * 只管取数，不做任何页面跳转 —— 跳转由设计层的 DNav 负责。
     */
    private suspend fun loadAll() {
        loadSitters()
        // 机构端数据只在账号真有机构资格时拉，纯主人账号不必白跑两趟 404。
        if (canUseSitter) {
            loadMyShop()
            loadReceivedOrders()
        }
        // 主人端数据按当前身份决定：机构账号切到主人端后才需要宠物 / 订单 / 收藏。
        if (!isSitterMode) {
            loadPets()
            loadOrders()
            loadFavorites()
        }
        loadNotifications()
    }

    /**
     * 切换当前身份，返回 true 表示切换成功（调用方据此跳到对应的工作台）。
     *
     * 切换不是换个高亮：主人端与机构端的订单、宠物、通知范围是两套数据，
     * 所以这里要把新身份的数据重新拉一遍，并清掉上一个身份留下的「选中对象」，
     * 否则会看到「切到主人端仍然展示机构订单」这类串台。
     */
    suspend fun switchIdentity(target: String): Boolean {
        val want = if (target == "sitter") "sitter" else "owner"
        if (want == activeIdentity) {
            toast(if (want == "sitter") "当前已是机构端" else "当前已是主人端")
            return false
        }
        if (want == "sitter" && !canUseSitter) {
            toast("机构端还没开通")
            return false
        }
        if (identitySwitching) return false
        identitySwitching = true
        try {
            activeIdentity = want
            session.activeRole = want
            clearIdentitySelection()
            if (want == "sitter") {
                loadMyShop()
                loadReceivedOrders()
                loadSitterStats()
            } else {
                loadPets()
                loadOrders()
                loadFavorites()
                loadCoupons()
                loadPoints()
            }
            loadNotifications(force = true)
            toast(if (want == "sitter") "已切换到机构端" else "已切换到主人端")
            return true
        } catch (e: Exception) {
            // 取数失败也留在新身份上：身份本身切换成功了，页面各自的空态 / 重试负责补救
            toast(e.message ?: "切换后数据加载失败")
            return true
        } finally {
            identitySwitching = false
        }
    }

    /** 换身份后这些「选中对象」指向的都是上一个身份的数据，必须清掉。 */
    private fun clearIdentitySelection() {
        selectedSitter = null
        similarSitters = emptyList()
        similarSitterId = 0L
        selectedOrder = null
        selectedReceivedOrder = null
        selectedOrderPets = emptyList()
        selectedReceivedOrderPets = emptyList()
        selectedDynamic = null
        selectedReview = null
        selectedCoupon = null
        selectedTicketId = 0L
        ticketDetail = null
        selectedOrderIdForDynamic = 0L
        viewingPet = null
        editingPet = null
        notificationsCenter.reset()
    }

    /* ================= 主人端：加载 ================= */

    suspend fun loadSitters(
        force: Boolean = false,
        priceMin: Double? = null,
        priceMax: Double? = null,
        sort: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        if (!force && sittersLoading) return
        if (!force && sitters.isNotEmpty() &&
            sitterPriceMin == priceMin && sitterPriceMax == priceMax &&
            sitterSort == sort && sitterLatitude == latitude && sitterLongitude == longitude
        ) return
        sitterPriceMin = priceMin
        sitterPriceMax = priceMax
        sitterSort = sort
        sitterLatitude = latitude
        sitterLongitude = longitude
        val seq = ++sittersRequestSeq
        sittersLoading = true
        sittersError = null
        try {
            val data = api.listSitters(sitterTypeCode(), priceMin, priceMax, sort, latitude, longitude)
            if (seq != sittersRequestSeq) return
            sitters = data
        } catch (e: Exception) {
            if (seq == sittersRequestSeq) sittersError = e.message ?: "机构列表加载失败"
        } finally {
            if (seq == sittersRequestSeq) sittersLoading = false
        }
    }

    /** 离开筛选页时恢复无价格/排序/距离条件的机构列表。 */
    fun clearSitterFilters() {
        if (sitterPriceMin == null && sitterPriceMax == null && sitterSort == null &&
            sitterLatitude == null && sitterLongitude == null
        ) return
        scope.launch { loadSitters(force = true) }
    }

    /** 机构详情页进入时刷新完整资料；列表对象只做首屏骨架。 */
    suspend fun refreshSitterDetail(sitterId: Long) {
        try {
            val updated = api.getSitter(sitterId).toUi()
            sitters = sitters.map { if (it.id == sitterId) updated else it }
            if (selectedSitter?.id == sitterId) selectedSitter = updated
        } catch (_: Exception) {
            // 详情刷新失败保留列表快照
        }
    }

    /** 加载当前机构的 pgvector 相似机构；失败时静默留空，不打断详情页。 */
    suspend fun loadSimilarSitters(sitterId: Long) {
        if (sitterId <= 0L) {
            similarSitters = emptyList()
            return
        }
        if (similarSittersLoading && similarSitterId == sitterId) return
        if (similarSitterId != sitterId) similarSitters = emptyList()
        similarSitterId = sitterId
        similarSittersLoading = true
        try {
            val data = api.similarSitters(sitterId)
            if (similarSitterId != sitterId) return
            similarSitters = data.map { it.sitter.toUi() to it.similarity }
        } catch (_: Exception) {
            if (similarSitterId == sitterId) similarSitters = emptyList()
        } finally {
            if (similarSitterId == sitterId) similarSittersLoading = false
        }
    }

    fun reloadSittersByType() {
        sitters = emptyList()
        sitterSearchResults = null
        sitterPriceMin = null
        sitterPriceMax = null
        sitterSort = null
        sitterLatitude = null
        sitterLongitude = null
        scope.launch { loadSitters(force = true) }
    }

    /**
     * 服务端关键词搜索：只覆盖 /sitters/search，不替换首页的 sitters 列表。
     * 失败时保持 null，让搜索页回退到本地过滤，不把网络错误变成“无结果”。
     */
    suspend fun searchSitters(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            sitterSearchResults = null
            return
        }
        sitterSearchResults = try {
            api.searchSitters(q)
        } catch (_: Exception) {
            null
        }
    }

    /** 首页推荐：有定位时按距离排序，没有定位时按默认综合排序。 */
    suspend fun loadRecommendedSitters(force: Boolean = false) {
        if (!force && recommendedLoading) return
        if (!force && recommendedSitters.isNotEmpty()) return
        val seq = ++recommendedRequestSeq
        recommendedLoading = true
        recommendedError = null
        try {
            val point = devicePoint
            val data = try {
                api.listSitters(
                    sort = if (point != null) "distance" else null,
                    latitude = point?.latitude,
                    longitude = point?.longitude,
                )
            } catch (e: Exception) {
                // 后端还没发布 sort=distance 时，不要让首页整块空掉：
                // 退回综合推荐，定位文案仍然保留。
                if (point == null) throw e
                api.listSitters()
            }
            if (seq != recommendedRequestSeq) return
            recommendedSitters = data
        } catch (e: Exception) {
            if (seq == recommendedRequestSeq) recommendedError = e.message ?: "推荐机构加载失败"
        } finally {
            if (seq == recommendedRequestSeq) recommendedLoading = false
        }
    }

    /**
     * 应用一次设备定位：先反查城市/区县，再按距离刷新首页推荐。
     * 逆地理失败不阻塞推荐，位置文案退回「当前位置」。
     */
    suspend fun applyDeviceLocation(point: DevicePoint) {
        devicePoint = point
        locating = true
        try {
            val geo = reverseAddress(point.latitude, point.longitude)
            locationLabel = listOfNotNull(
                geo?.resolvedCity?.takeIf { it.isNotBlank() },
                geo?.district?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { "当前位置" }
        } finally {
            locating = false
        }
        loadRecommendedSitters(force = true)
    }

    fun clearDeviceLocation() {
        devicePoint = null
        locationLabel = ""
        locating = false
        recommendedSitters = emptyList()
    }

    suspend fun loadPets(force: Boolean = false, species: String? = null) {
        if (!loggedIn) return
        if (!force && petsLoading) return
        if (!force && pets.isNotEmpty() && petsSpecies == species) return
        petsSpecies = species
        val seq = ++petsRequestSeq
        petsLoading = true
        petsError = null
        try {
            val data = api.listPetsPage(1, PAGE_SIZE, species = species)
            // 快速切物种时旧请求可能后到，只让最后一次请求落地。
            if (seq != petsRequestSeq) return
            pets = data.items
            petsPage = 1
            petsHasMore = data.items.size < data.total
        } catch (e: Exception) {
            if (seq == petsRequestSeq) petsError = e.message ?: "宠物档案加载失败"
        } finally {
            if (seq == petsRequestSeq) petsLoading = false
        }
    }

    /** 离开宠物筛选页时恢复全部，避免首页「我的宠物」统计只读到筛选结果。 */
    fun clearPetFilter() {
        if (petsSpecies == null) return
        scope.launch { loadPets(force = true, species = null) }
    }

    /** 宠物列表触底加载下一页 */
    suspend fun loadMorePets() {
        if (!loggedIn || petsLoading || petsLoadingMore || !petsHasMore) return
        petsLoadingMore = true
        try {
            val data = api.listPetsPage(petsPage + 1, PAGE_SIZE, species = petsSpecies)
            petsPage += 1
            pets = (pets + data.items).distinctBy { it.id }
            petsHasMore = pets.size < data.total
        } catch (_: Exception) {
            // 加载更多失败不打断已展示内容
        } finally {
            petsLoadingMore = false
        }
    }

    suspend fun loadOrders(force: Boolean = false, statusFilter: String? = null) {
        if (!loggedIn) return
        if (!force && ordersLoading) return
        if (!force && orders.isNotEmpty() && ordersStatusFilter == statusFilter) return
        ordersStatusFilter = statusFilter
        val seq = ++ordersRequestSeq
        ordersLoading = true
        ordersError = null
        try {
            val data = api.listOrdersPage(1, PAGE_SIZE, statusFilter)
            // 快速切状态 Tab 时旧请求可能后到，只让最后一次请求落地。
            if (seq != ordersRequestSeq) return
            orders = data.items
            ordersPage = 1
            ordersHasMore = data.items.size < data.total
        } catch (e: Exception) {
            if (seq == ordersRequestSeq) ordersError = e.message ?: "订单加载失败"
        } finally {
            if (seq == ordersRequestSeq) ordersLoading = false
        }
    }

    /** 离开订单筛选页时恢复“全部”，避免首页统计读到筛选后的列表。 */
    fun clearOrderFilter() {
        if (ordersStatusFilter == null) return
        scope.launch { loadOrders(force = true, statusFilter = null) }
    }

    /** 详情页进入时刷新订单，避免只展示列表里的旧快照。 */
    suspend fun refreshOrderDetail(orderId: Long) {
        if (selectedOrder?.id == orderId) selectedOrderPets = emptyList()
        try {
            val detail = api.getOrderDetail(orderId)
            val updated = detail.order.toUi()
            orders = orders.map { if (it.id == orderId) updated else it }
            if (selectedOrder?.id == orderId) {
                selectedOrder = updated
                selectedOrderPets = detail.pets
            }
        } catch (_: Exception) {
            // 详情刷新失败不覆盖已有列表快照
        }
    }

    /** 机构订单详情：刷新后同时回填列表和 selectedReceivedOrder。 */
    suspend fun refreshReceivedOrder(orderId: Long) {
        if (selectedReceivedOrder?.id == orderId) selectedReceivedOrderPets = emptyList()
        try {
            val detail = api.getReceivedOrder(orderId)
            val updated = detail.order.toReceivedOrder()
            receivedOrders = receivedOrders.map { if (it.id == orderId) updated else it }
            if (selectedReceivedOrder?.id == orderId) {
                selectedReceivedOrder = updated
                selectedReceivedOrderPets = detail.pets
            }
        } catch (_: Exception) {
            // 保留列表快照
        }
    }

    /** 订单列表触底加载下一页（第 51 条起以前永远拿不到） */
    suspend fun loadMoreOrders() {
        if (!loggedIn || ordersLoading || ordersLoadingMore || !ordersHasMore) return
        ordersLoadingMore = true
        try {
            val data = api.listOrdersPage(ordersPage + 1, PAGE_SIZE, ordersStatusFilter)
            ordersPage += 1
            orders = (orders + data.items).distinctBy { it.id }
            ordersHasMore = orders.size < data.total
        } catch (_: Exception) {
        } finally {
            ordersLoadingMore = false
        }
    }

    suspend fun loadNotifications(force: Boolean = false) = notificationsCenter.loadNotifications(force)

    /** 消息页：一次拉取两个视角；商家消息只在当前身份是机构端时才有意义 */
    suspend fun loadScopedNotifications(force: Boolean = false) =
        notificationsCenter.loadScopedNotifications(force, isSitterMode)

    /** 消息页当前展示的列表（委托到共享通知中心） */
    fun visibleNotifications(): List<NotificationDto> = notificationsCenter.visibleNotifications()

    suspend fun markScopedRead(id: Long) = notificationsCenter.markScopedNotificationRead(id)

    /** 一键已读只清当前视角，另一类消息的未读数不受影响（委托到共享通知中心） */
    suspend fun markAllScopedRead() = notificationsCenter.markAllScopedNotificationsRead()

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

    /** 动态详情页进入时刷新正文/关联订单，避免只依赖列表快照。 */
    suspend fun refreshDynamic(dynamicId: Long) {
        try {
            val updated = api.getDynamic(dynamicId)
            if (selectedDynamic?.id == dynamicId) selectedDynamic = updated
            ownerDynamics = ownerDynamics.map { if (it.id == dynamicId) updated else it }
            orderDynamics = orderDynamics.map { if (it.id == dynamicId) updated else it }
        } catch (_: Exception) {
            // 保留列表快照
        }
    }

    /**
     * 某条动态的留言列表。
     *
     * 先清空再拉：从 A 的详情返回立刻进 B 时，不清会先闪一屏 A 的留言。
     * 点赞数也一并从响应里取，比本地 likedDynamics 推算准（别人点的赞也算）。
     */
    suspend fun loadDynamicComments(dynamicId: Long, reset: Boolean = true) {
        if (dynamicId <= 0L || commentsLoading) return
        if (reset) {
            dynamicComments = emptyList()
            dynamicLikes = 0
        }
        commentsLoading = true
        try {
            val page = api.listDynamicComments(dynamicId)
            dynamicComments = page.items
            dynamicLikes = page.likes
        } catch (e: Exception) {
            toast(e.message ?: "留言加载失败")
        } finally {
            commentsLoading = false
        }
    }

    /** 发表留言，成功后重新拉一遍（后端不回完整对象，重拉比本地拼更省事也更准） */
    suspend fun sendDynamicComment(dynamicId: Long, content: String): Boolean {
        val text = content.trim()
        if (text.isEmpty()) {
            toast("请先写点什么")
            return false
        }
        if (commentSending) return false
        commentSending = true
        return try {
            api.createDynamicComment(dynamicId, text)
            loadDynamicComments(dynamicId, reset = false)
            toast("留言已发送")
            true
        } catch (e: Exception) {
            toast(e.message ?: "留言失败")
            false
        } finally {
            commentSending = false
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

    /** 邀请码与奖励状态；失败时静默置空，别把邀请页拖住 */
    suspend fun loadInvite() {
        if (!loggedIn) return
        try {
            invite = api.myInvite()
        } catch (_: Exception) {
            invite = null
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

    /** 打开某张工单的完整对话（后端按时间正序回消息） */
    suspend fun openTicket(ticketId: Long) {
        if (ticketId <= 0L) return
        ticketLoading = true
        ticketDetail = null
        try {
            ticketDetail = api.ticketDetail(ticketId)
        } catch (e: Exception) {
            toast(e.message ?: "工单加载失败")
        } finally {
            ticketLoading = false
        }
    }

    fun closeTicket() {
        ticketDetail = null
    }

    /**
     * 追加一条回复。
     *
     * 成功后原地刷新会话，不走 openTicket —— 那个会先把 ticketDetail 置空，
     * 界面上会闪一帧"加载中"，对话页体验很差。
     */
    suspend fun replyTicket(ticketId: Long, content: String): Boolean {
        val text = content.trim()
        if (text.isEmpty()) {
            toast("请填写回复内容")
            return false
        }
        if (ticketReplying) return false
        ticketReplying = true
        return try {
            api.replyTicket(ticketId, text)
            ticketDetail = runCatching { api.ticketDetail(ticketId) }.getOrNull() ?: ticketDetail
            loadTickets()
            true
        } catch (e: Exception) {
            toast(e.message ?: "回复失败")
            false
        } finally {
            ticketReplying = false
        }
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
        scheduleEstimate()
    }

    fun shiftEnd(days: Long) {
        val start = runCatching { LocalDate.parse(bookingStart) }.getOrNull() ?: return
        val next = (runCatching { LocalDate.parse(bookingEnd) }.getOrNull() ?: start.plusDays(1)).plusDays(days)
        if (!next.isAfter(start)) return
        bookingEnd = next.toString()
        scheduleEstimate()
    }

    fun pickBookingStart(day: String) {
        val start = runCatching { LocalDate.parse(day) }.getOrNull() ?: return
        bookingStart = day
        val end = runCatching { LocalDate.parse(bookingEnd) }.getOrNull()
        if (end == null || !end.isAfter(start)) bookingEnd = start.plusDays(1).toString()
        scheduleEstimate()
    }

    fun pickBookingEnd(day: String) {
        val start = runCatching { LocalDate.parse(bookingStart) }.getOrNull() ?: return
        val next = runCatching { LocalDate.parse(day) }.getOrNull() ?: return
        if (!next.isAfter(start)) {
            toast("结束日期要晚于开始日期")
            return
        }
        bookingEnd = day
        scheduleEstimate()
    }

    fun toggleBookingPet(petId: Long) {
        bookingPetIds = if (bookingPetIds.contains(petId)) {
            bookingPetIds.filterNot { it == petId }
        } else {
            bookingPetIds + petId
        }
        scheduleEstimate()
    }

    fun selectBookingCoupon(coupon: UserCouponDto?) {
        bookingCoupon = coupon
        scheduleEstimate()
    }

    fun selectBookingPoints(points: Int) {
        // 上限同时受余额与本单可抵扣额约束，避免用户选了「全部」却被服务端截断
        val cap = if (estimate == null) pointsBalance else minOf(pointsBalance, maxUsablePoints())
        bookingPoints = points.coerceIn(0, cap.coerceAtLeast(0))
        scheduleEstimate()
    }

    /** 设置某条加购的数量（0~9），改完立即重新估价 */
    fun setBookingAddonQty(addonId: Long, qty: Int) {
        val next = qty.coerceIn(0, 9)
        bookingAddonQty = if (next == 0) bookingAddonQty - addonId else bookingAddonQty + (addonId to next)
        scheduleEstimate()
    }

    /** 本单最多可抵扣的积分：不超过余额，也不超过「优惠后应付 × 100」 */
    fun maxUsablePoints(): Int {
        val est = estimate ?: return 0
        val remaining = (est.subtotal - est.discountAmount).coerceAtLeast(0.0)
        return minOf(pointsBalance, (remaining * 100).toInt())
    }

    /** 换机构时清掉上一家的券/积分/加购，避免把别家的 addon_id 带进新订单 */
    fun resetBookingExtras() {
        bookingCoupon = null
        bookingPoints = 0
        bookingAddonQty = emptyMap()
        sitterAddons = emptyList()
    }

    /** 拉取所选机构的加购服务（进下单页时调用） */
    suspend fun loadSitterAddons() {
        val sitter = selectedSitter ?: return
        sitterAddons = runCatching { api.sitterAddons(sitter.id) }.getOrDefault(emptyList())
    }

    /** 估价请求的句柄：改日期/宠物时会连发多次，必须取消上一次，否则慢请求会覆盖新结果 */
    private var estimateJob: Job? = null

    /** 重新估价（自动取消上一次未完成的请求） */
    fun scheduleEstimate() {
        estimateJob?.cancel()
        estimateJob = scope.launch { refreshEstimate() }
    }

    /** 下单前实时估价，参数不齐时清空；请求失败保留上一次结果，避免金额闪成空 */
    suspend fun refreshEstimate() {
        val sitter = selectedSitter
        if (sitter == null || bookingPetIds.isEmpty() || bookingDays <= 0) {
            estimate = null
            return
        }
        estimating = true
        try {
            val next = try {
                api.estimateOrder(
                    sitterId = sitter.id,
                    startDate = bookingStart,
                    endDate = bookingEnd,
                    petIds = bookingPetIds,
                    userCouponId = bookingCoupon?.id ?: 0L,
                    pointsUsed = bookingPoints,
                    addons = bookingAddonQty.filter { it.value > 0 }
                        .map { "${it.key}:${it.value}" },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (next != null) estimate = next
        } finally {
            estimating = false
        }
    }

    suspend fun createOrder(): Boolean {
        // 防重复下单：一次点击只允许一笔（后端创建订单不做幂等）
        if (busy) return false
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
            // 用返回值锁定刚创建的那笔单，别再用「列表里第一个待支付」去猜，
            // 否则用户本来就有待支付订单时会把钱付到旧单上
            val created = api.createOrder(
                CreateOrderPayload(
                    sitterId = sitter.id,
                    startDate = bookingStart,
                    endDate = bookingEnd,
                    pets = bookingPetIds.map { CreateOrderPetPayload(it) },
                    remark = bookingRemark.trim(),
                    userCouponId = bookingCoupon?.id ?: 0L,
                    pointsUsed = bookingPoints,
                    // 下单走 JSON 数组；估价接口才是 "id:qty" 字符串
                    addons = bookingAddonQty.filter { it.value > 0 }
                        .map { CreateOrderAddonPayload(it.key, it.value) },
                ),
            )
            lastCreatedOrderId = created.orderId
        } catch (e: Exception) {
            toast(e.message ?: "下单失败")
            busy = false
            return false
        }
        busy = false
        bookingCoupon = null
        bookingPoints = 0
        bookingAddonQty = emptyMap()
        estimate = null
        loadOrders(force = true)
        toast("订单已创建，请在 30 分钟内完成支付")
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
        return true
    }

    suspend fun cancelOrder(order: Order, reason: String) {
        try {
            api.cancelOrder(order.id, reason.ifBlank { "个人安排变化" })
            toast("订单已取消，容量已释放")
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "取消失败")
        }
    }

    suspend fun rescheduleOrder(order: Order, start: String, end: String) {
        try {
            val r = api.rescheduleOrder(order.id, start, end)
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
            toast("退款申请已提交，等待平台处理")
            loadRefunds()
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "提交失败")
        }
    }

    suspend fun submitReview(order: Order, overall: Int, service: Int, environment: Int, hygiene: Int, comment: String) {
        try {
            val result = api.submitReview(
                order.id,
                ReviewPayload(overall, service, environment, hygiene, comment.ifBlank { "服务满意，感谢照料。" }.take(1000)),
            )
            lastReviewId = result.id
            toast("评价已提交")
            loadOrders(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "评价提交失败")
        }
    }

    /** 24 小时内追评，只支持文字 */
    suspend fun appendReview(reviewId: Long, content: String): Boolean {
        if (reviewId <= 0L) {
            toast("没有找到可追评的评价")
            return false
        }
        if (content.isBlank()) {
            toast("追评内容不能为空")
            return false
        }
        return try {
            api.followupReview(reviewId, content.trim().take(1000))
            toast("追评已提交")
            true
        } catch (e: Exception) {
            toast(e.message ?: "追评提交失败")
            false
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

    suspend fun markAllRead() = notificationsCenter.markAllNotificationsRead()

    suspend fun markRead(id: Long) = notificationsCenter.markNotificationRead(id)

    suspend fun claimCoupon(couponId: Long) {
        try {
            api.claimCoupon(couponId)
            toast("优惠券领取成功")
            loadCoupons(force = true)
        } catch (e: Exception) {
            toast(e.message ?: "领取失败")
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
            toast("已发送，机构会在消息里回复")
        } catch (e: Exception) {
            toast(e.message ?: "发送失败")
        }
    }

    suspend fun saveProfile(nicknameInput: String, phone: String, address: String, contactName: String, contactPhone: String) {
        try {
            // 空字段传 null（不发这个键），否则后端 COALESCE 会把已有值覆盖成空串
            api.updateProfile(
                UpdateProfilePayload(
                    nickname = nicknameInput.trim().ifBlank { null },
                    phone = phone.trim().ifBlank { null },
                    address = address.trim().ifBlank { null },
                    contactName = contactName.trim().ifBlank { null },
                    contactPhone = contactPhone.trim().ifBlank { null },
                )
            )
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
            val payload = UpdateShopPayload(
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
            )
            // getMyShop 404 说明账号还没有机构记录，这时 PUT 也会失败，
            // 必须先走 POST /sitter/shop 建店。
            myShop = if (myShop == null) {
                api.createMyShop(payload)
            } else {
                api.updateMyShop(payload)
            }
            toast(if (myShop?.status == "pending") "机构资料已提交，等待审核" else "机构资料已保存")
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

    /**
     * 坐标换地址：拖动地图选点时用。
     *
     * 故意不 toast、不抛异常——拖动过程中会连着问好几次，每次都弹提示会很吵；
     * 拿不到就保持上一次的地址文本，用户仍然可以手动改。
     */
    suspend fun reverseAddress(latitude: Double, longitude: Double): GeocodeDto? =
        runCatching { api.reverseGeocode(ReversePayload(latitude, longitude)) }.getOrNull()

    suspend fun loadSitterStats() {
        if (!loggedIn) return
        sitterStats = runCatching { api.sitterStats() }.getOrNull() ?: sitterStats
    }

    suspend fun loadReceivedOrders(force: Boolean = false) {
        if (!loggedIn || receivedOrdersLoading) return
        if (!force && receivedOrders.isNotEmpty()) return
        receivedOrdersLoading = true
        try {
            val data = api.listReceivedOrdersPage(1, PAGE_SIZE)
            receivedOrders = data.items
            receivedOrdersPage = 1
            receivedOrdersHasMore = data.items.size < data.total
        } catch (e: Exception) {
            toast(e.message ?: "订单加载失败")
        } finally {
            receivedOrdersLoading = false
        }
    }

    suspend fun loadMoreReceivedOrders() {
        if (!loggedIn || receivedOrdersLoading || receivedOrdersLoadingMore || !receivedOrdersHasMore) return
        receivedOrdersLoadingMore = true
        try {
            val data = api.listReceivedOrdersPage(receivedOrdersPage + 1, PAGE_SIZE)
            receivedOrdersPage += 1
            receivedOrders = (receivedOrders + data.items).distinctBy { it.id }
            receivedOrdersHasMore = receivedOrders.size < data.total
        } catch (_: Exception) {
        } finally {
            receivedOrdersLoadingMore = false
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
            // 一结束订单，今日收益 / 近 30 天收益 / 结算口径都会变。
            // 收益数据带缓存（earnings != null 就不再请求），这里不主动刷，
            // 收益页会一直停留在结束订单之前的空数据。
            loadEarnings(force = true)
            loadSitterStats()
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
            // 空表示用后端默认区间（近 30 天）
            val from = earningsFrom.ifBlank { today.minusDays(29).toString() }
            val to = earningsTo.ifBlank { today.toString() }
            earnings = api.sitterEarnings(from, to)
        } catch (e: Exception) {
            toast(e.message ?: "收益加载失败")
        } finally {
            earningsLoading = false
        }
    }

    /** 切换收益统计区间并立即重取 */
    suspend fun loadEarningsRange(from: String, to: String) {
        earningsFrom = from
        earningsTo = to
        loadEarnings(force = true)
    }

    /** 本次可申请结算的区间；起点是上次结算之后，漏掉的月份会自动带上 */
    suspend fun loadSettleWindow() {
        if (!loggedIn) return
        settleWindow = runCatching { api.settlementWindow() }.getOrNull() ?: settleWindow
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
        try {
            // 不传区间：后端按「上次结算之后 → 今天」算。
            // 原来固定传「本月 1 号 → 今天」，机构漏点一个月，那段收益就再也申请不到了。
            api.applySettlement()
            toast("结算申请已提交")
            loadSettlements()
            loadSettleWindow()
            loadEarnings(force = true)
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
}