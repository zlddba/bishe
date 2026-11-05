package team.wd.atnestapp.ui.mobile

import android.content.Context
import android.content.SharedPreferences

/**
 * 手机端会话持久化：桌面端每次启动读 .env，手机没有这个条件，
 * 所以接口地址、访问令牌、刷新令牌、账号信息统一放 SharedPreferences，
 * 应用被系统杀掉后重新打开仍是登录态。
 */
class MobileSession(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("patnest_mobile", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        set(value) { prefs.edit().putString(KEY_BASE_URL, value.trim()).apply() }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_TOKEN, value).apply() }

    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_REFRESH, value).apply() }

    var account: String
        get() = prefs.getString(KEY_ACCOUNT, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_ACCOUNT, value).apply() }

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_NICKNAME, value).apply() }

    /** owner / sitter，决定进入主界面后是哪一套底部导航 */
    var role: String
        get() = prefs.getString(KEY_ROLE, "owner").orEmpty()
        set(value) { prefs.edit().putString(KEY_ROLE, value).apply() }

    val hasSession: Boolean get() = token.isNotBlank()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH)
            .remove(KEY_ACCOUNT)
            .remove(KEY_NICKNAME)
            .remove(KEY_ROLE)
            .apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.3.100:9200"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_ROLE = "role"
    }
}

