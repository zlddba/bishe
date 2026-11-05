package team.wd.atnestapp.ui.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 手机端的网络图片。
 * 用 BitmapFactory 解码，一是 Android 上它最稳，二是可以先按目标宽度采样，
 * 机构封面不会被服务器上的原图撑爆内存。
 *
 * 下载和解码都必须离开主线程：解码一张 4000px 的 JPEG 动辄十几毫秒，
 * 放在主线程上会让列表首帧、滚动都掉帧。
 */
private const val CACHE_LIMIT = 90

/** 图片没加载出来时的占位底色与图标色，取品牌粉的浅色阶，避免出现突兀的灰块。 */
private val PlaceholderBg = Color(0xFFFDF1F4)
private val PlaceholderIcon = Color(0xFFEFA0B3)

private val bitmapCache: MutableMap<String, ImageBitmap> =
    object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > CACHE_LIMIT
    }.let { Collections.synchronizedMap(it) }

/** 下载失败的地址短期不再重试，避免每次重组都重新发一遍请求。 */
private val failedUrls: MutableSet<String> =
    Collections.synchronizedSet(Collections.newSetFromMap(LinkedHashMap<String, Boolean>()))

private fun fetchBytesBlocking(url: String): ByteArray? = runCatching {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 6000
    conn.readTimeout = 12000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", "PatNest-Android/1.0")
    try {
        if (conn.responseCode !in 200..299) return@runCatching null
        conn.inputStream.use { input ->
            ByteArrayOutputStream().use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }
    } finally {
        conn.disconnect()
    }
}.getOrNull()

private fun decodeSampled(bytes: ByteArray, reqWidthPx: Int): ImageBitmap? = runCatching {
    var sample = 1
    var jpeg = false
    if (reqWidthPx > 0) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        jpeg = bounds.outMimeType == "image/jpeg"
        while (bounds.outWidth / (sample * 2) >= reqWidthPx) sample *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        // 照片没有透明通道，565 省一半内存也不会掉色；PNG（带 alpha）必须保持 8888。
        if (jpeg) inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}.getOrNull()

/**
 * 同时在跑的图片任务上限。
 *
 * 列表页一屏可能有二三十张封面，不限流的话会出现「二三十个连接 + 二三十次解码同时开工」，
 * 首屏那一下 CPU 直接打满。排队加载的图片仍然显示占位底色，观感和逐张淡入一致。
 */
private val loadSlots = Semaphore(6)

/** 下载 + 解码整段都在 IO 线程执行，主线程只收结果。 */
private suspend fun loadBitmap(url: String, reqWidthPx: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        bitmapCache[url]?.let { return@withContext it }
        if (url in failedUrls) return@withContext null
        loadSlots.withPermit {
            // 排队期间可能已经被别的组件加载好了，进来再查一次。
            bitmapCache[url]?.let { return@withContext it }
            val bytes = fetchBytesBlocking(url)
            if (bytes == null) {
                failedUrls.add(url)
                return@withContext null
            }
            val decoded = decodeSampled(bytes, reqWidthPx)
            if (decoded == null) failedUrls.add(url) else bitmapCache[url] = decoded
            decoded
        }
    }

@Composable
fun MobileImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
    placeholderSize: Dp = 22.dp,
) {
    var bitmap by remember(url) { mutableStateOf(if (url.isBlank()) null else bitmapCache[url]) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        bitmapCache[url]?.let {
            bitmap = it
            return@LaunchedEffect
        }
        loadBitmap(url, 480)?.let { bitmap = it }
    }

    Box(modifier.clip(shape).background(PlaceholderBg), contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Box(Modifier.size(placeholderSize), contentAlignment = Alignment.Center) {
                team.wd.atnestapp.ui.components.AppIcon(
                    team.wd.atnestapp.ui.components.AppIcon.Image,
                    Modifier.size(placeholderSize),
                    PlaceholderIcon,
                )
            }
        }
    }
}
