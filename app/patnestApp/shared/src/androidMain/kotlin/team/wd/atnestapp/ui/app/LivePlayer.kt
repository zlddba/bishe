package team.wd.atnestapp.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 商家直播的 Android 播放器（media3 / ExoPlayer）。
 *
 * 用法：
 * ```
 * room.playUrl?.let { LivePlayer(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
 * ```
 *
 * 说明：
 * - 优先播 HLS（`play_hls`），ExoPlayer 的 HLS 模块已在 shared 的 androidMain 依赖里；
 * - 直播间的房间信息、弹幕、心跳交给调用方（`ApiClient.joinLive` 30s 一次），这里只管画面；
 * - 页面销毁时自动 release，避免多个直播间来回切换时泄漏解码器。
 */
@Composable
fun LivePlayer(
    url: String,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            this.playWhenReady = playWhenReady
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                this.player = player
            }
        },
        update = { view -> view.player = player },
    )
}
