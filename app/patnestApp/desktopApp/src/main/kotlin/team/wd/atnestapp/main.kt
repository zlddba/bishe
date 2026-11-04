package team.wd.atnestapp

import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.runtime.LaunchedEffect
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
        // 页面大量使用左右两栏，窗口太窄会点不到东西。
        // 这一版 Compose 的 Window 参数表里没有 minimumSize，只能回到 AWT 层设置。
        LaunchedEffect(Unit) {
            frame.window.minimumSize = java.awt.Dimension(1080, 700)
        }
        PatNestTheme {
            AppRoot(
                titleBar = { frame.DesktopTitleBar(::exitApplication) },
                // 最小化时暂停轮询。这里用 extendedState 而不是 isMinimized，
                // 跟标题栏最小化按钮走的是同一个 API，确定能编过
                isMinimized = { frame.window.extendedState == java.awt.Frame.ICONIFIED },
            )
        }
    }
}

private fun loadWindowIcon(): Painter? =
    object {}.javaClass.getResourceAsStream("/appIcon.png")?.use {
        BitmapPainter(it.readAllBytes().decodeToImageBitmap())
    }
