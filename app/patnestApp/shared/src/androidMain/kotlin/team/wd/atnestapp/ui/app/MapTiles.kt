package team.wd.atnestapp.ui.app

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import team.wd.atnestapp.data.api.ApiClient
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.time.Duration.Companion.milliseconds

/** 一张瓦片的唯一标识，与后端 `/geo/tile?z=&x=&y=` 的三个参数一一对应。 */
data class MapTileKey(val zoom: Int, val x: Int, val y: Int)

/**
 * 地图瓦片的内存缓存。
 *
 * 瓦片由后端代理高德出图（Key 只留在服务端），客户端按需取格子。
 * 一屏在 z15 大约是 3×5 张，拖动一次只多出边缘一列/一行，所以命中率很高。
 *
 * 三处约束：
 * 1. 缓存有界——瓦片会随拖动越攒越多，不设上限就是确定的内存泄漏；
 * 2. 并发有度——一次请求 12 张同时打出去，弱网下反而全都慢；
 * 3. 失败冷却——后端没配 AMAP_KEY 时每张都会失败，不冷却就会一直重试刷屏。
 */
object MapTiles {
    /** 最多留 96 张（约 12MB，RGB_565 每张 128KB），够覆盖两次缩放前后的整屏。 */
    private const val MAX_TILES = 96

    /** 失败后 60 秒内不再重试同一个格子，避免网络抖动时反复打接口。 */
    private const val RETRY_AFTER_MS = 60_000L

    private val cache: MutableMap<MapTileKey, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<MapTileKey, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MapTileKey, ImageBitmap>?): Boolean =
                size > MAX_TILES
        },
    )

    private val failedAt = Collections.synchronizedMap(HashMap<MapTileKey, Long>())

    /** 同一个格子被两个 Composable 同时请求时只发一次网络。 */
    private val inFlight = Collections.synchronizedSet(HashSet<MapTileKey>())

    private val slots = Semaphore(6)

    fun cached(key: MapTileKey): ImageBitmap? = cache[key]

    /**
     * 取一张瓦片。命中缓存直接返回；已在别处请求则等它落缓存；
     * 失败返回 null，调用方显示底色即可，不要把整块地图变成错误页。
     */
    suspend fun load(api: ApiClient, key: MapTileKey): ImageBitmap? {
        cache[key]?.let { return it }
        if (recentlyFailed(key)) return null

        // 抢占失败说明别的协程正在拿这一张，轮询等它写进缓存即可（瓦片几 KB，通常几十毫秒）
        if (!inFlight.add(key)) {
            repeat(30) {
                cache[key]?.let { return it }
                kotlinx.coroutines.delay(60.milliseconds)
            }
            return cache[key]
        }

        return try {
            slots.withPermit {
                cache[key]?.let { return@withPermit it }
                withContext(Dispatchers.IO) { fetchAndDecode(api, key) }?.also { cache[key] = it }
            }
        } finally {
            inFlight.remove(key)
        }
    }

    /** 后端明确说"没配 Key / 路由不存在"时，反复请求没有意义，冷却期内直接放弃。 */
    private fun recentlyFailed(key: MapTileKey): Boolean {
        val at = failedAt[key] ?: return false
        if (System.currentTimeMillis() - at < RETRY_AFTER_MS) return true
        failedAt.remove(key)
        return false
    }

    private suspend fun fetchAndDecode(api: ApiClient, key: MapTileKey): ImageBitmap? {
        val bytes = runCatching { api.fetchTile(key.zoom, key.x, key.y) }.getOrNull()
        if (bytes == null) {
            failedAt[key] = System.currentTimeMillis()
            return null
        }
        // 底图是道路/地名，没有透明区域，用 RGB_565 省一半内存且肉眼无差
        val decoded = runCatching {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }.getOrNull()
        if (decoded == null) failedAt[key] = System.currentTimeMillis()
        return decoded
    }
}
