package team.wd.atnestapp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter

class ApiException(message: String) : Exception(message)

/**
 * 跨平台 API 客户端：响应统一是 { code, message, data } 信封，
 * 负责拆信封、携带登录令牌，并把异常翻译成可以直接展示的提示。
 * 引擎由各平台提供（JVM=CIO、Android=OkHttp、iOS=Darwin）。
 */
class ApiClient(rawBaseUrl: String) {
    val baseUrl: String = normalizeBaseUrl(rawBaseUrl)

    var token: String? = null

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
        val response = http.get(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            params.forEach { (key, value) -> parameter(key, value) }
        }
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified Req, reified Res> postData(path: String, payload: Req): Res {
        val response = http.post(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(payload))
        }
        return unwrap<Res>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified T> putEmpty(path: String): T {
        val response = http.put(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified T> postNoBody(path: String): T {
        val response = http.post(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified Req, reified Res> putData(path: String, payload: Req): Res {
        val response = http.put(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(payload))
        }
        return unwrap<Res>(response.bodyAsText(), response.status)
    }

    internal suspend inline fun <reified T> deleteData(path: String): T {
        val response = http.delete(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        return unwrap<T>(response.bodyAsText(), response.status)
    }

    /** 后端返回 data = null 的删除接口（只校验 code / HTTP 状态）。 */
    internal suspend inline fun deleteNoContent(path: String) {
        val response = http.delete(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        ensureSuccess(response.bodyAsText(), response.status)
    }

    /** 后端返回 data = null 的接口（只校验 code / HTTP 状态）。 */
    internal suspend inline fun putNoContent(path: String) {
        val response = http.put(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        ensureSuccess(response.bodyAsText(), response.status)
    }

    internal inline fun ensureSuccess(text: String, status: HttpStatusCode) {
        val envelope = runCatching { json.decodeFromString<ApiStatus>(text) }.getOrNull()
        if (status.value !in 200..299) {
            throw ApiException(
                sessionInvalidMessage(envelope?.message)
                    ?: envelope?.message?.takeIf { it.isNotBlank() }
                    ?: "请求失败（HTTP ${status.value}）",
            )
        }
        if (envelope == null) throw ApiException("服务返回格式异常")
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
        if (envelope == null) throw ApiException("服务返回格式异常")
        if (envelope.code != 0) {
            throw ApiException(sessionInvalidMessage(envelope.message) ?: envelope.message.ifBlank { "请求失败" })
        }
        return envelope.data ?: throw ApiException("服务返回数据为空")
    }

    suspend fun login(username: String, password: String): AuthResponseDto =
        postData("/auth/login", LoginPayload(username.trim(), password))

    /** 退出登录：显式携带令牌，避免本地状态清空后请求头丢失。 */
    suspend fun logout(token: String) {
        val response = http.post(apiBase + "/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        ensureSuccess(response.bodyAsText(), response.status)
    }

    suspend fun register(
        username: String,
        password: String,
        nickname: String = "",
        phone: String = "",
    ): AuthResponseDto =
        postData(
            "/auth/register",
            RegisterPayload(username.trim(), password, "owner", nickname.trim(), phone.trim()),
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

    suspend fun listPets(): List<Pet> {
        val page: PetPageDto = getData("/pet-owner/pets", mapOf("page" to "1", "per_page" to "50"))
        return page.items.map { it.toUi() }
    }

    suspend fun createPet(payload: CreatePetPayload): PetDto =
        postData("/pet-owner/pets", payload)

    suspend fun deletePet(petId: Long) = deleteNoContent("/pet-owner/pets/$petId")

    suspend fun listOrders(): List<Order> {
        val page: OrderPageDto = getData("/pet-owner/orders", mapOf("page" to "1", "per_page" to "50"))
        return page.items.map { it.toUi() }
    }

    suspend fun createOrder(payload: CreateOrderPayload): CreateOrderResult =
        postData("/pet-owner/orders", payload)

    suspend fun payOrder(orderId: Long): PayResult =
        putEmpty<PayResult>("/pet-owner/orders/$orderId/pay")

    suspend fun submitReview(orderId: Long, payload: ReviewPayload): ReviewResult =
        postData("/pet-owner/orders/$orderId/review", payload)

    suspend fun updateProfile(payload: UpdateProfilePayload): UpdatedResult =
        putData("/auth/profile", payload)

    suspend fun changePassword(oldPassword: String, newPassword: String): UpdatedResult =
        putData("/auth/password", ChangePasswordPayload(oldPassword, newPassword))

    suspend fun listNotifications(): List<NotificationDto> {
        val page: NotificationPageDto = getData("/notifications", mapOf("page" to "1", "per_page" to "50"))
        return page.items
    }

    suspend fun markNotificationRead(id: Long) {
        putNoContent("/notifications/$id/read")
    }

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

    suspend fun listCoupons(): CouponListDto = getData("/pet-owner/coupons")

    /** 删除券包中的优惠券（id 为券包记录 id；已使用的券服务端会拒绝删除）。 */
    suspend fun deleteMyCoupon(userCouponId: Long) = deleteNoContent("/pet-owner/coupons/mine/$userCouponId")

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
        val response = http.get(apiBase + path) {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            params.forEach { (key, value) -> parameter(key, value) }
        }
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

    suspend fun listReceivedOrders(): List<ReceivedOrderDto> {
        val page: ReceivedOrderPageDto = getData("/sitter/orders/manage", mapOf("page" to "1", "per_page" to "50"))
        return page.items
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

    suspend fun applySettlement(periodStart: String, periodEnd: String): SettlementDto =
        postData("/sitter/settlements", SettlementApplyPayload(periodStart, periodEnd))

    suspend fun sitterAddons(sitterId: Long): List<AddonDto> {
        val data: AddonListDto = getData("/sitters/$sitterId/addons")
        return data.items
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
        val response = http.post(apiBase + "/upload/image") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }))
        }
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
