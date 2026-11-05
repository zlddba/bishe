package team.wd.atnestapp.ui.mobile

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 手机端的网络图片。
 * 用 BitmapFactory 解码，一是 Android 上它最稳，二是可以先按目标宽度采样，
 * 机构封面不会被服务器上的原图撑爆内存。下载在 IO 线程，列表滚动不卡主线程。
 */
private const val CACHE_LIMIT = 90

private val bitmapCache: MutableMap<String, ImageBitmap> =
    object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > CACHE_LIMIT
    }.let { Collections.synchronizedMap(it) }

private suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
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
}

private fun decodeSampled(bytes: ByteArray, reqWidthPx: Int): ImageBitmap? = runCatching {
    var sample = 1
    if (reqWidthPx > 0) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        while (bounds.outWidth / (sample * 2) >= reqWidthPx) sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}.getOrNull()

@Composable
fun MobileImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop,
    placeholderSize: Dp = 22.dp,
) {
    var bitmap by remember(url) { mutableStateOf(if (url.isBlank()) null else bitmapCache[url]) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val cached = bitmapCache[url]
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val bytes = fetchBytes(url) ?: return@LaunchedEffect
        val decoded = decodeSampled(bytes, 480) ?: return@LaunchedEffect
        bitmapCache[url] = decoded
        bitmap = decoded
    }

    Box(modifier.clip(shape).background(MobileColors.Brand50), contentAlignment = Alignment.Center) {
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
                    MobileColors.Brand300,
                )
            }
        }
    }
}
