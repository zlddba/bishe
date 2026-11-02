package team.wd.atnestapp

import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.FrameWindowScope
import team.wd.atnestapp.ui.AppRoot
import team.wd.atnestapp.ui.theme.PatNestTheme

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "宠栖 PetNest · Desktop",
        icon = loadWindowIcon(),
        undecorated = true,
        // 不使用透明窗口：Windows 上透明窗口在最小化/缩放时会触发
        // TranslucentWindowPainter 的 0×0 尺寸异常（AWT-EventQueue 刷错、闪烁）。
        transparent = false,
        state = windowState,
    ) {
        val frame: FrameWindowScope = this
        PatNestTheme {
            AppRoot(titleBar = { frame.DesktopTitleBar(::exitApplication) })
        }
    }
}

private fun loadWindowIcon(): Painter? =
    object {}.javaClass.getResourceAsStream("/appIcon.png")?.use {
        BitmapPainter(it.readAllBytes().decodeToImageBitmap())
    }
