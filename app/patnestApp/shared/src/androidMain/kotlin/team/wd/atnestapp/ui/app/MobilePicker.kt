package team.wd.atnestapp.ui.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream

/**
 * 系统相册选图。用 Android 13 的图片选取器，旧系统会自动退回 GET_CONTENT，
 * 因此不需要在 manifest 里申请存储权限，也不会触发分区存储的各种坑。
 * 回调给出字节流和推算出的文件名，调用方直接交给 ApiClient 上传。
 */
@Composable
fun rememberPhotoPicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = readBytes(context, uri)
        if (bytes == null) {
            onPicked("", ByteArray(0))
            return@rememberLauncherForActivityResult
        }
        onPicked(guessName(context, uri), bytes)
    }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
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
