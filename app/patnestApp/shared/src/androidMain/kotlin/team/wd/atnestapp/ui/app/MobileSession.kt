package team.wd.atnestapp.ui.app

import android.content.Context
import team.wd.atnestapp.data.session.androidSessionStore

/**
 * 手机端会话的读写门面。
 *
 * 真正的落盘在 `data/session/SessionStore`（commonMain，DataStore Preferences），
 * 这里只是把「同步读、异步写」包成属性，让 `MobileState` 与 61 屏里的读取保持原样。
 *
 * 接口地址不在这里存：它由构建期从项目根的 .env（PATNEST_API_BASE）注入，
 * 通过构造参数传进来，App 内不提供修改入口，避免旧值把配置盖掉。
 */
class MobileSession(context: Context, apiBaseUrl: String) {
    private val store = androidSessionStore(context)

    /** 接口根地址，来自 BuildConfig.API_BASE（即 .env 的 PATNEST_API_BASE）。 */
    val baseUrl: String = apiBaseUrl.trim()

    var token: String
        get() = store.current.token
        set(value) = store.setToken(value)

    var refreshToken: String
        get() = store.current.refreshToken
        set(value) = store.setRefreshToken(value)

    var account: String
        get() = store.current.account
        set(value) = store.setAccount(value)

    var nickname: String
        get() = store.current.nickname
        set(value) = store.setNickname(value)

    /** owner / sitter，账号本身注册的角色（服务端事实，不随切换身份改变） */
    var role: String
        get() = store.current.role
        set(value) = store.setRole(value)

    /** 当前使用的身份，切换后落盘，冷启动直接回到这套工作台 */
    var activeRole: String
        get() = store.current.activeRole
        set(value) = store.setActiveRole(value)

    val hasSession: Boolean get() = store.current.isLoggedIn

    /**
     * 静默续期成功后回写。
     * 服务端每次刷新都会轮换刷新令牌，本地不跟着更新的话，下次冷启动就刷不动了。
     */
    fun updateTokens(access: String, refresh: String?) = store.setTokens(access, refresh)

    fun clearAuth() = store.clear()
}
