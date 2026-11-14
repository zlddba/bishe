package team.wd.atnestapp.ui.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/**
 * 玻璃质感（基于已发布的 miuix-blur 0.9.4，官方未发布的 miuix-glass 不引入）。
 *
 * 架构与官方 glass.md 一致的两层模型：
 * 1. [rememberDGlassBackdrop] + [dGlassBackdropSource] —— 把页面背景（渐变 hero、
 *    内容底）记录为可被模糊的图层；
 * 2. [DGlassPanel] —— 玻璃面板，真实模糊「它后面被记录的那层」，再叠品牌色调与亮边描边。
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
 * 所有 [DGlassPanel] 共享它，模糊的都是同一份背景。
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
 * 玻璃面板。
 *
 * @param backdrop [rememberDGlassBackdrop] 返回的背景记录器
 * @param tint    面板色调：叠在模糊结果上的半透明色，白色系压在品牌渐变上呈浅粉玻璃
 * @param blurRadius 模糊半径（px），24 起步才有明显「磨砂」感
 */
@Composable
fun DGlassPanel(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = Color.White.copy(alpha = 0.40f),
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
                )
                .background(tint)
                .border(1.dp, Color.White.copy(alpha = 0.35f), shape),
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
