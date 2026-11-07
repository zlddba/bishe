package team.wd.atnestapp.ui.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.SurfaceHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.socket.base.SocketType
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.view.OpenGlView

/**
 * 商家直播：**软件内直接开播**（不依赖 OBS）。
 *
 * 用 RootEncoder 的 `RtmpCamera2` 采集摄像头 + 麦克风，H.264/AAC 编码后直接推到后端下发的
 * `push_url`（`rtmp://host:1935/live/<stream_key>`）。预览就是同一个 [OpenGlView]。
 *
 * 使用方式：
 * ```
 * // 1) 先调 ApiClient.startLive(title) 拿 room.pushUrl
 * // 2) 进开播页
 * LiveBroadcaster(
 *     rtmpUrl = room.pushUrl!!,
 *     onState = { status -> "连接中 / 直播中 / 失败原因" },
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
 * )
 * // 3) 页面退出时组件自动 stopStream + stopCamera；再调 ApiClient.stopLive(room.id)
 * ```
 *
 * RootEncoder 2.x 与 1.x 的差异（踩过一遍，别再改回去）：
 * - 预览必须是 `OpenGlView`（`SurfaceView` 不行，构造函数只认 OpenGlView / Context）；
 * - `ConnectChecker` 多了 `onAuthSuccess` / `onAuthError` 两个抽象方法，不实现编译不过；
 * - `prepareVideo` 不再顺手拉起摄像头，要自己 `startPreview()`；
 * - `release()` 已移除，收尾用 `stopStream()` + `stopCamera()`。
 *
 * 说明：
 * - 需要 CAMERA / RECORD_AUDIO 运行时权限，组件内部会自动申请；
 * - 分辨率/码率按演示场景固定在 720p / 2.5Mbps，弱网可在 `prepareVideo` 处调低；
 * - 推流期间不要再调 `join` 当观众，二者会抢摄像头。
 */
@Composable
fun LiveBroadcaster(
    rtmpUrl: String,
    onState: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasBroadcastPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = hasBroadcastPermission(context) && result.values.none { !it }
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    if (!granted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("需要相机与麦克风权限才能开播")
        }
        return
    }

    // GL 预览视图要在整个推流周期里复用，不能每次重组新建
    val glView = remember(context) { OpenGlView(context) }

    DisposableEffect(glView, rtmpUrl) {
        val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) = onState("连接中…")
            override fun onConnectionSuccess() = onState("直播中")
            override fun onConnectionFailed(reason: String) = onState("连接失败：$reason")
            override fun onNewBitrate(bitrate: Long) = Unit
            override fun onDisconnect() = onState("已断开")
            override fun onAuthError() = onState("推流鉴权失败，请确认推流地址")
            override fun onAuthSuccess() = Unit
        }

        var camera: RtmpCamera2? = null
        var launched = false

        // 真正开播：等 GL 画面就绪后再建采集器，否则摄像头拿不到可用的 Surface
        fun begin() {
            if (launched) return
            launched = true
            val c = RtmpCamera2(glView, checker)
            camera = c
            // RTMP 走 Java 原生 Socket。库默认用 Ktor，但它编译期的 Ktor 版本和本项目
            // 的 Ktor 3.6 不是同一套字节码，走 Ktor 会在 connect 时 NoSuchMethodError
            // （ByteWriteChannel.writeByte）。切到 JAVA 只影响这一条 TCP 连接。
            runCatching { c.getStreamClient().setSocketType(SocketType.JAVA) }
            val ready = runCatching {
                // 5 个参数的重载是 (w,h,fps,bitrate,rotation)，没有 iFrameInterval 的位置，
                // 想让关键帧 2 秒一个必须走 6 参重载，否则 2 会被当成旋转角度。
                c.prepareAudio() && c.prepareVideo(1280, 720, 30, 2_500_000, 2, 0)
            }.getOrElse { t ->
                onState(t.message ?: "摄像头/麦克风不可用")
                false
            }
            if (!ready) {
                onState("初始化摄像头/麦克风失败")
                return
            }
            // 2.x 的 prepareVideo 不会自动拉起预览，要显式 startPreview 才有画面
            runCatching { c.startPreview() }
            runCatching { c.startStream(rtmpUrl) }
                .onFailure { onState(it.message ?: "推流失败") }
        }

        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = begin()
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        }
        glView.holder.addCallback(callback)
        // 复用已有画面（换推流地址时 Surface 早就建好了，不会再来一次 surfaceCreated）
        if (glView.holder.surface?.isValid == true) begin()

        onDispose {
            glView.holder.removeCallback(callback)
            camera?.let { c ->
                runCatching { c.stopStream() }
                runCatching { c.stopCamera() }
            }
        }
    }

    AndroidView(factory = { glView }, modifier = modifier)
}

private fun hasBroadcastPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
