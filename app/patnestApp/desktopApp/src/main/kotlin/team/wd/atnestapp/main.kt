package team.wd.atnestapp

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.FrameWindowScope
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import team.wd.atnestapp.ui.AppRoot
import team.wd.atnestapp.ui.theme.PatNestTheme

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
    // 图标只解析一次：Window 会随状态重组，没必要反复读资源、重跑一遍 SVG 渲染。
    val windowIcon = remember { loadWindowIcon() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "宠栖 PetNest · Desktop",
        icon = windowIcon,
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

// 直接渲染打包进 resources 的 SVG。Compose Desktop 本身没有读 SVG 文件的 API，
// 但它底层就是 skiko，skiko 自带 SVG 解码器（org.jetbrains.skia.svg.SVGDOM），
// 所以不需要额外依赖，也不用再准备一份 PNG。
// 先按目标尺寸栅格化成位图再交给 BitmapPainter：AWT 侧拿到的必须是真实像素，
// 尺寸取 256 足够（标题栏 16~32，任务栏 48~64，留出高分屏余量）。
private fun loadWindowIcon(sizePx: Int = 256): Painter? =
    object {}.javaClass.getResourceAsStream("/appicon1.svg")?.use { stream ->
        runCatching {
            val svg = SVGDOM(Data.makeFromBytes(stream.readBytes()))
            svg.setContainerSize(sizePx.toFloat(), sizePx.toFloat())
            val surface = Surface.makeRasterN32Premul(sizePx, sizePx)
            svg.render(surface.canvas)
            surface.makeImageSnapshot().toComposeImageBitmap()
        }.getOrNull()?.let { BitmapPainter(it) }
    }
