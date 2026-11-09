package team.wd.atnestapp.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口宽度分档。阈值取 Material 3 的 window size class 口径：
 *
 * | 档位 | 宽度 | 典型设备 | 导航形态 |
 * | --- | --- | --- | --- |
 * | Compact | < 600dp | 手机竖屏、小折叠外屏 | 底部 Tab |
 * | Medium | 600~839dp | 平板竖屏、手机横屏、折叠展开 | 左侧导航栏 |
 * | Expanded | ≥ 840dp | 平板横屏、桌面模式、分屏大窗 | 左侧导航栏 + 多列 |
 *
 * 这套 UI 是照手机竖屏设计稿还原的，所有尺寸（86dp 封面、18dp 边距、单列卡片）
 * 在平板上直接跑会得到「一张卡横贯 1200dp」的观感。适配不靠给每个页面改尺寸，
 * 而是在骨架层统一注入窗口信息，让骨架自己决定：导航放哪、内容多宽、分几列。
 */
enum class DSizeClass { Compact, Medium, Expanded }

/**
 * 当前窗口的尺寸信息。由 [DesignApp] 在根节点用 BoxWithConstraints 实测得到，
 * 所以分屏拖拽、折叠屏开合、旋转都会即时更新，不依赖 Activity 重建。
 */
@Immutable
data class DWindow(
    val width: Dp,
    val height: Dp,
    val sizeClass: DSizeClass,
) {
    val isCompact: Boolean get() = sizeClass == DSizeClass.Compact

    val isExpanded: Boolean get() = sizeClass == DSizeClass.Expanded

    /** 宽屏用左侧常驻导航栏代替底部 Tab。 */
    val showRail: Boolean get() = sizeClass != DSizeClass.Compact

    /**
     * 页面正文的最大宽度。手机不限制（Infinity），宽屏限宽后居中。
     *
     * 限宽不是审美选择：文字行宽超过约 80 个字符后阅读时眼球回扫距离变长，
     * 表单和控制项也会散得过开。桌面端那套界面是独立实现的，这里只保证手机版式
     * 放到大屏上仍然「像是一个 App」。
     */
    // 手机档用 POSITIVE_INFINITY 表示「不限宽」。不用 Dp.Infinity 是为了不依赖
    // 该版本 Compose 是否导出了这个伴生常量，行为完全一样。
    val contentMaxWidth: Dp
        get() = when (sizeClass) {
            DSizeClass.Compact -> Dp(Float.POSITIVE_INFINITY)
            DSizeClass.Medium -> 800.dp
            DSizeClass.Expanded -> 1040.dp
        }

    val hasContentLimit: Boolean get() = sizeClass != DSizeClass.Compact

    /** 页面左右安全边距：窄屏沿用设计稿的 18dp，宽屏逐档放大。 */
    val gutter: Dp
        get() = when (sizeClass) {
            DSizeClass.Compact -> 18.dp
            DSizeClass.Medium -> 24.dp
            DSizeClass.Expanded -> 32.dp
        }

    /** 左侧导航栏宽度。 */
    val railWidth: Dp get() = if (isExpanded) 96.dp else 88.dp

    /** 底部提示条的落位：有 Tab 时要避开 Tab，宽屏没有 Tab 就贴近底部。 */
    val toastBottom: Dp get() = if (isCompact) 88.dp else 28.dp

    /**
     * 按最小卡片宽度算列数。
     *
     * 用「可用宽度 ÷ 最小宽度」而不是写死档位，是为了照顾 600dp 的竖屏平板
     * 和 1280dp 的横屏平板这种差距极大的情况：前者两列就满，后者三列才不空旷。
     */
    fun columns(minItemWidth: Dp = 320.dp): Int {
        if (isCompact) return 1
        val rail = if (showRail) railWidth else 0.dp
        val usable = width - rail - gutter * 2
        if (usable <= 0.dp) return 1
        return (usable / minItemWidth).toInt().coerceIn(1, 3)
    }

    companion object {
        fun of(width: Dp, height: Dp): DWindow {
            val cls = when {
                width < 600.dp -> DSizeClass.Compact
                width < 840.dp -> DSizeClass.Medium
                else -> DSizeClass.Expanded
            }
            return DWindow(width, height, cls)
        }
    }
}

/**
 * 窗口信息的默认值取手机竖屏：任何没被 [DesignApp] 包住的预览 / 单测
 * 都会退化成改动前的手机版式，而不是抛出异常。
 *
 * 显式写出类型（而不是靠推断）有两个作用：
 * 1. 保证是 ProvidableCompositionLocal，`LocalDWindow provides window` 才能编译；
 * 2. 推断型顶层 val 在增量编译里偶尔会因文件未重新分析而丢失可见性，
 *    写成显式类型可以避开这一类问题。
 */
val LocalDWindow: ProvidableCompositionLocal<DWindow> =
    staticCompositionLocalOf<DWindow> { DWindow(360.dp, 780.dp, DSizeClass.Compact) }

@Composable
@ReadOnlyComposable
fun dWindow(): DWindow = LocalDWindow.current

/**
 * 宽屏多列栅格。
 *
 * 只解决「一排多张卡片」这一件事，所以不引入 LazyVerticalGrid：
 * 这些页面本身已经在 DFrame 的滚动容器里，嵌套一个可滚动网格会和外层抢滚动权。
 * 列数是均分的 weight，不是固定宽度，因此两侧不会出现参差的留白。
 *
 * 卡片自带的底部间距依然生效；[horizontalSpacing] 只负责列间距，
 * 默认 12dp 与设计稿里卡片之间的视觉间隔一致。
 */
@Composable
fun <T> DGrid(
    items: List<T>,
    minItemWidth: Dp = 320.dp,
    horizontalSpacing: Dp = 12.dp,
    content: @Composable (T) -> Unit,
) {
    val cols = LocalDWindow.current.columns(minItemWidth)
    if (cols <= 1) {
        Column(Modifier.fillMaxWidth()) { items.forEach { content(it) } }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        items.chunked(cols).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) { content(item) }
                }
                // 最后一行不足列数时补空位，避免两张卡各占半屏、三张卡挤成三份
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
