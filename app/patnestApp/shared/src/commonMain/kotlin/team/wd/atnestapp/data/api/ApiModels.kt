package team.wd.atnestapp.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
)

@Serializable
data class LoginPayload(
    val username: String,
    val password: String,
    /** app=桌面/手机客户端，服务端会拒绝管理员账号。 */
    val client: String = "app",
)

@Serializable
data class RegisterPayload(
    val username: String,
    val password: String,
    val role: String = "owner",
    val nickname: String = "",
    val phone: String = "",
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val username: String = "",
    val nickname: String? = null,
    val role: String = "owner",
    val phone: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val status: Int = 1,
    val points: Int = 0,
    val address: String? = null,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("cancel_mode") val cancelMode: String? = null,
    @SerialName("cancel_deadline") val cancelDeadline: String? = null,
    @SerialName("sitter_application") val sitterApplication: SitterApplicationDto? = null,
)

/** 机构入驻申请状态：pending=审核中，rejected=已驳回（含原因）。 */
@Serializable
data class SitterApplicationDto(
    val status: String = "",
    @SerialName("review_note") val reviewNote: String? = null,
)

@Serializable
data class AuthResponseDto(
    val user: UserDto = UserDto(),
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("single_session") val singleSession: Boolean = false,
    @SerialName("cancel_withdrawn") val cancelWithdrawn: Boolean = false,
)

/** 用刷新令牌换一张新的访问令牌 */
@Serializable
data class RefreshPayload(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class RefreshResult(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    /** 服务端做了刷新令牌轮换：每次刷新都会给一张新的，旧的当场作废 */
    @SerialName("refresh_token") val refreshToken: String = "",
)

/** 申请注销：mode=account 整个账户 / sitter 仅注销机构。 */
@Serializable
data class CancelRequestPayload(val mode: String)

/** 机构入驻申请：提交营业执照 / 身份证等资质材料，平台审核通过后账号才升级为机构。 */
@Serializable
data class ApplySitterPayload(
    val name: String,
    val type: String,
    @SerialName("contact_person") val contactPerson: String,
    @SerialName("license_photo_url") val licensePhotoUrl: String,
    @SerialName("id_card_photo_url") val idCardPhotoUrl: String = "",
    val address: String = "",
    val description: String = "",
)

@Serializable
data class ApplySitterResult(
    @SerialName("sitter_id") val sitterId: Long = 0,
    val status: String = "pending",
)

@Serializable
data class CancelRequestResult(val mode: String = "", val deadline: String = "")

@Serializable
data class WithdrawCancelResult(val withdrawn: Boolean = false)

@Serializable
data class SitterDto(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    val name: String = "",
    val type: String = "home",
    val description: String? = null,
    @SerialName("daily_price") val dailyPrice: Double = 0.0,
    @SerialName("visit_price") val visitPrice: Double = 0.0,
    @SerialName("price_mode") val priceMode: String = "per_day",
    val capacity: Int = 0,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("avg_score") val avgScore: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerialName("detail_photos") val detailPhotos: List<String> = emptyList(),
    val status: String = "active",
)

@Serializable
data class SitterPageDto(
    val items: List<SitterDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
)

@Serializable
data class PetDto(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    val name: String = "",
    val species: String = "other",
    val breed: String? = null,
    @SerialName("body_size") val bodySize: String? = null,
    @SerialName("age_month") val ageMonth: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val vaccinated: Boolean = false,
    val sterilized: Boolean = false,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("vaccine_urls") val vaccineUrls: List<String> = emptyList(),
    @SerialName("vaccine_status") val vaccineStatus: String = "missing",
    @SerialName("vaccine_note") val vaccineNote: String? = null,
    val notes: String? = null,
)

@Serializable
data class PetPageDto(
    val items: List<PetDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
)

@Serializable
data class OrderDto(
    val id: Long = 0,
    @SerialName("order_no") val orderNo: String = "",
    val status: String = "",
    @SerialName("payment_status") val paymentStatus: String = "",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("pet_count") val petCount: Int = 0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("sitter_name") val sitterName: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class OrderPageDto(
    val items: List<OrderDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
)

@Serializable
data class CreateOrderPetPayload(
    @SerialName("pet_id") val petId: Long,
    @SerialName("stay_date") val stayDate: String = "",
)

@Serializable
data class CreateOrderPayload(
    @SerialName("sitter_id") val sitterId: Long,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val pets: List<CreateOrderPetPayload>,
    val remark: String = "",
    @SerialName("user_coupon_id") val userCouponId: Long = 0,
    @SerialName("points_used") val pointsUsed: Int = 0,
)

@Serializable
data class CreateOrderResult(
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("order_no") val orderNo: String = "",
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("total_days") val totalDays: Int = 0,
)

@Serializable
data class PayResult(
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("transaction_id") val transactionId: String = "",
    val status: String = "",
)

@Serializable
data class ReviewPayload(
    @SerialName("overall_score") val overallScore: Int,
    @SerialName("service_score") val serviceScore: Int,
    @SerialName("environment_score") val environmentScore: Int,
    @SerialName("hygiene_score") val hygieneScore: Int,
    @SerialName("owner_comment") val ownerComment: String,
)

@Serializable
data class ReviewResult(val id: Long = 0)

/** 追评：只带一段文字，服务端校验 24 小时窗口 */
@Serializable
data class FollowupPayload(val content: String)

@Serializable
data class UpdateProfilePayload(
    val nickname: String,
    val phone: String,
    val address: String = "",
    @SerialName("contact_name") val contactName: String = "",
    @SerialName("contact_phone") val contactPhone: String = "",
)

@Serializable
data class NotificationDto(
    val id: Long = 0,
    val type: String = "",
    val scope: String = "personal",
    val title: String = "",
    val body: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
)

@Serializable
data class FavoriteDto(
    @SerialName("sitter_id") val sitterId: Long = 0,
    val name: String = "",
    val type: String = "home",
    @SerialName("daily_price") val dailyPrice: Double = 0.0,
    @SerialName("avg_score") val avgScore: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    val address: String = "",
    val status: String = "active",
)

@Serializable
data class FavoriteListDto(val items: List<FavoriteDto> = emptyList())

@Serializable
data class FavoritePayload(@SerialName("sitter_id") val sitterId: Long)

@Serializable
data class FavoriteResult(@SerialName("sitter_id") val sitterId: Long = 0)

@Serializable
data class UpdatedResult(val updated: Boolean = false)

/** 后端用 {"updated": 条数} 回值的接口（如通知全部已读） */
@Serializable
data class CountResult(val updated: Int = 0)

/** 无 data 字段时的响应信封（仅用于读取 code / message）。 */
@Serializable
data class ApiStatus(val code: Int = 0, val message: String = "")

@Serializable
data class DynamicDto(
    val id: Long = 0,
    @SerialName("order_id") val orderId: Long = 0,
    val content: String = "",
    val photos: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("sitter_name") val sitterName: String = "",
    @SerialName("order_no") val orderNo: String = "",
)

@Serializable
data class DynamicPageDto(
    val items: List<DynamicDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
)

/**
 * 动态下的一条留言。
 *
 * 后端只回展示名（users.nickname 缺省回退 username）而不回 user_id：
 * App 侧只展示，不按人做二次查询，够用且少一次关联。
 */
@Serializable
data class DynamicCommentDto(
    val id: Long = 0,
    val user: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/** GET /pet-owner/dynamics/:id/comments —— 顺带把点赞数带回来，省一次单独计数。 */
@Serializable
data class DynamicCommentListDto(
    @SerialName("dynamic_id") val dynamicId: Long = 0,
    val likes: Int = 0,
    val items: List<DynamicCommentDto> = emptyList(),
)

@Serializable
data class CommentPayload(val content: String)

@Serializable
data class UserCouponDto(
    val id: Long = 0,
    @SerialName("coupon_id") val couponId: Long = 0,
    val code: String = "",
    val title: String = "",
    val type: String = "fixed",
    val value: Double = 0.0,
    @SerialName("min_amount") val minAmount: Double = 0.0,
    val status: String = "unused",
    @SerialName("ends_at") val endsAt: String = "",
    /** 机构专属券的发券机构 id；0 = 平台官方券 */
    @SerialName("sitter_id") val sitterId: Long = 0,
    val issuer: String = "",
)

@Serializable
data class ClaimableCouponDto(
    @SerialName("coupon_id") val couponId: Long = 0,
    val code: String = "",
    val title: String = "",
    val type: String = "fixed",
    val value: Double = 0.0,
    @SerialName("min_amount") val minAmount: Double = 0.0,
    @SerialName("ends_at") val endsAt: String = "",
    /** 机构专属券的发券机构 id；0 = 平台官方券 */
    @SerialName("sitter_id") val sitterId: Long = 0,
    val issuer: String = "",
)

@Serializable
data class CouponListDto(
    val items: List<UserCouponDto> = emptyList(),
    val claimable: List<ClaimableCouponDto> = emptyList(),
)

/** 机构端：本机构发布的优惠券（含领取/核销统计）。 */
@Serializable
data class SitterCouponDto(
    val id: Long = 0,
    val code: String = "",
    val title: String = "",
    val type: String = "fixed",
    val value: Double = 0.0,
    @SerialName("min_amount") val minAmount: Double = 0.0,
    @SerialName("total_qty") val totalQty: Int = 0,
    @SerialName("claimed_qty") val claimedQty: Int = 0,
    @SerialName("used_qty") val usedQty: Int = 0,
    @SerialName("per_user") val perUser: Int = 1,
    val enabled: Boolean = true,
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String = "",
)

@Serializable
data class SitterCouponPageDto(
    val items: List<SitterCouponDto> = emptyList(),
)

@Serializable
data class SitterCouponPayload(
    val title: String,
    val type: String,
    val value: Double,
    @SerialName("min_amount") val minAmount: Double,
    @SerialName("total_qty") val totalQty: Int,
    @SerialName("per_user") val perUser: Int,
    val enabled: Boolean = true,
    /** 空字符串 = 立即生效 / 长期有效 */
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String = "",
)
@Serializable
data class ClaimResult(val id: Long = 0)

/** 取消订单：后端只回一句说明 */
@Serializable
data class CancelResult(val message: String = "")

@Serializable
data class RescheduleResult(
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
)

@Serializable
data class RenewResult(
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("end_date") val endDate: String = "",
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("extra_days") val extraDays: Int = 0,
    @SerialName("extra_amount") val extraAmount: Double = 0.0,
    @SerialName("transaction_id") val transactionId: String = "",
)

/** 宠物历史寄养记录（后端把 DATE 都转成了字符串） */
@Serializable
data class PetStayDto(
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("order_no") val orderNo: String = "",
    @SerialName("sitter_id") val sitterId: Long = 0,
    @SerialName("sitter_name") val sitterName: String = "",
    val status: String = "",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("stay_date") val stayDate: String = "",
    @SerialName("total_amount") val totalAmount: Double = 0.0,
)

@Serializable
data class PetStayListDto(
    @SerialName("pet_id") val petId: Long = 0,
    val total: Int = 0,
    val items: List<PetStayDto> = emptyList(),
)

/** 动态点赞结果 */
@Serializable
data class LikeResult(
    @SerialName("dynamic_id") val dynamicId: Long = 0,
    val liked: Boolean = false,
)

@Serializable
data class UpdateShopPayload(
    val name: String,
    val type: String,
    val description: String,
    @SerialName("daily_price") val dailyPrice: Double,
    @SerialName("visit_price") val visitPrice: Double,
    @SerialName("price_mode") val priceMode: String,
    val capacity: Int,
    val address: String,
    @SerialName("cover_photo_url") val coverPhotoUrl: String = "",
    @SerialName("detail_photos") val detailPhotos: List<String> = emptyList(),
    // null 表示本次不改动坐标（后端按指针字段判空更新）
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** 桌面端等没有系统定位的客户端，用"城市 + 地址"向后端换 GCJ-02 坐标。 */
@Serializable
data class GeocodePayload(
    val address: String,
    val city: String = "",
)

/** 坐标 → 地址（弹框里选完点后回填地址文本）。 */
@Serializable
data class ReversePayload(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class GeocodeDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("formatted_address") val formattedAddress: String = "",
    @SerialName("resolved_city") val resolvedCity: String = "",
    val district: String = "",
    val adcode: String = "",
    val level: String = "",
    @SerialName("from_cache") val fromCache: Boolean = false,
)

/** 桌面端地图选点：先建会话拿到一次性链接，再轮询取回用户浏览器里选中的坐标。 */
@Serializable
data class GeoPickPayload(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("place_name") val placeName: String = "",
)

@Serializable
data class GeoPickSessionDto(
    val token: String = "",
    val path: String = "",
    @SerialName("expires_in") val expiresIn: Int = 0,
)

@Serializable
data class GeoPickResultDto(
    val status: String = "pending",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("formatted_address") val formattedAddress: String = "",
)

@Serializable
data class ReceivedOrderDto(
    val id: Long = 0,
    @SerialName("order_no") val orderNo: String = "",
    val status: String = "",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("pet_count") val petCount: Int = 0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ReceivedOrderPageDto(
    val items: List<ReceivedOrderDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class CapacityItemDto(
    val date: String = "",
    val used: Int = 0,
    val capacity: Int = 0,
    val available: Int = 0,
    val full: Boolean = false,
)

@Serializable
data class CapacityDto(
    @SerialName("sitter_id") val sitterId: Long = 0,
    val items: List<CapacityItemDto> = emptyList(),
)

/** 机构调整单日接待容量（写入按日覆盖表，未覆盖日期沿用店铺默认容量）。 */
@Serializable
data class CapacitySetPayload(
    val date: String,
    val capacity: Int,
)
@Serializable
data class EarningsSummaryDto(
    @SerialName("order_count") val orderCount: Int = 0,
    @SerialName("gross_amount") val grossAmount: Double = 0.0,
    @SerialName("commission_rate") val commissionRate: Double = 0.0,
    @SerialName("commission_amount") val commissionAmount: Double = 0.0,
    @SerialName("net_amount") val netAmount: Double = 0.0,
)

@Serializable
data class EarningsItemDto(
    @SerialName("order_no") val orderNo: String = "",
    val date: String = "",
    val amount: Double = 0.0,
    val owner: String = "",
)

@Serializable
data class PeriodDto(val from: String = "", val to: String = "")

@Serializable
data class EarningsDto(
    val period: PeriodDto = PeriodDto(),
    val summary: EarningsSummaryDto = EarningsSummaryDto(),
    val items: List<EarningsItemDto> = emptyList(),
)

@Serializable
data class SitterReviewDto(
    val id: Long = 0,
    @SerialName("overall_score") val overallScore: Int = 0,
    @SerialName("service_score") val serviceScore: Int = 0,
    @SerialName("environment_score") val environmentScore: Int = 0,
    @SerialName("hygiene_score") val hygieneScore: Int = 0,
    @SerialName("owner_comment") val ownerComment: String? = null,
    @SerialName("reply_text") val replyText: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SitterReviewListDto(val items: List<SitterReviewDto> = emptyList())

@Serializable
data class DynamicListDto(val items: List<DynamicDto> = emptyList())

@Serializable
data class CreateDynamicPayload(
    val content: String,
    val photos: List<String> = emptyList(),
)

@Serializable
data class ReplyPayload(@SerialName("reply_text") val replyText: String)

@Serializable
data class StatusResult(val status: String = "")

@Serializable
data class RejectPayload(val reason: String)

@Serializable
data class PublicReviewDto(
    val id: Long = 0,
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("sitter_id") val sitterId: Long = 0,
    @SerialName("overall_score") val overallScore: Int = 0,
    @SerialName("service_score") val serviceScore: Int = 0,
    @SerialName("environment_score") val environmentScore: Int = 0,
    @SerialName("hygiene_score") val hygieneScore: Int = 0,
    @SerialName("owner_comment") val ownerComment: String = "",
    @SerialName("owner_photos") val ownerPhotos: List<String> = emptyList(),
    val ownerName: String = "",
    @SerialName("reply_text") val replyText: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PublicReviewListDto(
    val items: List<PublicReviewDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
)

@Serializable
data class SettlementDto(
    val id: Long = 0,
    @SerialName("period_start") val periodStart: String = "",
    @SerialName("period_end") val periodEnd: String = "",
    @SerialName("order_count") val orderCount: Int = 0,
    @SerialName("gross_amount") val grossAmount: Double = 0.0,
    @SerialName("commission_amount") val commissionAmount: Double = 0.0,
    @SerialName("net_amount") val netAmount: Double = 0.0,
    val status: String = "pending",
    @SerialName("applied_at") val appliedAt: String = "",
    @SerialName("paid_at") val paidAt: String = "",
)

@Serializable
data class SettlementListDto(val items: List<SettlementDto> = emptyList())

@Serializable
data class SettlementApplyPayload(
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
)

@Serializable
data class AddonDto(
    val id: Long = 0,
    val name: String = "",
    val price: Double = 0.0,
    val unit: String = "次",
    val enabled: Boolean = true,
)

/** 新增加购服务：price 后端要求必填且大于 0 */
@Serializable
data class AddonPayload(
    val name: String,
    val price: Double,
    val unit: String = "per_order",
)

/** 改加购服务：null 表示这一项不动 */
@Serializable
data class AddonUpdatePayload(
    val name: String? = null,
    val price: Double? = null,
    val unit: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class AddonListDto(val items: List<AddonDto> = emptyList())

@Serializable
data class CreatePetPayload(
    val name: String,
    val species: String,
    val breed: String = "",
    @SerialName("body_size") val bodySize: String = "",
    @SerialName("age_month") val ageMonth: Int? = null,
    val vaccinated: Boolean = true,
    val sterilized: Boolean = false,
    val notes: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
)

/** 编辑宠物档案：可空字段，后端按指针只更新传了的那几项 */
@Serializable
data class UpdatePetPayload(
    val name: String? = null,
    val species: String? = null,
    val breed: String? = null,
    @SerialName("body_size") val bodySize: String? = null,
    @SerialName("age_month") val ageMonth: Int? = null,
    val vaccinated: Boolean? = null,
    val sterilized: Boolean? = null,
    @SerialName("photo_urls") val photoUrls: List<String>? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val notes: String? = null,
)

/** 只需要一个原因字段的接口（取消订单等） */
@Serializable
data class ReasonPayload(val reason: String = "")

@Serializable
data class ReschedulePayload(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
)

@Serializable
data class RenewPayload(@SerialName("end_date") val endDate: String)

@Serializable
data class RefundPayload(val reason: String, val amount: Double? = null)

@Serializable
data class TicketPayload(
    val category: String = "other",
    val title: String,
    val content: String,
)

@Serializable
data class TicketDto(
    val id: Long = 0,
    @SerialName("ticket_no") val ticketNo: String = "",
    val category: String = "",
    val title: String = "",
    val status: String = "open",
    val priority: String = "normal",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class TicketListDto(val items: List<TicketDto> = emptyList())

/** 工单会话里的一条消息；sender_role 只有 user（本人）与 admin（客服）两种。 */
@Serializable
data class TicketMessageDto(
    @SerialName("sender_role") val senderRole: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/** GET /support/tickets/:id —— 标题、状态与按时间正序的完整对话。 */
@Serializable
data class TicketDetailDto(
    val id: Long = 0,
    @SerialName("ticket_no") val ticketNo: String = "",
    val title: String = "",
    val status: String = "open",
    val messages: List<TicketMessageDto> = emptyList(),
)

@Serializable
data class TicketReplyPayload(val content: String)

@Serializable
data class RefundDto(
    val id: Long = 0,
    @SerialName("order_id") val orderId: Long = 0,
    @SerialName("order_no") val orderNo: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class RefundListDto(val items: List<RefundDto> = emptyList())

@Serializable
data class PointsEntryDto(
    val change: Int = 0,
    val balance: Int = 0,
    val reason: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PointsDto(
    val balance: Int = 0,
    val rate: Int = 0,
    val items: List<PointsEntryDto> = emptyList(),
)

/** 下单前估价的明细行：type 为 base / addon / discount / points */
@Serializable
data class EstimateLineDto(
    val type: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    val price: Double? = null,
    val qty: Int? = null,
    @SerialName("points_used") val pointsUsed: Int? = null,
)

/** 与下单同一套计费口径，页面用它显示"钱是怎么算出来的" */
@Serializable
data class EstimateDto(
    @SerialName("sitter_id") val sitterId: Long = 0,
    @SerialName("sitter_name") val sitterName: String = "",
    @SerialName("total_days") val totalDays: Int = 0,
    @SerialName("pet_count") val petCount: Int = 0,
    @SerialName("estimated_amount") val estimatedAmount: Double = 0.0,
    @SerialName("addon_amount") val addonAmount: Double = 0.0,
    val subtotal: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("points_used") val pointsUsed: Int = 0,
    @SerialName("points_discount") val pointsDiscount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    val breakdown: List<EstimateLineDto> = emptyList(),
)

@Serializable
data class UploadResult(
    val url: String = "",
    val key: String = "",
    val size: Long = 0,
    val name: String = "",
)

@Serializable
data class VaccinePayload(@SerialName("vaccine_urls") val vaccineUrls: List<String>)

@Serializable
data class DeleteResult(val deleted: Boolean = false)

@Serializable
data class ContactPayload(val message: String)

@Serializable
data class ContactResult(
    @SerialName("sitter_id") val sitterId: Long = 0,
    val sent: Boolean = false,
)

@Serializable
data class SitterStatsPointDto(
    val date: String = "",
    val orders: Int = 0,
    val revenue: Double = 0.0,
)

@Serializable
data class SitterStatsDto(
    @SerialName("sitter_id") val sitterId: Long = 0,
    val trend: List<SitterStatsPointDto> = emptyList(),
    val pending: Int = 0,
    val active: Int = 0,
    @SerialName("today_revenue") val todayRevenue: Double = 0.0,
)

@Serializable
data class ChangePasswordPayload(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class PriceRuleDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    val multiplier: Double = 1.0,
)

@Serializable
data class PriceRuleListDto(val items: List<PriceRuleDto> = emptyList())

@Serializable
data class PayOrderPayload(val method: String)

/* ---------- 钱包（真实货币） ---------- */

@Serializable
data class WalletDto(
    @SerialName("user_id") val userId: Long = 0,
    val balance: Double = 0.0,
    @SerialName("frozen_balance") val frozenBalance: Double = 0.0,
    @SerialName("total_recharge") val totalRecharge: Double = 0.0,
    @SerialName("total_consume") val totalConsume: Double = 0.0,
)

@Serializable
data class WalletTransactionDto(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    val type: String = "",
    val amount: Double = 0.0,
    @SerialName("balance_after") val balanceAfter: Double = 0.0,
    val status: String = "",
    val channel: String = "",
    @SerialName("ref_type") val refType: String = "",
    @SerialName("ref_id") val refId: Long = 0,
    @SerialName("transaction_id") val transactionId: String = "",
    val remark: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class WalletTransactionListDto(val items: List<WalletTransactionDto> = emptyList())

@Serializable
data class RechargeRequestDto(val amount: Double, val channel: String = "mock")

@Serializable
data class RechargeResultDto(
    @SerialName("recharge_id") val rechargeId: Long = 0,
    val amount: Double = 0.0,
    val channel: String = "",
    val status: String = "",
    @SerialName("pay_params") val payParams: Map<String, String> = emptyMap(),
)

@Serializable
data class RechargeConfirmDto(val status: String = "", val balance: Double = 0.0)

@Serializable
data class PriceRulePayload(
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val multiplier: Double,
)

/* ------------------------------------------------------------------ *
 * 商家直播（后端 /live 系列，见 docs/18-商家直播功能.md）
 * ------------------------------------------------------------------ */

@Serializable
data class LiveRoomDto(
    val id: Long = 0,
    @SerialName("sitter_id") val sitterId: Long = 0,
    @SerialName("sitter_name") val sitterName: String = "",
    @SerialName("sitter_cover") val sitterCover: String? = null,
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String = "",
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("peak_viewers") val peakViewers: Int = 0,
    @SerialName("total_views") val totalViews: Long = 0,
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("ban_reason") val banReason: String? = null,
    // 推流钥匙只在「开播」响应里出现；观看端拿到的恒为 null
    @SerialName("stream_key") val streamKey: String? = null,
    @SerialName("push_url") val pushUrl: String? = null,
    @SerialName("play_hls") val playHls: String? = null,
    @SerialName("play_flv") val playFlv: String? = null,
) {
    val isLiving: Boolean get() = status == "living"

    /** 播放地址优先 HLS（兼容性最好），没有就用 HTTP-FLV */
    val playUrl: String?
        get() = playHls?.takeIf { it.isNotBlank() } ?: playFlv?.takeIf { it.isNotBlank() }
}

@Serializable
data class LiveRoomListDto(
    val items: List<LiveRoomDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class LiveRoomEnvelope(val room: LiveRoomDto = LiveRoomDto())

@Serializable
data class LiveJoinDto(
    @SerialName("viewer_count") val viewerCount: Int = 0,
    val room: LiveRoomDto = LiveRoomDto(),
)

@Serializable
data class LiveMessageDto(
    val id: Long = 0,
    @SerialName("room_id") val roomId: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    val nickname: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val content: String = "",
    val status: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class LiveMessageListDto(val items: List<LiveMessageDto> = emptyList())

/** 机构端的「我的直播」：当前场 + 最近历史 + 封禁状态 */
@Serializable
data class MyLiveDto(
    val items: List<LiveRoomDto> = emptyList(),
    val current: LiveRoomDto? = null,
    @SerialName("live_banned") val liveBanned: Boolean = false,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("banned_at") val bannedAt: String? = null,
)

@Serializable
data class StartLivePayload(
    val title: String,
    @SerialName("cover_url") val coverUrl: String = "",
)

@Serializable
data class LiveMessagePayload(val content: String)

@Serializable
data class LiveReactionDto(@SerialName("viewer_count") val viewerCount: Int = 0)

