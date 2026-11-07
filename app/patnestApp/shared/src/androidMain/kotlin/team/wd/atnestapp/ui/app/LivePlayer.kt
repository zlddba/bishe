package team.wd.atnestapp.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
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
    /** 直播追帧目标（毫秒）：越小越实时，网络差时更容易卡，建议 1000~2000 */
    lowLatencyMs: Long = 1_200,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context)
            // 直播缓冲：只留 1 秒左右，追帧靠播放器微调倍速，不靠停住等。
            // setBufferDurationsMs 是 Java 方法，参数名不保留，必须按位置传；写命名参数会编译失败。
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_000, 4_000, 500, 800)
                    .build(),
            )
            .build()
            .apply {
                // HLS 直播的「追直播点」目标：默认 3 段，这里压到 1.2s
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(lowLatencyMs)
                            .setMinPlaybackSpeed(0.95f)
                            .setMaxPlaybackSpeed(1.08f)
                            .build(),
                    )
                    .build()
                setMediaItem(item)
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
