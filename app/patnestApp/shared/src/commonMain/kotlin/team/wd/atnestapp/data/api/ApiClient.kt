package team.wd.atnestapp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter

class ApiException(message: String) : Exception(message)

/** 一页数据 + 总数：列表分页时据此判断还有没有下一页 */
data class PagedItems<T>(val items: List<T>, val total: Int)

/**
 * 跨平台 API 客户端：响应统一是 { code, message, data } 信封，
 * 负责拆信封、携带登录令牌，并把异常翻译成可以直接展示的提示。
 * 引擎由各平台提供（JVM=CIO、Android=OkHttp、iOS=Darwin）。
 */
class ApiClient(rawBaseUrl: String) {
    val baseUrl: String = normalizeBaseUrl(rawBaseUrl)

    var token: String? = null

    /**
     * 刷新令牌。访问令牌过期时用它换一张新的，用户不用重新登录。
     * 标 @PublishedApi 是因为下面的请求封装是 internal inline 函数，inline 不能访问 private 成员。
     */
    @PublishedApi internal var refreshToken: String? = null

    /**
     * 令牌被静默续期后的回调：新的访问令牌 + 轮换后的刷新令牌。
     *
     * 服务端每次刷新都会换一张刷新令牌，本地存储必须跟着更新，
     * 否则下次冷启动手里握着已经作废的那张，一过期就被踢下线。
     * 不设置回调也不影响请求，只是不落盘。
     */
    var onAuthRefreshed: ((access: String, refresh: String) -> Unit)? = null

    /** 登录/注册成功后一次性写入会话，避免调用方只记住 access_token */
    fun applyAuth(access: String, refresh: String?, expiresIn: Long) {
        token = access
        refreshToken = refresh?.takeIf { it.isNotBlank() }
    }

    /** 清空整个会话（登出、令牌彻底失效、被踢下线） */
    fun clearAuth() {
        token = null
        refreshToken = null
    }

    /**
     * 用刷新令牌换一张新的访问令牌。成功返回 true，调用方重放原请求。
     * 失败时把刷新令牌丢掉，之后就是正常的"请重新登录"，不会反复重试。
     */
    @PublishedApi internal suspend fun refreshAccessToken(): Boolean {
        val refresh = refreshToken ?: return false
        return try {
            val response = http.post(apiBase + "/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(RefreshPayload(refresh)))
            }
            val result: RefreshResult = unwrap(response.bodyAsText(), response.status)
            if (result.accessToken.isBlank()) {
                refreshToken = null
                return false
            }
            token = result.accessToken
            // 服务端每次刷新都轮换刷新令牌，必须把新的收下；
            // 万一后端是没带轮换的旧版本（字段为空），就继续沿用手里这张。
            if (result.refreshToken.isNotBlank()) {
                refreshToken = result.refreshToken
            }
            // 落盘：轮换后的凭据立刻写回本地，冷启动才不会拿着旧刷新令牌
            refreshToken?.let { onAuthRefreshed?.invoke(result.accessToken, it) }
            true
        } catch (_: Exception) {
            refreshToken = null
            false
        }
    }

    /**
     * 发一次请求；如果撞上 401 且手里有刷新令牌，就换一张令牌再重放一次。
     * 这样访问令牌可以设得很短（比如 30 分钟），用户却不会被中途踢下线。
     *
     * usedToken 是这次请求实际带出去的令牌。并发请求一起撞上 401 时，
     * 只要有一个请求已经换到了新令牌，其余请求直接重放，不再重复调用刷新接口。
     */
    @PublishedApi internal suspend fun shouldRetryUnauthorized(
        status: HttpStatusCode,
        usedToken: String?,
    ): Boolean {
        if (status != HttpStatusCode.Unauthorized) return false
        // 本来就没登录（登录、注册接口的 401），交给调用方按普通报错提示
        if (usedToken == null) return false
        val refreshed = if (refreshToken == null) {
            false
        } else {
            refreshMutex.withLock {
                if (token != usedToken) true else refreshToken != null && refreshAccessToken()
            }
        }
        if (refreshed) return true
        // 换不到新令牌说明这次会话真的结束了，让 UI 退出登录，别继续挂着一个假登录态
        onAuthExpired?.invoke()
        return false
    }

    /**
     * 会话确认结束（刷新令牌过期、被吊销，或本地已经没有刷新令牌）时回调。
     * 由 UI 层负责清空登录态并提示重新登录。
     */
    var onAuthExpired: (() -> Unit)? = null

    /** 串行化刷新动作，保证同一时刻只有一个请求在换令牌。 */
    @PublishedApi internal val refreshMutex = Mutex()

    /**
     * 服务端确认当前账号已不存在（如被后台删除）时回调，由 UI 层强制退出并清理会话。
     * 命中"用户不存在"时先触发该回调，再返回替换后的友好文案，调用方仍按原逻辑抛 ApiException。
     */
    var onSessionInvalid: (() -> Unit)? = null

    internal fun sessionInvalidMessage(message: String?): String? {
        if (message.isNullOrBlank() || !message.contains("用户不存在")) return null
        onSessionInvalid?.invoke()
        return "账号不存在，已退出登录"
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        // 后端有 required 校验的字段（如注册的 role）带默认值时也必须发出去
        encodeDefaults = true
    }

    internal val http = HttpClient(platformHttpClientEngine()) {
        install(ContentNegotiation) { json(this@ApiClient.json) }
    }

    internal val apiBase: String
        get() = baseUrl.ifBlank {
            throw ApiException("接口地址未配置，请检查 .env 中的 PATNEST_API_BASE")
        }

    internal suspend inline fun <reified T> getData(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): T {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            params.forEach { (key, value) -> parameter(key, value) }
        }
        var response = http.get(apiBase + path, config)
        // 访问令牌过期：换一张，原请求重放一次
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.get(apiBase + path, config)
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified Req, reified Res> postData(path: String, payload: Req): Res {
        val body = json.encodeToString(payload)
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        var response = http.post(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.post(apiBase + path, config)
        return unwrap<Res>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified T> putEmpty(path: String): T {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        var response = http.put(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.put(apiBase + path, config)
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified T> postNoBody(path: String): T {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        var response = http.post(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.post(apiBase + path, config)
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified Req, reified Res> putData(path: String, payload: Req): Res {
        val body = json.encodeToString(payload)
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        var response = http.put(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.put(apiBase + path, config)
        return unwrap<Res>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified T> deleteData(path: String): T {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        var response = http.delete(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.delete(apiBase + path, config)
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    /** 后端返回 data = null 的删除接口（只校验 code / HTTP 状态）。 */
    internal suspend inline fun deleteNoContent(path: String) {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        var response = http.delete(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.delete(apiBase + path, config)
        ensureSuccess(response.bodyAsText(), response.status)
    }

    /** 后端返回 data = null 的接口（只校验 code / HTTP 状态）。 */
    internal suspend inline fun putNoContent(path: String) {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        var response = http.put(apiBase + path, config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.put(apiBase + path, config)
        ensureSuccess(response.bodyAsText(), response.status)
    }

    internal fun ensureSuccess(text: String, status: HttpStatusCode) {
        val envelope = runCatching { json.decodeFromString<ApiStatus>(text) }.getOrNull()
        if (status.value !in 200..299) {
            throw ApiException(
                sessionInvalidMessage(envelope?.message)
                    ?: envelope?.message?.takeIf { it.isNotBlank() }
                    ?: "请求失败（HTTP ${status.value}）",
            )
        }
        if (envelope == null) throw ApiException(describeNonEnvelope(text, status))
        if (envelope.code != 0) {
            throw ApiException(sessionInvalidMessage(envelope.message) ?: envelope.message.ifBlank { "请求失败" })
        }
    }

    internal inline fun <reified T> unwrap(text: String, status: HttpStatusCode): T {
        val envelope = runCatching { json.decodeFromString<ApiEnvelope<T>>(text) }.getOrNull()
        if (status.value !in 200..299) {
            throw ApiException(
                sessionInvalidMessage(envelope?.message)
                    ?: envelope?.message?.takeIf { it.isNotBlank() }
                    ?: "请求失败（HTTP ${status.value}）",
            )
        }
        if (envelope == null) throw ApiException(describeNonEnvelope(text, status))
        if (envelope.code != 0) {
            throw ApiException(sessionInvalidMessage(envelope.message) ?: envelope.message.ifBlank { "请求失败" })
        }
        return envelope.data ?: throw ApiException("服务返回数据为空")
    }

    /**
     * 响应不是 { code, message, data } 信封时，把状态码和响应开头一起带出来。
     * 只说「格式异常」没法排查——最常见的原因就是接口根地址写错了，
     * 少了 /api/v1 就会被网关或静态站点兜回一个 HTML 页面。
     */
    @PublishedApi
    internal fun describeNonEnvelope(text: String, status: HttpStatusCode): String {
        val snippet = text.replace(Regex("\\s+"), " ").trim().take(80)
        return if (snippet.isEmpty()) {
            "服务返回空响应（HTTP ${status.value}），请检查 .env 里的 PATNEST_API_BASE 是否为「主机:端口/api/v1」"
        } else {
            "服务返回的不是接口数据（HTTP ${status.value}）：$snippet —— " +
                "请检查 .env 里的 PATNEST_API_BASE 是否为「主机:端口/api/v1」"
        }
    }

    suspend fun login(username: String, password: String): AuthResponseDto =
        postData("/auth/login", LoginPayload(username.trim(), password))

    /** 退出登录：显式携带令牌，避免本地状态清空后请求头丢失。 */
    suspend fun logout(token: String) {
        val response = http.post("$apiBase/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        ensureSuccess(response.bodyAsText(), response.status)
    }

    suspend fun register(
        username: String,
        password: String,
        nickname: String = "",
        phone: String = "",
        inviteCode: String = "",
    ): AuthResponseDto =
        postData(
            "/auth/register",
            RegisterPayload(
                username.trim(), password, "owner", nickname.trim(), phone.trim(),
                inviteCode.trim().uppercase(),
            ),
        )

    /** 申请注销（冷静期 3 天，期间登录自动撤销；到期由服务端自动执行）。 */
    suspend fun requestAccountCancel(mode: String): CancelRequestResult =
        postData("/pet-owner/account/cancel-request", CancelRequestPayload(mode))

    /** 撤销注销申请。 */
    suspend fun withdrawAccountCancel(): WithdrawCancelResult =
        postNoBody("/pet-owner/account/cancel-withdraw")

    suspend fun me(): UserDto = getData("/auth/me")

    suspend fun listSitters(type: String? = null): List<Sitter> {
        val params = mutableMapOf("page" to "1", "per_page" to "50")
        if (!type.isNullOrBlank()) params["type"] = type
        val page: SitterPageDto = getData("/sitters", params)
        return page.items.map { it.toUi() }
    }

    /** 关键词搜索（服务端按名称/地址/描述 ILIKE 匹配，见 SearchSitters） */
    suspend fun searchSitters(keyword: String): List<Sitter> {
        val q = keyword.trim()
        if (q.isEmpty()) return listSitters()
        val page: SitterPageDto = getData(
            "/sitters/search",
            mapOf("q" to q, "page" to "1", "per_page" to "50"),
        )
        return page.items.map { it.toUi() }
    }

    suspend fun listPets(): List<Pet> = listPetsPage(1, 50).items

    suspend fun listPetsPage(page: Int, perPage: Int = 50): PagedItems<Pet> {
        val data: PetPageDto = getData(
            "/pet-owner/pets",
            mapOf("page" to page.toString(), "per_page" to perPage.toString()),
        )
        return PagedItems(data.items.map { it.toUi() }, data.total)
    }

    suspend fun createPet(payload: CreatePetPayload): PetDto =
        postData("/pet-owner/pets", payload)

    /** 编辑宠物档案（只传要改的字段） */
    suspend fun updatePet(petId: Long, payload: UpdatePetPayload): PetDto =
        putData("/pet-owner/pets/$petId", payload)

    /** 某只宠物的历史寄养记录 */
    suspend fun listPetStays(petId: Long): List<PetStayDto> {
        val data: PetStayListDto = getData("/pet-owner/pets/$petId/stays")
        return data.items
    }

    suspend fun deletePet(petId: Long) = deleteNoContent("/pet-owner/pets/$petId")

    suspend fun listOrders(): List<Order> = listOrdersPage(1, 50).items

    /** 订单分页：后端 per_page 上限 50，超过必须翻页，否则第 51 条起永远拿不到 */
    suspend fun listOrdersPage(page: Int, perPage: Int = 50): PagedItems<Order> {
        val data: OrderPageDto = getData(
            "/pet-owner/orders",
            mapOf("page" to page.toString(), "per_page" to perPage.toString()),
        )
        return PagedItems(data.items.map { it.toUi() }, data.total)
    }

    suspend fun createOrder(payload: CreateOrderPayload): CreateOrderResult =
        postData("/pet-owner/orders", payload)

    suspend fun payOrder(orderId: Long, method: String = "mock"): PayResult =
        putData("/pet-owner/orders/$orderId/pay", PayOrderPayload(method))

    /** 取消订单：后端会释放已占容量 */
    suspend fun cancelOrder(orderId: Long, reason: String): CancelResult =
        putData("/pet-owner/orders/$orderId/cancel", ReasonPayload(reason))

    /** 改期（待支付/已确认状态下可用） */
    suspend fun rescheduleOrder(orderId: Long, startDate: String, endDate: String): RescheduleResult =
        putData("/pet-owner/orders/$orderId/reschedule", ReschedulePayload(startDate, endDate))

    /** 续住：把结束日期往后推，费用按新周期重算 */
    suspend fun renewOrder(orderId: Long, endDate: String): RenewResult =
        putData("/pet-owner/orders/$orderId/renew", RenewPayload(endDate))

    /** 申请退款（进入平台审核，不直接退款） */
    suspend fun requestRefund(orderId: Long, reason: String, amount: Double? = null): ClaimResult =
        postData("/pet-owner/orders/$orderId/refund-request", RefundPayload(reason, amount))

    suspend fun listRefundRequests(): List<RefundDto> {
        val data: RefundListDto = getData("/pet-owner/refunds")
        return data.items
    }

    /**
     * 下单前估价。参数与下单一致，后端用同一套计费口径，
     * 所以页面显示的金额和真正下单的结果不会打架。
     */
    suspend fun estimateOrder(
        sitterId: Long,
        startDate: String,
        endDate: String,
        petIds: List<Long>,
        userCouponId: Long = 0,
        pointsUsed: Int = 0,
        addons: List<String> = emptyList(),
    ): EstimateDto = getData(
        "/pet-owner/sitters/$sitterId/estimate",
        mapOf(
            "start_date" to startDate,
            "end_date" to endDate,
            "pet_ids" to petIds.joinToString(","),
            "user_coupon_id" to userCouponId.toString(),
            "points_used" to pointsUsed.toString(),
            "addons" to addons.joinToString(","),
        ),
    )

    /** 积分余额与流水 */
    suspend fun myPoints(): PointsDto = getData("/pet-owner/points")

    /** 我的邀请码、邀请人数与奖励状态（迁移 0022） */
    suspend fun myInvite(): InviteDto = getData("/pet-owner/invite")

    /* ---------- 钱包（真实货币） ---------- */

    /** 钱包概览（余额、冻结、累计充值/消费） */
    suspend fun walletOverview(): WalletDto = getData("/pet-owner/wallet")

    /** 钱包流水（按类型筛选、分页） */
    suspend fun walletTransactions(page: Int = 1, type: String = ""): WalletTransactionListDto {
        val params = mutableMapOf("page" to page.toString(), "per_page" to "20")
        if (type.isNotBlank()) params["type"] = type
        return getData("/pet-owner/wallet/transactions", params)
    }

    /** 发起充值，返回充值单与支付参数（演示阶段模拟支付） */
    suspend fun createRecharge(amount: Double, channel: String = "mock"): RechargeResultDto =
        postData("/pet-owner/wallet/recharge", RechargeRequestDto(amount, channel))

    /** 模拟确认充值（演示阶段直接确认到账） */
    suspend fun confirmRecharge(rechargeId: Long): RechargeConfirmDto =
        postNoBody<RechargeConfirmDto>("/pet-owner/wallet/recharge/$rechargeId/pay")

    /**
     * 申请把钱包余额退回。钱包没有提现通道，这里只登记申请单，
     * 客服线下打款后由后台确认；提交时余额已扣走，不会重复占用。
     */
    suspend fun applyWalletWithdrawal(
        amount: Double,
        accountName: String,
        accountNo: String,
        contact: String,
    ): WalletWithdrawalResultDto =
        postData(
            "/pet-owner/wallet/withdrawals",
            WalletWithdrawalPayload(amount, accountName, accountNo, contact),
        )

    /** 提交售后工单（联系客服） */
    suspend fun createTicket(category: String, title: String, content: String): ClaimResult =
        postData("/support/tickets", TicketPayload(category, title, content))

    suspend fun listTickets(): List<TicketDto> {
        val data: TicketListDto = getData("/support/tickets")
        return data.items
    }

    /** 工单会话详情：标题、状态与按时间正序的完整往返记录 */
    suspend fun ticketDetail(ticketId: Long): TicketDetailDto = getData("/support/tickets/$ticketId")

    /** 追加一条回复；工单已关闭时后端回 409，走统一错误文案 */
    suspend fun replyTicket(ticketId: Long, content: String): ClaimResult =
        postData("/support/tickets/$ticketId/messages", TicketReplyPayload(content.trim()))

    suspend fun submitReview(orderId: Long, payload: ReviewPayload): ReviewResult =
        postData("/pet-owner/orders/$orderId/review", payload)

    /** 24 小时内追加评价（追评），只允许文字 */
    suspend fun followupReview(reviewId: Long, content: String): UpdatedResult =
        postData("/pet-owner/reviews/$reviewId/followup", FollowupPayload(content))

    suspend fun updateProfile(payload: UpdateProfilePayload): UpdatedResult =
        putData("/auth/profile", payload)

    suspend fun changePassword(oldPassword: String, newPassword: String): UpdatedResult =
        putData("/auth/password", ChangePasswordPayload(oldPassword, newPassword))

    /** 兼容旧版统一接口：返回全部消息，仅用于未读角标等聚合场景 */
    suspend fun listNotifications(): List<NotificationDto> {
        val page: NotificationPageDto = getData("/notifications", mapOf("page" to "1", "per_page" to "50"))
        return page.items
    }

    /** 个人消息：主人视角，scope=personal */
    suspend fun listPersonalNotifications(): List<NotificationDto> {
        val page: NotificationPageDto = getData("/pet-owner/notifications", mapOf("page" to "1", "per_page" to "50"))
        return page.items
    }

    /** 商家消息：机构视角，scope=business，仅 sitter 账号可调用 */
    suspend fun listBusinessNotifications(): List<NotificationDto> {
        val page: NotificationPageDto = getData("/sitter/notifications", mapOf("page" to "1", "per_page" to "50"))
        return page.items
    }

    suspend fun markNotificationRead(id: Long) {
        putNoContent("/notifications/$id/read")
    }

    suspend fun markPersonalNotificationRead(id: Long) {
        putNoContent("/pet-owner/notifications/$id/read")
    }

    suspend fun markBusinessNotificationRead(id: Long) {
        putNoContent("/sitter/notifications/$id/read")
    }

    /** 一键全部已读，返回被标记的条数 */
    suspend fun markAllNotificationsRead(): CountResult = putEmpty("/notifications/read-all")

    /** 只清个人消息，商家消息未读数不受影响 */
    suspend fun markAllPersonalNotificationsRead(): CountResult = putEmpty("/pet-owner/notifications/read-all")

    /** 只清商家消息，个人消息未读数不受影响 */
    suspend fun markAllBusinessNotificationsRead(): CountResult = putEmpty("/sitter/notifications/read-all")

    suspend fun listFavorites(): List<Sitter> {
        val data: FavoriteListDto = getData("/pet-owner/favorites")
        return data.items.map { it.toUi() }
    }

    suspend fun addFavorite(sitterId: Long) {
        postData<FavoritePayload, FavoriteResult>("/pet-owner/favorites", FavoritePayload(sitterId))
    }

    suspend fun removeFavorite(sitterId: Long) {
        deleteData<FavoriteResult>("/pet-owner/favorites/$sitterId")
    }

    suspend fun listDynamics(orderId: Long): List<DynamicDto> {
        val page: DynamicPageDto = getData(
            "/pet-owner/orders/$orderId/dynamics",
            mapOf("page" to "1", "per_page" to "50"),
        )
        return page.items
    }

    suspend fun listOwnerDynamics(): List<DynamicDto> {
        val data: DynamicListDto = getData("/pet-owner/dynamics")
        return data.items
    }

    /** 点赞动态（后端 ON CONFLICT DO NOTHING，重复调用安全） */
    suspend fun likeDynamic(dynamicId: Long): LikeResult =
        postNoBody("/pet-owner/dynamics/$dynamicId/like")

    /** 取消点赞 */
    suspend fun unlikeDynamic(dynamicId: Long) {
        deleteNoContent("/pet-owner/dynamics/$dynamicId/like")
    }

    /**
     * 动态留言列表。
     *
     * 返回体里同时带 likes（点赞总数），所以详情页进页面拉一次留言就能把
     * 「已有 N 人点赞」渲染出来，不必再让 /dynamics 列表的数据当缓存。
     */
    suspend fun listDynamicComments(dynamicId: Long): DynamicCommentListDto =
        getData("/pet-owner/dynamics/$dynamicId/comments")

    /** 发表留言；后端只校验长度 1~500，返回新建记录的 id。 */
    suspend fun createDynamicComment(dynamicId: Long, content: String): ClaimResult =
        postData("/pet-owner/dynamics/$dynamicId/comments", CommentPayload(content.trim()))

    suspend fun listCoupons(): CouponListDto = getData("/pet-owner/coupons")


    suspend fun claimCoupon(couponId: Long) {
        postNoBody<ClaimResult>("/pet-owner/coupons/$couponId/claim")
    }

    /* ---------- 机构端 ---------- */

    suspend fun getMyShop(): SitterDto = getData("/sitter/shop/me")

    suspend fun updateMyShop(payload: UpdateShopPayload): SitterDto =
        putData("/sitter/shop/me", payload)

    /** 城市 + 地址 → GCJ-02 坐标（后端调高德地理编码，桌面端没有系统定位时用）。 */
    suspend fun geocodeAddress(payload: GeocodePayload): GeocodeDto =
        postData("/geo/geocode", payload)

    /** GCJ-02 坐标 → 地址文本。 */
    suspend fun reverseGeocode(payload: ReversePayload): GeocodeDto =
        postData("/geo/reverse", payload)

    /**
     * 取一张以 (latitude, longitude) 为中心的静态地图 PNG 字节。
     * 成功时响应是图片，失败时后端仍返回统一信封，这里照样翻译成能直接展示的提示。
     */
    suspend fun fetchStaticMap(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        width: Int,
        height: Int,
    ): ByteArray {
        val params = mapOf(
            "latitude" to latitude.toString(),
            "longitude" to longitude.toString(),
            "zoom" to zoom.toString(),
            "width" to width.toString(),
            "height" to height.toString(),
        )
        return getImage("/geo/static-map", params)
    }

    /** 取一张 256x256 地图瓦片（桌面端弹框连续拖动用）。 */
    suspend fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray = getImage(
        "/geo/tile",
        mapOf("z" to zoom.toString(), "x" to x.toString(), "y" to y.toString()),
    )

    /**
     * 图片类接口的统一读取：body 只能读一次，所以先取字节再判断。
     * 光看 Content-Type 不可靠（代理或老版本可能不声明），因此同时认 PNG 魔数。
     */
    private suspend fun getImage(path: String, params: Map<String, String>): ByteArray {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            params.forEach { (key, value) -> parameter(key, value) }
        }
        var response = http.get(apiBase + path, config)
        // 地图瓦片也是一次次请求，令牌过期时同样先续期再重放
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.get(apiBase + path, config)
        val bytes = response.bodyAsBytes()
        val mimeType = response.contentType()?.contentType.orEmpty()
        if (mimeType.startsWith("image/") || isPngImage(bytes)) return bytes

        val text = bytes.decodeToString()
        if (response.status.value == 404) {
            throw ApiException("后端没有 $path 这条路由（HTTP 404），需要重新发布后端")
        }
        // 后端正常报错时仍是统一 JSON 信封，优先用它给出的原因
        ensureSuccess(text, response.status)
        // 走到这里说明 HTTP 成功、但既不是图片也不是信封：
        // 多半是请求没打到接口（被网关或静态站点兜回了页面），把状态码、类型和响应开头一起报出来
        val snippet = text.replace("\n", " ").trim().take(60)
        throw ApiException(
            "接口没返回图片（HTTP ${response.status.value}，类型 ${mimeType.ifBlank { "未声明" }}，" +
                "响应开头「$snippet」）",
        )
    }

    /** 地图选点第一步：创建一次性选点会话，返回浏览器里打开的相对路径。 */
    suspend fun createGeoPick(payload: GeoPickPayload): GeoPickSessionDto =
        postData("/geo/pick", payload)

    /** 轮询选点结果；done 之后后端即销毁会话，重复轮询会得到 expired。 */
    suspend fun pollGeoPick(token: String): GeoPickResultDto =
        getData("/geo/pick-result/$token")

    /** 机构入驻申请：提交资质材料，平台审核通过后账号才升级为机构。 */
    suspend fun applySitter(payload: ApplySitterPayload): ApplySitterResult =
        postData("/pet-owner/apply-sitter", payload)

    /* ---------- 机构优惠券管理 ---------- */

    suspend fun listSitterCoupons(): SitterCouponPageDto = getData("/sitter/coupons")

    suspend fun createSitterCoupon(payload: SitterCouponPayload): ClaimResult =
        postData("/sitter/coupons", payload)

    suspend fun updateSitterCoupon(couponId: Long, payload: SitterCouponPayload): ClaimResult =
        putData("/sitter/coupons/$couponId", payload)

    /** 删除本机构优惠券；已被订单核销的券服务端会拒绝删除。 */
    suspend fun deleteSitterCoupon(couponId: Long) = deleteNoContent("/sitter/coupons/$couponId")

    suspend fun listReceivedOrders(): List<ReceivedOrderDto> = listReceivedOrdersPage(1, 50).items

    suspend fun listReceivedOrdersPage(page: Int, perPage: Int = 50): PagedItems<ReceivedOrderDto> {
        val data: ReceivedOrderPageDto = getData(
            "/sitter/orders/manage",
            mapOf("page" to page.toString(), "per_page" to perPage.toString()),
        )
        return PagedItems(data.items, data.total)
    }

    suspend fun acceptOrder(orderId: Long) {
        putEmpty<StatusResult>("/sitter/orders/$orderId/accept")
    }

    suspend fun rejectOrder(orderId: Long, reason: String) {
        putData<RejectPayload, StatusResult>("/sitter/orders/$orderId/reject", RejectPayload(reason))
    }

    suspend fun completeOrder(orderId: Long) {
        putEmpty<StatusResult>("/sitter/orders/$orderId/complete")
    }

    suspend fun myCapacity(from: String, to: String): CapacityDto =
        getData("/sitter/capacity", mapOf("from" to from, "to" to to))

    /** 调整指定日期的接待容量（1-200，且不低于当日已在住数量）。 */
    suspend fun setMyCapacity(date: String, capacity: Int): StatusResult =
        putData("/sitter/capacity", CapacitySetPayload(date, capacity))
    suspend fun sitterEarnings(from: String, to: String): EarningsDto =
        getData("/sitter/earnings", mapOf("from" to from, "to" to to))

    suspend fun listSitterReviews(): List<SitterReviewDto> {
        val data: SitterReviewListDto = getData("/sitter/reviews")
        return data.items
    }

    suspend fun replyReview(reviewId: Long, text: String) {
        putData<ReplyPayload, StatusResult>("/sitter/reviews/$reviewId/reply", ReplyPayload(text))
    }

    suspend fun listSitterDynamics(): List<DynamicDto> {
        val data: DynamicListDto = getData("/sitter/dynamics")
        return data.items
    }

    suspend fun publishDynamic(orderId: Long, content: String, photos: List<String> = emptyList()): DynamicDto =
        postData("/sitter/orders/$orderId/dynamics", CreateDynamicPayload(content, photos))

    suspend fun sitterCapacityPublic(sitterId: Long, from: String, to: String): CapacityDto =
        getData("/sitters/$sitterId/capacity", mapOf("from" to from, "to" to to))

    suspend fun sitterReviewsPublic(sitterId: Long): List<PublicReviewDto> {
        val data: PublicReviewListDto = getData("/sitters/$sitterId/reviews")
        return data.items
    }

    suspend fun listSettlements(): List<SettlementDto> {
        val data: SettlementListDto = getData("/sitter/settlements")
        return data.items
    }

    suspend fun applySettlement(periodStart: String = "", periodEnd: String = ""): SettlementDto =
        postData("/sitter/settlements", SettlementApplyPayload(periodStart, periodEnd))

    /** 本次可申请结算的区间（起点为上次结算之后，漏掉的月份会自动带上） */
    suspend fun settlementWindow(): SettlementWindowDto = getData("/sitter/settlements/window")

    suspend fun sitterAddons(sitterId: Long): List<AddonDto> {
        val data: AddonListDto = getData("/sitters/$sitterId/addons")
        return data.items
    }

    /** 机构自己看加购服务：包含已下架的，方便重新上架 */
    suspend fun myAddons(): List<AddonDto> {
        val data: AddonListDto = getData("/sitter/addons")
        return data.items
    }

    suspend fun createAddon(payload: AddonPayload): ClaimResult = postData("/sitter/addons", payload)

    suspend fun updateAddon(addonId: Long, payload: AddonUpdatePayload): UpdatedResult =
        putData("/sitter/addons/$addonId", payload)

    suspend fun deletePriceRule(ruleId: Long) {
        deleteNoContent("/sitter/pricing-rules/$ruleId")
    }

    suspend fun deleteSitterDynamic(dynamicId: Long) {
        deleteData<DeleteResult>("/sitter/dynamics/$dynamicId")
    }

    suspend fun contactSitter(sitterId: Long, message: String) {
        postData<ContactPayload, ContactResult>("/pet-owner/sitters/$sitterId/contact", ContactPayload(message))
    }

    suspend fun sitterStats(): SitterStatsDto = getData("/sitter/stats")

    suspend fun listPriceRules(): List<PriceRuleDto> {
        val data: PriceRuleListDto = getData("/sitter/pricing-rules")
        return data.items
    }

    suspend fun createPriceRule(payload: PriceRulePayload): PriceRuleDto =
        postData("/sitter/pricing-rules", payload)

    /** 上传图片到对象存储，返回可访问 URL。 */
    suspend fun uploadImage(bytes: ByteArray, fileName: String): String {
        val usedToken = token
        val config: HttpRequestBuilder.() -> Unit = {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }))
        }
        var response = http.post(apiBase + "/upload/image", config)
        if (shouldRetryUnauthorized(response.status, usedToken)) response = http.post(apiBase + "/upload/image", config)
        val result: UploadResult = unwrap(response.bodyAsText(), response.status)
        return result.url
    }

    suspend fun updatePetVaccine(petId: Long, urls: List<String>) {
        putData<VaccinePayload, UpdatedResult>("/pet-owner/pets/$petId/vaccine", VaccinePayload(urls))
    }

}

private fun normalizeBaseUrl(raw: String): String {
    var value = raw.trim()
    if (value.isEmpty()) return ""
    if (!value.startsWith("http://", ignoreCase = true) &&
        !value.startsWith("https://", ignoreCase = true)
    ) {
        value = "http://$value"
    }
    return value.trimEnd('/')
}

/** PNG 文件头 \x89PNG\r\n\x1a\n 的前四字节，用来在 Content-Type 缺失时仍能认出图片。 */
private fun isPngImage(bytes: ByteArray): Boolean =
    bytes.size > 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() &&
        bytes[3] == 'G'.code.toByte()
