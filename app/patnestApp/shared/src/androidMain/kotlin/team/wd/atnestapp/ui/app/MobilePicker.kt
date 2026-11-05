package team.wd.atnestapp.ui.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.design.DColor
import team.wd.atnestapp.ui.design.DShape
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * 选图：点一下先弹「拍照 / 从相册选择」，再走对应的系统能力。
 *
 * 两条路都返回字节流和文件名，调用方拿到后直接交给 ApiClient 上传，用法与之前完全一致：
 *
 *     val pickCover = rememberPhotoPicker { name, bytes -> ... }
 *
 * 相册走 Android 13 的图片选取器（旧系统自动退回 GET_CONTENT），不需要存储权限；
 * 拍照走 ACTION_IMAGE_CAPTURE + FileProvider，把原图写进应用私有缓存，
 * 因此也不需要 CAMERA 权限（权限只在 App 自己开相机预览时才要）。
 */
@Composable
fun rememberPhotoPicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    // 回调可能在任意一次重组之后才触发，用 rememberUpdatedState 保证拿到的是最新那个 lambda
    val currentOnPicked by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    var sheetVisible by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    /**
     * 读字节 + 压缩都放到 IO 线程：相机原图十几兆，在主线程解一遍会明显掉帧。
     * 算完再切回主线程回调，调用方照旧可以直接改 Compose 状态。
     */
    fun deliver(uri: Uri, tempFile: File? = null) {
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                try {
                    val raw = readBytes(context, uri)
                    if (raw == null) null else normalizeForUpload(raw, guessName(context, uri))
                } finally {
                    tempFile?.delete()
                }
            }
            if (prepared == null) {
                // 读不出来时仍然回调一次空字节，让调用方统一走「没有读取到图片」的提示
                currentOnPicked("", ByteArray(0))
            } else {
                currentOnPicked(prepared.first, prepared.second)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) deliver(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(TakePictureToUri()) { saved ->
        val uri = cameraUri
        val file = cameraFile
        cameraUri = null
        cameraFile = null
        // 用户点了取消（saved == false）就什么都不做，别弹「没有读取到图片」；
        // 临时文件这时还没人读，直接删掉。
        if (saved && uri != null) deliver(uri, file) else file?.delete()
    }

    fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun openCamera() {
        val file = runCatching { newCameraFile(context) }.getOrNull()
        val uri = file?.let {
            runCatching {
                FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, it)
            }.getOrNull()
        }
        if (file == null || uri == null) {
            // 没有 FileProvider 授权（清单被改坏）时不要硬崩，退回相册
            openGallery()
            return
        }
        cameraFile = file
        cameraUri = uri
        val started = runCatching { cameraLauncher.launch(uri) }.isSuccess
        if (!started) {
            file.delete()
            cameraFile = null
            cameraUri = null
            openGallery()
        }
    }

    val cameraReady = remember(context) { hasCameraApp(context) }
    if (sheetVisible) {
        PhotoSourceSheet(
            cameraReady = cameraReady,
            onDismiss = { sheetVisible = false },
            onCamera = {
                sheetVisible = false
                if (cameraReady) openCamera() else openGallery()
            },
            onGallery = {
                sheetVisible = false
                openGallery()
            },
        )
    }
    return { sheetVisible = true }
}

/** FileProvider 的 authority 后缀，和 AndroidManifest 里的 `${applicationId}.fileprovider` 对齐。 */
private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

/** 后端 /upload/image 硬上限 5MB，留 1MB 余量给 multipart 头和编码波动。 */
private const val SAFE_UPLOAD_BYTES = 4 * 1024 * 1024

/** 压缩后的最长边：2000px 对上传到手机端展示的图完全够，也远小于 5MB。 */
private const val MAX_EDGE_PX = 2000

/** 后端只认这几种扩展名，其它格式（HEIC/HEIF/BMP…）一律转成 jpg 再传。 */
private val UPLOAD_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp")

/**
 * 相机原图动辄 5~12MB，直接传会被后端以「图片不能超过 5MB」拒掉；
 * 另外 iPhone 拍的 HEIC 后端也不认。这里统一在客户端兜一层：
 * 能原样传就原样传（PNG 截图不要重编码，避免文字发虚），超限或格式不支持才压。
 */
private fun normalizeForUpload(bytes: ByteArray, name: String): Pair<String, ByteArray> {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext in UPLOAD_EXTS && bytes.size <= SAFE_UPLOAD_BYTES) return name to bytes
    val jpeg = runCatching { bytes.toJpegUnderLimit() }.getOrNull() ?: return name to bytes
    val base = name.substringBeforeLast('.').ifBlank { "photo" }
    return "$base.jpg" to jpeg
}

private fun ByteArray.toJpegUnderLimit(): ByteArray {
    // ImageDecoder（API 28+）比 BitmapFactory 好用的地方：自动按 EXIF 把照片转正，
    // 并且能在解码阶段直接缩放到目标尺寸，不用先解一张全尺寸大图进内存。
    val source = ImageDecoder.createSource(ByteBuffer.wrap(this))
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // 硬件位图不能回读，compress 会抛异常，这里必须用软件位图
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / longest
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
    val out = ByteArrayOutputStream()
    var quality = 88
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    // 尺寸已经限住了，画质兜底再降几档，保证一定落在上限内
    while (out.size() > SAFE_UPLOAD_BYTES && quality > 56) {
        out.reset()
        quality -= 8
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    bitmap.recycle()
    return out.toByteArray()
}

/**
 * 系统相机写原图的目标文件。放私有缓存目录，由 FileProvider 授权给相机 App 写，
 * 用完（读完字节或用户取消）立刻删，不在用户手机上留垃圾。
 */
private fun newCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File.createTempFile("shot_", ".jpg", dir)
}

private fun hasCameraApp(context: Context): Boolean =
    runCatching {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }.getOrDefault(false)

/**
 * 系统自带的 TakePicture 不带 URI 授权标志，个别机型上相机会因为写不进 EXTRA_OUTPUT
 * 而返回失败（表现为「拍完没反应」）。这里补上读写授权。
 */
private class TakePictureToUri : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

private fun readBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    val stream: InputStream? = context.contentResolver.openInputStream(uri)
    stream?.use { it.readBytes() }
}.getOrNull()

/** 从 content uri 里猜一个带扩展名的文件名，后端会按扩展名决定存储类型 */
private fun guessName(context: Context, uri: Uri): String {
    val raw = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    val name = raw?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast("/") ?: "photo.jpg"
    return if (name.contains(".")) name else name + ".jpg"
}

/**
 * 照片来源选择面板。
 *
 * 用底部弹层而不是系统弹出的 Intent 选择器：系统那个是一排灰突突的应用图标，
 * 用户根本分不清哪个是相机、哪个是相册。这里两行字说清楚，也符合扁平化的一片式设计。
 */
@Composable
private fun PhotoSourceSheet(
    cameraReady: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // 关掉默认宽度、自己铺满整屏：否则弹层会被系统塞进一个小方框里，
        // 底部面板必须贴边；decorFitsSystemWindows=false 让背景铺到屏幕底，
        // 内容再用 navigationBarsPadding 顶上来（否则两条系统栏各加一次内边距）。
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            // 遮罩：点空白处关掉面板
            Box(
                Modifier.fillMaxSize()
                    .background(DColor.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(DColor.Card)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
            ) {
                // 顶部小把手，暗示这是一张可以下拉收起的面板
                Box(
                    Modifier.padding(top = 8.dp).size(width = 36.dp, height = 4.dp)
                        .clip(DShape.Pill).background(DColor.Line2).align(Alignment.CenterHorizontally),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("添加照片", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text("选择照片来源", fontSize = 11.5.sp, color = DColor.Ink3)
                    }
                    Box(
                        Modifier.size(28.dp).clip(DShape.Pill).background(DColor.Bg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Close, Modifier.size(13.dp), DColor.Ink3)
                    }
                }
                Column(
                    Modifier.padding(horizontal = 18.dp)
                        .clip(DShape.Lg).background(DColor.Bg),
                ) {
                    PhotoSourceRow(
                        icon = AppIcon.Camera,
                        title = "拍照",
                        subtitle = if (cameraReady) "用相机拍一张" else "当前设备没有可用的相机",
                        enabled = cameraReady,
                        onClick = onCamera,
                    )
                    Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(1.dp).background(DColor.Line))
                    PhotoSourceRow(
                        icon = AppIcon.Image,
                        title = "从相册选择",
                        subtitle = "使用手机里已有的照片",
                        enabled = true,
                        onClick = onGallery,
                    )
                }
                Text(
                    "照片会自动压缩后上传，单张不超过 5MB",
                    fontSize = 11.sp,
                    color = DColor.Ink3,
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(44.dp)
                        .clip(DShape.Md).background(DColor.Card)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DColor.Ink2)
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceRow(
    icon: AppIcon,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier.size(34.dp).clip(DShape.Md)
                .background(if (enabled) DColor.BrandSoft else DColor.NeutralSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(19.dp), if (enabled) DColor.BrandInk else DColor.Ink3)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) DColor.Ink else DColor.Ink3,
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.5.sp, color = DColor.Ink3)
        }
    }
}
