package team.wd.atnestapp.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

/** 会话文件名。两端（手机 / 桌面）用同一个名字，只是所在目录不同。 */
const val SESSION_FILE_NAME = "patnest_session.preferences_pb"

/**
 * 各平台自己给出文件路径：Android 是 `filesDir/datastore`，桌面是 `~/.patnest`。
 * 路径由调用方算好传进来，common 层不碰任何平台文件 API。
 */
fun createSessionDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() },
    )

/**
 * 一份完整的登录态快照。
 *
 * 令牌之外还带上账号 / 昵称 / 角色：冷启动时先按这份快照把界面点亮，
 * 再在后台用 `/auth/me` 校正，避免每次启动都闪一下登录页。
 */
data class SessionData(
    val token: String = "",
    val refreshToken: String = "",
    val account: String = "",
    val nickname: String = "",
    /** 账号本身注册的角色（服务端给的事实）：owner / sitter */
    val role: String = "owner",
    /**
     * 用户上一次选的身份（owner / sitter），空表示还没选过、按 [role] 走。
     *
     * 和 [role] 分开存：机构账号也能切回主人端下单，下次冷启动要回到他选的那个身份，
     * 而不是每次都被服务端的 role 拽回机构端。
     */
    val activeRole: String = "",
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
}

/**
 * 会话持久化（DataStore Preferences）。
 *
 * 设计要点：
 * - **读**：构造时同步读一次做内存快照。首帧就要知道「登没登录」，不能等 Flow；
 *   文件只有几百字节，这一下阻塞在启动期可忽略（SharedPreferences 首次加载同样是同步 IO）。
 * - **写**：不阻塞调用方，落到 IO 线程串行执行，用 [Mutex] 保证两次写入不会交错。
 * - **单实例**：同一个文件只能有一个 DataStore 实例，由各平台的工厂函数保证（见
 *   `androidSessionStore` / `desktopSessionStore`），不要直接 new 这个类。
 */
class SessionStore(private val dataStore: DataStore<Preferences>) {

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(readNow())
    val state: StateFlow<SessionData> = _state.asStateFlow()

    /** 已经落盘的那一份；内存快照与它一致时就不必再写。只在 [writeLock] 内读写。 */
    private var persisted: SessionData = _state.value

    /** 同步快照，界面里直接读它。 */
    val current: SessionData get() = _state.value

    private fun readNow(): SessionData = runCatching {
        runBlocking {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .first()
                .toSession()
        }
    }.getOrDefault(SessionData())

    /**
     * 改一份会话并落盘。值没变化时直接返回，避免重复写盘。
     *
     * 写盘永远取「当前最新快照」而不是这一次的 next：连着改 3 个字段时会有 3 个协程排队，
     * 若各自把自己那份写下去，IO 线程的调度顺序不保证，最后落盘的可能是中间态。
     */
    fun update(transform: (SessionData) -> SessionData) {
        val before = _state.value
        val next = transform(before)
        if (next == before) return
        _state.value = next
        writeScope.launch {
            writeLock.withLock {
                val snapshot = _state.value
                if (snapshot == persisted) return@withLock
                if (persist(snapshot)) persisted = snapshot
            }
        }
    }

    fun setToken(value: String) = update { it.copy(token = value) }

    fun setRefreshToken(value: String) = update { it.copy(refreshToken = value) }

    fun setAccount(value: String) = update { it.copy(account = value) }

    fun setNickname(value: String) = update { it.copy(nickname = value) }

    fun setRole(value: String) = update { it.copy(role = value) }

    /** 切换身份：记住当前用的是哪一套工作台，冷启动直接回到这里。 */
    fun setActiveRole(value: String) = update { it.copy(activeRole = value) }

    /** 静默续期成功后回写：访问令牌与轮换后的刷新令牌必须一起更新，否则下次启动刷不动。 */
    fun setTokens(access: String, refresh: String?) =
        update { it.copy(token = access, refreshToken = refresh.orEmpty()) }

    /** 登录 / 注册成功后一次性写入。 */
    fun saveAuth(
        token: String,
        refreshToken: String?,
        account: String,
        nickname: String,
        role: String,
    ) = update {
        SessionData(
            token = token,
            refreshToken = refreshToken.orEmpty(),
            account = account,
            nickname = nickname,
            role = role.ifBlank { "owner" },
        )
    }

    /** 登出 / 被踢下线：整份清掉（角色回到 owner）。 */
    fun clear() = update { SessionData() }

    private suspend fun persist(data: SessionData): Boolean = runCatching {
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = data.token
            prefs[KEY_REFRESH] = data.refreshToken
            prefs[KEY_ACCOUNT] = data.account
            prefs[KEY_NICKNAME] = data.nickname
            prefs[KEY_ROLE] = data.role
            prefs[KEY_ACTIVE_ROLE] = data.activeRole
        }
    }.isSuccess
}

private val KEY_TOKEN = stringPreferencesKey("access_token")
private val KEY_REFRESH = stringPreferencesKey("refresh_token")
private val KEY_ACCOUNT = stringPreferencesKey("account")
private val KEY_NICKNAME = stringPreferencesKey("nickname")
private val KEY_ROLE = stringPreferencesKey("role")
private val KEY_ACTIVE_ROLE = stringPreferencesKey("active_role")

private fun Preferences.toSession() = SessionData(
    token = this[KEY_TOKEN].orEmpty(),
    refreshToken = this[KEY_REFRESH].orEmpty(),
    account = this[KEY_ACCOUNT].orEmpty(),
    nickname = this[KEY_NICKNAME].orEmpty(),
    role = this[KEY_ROLE]?.takeIf { it.isNotBlank() } ?: "owner",
    activeRole = this[KEY_ACTIVE_ROLE]?.takeIf { it.isNotBlank() } ?: "",
)
