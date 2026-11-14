package team.wd.atnestapp.data.api

import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.utils.formatMoney

fun SitterDto.toUi(): Sitter {
    val perVisit = priceMode == "per_visit"
    return Sitter(
        name = name.ifBlank { "未命名机构" },
        addr = address?.takeIf { it.isNotBlank() } ?: "地址待完善",
        type = when (type) {
            "home" -> "家庭寄养"
            "shop" -> "门店寄养"
            "visit" -> "上门喂养"
            else -> "其他"
        },
        price = if (perVisit) visitPrice else dailyPrice,
        unit = if (perVisit) "次" else "天",
        score = avgScore.coerceIn(0.0, 5.0),
        cap = if (capacity > 0) "$capacity 只" else "—",
        owner = "—",
        tel = "—",
        tags = if (reviewCount > 0) "$reviewCount 条评价" else "新入驻机构",
        desc = description?.takeIf { it.isNotBlank() } ?: "机构暂未填写简介。",
        id = id,
        status = status,
        coverPhoto = coverPhotoUrl.orEmpty(),
        photos = detailPhotos,
        latitude = latitude,
        longitude = longitude,
    )
}

fun PetDto.toUi(): Pet = Pet(
    name = name.ifBlank { "未命名宠物" },
    breed = breed?.takeIf { it.isNotBlank() } ?: "品种待完善",
    type = when (species) {
        "cat" -> "猫"
        "dog" -> "狗"
        else -> "其他"
    },
    size = when (bodySize) {
        "tiny" -> "迷你"
        "small" -> "小型"
        "medium" -> "中型"
        "large" -> "大型"
        "xlarge" -> "超大型"
        else -> "—"
    },
    age = ageMonth?.let { months ->
        if (months >= 12) "${months / 12} 岁" else "${months} 个月"
    } ?: "—",
    vaccine = if (vaccinated) "已接种" else "待接种",
    // 免疫核验状态来自后端 vaccine_status，之前这里写死 "—"，列表永远显示未核验
    cert = when (vaccineStatus) {
        "verified" -> "已核验"
        "pending" -> "待核验"
        "rejected" -> "未通过"
        else -> "缺证明"
    },
    note = notes?.takeIf { it.isNotBlank() } ?: "暂无健康备注",
    id = id,
    photos = (listOfNotNull(avatarUrl) + photoUrls).filter { it.isNotBlank() }.distinct(),
    vaccinePhotos = vaccineUrls.filter { it.isNotBlank() },
    vaccineNote = vaccineNote.orEmpty(),
)

fun OrderDto.toUi(): Order = Order(
    no = orderNo,
    org = sitterName.ifBlank { "—" },
    pet = if (petCount > 0) "$petCount 只" else "—",
    range = "${shortDate(startDate)} - ${shortDate(endDate)}",
    days = "$totalDays 天",
    amount = totalAmount,
    fee = "¥" + formatMoney(totalAmount),
    status = orderStatusLabel(status),
    made = formatDateTime(createdAt),
    id = id,
    rawStatus = status,
    startDate = startDate,
    endDate = endDate,
    paid = paymentStatus == "paid",
)

fun FavoriteDto.toUi(): Sitter = Sitter(
    name = name.ifBlank { "未命名机构" },
    addr = address.ifBlank { "地址待完善" },
    type = when (type) {
        "home" -> "家庭寄养"
        "shop" -> "门店寄养"
        "visit" -> "上门喂养"
        else -> "其他"
    },
    price = dailyPrice,
    unit = "天",
    score = avgScore.coerceIn(0.0, 5.0),
    cap = "—",
    owner = "—",
    tel = "—",
    tags = if (reviewCount > 0) "$reviewCount 条评价" else "已收藏",
    desc = "已收藏的机构",
    id = sitterId,
    status = status,
)

private fun orderStatusLabel(status: String): String = when (status) {
    "pending_pay" -> "待支付"
    "confirmed" -> "待确认"
    "accepted" -> "已接单"
    "staying" -> "寄养中"
    "completed" -> "已结束"
    "evaluated" -> "已评价"
    "cancelled" -> "已取消"
    "rejected" -> "已拒绝"
    else -> status
}

private fun shortDate(raw: String): String =
    if (raw.length >= 10) raw.substring(5, 10).replace('-', '/') else raw

private fun formatDateTime(raw: String): String {
    if (raw.length < 16) return raw
    return "${shortDate(raw)} ${raw.substring(11, 16)}"
}
