package team.wd.atnestapp.ui.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/**
 * 玻璃质感（基于已发布的 miuix-blur 0.9.4，官方未发布的 miuix-glass 不引入）。
 *
 * 通透配方来自官方 blur 指南（iOS/HyperOS 同款思路）——玻璃感不是靠白 tint 堆出来的：
 * 1. 模糊（20~28dp）：底下内容变软；
 * 2. **提饱和度（1.45）**：模糊后的内容保持色彩，玻璃才「活」——纯白磨砂板
 *    的最大原因就是模糊把色彩抹掉了；
 * 3. **Screen 混合轻提亮（白 16%）**：代替厚白 tint，提亮但不遮底；
 * 4. Overlay 轻压暗（黑 6%）+ 亮度 +0.04 + 对比 1.08：保住层次；
 * 5. **官方边缘光 highlight**：受光边缘（上半亮、下半弱），模拟玻璃厚度折光，
 *    比 1px 白描边真实得多；
 * 6. 极薄中和层 tint（~10%）：只负责统一底色，不负责「填白」。
 *
 * 能力边界：模糊依赖 Android 13（API 33）的 RuntimeShader。
 * - Android 13+：完整玻璃质感；
 * - API 28~32：自动退化为 DColor.Card 实体卡，布局与内容完全一致
 *   （manifest 里 tools:overrideLibrary 已放行 minSdk 33 的 blur 模块合并）。
 */

/** 玻璃模糊在本设备是否可用。 */
val isGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= 33

/**
 * 背景记录器。一屏一个，放在页面根组合里；
 * 所有玻璃面共享它，模糊的都是同一份背景。
 */
@Composable
fun rememberDGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/**
 * 把本容器绘制的内容（背景底色、渐变、装饰图形）记录进玻璃 backdrop。
 * 注意：玻璃面板本身不要挂这个修饰符，否则会把面板自己也算进被模糊的背景。
 */
fun Modifier.dGlassBackdropSource(backdrop: LayerBackdrop): Modifier =
    this.layerBackdrop(backdrop)

/**
 * 骨架层提供的页面背景记录器。
 *
 * - 骨架（DMiuixScrollScaffold / ListScaffold）把整页内容记录进它，
 *   并 provide 给自己的内容与底部浮层——[DFixBar]、[DBottomTabs] 读它做真实模糊；
 * - [DesignApp] 在根部记录一份全窗口的——[DToastHost] 这类根级浮层读它；
 * - 页内骨架 provide 的会遮蔽根部那份，各浮层总是模糊「自己正下方的页面」。
 *
 * 没有记录器（预览、未接入骨架的调用方）时玻璃自动退化为实体卡。
 */
val LocalDGlassBackdrop: ProvidableCompositionLocal<LayerBackdrop?> =
    staticCompositionLocalOf { null }

/**
 * 通透玻璃的光学配方（官方 blur 指南推荐区间）。
 * remember 包装：BlurColors 含 List 字段，Compose 把它视为不稳定对象，
 * 每次重组新建会让 textureBlur 的效果链失效重建。
 */
@Composable
private fun rememberDGlassOptics(): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(
        // Screen 混合：提亮但不遮底（对比 SrcOver 的奶白效果）
        BlendColorEntry(Color.White.copy(alpha = 0.16f), BlurBlendMode.Screen),
        // Overlay 压暗：模糊内容过曝时保住对比
        BlendColorEntry(Color.Black.copy(alpha = 0.06f), BlurBlendMode.Overlay),
    ),
    brightness = 0.04f,
    contrast = 1.08f,
    saturation = 1.45f,
)

/** 按主题选官方边缘光预设（上半受光、下半衰减，模拟玻璃折光）。 */
@Composable
private fun rememberDGlassHighlight(): Highlight =
    if (DColor.Bg.luminance() < 0.5f) Highlight.GlassStrokeMiddleDark
    else Highlight.GlassStrokeMiddleLight

/**
 * 玻璃面板（登录页表单卡等 Column 内容场景），显式传入背景记录器。
 *
 * @param backdrop [rememberDGlassBackdrop] 返回的背景记录器
 * @param tint    极薄中和层：只统一底色，通透感来自上面的光学配方
 * @param blurRadius 模糊半径（dp），库内部转像素，官方悬浮栏 20 / 弹层 60
 */
@Composable
fun DGlassPanel(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = Color.White.copy(alpha = 0.22f),
    blurRadius: Float = 24f,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isGlassSupported) {
        Box(
            modifier
                .clip(shape)
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = blurRadius,
                    colors = rememberDGlassOptics(),
                    highlight = rememberDGlassHighlight(),
                )
                .background(tint),
        ) {
            androidx.compose.foundation.layout.Column(content = content)
        }
    } else {
        Box(
            modifier
                .clip(shape)
                .background(DColor.Card)
                .border(1.dp, DColor.Line, shape),
        ) {
            androidx.compose.foundation.layout.Column(content = content)
        }
    }
}

/**
 * 通用玻璃面（任意内容、跟随主题的自动退化）。
 *
 * 与 [DGlassPanel] 的差别：不需要显式传 backdrop——从 [LocalDGlassBackdrop] 取
 * 当前骨架记录的页面背景；取不到（或设备不支持）就退化为 [fallbackColor] 实体面，
 * 布局与内容零差异。浮层组件（结算栏 / 底部 Tab / Toast）统一走这里。
 *
 * tint 默认只有 10%：通透感来自 [rememberDGlassOptics] 的光学配方与边缘光，
 * tint 只是极薄的中和层——给高了就回到奶白磨砂板。
 */
@Composable
fun DGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = DColor.Card.copy(alpha = 0.10f),
    blurRadius: Float = 24f,
    fallbackColor: Color = DColor.Card,
    fallbackBorderColor: Color = DColor.Line,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalDGlassBackdrop.current
    if (isGlassSupported && backdrop != null) {
        Box(
            modifier
                .clip(shape)
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = blurRadius,
                    colors = rememberDGlassOptics(),
                    highlight = rememberDGlassHighlight(),
                )
                .background(tint),
            content = content,
        )
    } else {
        Box(
            modifier
                .clip(shape)
                .background(fallbackColor)
                .border(1.dp, fallbackBorderColor, shape),
            content = content,
        )
    }
}
