package team.wd.atnestapp.utils

private val PHONE_REGEX = Regex("^1[3-9]\\d{9}$")

/** 中国大陆手机号校验，桌面端 / 手机端共用。 */
object PhoneValidator {
    const val MESSAGE = "请输入正确的 11 位手机号"

    fun isValid(value: String): Boolean = PHONE_REGEX.matches(value.trim())

    /** 输入框里看起来是在填手机号（纯数字）时才强制按手机号规则校验。 */
    fun looksLikePhone(value: String): Boolean {
        val text = value.trim()
        return text.length >= 7 && text.all { it.isDigit() }
    }
}
