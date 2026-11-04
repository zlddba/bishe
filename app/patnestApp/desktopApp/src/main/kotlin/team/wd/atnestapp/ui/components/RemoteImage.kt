package team.wd.atnestapp.ui.components

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap

/** 按 URL 缓存解码后的位图，超出上限时淘汰最早放入的一张 */
private class BitmapCache(private val maxEntries: Int = 80) {
    private val lock = Any()
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun get(url: String): ImageBitmap? = synchronized(lock) { cache[url] }

    fun put(url: String, bitmap: ImageBitmap) = synchronized(lock) {
        cache[url] = bitmap
        if (cache.size > maxEntries) {
            val oldest = cache.keys.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
    }
}

/**
 * 桌面端之前只画占位图标，照片从来没显示过；这里按 URL 缓存解码后的位图，
 * 列表滚动和反复进出页面都不重新下载。
 */
private val ImageCache = BitmapCache()

private suspend fun downloadImage(url: String): ByteArray = withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 10000
        instanceFollowRedirects = true
        // requestProperties 的 getter 返回 Map<String, List<String>>，只能用 setter 赋值
        setRequestProperty("User-Agent", "PatNest-Desktop/1.0")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        conn.inputStream.use { it.readAllBytes() }
    } finally {
        conn.disconnect()
    }
}

/**
 * 远程图片。加载中显示 [placeholder]，失败时保持占位并回调 [onError]，
 * 这样列表不会因为一张坏图变成空白块。
 */
@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable (Boolean) -> Unit = { failed -> ImagePlaceholder(failed) },
    onError: (String) -> Unit = {},
) {
    var bitmap by remember(url) { mutableStateOf(ImageCache.get(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap != null || url.isBlank()) return@LaunchedEffect
        runCatching { downloadImage(url) }
            .onSuccess { bytes ->
                runCatching { bytes.decodeToImageBitmap() }.getOrNull()?.let {
                    ImageCache.put(url, it)
                    bitmap = it
                } ?: run {
                    failed = true
                    onError("图片解码失败")
                }
            }
            .onFailure {
                failed = true
                onError(it.message ?: "图片加载失败")
            }
    }

    val current = bitmap
    Box(modifier.clip(shape).background(MaterialSurface), contentAlignment = Alignment.Center) {
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            placeholder(failed)
        }
    }
}

/** 默认占位：淡粉底 + 图片图标，加载中与加载失败共用（失败时图标更淡一些） */
@Composable
fun ImagePlaceholder(failed: Boolean = false) {
    Box(
        Modifier.fillMaxSize().background(
            if (failed) MaterialSurface else androidx.compose.ui.graphics.Color(0x1FE0708A),
        ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            AppIcon.Image,
            Modifier.size(22.dp),
            if (failed) PlaceholderMuted else PlaceholderTint,
        )
    }
}

private val MaterialSurface = androidx.compose.ui.graphics.Color(0xFFF4F1F2)
private val PlaceholderTint = androidx.compose.ui.graphics.Color(0xFFAF4A62)
private val PlaceholderMuted = androidx.compose.ui.graphics.Color(0xFFB2A0A5)
