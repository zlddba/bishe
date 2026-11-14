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
 * 2. [DGlassPanel] / [DGlassSurface] —— 玻璃面板，真实模糊「它后面被记录的那层」，
 *    再叠品牌色调与亮边描边。
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
 * 骨架层提供的页面背景记录器。
 *
 * - 骨架（DMiuixScrollScaffold / ListScaffold）把整页内容记录进它，
 *   并 provide 给自己的内容与底部浮层——[DFixBar]、[DBottomTabs] 读它做真实模糊；
 * - [DesignApp] 在根部 provide 一份全窗口的——[DToastHost] 这类根级浮层读它；
 * - 页内骨架 provide 的会遮蔽根部那份，各浮层总是模糊「自己正下方的页面」。
 *
 * 没有记录器（预览、未接入骨架的调用方）时玻璃自动退化为实体卡。
 */
val LocalDGlassBackdrop: ProvidableCompositionLocal<LayerBackdrop?> =
    staticCompositionLocalOf { null }

/**
 * 玻璃面板（登录页表单卡等 Column 内容场景）。
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

/**
 * 通用玻璃面（任意内容、跟随主题的自动退化）。
 *
 * 与 [DGlassPanel] 的差别：不需要显式传 backdrop——从 [LocalDGlassBackdrop] 取
 * 当前骨架记录的页面背景；取不到（或设备不支持）就退化为 [fallbackColor] 实体面，
 * 布局与内容零差异。浮层组件（结算栏 / 底部 Tab / Toast）统一走这里。
 *
 * tint 用 DColor.Card.copy(alpha=…) 这类跟随主题的色，深色模式下不会发灰发白。
 */
@Composable
fun DGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = DColor.Card.copy(alpha = 0.55f),
    blurRadius: Float = 24f,
    border: Color? = Color.White.copy(alpha = 0.30f),
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
                )
                .background(tint)
                .then(
                    if (border != null) Modifier.border(1.dp, border, shape) else Modifier,
                ),
            content = content,
        )
    } else {
        Box(
            modifier
                .clip(shape)
                .background(fallbackColor)
                .then(
                    if (border != null) Modifier.border(1.dp, fallbackBorderColor, shape)
                    else Modifier,
                ),
            content = content,
        )
    }
}
