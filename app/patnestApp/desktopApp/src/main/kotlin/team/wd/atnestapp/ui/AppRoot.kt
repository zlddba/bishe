package team.wd.atnestapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.ApiClient
import team.wd.atnestapp.data.api.AuthResponseDto
import team.wd.atnestapp.data.api.CreateOrderPayload
import team.wd.atnestapp.data.api.CreateOrderPetPayload
import team.wd.atnestapp.data.api.CreatePetPayload
import team.wd.atnestapp.data.api.CouponListDto
import team.wd.atnestapp.data.api.DynamicDto
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.EarningsDto
import team.wd.atnestapp.data.api.ReceivedOrderDto
import team.wd.atnestapp.data.api.SitterDto
import team.wd.atnestapp.data.api.SitterReviewDto
import team.wd.atnestapp.data.api.UpdateShopPayload
import team.wd.atnestapp.data.api.PublicReviewDto
import team.wd.atnestapp.data.api.SettlementDto
import team.wd.atnestapp.data.api.AddonDto
import team.wd.atnestapp.data.api.SitterStatsDto
import team.wd.atnestapp.data.api.PriceRuleDto
import team.wd.atnestapp.data.api.PriceRulePayload
import team.wd.atnestapp.ui.data.SbOrder
import team.wd.atnestapp.data.api.NotificationDto
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
    SbDash("经营概览"),
    SbOrders("接单管理"),
    SbCapacity("容量日历"),
    SbPublish("寄养动态发布"),
    SbReviews("评价管理"),
    SbIncome("收益结算"),
    SbProfile("机构资料"),
}

private enum class Screen { Launch, Main }

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
}

class AppState {
    val api = ApiClient(runCatching { AppConfig.apiBaseUrl }.getOrDefault("")).also { client ->
        // 任意接口返回"用户不存在"（账号已被删除）→ 强制退出并清空本地会话数据
        client.onSessionInvalid = { forceLogoutOnMissingUser() }
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

    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var notificationsLoading by mutableStateOf(false)
    var notificationsError by mutableStateOf<String?>(null)

    var profile by mutableStateOf<UserDto?>(null)

    var ownerDynamics by mutableStateOf<List<DynamicDto>>(emptyList())
    var dynamicsLoading by mutableStateOf(false)
    var dynamicsError by mutableStateOf<String?>(null)

    var coupons by mutableStateOf<CouponListDto?>(null)
    var couponsLoading by mutableStateOf(false)
    var couponsError by mutableStateOf<String?>(null)

    var myShop by mutableStateOf<SitterDto?>(null)
    var shopLoading by mutableStateOf(false)
    var shopError by mutableStateOf<String?>(null)

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

    var selectedCouponId by mutableStateOf(0L)
    var selectedCouponTitle by mutableStateOf("")
    var selectedCouponType by mutableStateOf("")
    var selectedCouponValue by mutableStateOf(0.0)

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
        api.token = authResponse.accessToken
        // 登录后查询一次用户信息，角色以服务端 /auth/me 为准
        val me = try {
            api.me()
        } catch (e: Exception) {
            api.token = null
            toast(e.message ?: "获取用户信息失败")
            return
        }
        if (me.role == "admin") {
            api.token = null
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
        loadNotifications(force = true)
        loadFavorites(force = true)
    }

    fun logout() {
        val currentToken = api.token
        if (!currentToken.isNullOrBlank()) {
            scope.launch { runCatching { api.logout(currentToken) } }
        }
        clearSession()
        toast("已退出登录")
    }

    /** 申请注销：account=整个账户，sitter=仅注销机构；提交成功后立即退出登录，冷静期 3 天内重新登录自动撤销。 */
    suspend fun requestAccountCancel(mode: String) {
        try {
            api.requestAccountCancel(mode)
            // 直接退出登录：吊销刷新令牌并清空本地会话（冷静期内重新登录会自动撤销注销）
            val currentToken = api.token
            if (!currentToken.isNullOrBlank()) {
                runCatching { api.logout(currentToken) }
            }
            clearSession()
            toast(
                if (mode == "sitter") {
                    "机构注销申请已提交，3 天内登录可撤销"
                } else {
                    "注销申请已提交，3 天内登录可撤销"
                },
            )
        } catch (e: Exception) {
            toast(e.message ?: "提交注销申请失败")
        }
    }

    /** 撤销注销申请。 */
    suspend fun withdrawAccountCancel() {
        try {
            api.withdrawAccountCancel()
            toast("已撤销注销申请")
            loadProfile()
        } catch (e: Exception) {
            toast(e.message ?: "撤销失败")
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
     * 清空登录态与所有按用户缓存的数据。
     * 桌面端没有磁盘持久化（无本地令牌/凭据文件），内存里的这份状态就是全部"保存的数据"。
     */
    private fun clearSession() {
        // 登录态与令牌
        loggedIn = false
        isSitterAccount = false
        accountRole = "owner"
        account = ""
        nickname = ""
        pendingPage = null
        api.token = null
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
        notifications = emptyList()
        notificationsLoading = false
        notificationsError = null
        profile = null
        ownerDynamics = emptyList()
        dynamicsLoading = false
        dynamicsError = null
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
        val species = when (typeLabel) {
            "猫" -> "cat"
            "狗" -> "dog"
            else -> "other"
        }
        val bodySize = when (sizeLabel) {
            "迷你" -> "tiny"
            "小型" -> "small"
            "中型" -> "medium"
            "大型" -> "large"
            "超大型" -> "xlarge"
            else -> ""
        }
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

    suspend fun createOrder(sitterId: Long, startDate: String, endDate: String, petId: Long) {
        try {
            val result = api.createOrder(
                CreateOrderPayload(
                    sitterId = sitterId,
                    startDate = startDate,
                    endDate = endDate,
                    pets = listOf(CreateOrderPetPayload(petId)),
                    userCouponId = selectedCouponId,
                ),
            )
            selectedCouponId = 0L
            selectedCouponTitle = ""
            selectedCouponType = ""
            selectedCouponValue = 0.0
            loadOrders(force = true)
            val index = orders.indexOfFirst { it.id == result.orderId }
            if (index >= 0) orderIndex = index
            toast("订单已创建，请在 30 分钟内完成支付")
            go(AppPage.Pay)
        } catch (e: Exception) {
            toast(e.message ?: "下单失败")
        }
    }

    suspend fun payOrder(orderId: Long): Boolean = try {
        api.payOrder(orderId)
        loadOrders(force = true)
        toast("支付成功")
        true
    } catch (e: Exception) {
        toast(e.message ?: "支付失败")
        false
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

    suspend fun loadNotifications(force: Boolean = false) {
        if (!loggedIn || notificationsLoading) return
        if (!force && notifications.isNotEmpty()) return
        notificationsLoading = true
        notificationsError = null
        try {
            notifications = api.listNotifications()
        } catch (e: Exception) {
            notificationsError = e.message ?: "加载消息失败"
        } finally {
            notificationsLoading = false
        }
    }

    suspend fun markNotificationRead(id: Long) {
        runCatching { api.markNotificationRead(id) }
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
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

    suspend fun applySitter(
        name: String,
        typeLabel: String,
        address: String,
        capacity: Int,
        dailyPrice: Double,
        visitPrice: Double,
        description: String,
    ) {
        val type = when (typeLabel) {
            "家庭寄养" -> "home"
            "门店寄养" -> "shop"
            else -> "visit"
        }
        try {
            val auth = api.applySitter(
                UpdateShopPayload(
                    name = name.trim(),
                    type = type,
                    description = description.trim(),
                    dailyPrice = dailyPrice,
                    visitPrice = visitPrice,
                    priceMode = if (type == "visit") "per_visit" else "per_day",
                    capacity = capacity,
                    address = address.trim(),
                ),
            )
            api.token = auth.accessToken
            account = auth.user.username.ifBlank { account }
            nickname = auth.user.nickname?.takeIf { it.isNotBlank() } ?: nickname
            accountRole = "sitter"
            isSitterAccount = true
            role = AppRole.Owner
            modal = null
            toast("机构申请已提交，等待平台审核")
            loadMyShop(force = true)
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
        } catch (e: Exception) {
            shopError = e.message ?: "加载机构资料失败"
        } finally {
            shopLoading = false
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
    ) {
        try {
            myShop = api.updateMyShop(
                UpdateShopPayload(
                    name, type, description, dailyPrice, visitPrice, priceMode, capacity, address,
                    coverPhotoUrl, detailPhotos,
                ),
            )
            toast("机构资料已保存")
        } catch (e: Exception) {
            toast(e.message ?: "保存失败")
        }
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
            AppPage.Favorites, AppPage.Coupons, AppPage.Messages,
            AppPage.SbDash, AppPage.SbOrders, AppPage.SbCapacity, AppPage.SbPublish,
            AppPage.SbReviews, AppPage.SbIncome, AppPage.SbProfile,
        )

        /** 只有机构账号才能进入的页面。 */
        private val sitterPages = setOf(
            AppPage.SbDash, AppPage.SbOrders, AppPage.SbCapacity, AppPage.SbPublish,
            AppPage.SbReviews, AppPage.SbIncome, AppPage.SbProfile,
        )
    }
}

/**
 * 应用根：启动页 → 登录页 → 主界面。titleBar 由桌面端注入（窗口拖拽与最小化/关闭）。
 */
@Composable
fun AppRoot(titleBar: @Composable () -> Unit = {}) {
    var screen by remember { mutableStateOf(Screen.Launch) }
    val scope = rememberCoroutineScope()
    val app = remember { AppState().also { it.scope = scope } }

    LaunchedEffect(screen) {
        if (screen == Screen.Launch) {
            delay(1500)
            screen = Screen.Main
            app.loadSitters()
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
            listOf(
                "宠物名" to p.name,
                "品种" to p.breed,
                "类型" to p.type,
                "体型" to p.size,
                "年龄" to p.age,
                "疫苗情况" to p.vaccine,
                "免疫证明" to p.cert,
                "累计寄养" to "${p.orders} 单",
                "健康备注" to p.note,
            ).forEach { (k, v) -> KeyValueRow(k, v) }
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
        }
        is DrawerKind.DynDetail -> {
            val d = app.ownerDynamics.getOrNull(kind.index) ?: return
            listOf(
                "发布时间" to d.createdAt.replace('T', ' ').take(16),
                "照片" to "${d.photos.size} 张",
                "动态内容" to d.content,
            ).forEach { (k, v) -> KeyValueRow(k, v) }
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
            PrimaryButton("点赞", {
                app.drawer = null
                app.toast("已点赞")
            })
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
            .width(420.dp)
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
                    ModalKind.ApplySitter -> "申请成为机构账号"
                    ModalKind.PriceRule -> "节假日定价"
                },
                color = PatNestColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(26.dp).clickable { app.modal = null }, contentAlignment = Alignment.Center) {
                AppIcon(AppIcon.Close, Modifier.size(16.dp), PatNestColors.Ink3)
            }
        }
        ThinDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
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
            var capacityText by remember { mutableStateOf("3") }
            var dailyPriceText by remember { mutableStateOf("130") }
            var visitPriceText by remember { mutableStateOf("60") }
            var description by remember { mutableStateOf("") }
            FieldLabel("机构名称")
            SoftTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "如：萌宠之家",
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel("机构类型")
            ChipRow(listOf("家庭寄养", "门店寄养", "上门喂养"), type) { type = it }
            Spacer(Modifier.height(10.dp))
            FieldLabel("所在地址")
            SoftTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "如：呼和浩特 · 赛罕区",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("可接待容量")
                    SoftTextField(capacityText, { capacityText = it }, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("日单价（元）")
                    SoftTextField(dailyPriceText, { dailyPriceText = it }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("上门单价（元）")
                    SoftTextField(visitPriceText, { visitPriceText = it }, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldLabel("机构简介")
            SoftTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "介绍寄养环境、照料方式等",
                singleLine = false,
                minHeight = 72.dp,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton("取消", { app.modal = null }, Modifier.weight(1f))
                PrimaryButton("提交申请", {
                    when {
                        name.isBlank() -> app.toast("请输入机构名称")
                        (capacityText.filter { it.isDigit() }.toIntOrNull() ?: 0) < 1 -> app.toast("请填写正确的接待容量")
                        else -> app.scope.launch {
                            app.applySitter(
                                name = name,
                                typeLabel = type,
                                address = address,
                                capacity = capacityText.filter { it.isDigit() }.toIntOrNull() ?: 1,
                                dailyPrice = dailyPriceText.toDoubleOrNull() ?: 0.0,
                                visitPrice = visitPriceText.toDoubleOrNull() ?: 0.0,
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
    }
}

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
