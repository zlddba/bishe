package team.wd.atnestapp.utils

import kotlin.math.roundToLong

/**
 * 金额展示：最多两位小数，整数不带 `.0`，不带货币符号。
 *
 * 后端所有金额都是 `float64`（元），客户端模型以前用 `Int` 取整，
 * 88.5 会显示成 89、4.5 分显示成 5.0。这里统一按「分」四舍五入到两位，
 * 避免 `Double.toString()` 出现 `20.0`、`265.49999999` 这类文本。
 */
fun formatMoney(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val cents = (value * 100).roundToLong()
    val negative = cents < 0
    val abs = if (negative) -cents else cents
    val whole = abs / 100
    val frac = (abs % 100).toInt()
    val sign = if (negative) "-" else ""
    return when {
        frac == 0 -> "$sign$whole"
        frac % 10 == 0 -> "$sign$whole.${frac / 10}"
        frac < 10 -> "$sign$whole.0$frac"
        else -> "$sign$whole.$frac"
    }
}

/** 评分展示：保留一位小数，整数不带 `.0`（4.5 → "4.5"，5.0 → "5"）。 */
fun formatScore(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val tenths = (value * 10).roundToLong()
    val whole = tenths / 10
    val frac = (tenths % 10).toInt()
    return if (frac == 0) whole.toString() else "$whole.$frac"
}
