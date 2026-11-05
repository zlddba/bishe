package team.wd.atnestapp.data.session

import java.io.File

private val lock = Any()

@Volatile
private var instance: SessionStore? = null

/**
 * 桌面端的会话存储单例，文件落在 `~/.patnest/patnest_session.preferences_pb`。
 *
 * 为什么不放程序目录：安装到 `Program Files` 后普通用户没有写权限，
 * 打包装成 msi/dmg 后往那里写会静默失败。用户目录三端都可写。
 */
fun desktopSessionStore(): SessionStore {
    instance?.let { return it }
    return synchronized(lock) {
        instance ?: build().also { instance = it }
    }
}

private fun build(): SessionStore {
    // user.home 理论上总有值，但 jpackage 的沙箱里偶发为空，这里给个兜底避免启动就崩
    val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: "."
    val dir = File(home, ".patnest").apply { mkdirs() }
    return SessionStore(
        createSessionDataStore { File(dir, SESSION_FILE_NAME).absolutePath },
    )
}
