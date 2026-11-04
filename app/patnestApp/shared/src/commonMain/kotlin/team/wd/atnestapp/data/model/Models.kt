package team.wd.atnestapp.data.model

/**
 * 跨平台领域模型：接口层映射后的统一数据结构，桌面端和手机端共用。
 */
data class Sitter(
    val name: String,
    val addr: String,
    val type: String,
    val price: Int,
    val unit: String,
    val score: Int,
    val cap: String,
    val owner: String,
    val tel: String,
    val tags: String,
    val desc: String,
    val id: Long = 0,
    val status: String = "active",
    val coverPhoto: String = "",
    val photos: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class Pet(
    val name: String,
    val breed: String,
    val type: String,
    val size: String,
    val age: String,
    val vaccine: String,
    val cert: String,
    val note: String,
    val id: Long = 0,
    val photos: List<String> = emptyList(),
    val vaccinePhotos: List<String> = emptyList(),
    val vaccineNote: String = "",
)

data class Order(
    val no: String,
    val org: String,
    val pet: String,
    val range: String,
    val days: String,
    val amount: Int,
    val fee: String,
    val status: String,
    val made: String,
    val id: Long = 0,
    // 后端原始状态与完整日期：中文标签给人看，按钮该不该出现要看原始值
    val rawStatus: String = "",
    val startDate: String = "",
    val endDate: String = "",
    // 续住和退款都要求已支付，按钮显隐要用它判断
    val paid: Boolean = false,
)
