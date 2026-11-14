package team.wd.atnestapp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import team.wd.atnestapp.data.api.ApiClient
import team.wd.atnestapp.data.api.NotificationDto

/**
 * 通知状态中心（两端公共逻辑）。
 *
 * 原本 Desktop 的 [team.wd.atnestapp.ui.AppState] 与 Android 的 MobileState 各自实现了一遍
 * 几乎相同的通知状态：旧统一列表 `notifications`（仅用于未读角标）、以及按归属视角拆分的
 * `personalNotifications` / `businessNotifications`。本类把这部分逻辑收敛到 shared，
 * 两个平台的状态类各持一个实例并委托，避免重复实现与日后两端行为分叉。
 *
 * `toast` 由各平台以回调注入（桌面与安卓的 toast 机制不同）。
 */
class NotificationCenter(
    private val api: ApiClient,
    private val toast: (String) -> Unit = {},
) {
    /* ---- 旧统一列表：仅用于未读角标与新消息提示，不做视角过滤 ---- */
    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var notificationsLoading by mutableStateOf(false)
    var notificationsError by mutableStateOf<String?>(null)

    /* ---- 消息页按归属视角分开拉取 ---- */
    var notificationScope by mutableStateOf("personal")
    var personalNotifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var businessNotifications by mutableStateOf<List<NotificationDto>>(emptyList())
    var scopedNotificationsLoading by mutableStateOf(false)
    var scopedNotificationsError by mutableStateOf<String?>(null)

    /** 旧兼容接口：拉取当前用户全部通知（用于角标）。 */
    suspend fun loadNotifications(force: Boolean = false) {
        if (notificationsLoading) return
        if (!force && notifications.isNotEmpty()) return
        notificationsLoading = true
        notificationsError = null
        try {
            notifications = api.listNotifications()
        } catch (e: Exception) {
            notificationsError = e.message ?: "加载消息失败"
        } finally {
            notificationsLoading = false
        }
    }

    suspend fun markNotificationRead(id: Long) {
        runCatching { api.markNotificationRead(id) }
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    suspend fun markAllNotificationsRead() {
        runCatching { api.markAllNotificationsRead() }
        if (notifications.any { !it.isRead }) {
            notifications = notifications.map { it.copy(isRead = true) }
            toast("已全部标为已读")
        } else {
            toast("没有未读消息")
        }
    }

    /**
     * 消息页：一次拉取两个视角。商家消息只有机构账号才有，由 [isSitter] 决定要不要拉取。
     * 不写 `notifications`（角标列表）以免改变旧行为，角标仍由 [loadNotifications] / 标记方法维护。
     */
    suspend fun loadScopedNotifications(force: Boolean = false, isSitter: Boolean = false) {
        if (scopedNotificationsLoading) return
        // 只看「当前视角」是否已有数据：原来用「任一视角非空」短路，
        // 个人消息一有数据，切换身份后就再也不刷新商家消息了。
        val current = if (notificationScope == "business") businessNotifications else personalNotifications
        if (!force && current.isNotEmpty()) return
        scopedNotificationsLoading = true
        scopedNotificationsError = null
        try {
            personalNotifications = api.listPersonalNotifications()
            businessNotifications = if (isSitter) api.listBusinessNotifications() else emptyList()
        } catch (e: Exception) {
            scopedNotificationsError = e.message ?: "加载消息失败"
        } finally {
            scopedNotificationsLoading = false
        }
    }

    /** 当前展示视角下的列表（消息页按个人/商家切换）。 */
    fun visibleNotifications(): List<NotificationDto> =
        if (notificationScope == "business") businessNotifications else personalNotifications

    suspend fun markScopedNotificationRead(id: Long) {
        val isBusiness = notificationScope == "business"
        runCatching {
            if (isBusiness) api.markBusinessNotificationRead(id)
            else api.markPersonalNotificationRead(id)
        }
        if (isBusiness) {
            businessNotifications = businessNotifications.map { if (it.id == id) it.copy(isRead = true) else it }
        } else {
            personalNotifications = personalNotifications.map { if (it.id == id) it.copy(isRead = true) else it }
        }
        // 同步角标列表
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    /** 一键已读只清当前视角，另一类消息的未读数不受影响。 */
    suspend fun markAllScopedNotificationsRead() {
        val isBusiness = notificationScope == "business"
        val current = visibleNotifications()
        if (current.none { !it.isRead }) {
            toast("没有未读消息")
            return
        }
        runCatching {
            if (isBusiness) api.markAllBusinessNotificationsRead()
            else api.markAllPersonalNotificationsRead()
        }
        val ids = current.map { it.id }.toSet()
        if (isBusiness) {
            businessNotifications = businessNotifications.map { it.copy(isRead = true) }
        } else {
            personalNotifications = personalNotifications.map { it.copy(isRead = true) }
        }
        notifications = notifications.map { if (it.id in ids) it.copy(isRead = true) else it }
    }

    /** 退出登录时清空全部通知状态。 */
    fun reset() {
        notifications = emptyList()
        notificationsLoading = false
        notificationsError = null
        notificationScope = "personal"
        personalNotifications = emptyList()
        businessNotifications = emptyList()
        scopedNotificationsLoading = false
        scopedNotificationsError = null
    }
}
