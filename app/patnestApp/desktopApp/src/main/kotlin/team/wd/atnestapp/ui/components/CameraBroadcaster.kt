package team.wd.atnestapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter

/**
 * 商家直播：**桌面端软件内直接开播**（不依赖 OBS）。
 *
 * 用 JavaCV 的 FFmpeg 采集本机摄像头/麦克风，H.264 + AAC 编码后推到后端下发的
 * `push_url`（`rtmp://host:1935/live/<stream_key>`），同时把画面当预览渲染到 Compose。
 *
 * 使用方式：
 * ```
 * val room = api.startLive("直播标题")   // 拿 room.pushUrl
 * CameraBroadcastPanel(room.pushUrl!!, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
 * // 离开页面自动停止；再调 api.stopLive(room.id)
 * ```
 *
 * 摄像头设备名：
 * - Windows：dshow 设备名，如 `video=Integrated Camera`（默认值，可在系统「相机」里看到名称）
 * - macOS：`0`（avfoundation 的索引）
 * - Linux：`/dev/video0`
 * 设备名不对时状态栏会显示 FFmpeg 的报错，换一个名字重试即可。
 */
class CameraRtmpBroadcaster(
    private val rtmpUrl: String,
    private val cameraInput: String = defaultCameraInput(),
    private val inputFormat: String = defaultInputFormat(),
    private val width: Int = 1280,
    private val height: Int = 720,
    private val fps: Int = 30,
    private val videoBitrate: Int = 2_500_000,
) {
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set

    var status by mutableStateOf("待机")
        private set

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            status = "打开摄像头…"
            var grabber: FFmpegFrameGrabber? = null
            var recorder: FFmpegFrameRecorder? = null
            val converter = Java2DFrameConverter()
            try {
                grabber = FFmpegFrameGrabber(cameraInput).apply {
                    format = inputFormat
                    frameRate = fps.toDouble()
                    imageWidth = width
                    imageHeight = height
                    start()
                }
                recorder = FFmpegFrameRecorder(rtmpUrl, width, height, 1).apply {
                    format = "flv"
                    videoCodec = avcodec.AV_CODEC_ID_H264
                    videoBitrate = videoBitrate
                    frameRate = fps.toDouble()
                    // 让编码器尽量实时，缓冲区别攒太大
                    gopSize = fps * 2.0
                    audioCodec = avcodec.AV_CODEC_ID_AAC
                    audioBitrate = 128_000
                    audioChannels = 1
                    start()
                }
                status = "直播中"
                var frameIndex = 0
                while (isActive) {
                    val frame = grabber.grab() ?: continue
                    recorder.record(frame)
                    // 预览不需要 30fps，每 3 帧转一张图，降低 UI 线程压力
                    if (frameIndex++ % 3 == 0) {
                        runCatching {
                            converter.convert(frame)?.let { preview = it.toComposeImageBitmap() }
                        }
                    }
                }
            } catch (t: Throwable) {
                status = "开播失败：${t.message ?: t::class.simpleName}"
            } finally {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
                if (status == "直播中") status = "已结束"
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/** 开播面板：自动开始推流，显示预览与状态；组件销毁时自动停播。 */
@Composable
fun CameraBroadcastPanel(
    rtmpUrl: String,
    modifier: Modifier = Modifier,
    cameraInput: String = defaultCameraInput(),
    /** 预览区高度。给死高度是因为 Column 里没有确定高度的 Box 会塌成 0，预览就只剩一条缝。 */
    previewHeight: Dp = 360.dp,
    onStatus: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val broadcaster = remember(rtmpUrl, cameraInput) {
        // 传空串表示「用这台机器的默认设备」：设备名因系统而异，页面留空比瞎填安全
        CameraRtmpBroadcaster(rtmpUrl = rtmpUrl, cameraInput = cameraInput.ifBlank { defaultCameraInput() })
    }

    DisposableEffect(broadcaster) {
        broadcaster.start(scope)
        onDispose { broadcaster.stop() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val frame = broadcaster.preview
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = "摄像头预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("正在打开摄像头…", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
            Text(
                broadcaster.status,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "推流地址：$rtmpUrl",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
        )
        // 状态变化同步给外层（房间页用来更新按钮/提示）
        Text("状态：${broadcaster.status}", color = Color(0xFF6B7280), fontSize = 11.sp)
        LaunchedEffect(broadcaster.status) { onStatus(broadcaster.status) }
    }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").lowercase().contains("win")

private fun isMac(): Boolean =
    System.getProperty("os.name").lowercase().contains("mac")

private fun defaultInputFormat(): String = when {
    isWindows() -> "dshow"
    isMac() -> "avfoundation"
    else -> "v4l2"
}

/** 默认摄像头：Windows 需要设备名，mac/Linux 用索引或设备节点。 */
private fun defaultCameraInput(): String = when {
    isWindows() -> "video=Integrated Camera"
    isMac() -> "0"
    else -> "/dev/video0"
}
