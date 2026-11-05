package team.wd.atnestapp.data.session

import android.content.Context
import java.io.File

private val lock = Any()

@Volatile
private var instance: SessionStore? = null

/**
 * 手机端的会话存储单例。
 *
 * 同一个文件只能有一个 DataStore 实例，否则库会直接抛
 * "There are multiple DataStores active for the same file"。
 * Activity 重建（旋转、切深色、进程内重进）都会再走一遍 Composition，
 * 因此这里必须返回同一个对象，不能每次 new。
 */
fun androidSessionStore(context: Context): SessionStore {
    instance?.let { return it }
    return synchronized(lock) {
        instance ?: build(context.applicationContext).also { instance = it }
    }
}

private fun build(context: Context): SessionStore {
    // filesDir 下的子目录不会被系统清理，也不会被用户当缓存清掉
    val dir = File(context.filesDir, "datastore").apply { mkdirs() }
    return SessionStore(
        createSessionDataStore { File(dir, SESSION_FILE_NAME).absolutePath },
    )
}
