package team.wd.atnestapp.ui.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** 打开系统文件选择框，返回选中的图片文件；取消时返回 null。 */
fun chooseImageFile(): File? {
    val dialog = FileDialog(null as Frame?, "选择图片", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val lower = name.lowercase()
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".gif") || lower.endsWith(".webp")
    }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
