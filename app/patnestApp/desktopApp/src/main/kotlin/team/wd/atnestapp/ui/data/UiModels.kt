package team.wd.atnestapp.ui.data

/** 机构端订单视图模型（来自后端 ReceivedOrderDto）。 */
data class SbOrder(
    val no: String,
    val owner: String,
    val pet: String,
    val range: String,
    val days: String,
    /** 实付金额（元，后端 float64） */
    val amount: Double,
    val status: String,
    val made: String,
    val id: Long = 0,
    val createdAt: String = "",
    /** 后端原始状态：按钮显隐与筛选都看它，中文 status 只用于展示 */
    val rawStatus: String = "",
)

data class Coupon(
    val title: String,
    val value: String,
    val cond: String,
    val from: String,
    val to: String,
    val state: String,
    /** 机构专属券的发券机构名；平台券为空 */
    val issuer: String = "",
)

data class ReviewItemData(
    val user: String,
    val score: Int,
    val time: String,
    val txt: String,
)

data class CalDay(
    val date: String,
    val week: String,
    val slots: List<Pair<String, String>>,
    val holiday: Boolean = false,
    /** 原始 YYYY-MM-DD 日期，用于容量调整接口 */
    val rawDate: String = "",
)
