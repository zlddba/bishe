package team.wd.atnestapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.Canvas
import java.awt.Color
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

/**
 * 商家直播的桌面播放器（VLCJ）。
 *
 * 用法：
 * ```
 * room.playUrl?.let { url ->
 *     VlcVideoPlayer(
 *         url = url,
 *         modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
 *         onError = { message -> /* 提示：本机没装 VLC */ },
 *     )
 * }
 * ```
 *
 * 说明：
 * - 运行机需要安装 VLC（提供 libvlc）；没装时 `MediaPlayerFactory()` 会抛异常，
 *   这里捕获后回调 `onError`，页面上显示「请先安装 VLC」即可，不影响其它功能。
 * - 支持 HLS(m3u8) / RTMP / HTTP-FLV，直接吃后端下发的 `play_*` 地址。
 * - 组合项离开时释放播放器与工厂，避免切换直播间后声音/解码器残留。
 */
@Composable
fun VlcVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
) {
    val canvas = remember { Canvas().apply { background = Color.BLACK } }

    DisposableEffect(url) {
        var factory: MediaPlayerFactory? = null
        var player: EmbeddedMediaPlayer? = null
        try {
            factory = MediaPlayerFactory()
            player = factory.mediaPlayers().newEmbeddedMediaPlayer()
            // 把 libvlc 的画面画到 AWT Canvas 上，再由 SwingPanel 嵌进 Compose
            player.videoSurface().set(factory.videoSurfaces().newVideoSurface(canvas))
            player.media().play(url)
        } catch (t: Throwable) {
            onError(t.message ?: "VLC 初始化失败，请先安装 VLC 后重试")
            runCatching { player?.release() }
            runCatching { factory?.release() }
            player = null
            factory = null
        }
        onDispose {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            runCatching { factory?.release() }
        }
    }

    SwingPanel(factory = { canvas }, modifier = modifier)
}
