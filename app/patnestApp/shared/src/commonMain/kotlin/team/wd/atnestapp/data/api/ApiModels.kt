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

/** 申请注销：mode=account 整个账户 / sitter 仅注销机构。 */
@Serializable
data class CancelRequestPayload(val mode: String)

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
)

@Serializable
data class CouponListDto(
    val items: List<UserCouponDto> = emptyList(),
    val claimable: List<ClaimableCouponDto> = emptyList(),
)

@Serializable
data class ClaimResult(val id: Long = 0)

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
    @SerialName("overall_score") val overallScore: Int = 0,
    @SerialName("service_score") val serviceScore: Int = 0,
    @SerialName("environment_score") val environmentScore: Int = 0,
    @SerialName("hygiene_score") val hygieneScore: Int = 0,
    @SerialName("owner_comment") val ownerComment: String = "",
    @SerialName("reply_text") val replyText: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class PublicReviewListDto(val items: List<PublicReviewDto> = emptyList())

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
data class PriceRulePayload(
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val multiplier: Double,
)
