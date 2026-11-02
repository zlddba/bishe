package team.wd.atnestapp.ui.data

/** 机构端订单视图模型（来自后端 ReceivedOrderDto）。 */
data class SbOrder(
    val no: String,
    val owner: String,
    val pet: String,
    val range: String,
    val days: String,
    val amount: Int,
    val status: String,
    val made: String,
    val id: Long = 0,
    val createdAt: String = "",
)

data class Coupon(
    val title: String,
    val value: String,
    val cond: String,
    val from: String,
    val to: String,
    val state: String,
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
)
