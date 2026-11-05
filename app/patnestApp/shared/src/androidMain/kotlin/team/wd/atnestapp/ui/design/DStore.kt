package team.wd.atnestapp.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.ui.app.MobileState

/**
 * 设计层的数据入口。
 *
 * 61 屏原本全部是死数据，现在统一从这里拿 [MobileState]：
 * - 数据层完全复用 ui/mobile 的 MobileState（它本身就只是状态中枢，不依赖旧 UI）；
 * - 用 CompositionLocal 下发，而不是把 store 一路当参数传 61 次，屏函数签名保持不变；
 * - [DesignApp] 负责创建、恢复会话、拉基础数据。
 */
val DLocalStore = staticCompositionLocalOf<MobileState> {
    error("DLocalStore 尚未注入：DesignApp 里必须用 CompositionLocalProvider 包裹页面")
}

/* ------------------------------------------------------------------ *
 * 领域模型 → 设计稿卡片数据的映射与展示口径
 * ------------------------------------------------------------------ */

/** 机构卡片副标题：类型 · 区域 · 价格。 */
internal fun Sitter.dSubtitle(): String {
    val area = addr.split("区").firstOrNull()?.takeIf { it.isNotBlank() }?.plus("区") ?: addr
    return "$type · $area · ¥$price/${unit}"
}

/** 评分文案：没有评价时不要伪装成 5.0。 */
internal fun Sitter.dRatingText(): String =
    if (tags.contains("条评价")) tags else "暂无评价"

/** 订单状态 → 标签配色。 */
internal fun dOrderTone(raw: String): DTagTone = when (raw) {
    "pending_pay" -> DTagTone.Warn
    "confirmed" -> DTagTone.Info
    "accepted", "staying" -> DTagTone.Ok
    "completed", "evaluated" -> DTagTone.Line
    "cancelled", "rejected" -> DTagTone.Danger
    else -> DTagTone.Line
}

/** 订单卡片右侧动作：按原始状态决定按钮文案与目标页。 */
internal fun dOrderAction(order: Order): Pair<String, DScreen> = when (order.rawStatus) {
    "pending_pay" -> "去支付" to DScreen.Checkout
    "staying" -> "查看动态" to DScreen.Dynamics
    "completed" -> "去评价" to DScreen.ReviewWrite
    "evaluated" -> "查看评价" to DScreen.ReviewDetail
    "cancelled", "rejected" -> "再次预约" to DScreen.FindSitters
    else -> "查看详情" to DScreen.OrderDetail
}

/** 宠物卡片副标题第一行。 */
internal fun Pet.dPetInfo(): String = listOf(type, breed, age, size)
    .filter { it.isNotBlank() && it != "—" }
    .joinToString(" · ")

/** 宠物卡片副标题第二行。 */
internal fun Pet.dPetNote(): String = listOf(vaccine, cert, note)
    .filter { it.isNotBlank() && it != "—" }
    .joinToString(" · ")

/** 手机号脱敏：138****2201。 */
internal fun dMaskPhone(raw: String?): String {
    val phone = raw.orEmpty().filter { it.isDigit() }
    if (phone.length < 7) return raw.orEmpty().ifBlank { "未绑定手机号" }
    return phone.take(3) + "****" + phone.takeLast(4)
}

/** 取首字做头像占位，中文取第一个字，英文取首字母。 */
internal fun dInitial(text: String?): String =
    text.orEmpty().trim().take(1).ifBlank { "宠" }
