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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 液态玻璃（基于已发布的 miuix-blur 0.9.4，官方未发布的 miuix-glass 不引入）。
 *
 * 与普通「磨砂」的本质区别是**边缘折射**：真实玻璃不是均匀介质，
 * 光线穿过边缘时会向内弯折，把边缘外的内容「压」进边框里，
 * 红蓝两色折射率不同还会产生极细的色散描边——这就是液态玻璃的「液」。
 *
 * 实现走 miuix-blur 的底层效果链（官方 blur 指南的进阶用法）：
 * `drawBackdrop(effects = { blur → colorControls → blendColors → 折射着色器 })`。
 * 折射是一段自定义 AGSL 着色器：圆角矩形 SDF 求出每个像素到边缘的距离，
 * 越靠边采样坐标越向外偏移（透镜压缩），同时红蓝通道反向微量偏移（色散）。
 *
 * 全链路依赖 Android 13（API 33）RuntimeShader：
 * - Android 13+：完整液态玻璃；
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

/** 按主题选官方边缘光预设（上半受光、下半衰减，模拟玻璃折光）。 */
@Composable
private fun rememberDGlassHighlight(): Highlight =
    if (DColor.Bg.luminance() < 0.5f) Highlight.GlassStrokeMiddleDark
    else Highlight.GlassStrokeMiddleLight

/** 矮表面（结算栏 / Tab / 输入栏）用细光晕：Middle 光晕在矮面上会盖住大半高度。 */
@Composable
private fun rememberDGlassBarHighlight(): Highlight =
    if (DColor.Bg.luminance() < 0.5f) Highlight.GlassStrokeSmallDark
    else Highlight.GlassStrokeSmallLight

/**
 * 液态折射 AGSL 着色器。
 *
 * 圆角矩形 SDF 给出每个像素到边缘的有符号距离：越靠边（距离趋近 0），
 * 采样坐标越沿「向外」方向偏移——等于把边缘外一小圈内容压进来，
 * 呈现透镜般的内缩折射；红蓝通道再做一次反向微量偏移产生色散。
 * 所有 uniform 都是「降采样后图层」的像素空间，块内要除以 downscaleFactor
 * （官方 runtimeShaderEffect 文档的明确要求，否则采样全部落在图层外）。
 */
private const val LIQUID_REFRACTION_AGSL = """
    uniform shader inputShader;
    uniform float2 uSize;      // 表面尺寸（降采样 px）
    uniform float uRadius;     // 圆角半径（降采样 px）
    uniform float uBand;       // 折射带宽（降采样 px）
    uniform float uAmount;     // 边缘最大位移（降采样 px）
    uniform float uChroma;     // 色散位移（降采样 px）

    half4 main(float2 fragCoord) {
        float2 halfSize = uSize * 0.5;
        float2 p = fragCoord - halfSize;
        float2 q = abs(p) - (halfSize - float2(uRadius));
        float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
        float inside = -sd;                              // >0 在形状内
        float t = clamp(inside / uBand, 0.0, 1.0);       // 1=深处 0=贴边
        float disp = (1.0 - t) * uAmount;
        float2 dir = p / (length(p) + 0.0001);           // 向外的方向
        float2 lo = float2(0.0);
        float2 hi = uSize - float2(1.0);
        float2 base = clamp(fragCoord + dir * disp, lo, hi);
        half4 c = inputShader.eval(base);
        if (uChroma > 0.5 && disp > 0.0) {
            float2 ca = dir * disp * 0.35;
            half4 cr = inputShader.eval(clamp(base + ca, lo, hi));
            half4 cb = inputShader.eval(clamp(base - ca, lo, hi));
            c = half4(cr.r, c.g, cb.b, c.a);
        }
        return c;
    }
"""

/**
 * 液态玻璃效果链：模糊 → 光学配方 → 色彩混合 → 折射着色器。
 * 抽出来让 [DGlassPanel] 和 [DGlassSurface] 共用同一套光学。
 */
@Composable
private fun Modifier.liquidGlassDraw(
    backdrop: LayerBackdrop,
    shape: Shape,
    blurRadius: Float,
    cornerDp: Dp,
    refraction: Boolean,
    highlight: Highlight,
): Modifier {
    val density = LocalDensity.current
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            noiseCoefficient = BlurDefaults.NoiseCoefficient
            blur(with(density) { blurRadius.dp.toPx() })
            colorControls(brightness = 0.04f, contrast = 1.08f, saturation = 1.45f)
            blendColors(
                BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(Color.White.copy(alpha = 0.16f), BlurBlendMode.Screen),
                        BlendColorEntry(Color.Black.copy(alpha = 0.06f), BlurBlendMode.Overlay),
                    ),
                ),
            )
            if (refraction) {
                runtimeShaderEffect(
                    key = "patnest_liquid_refraction",
                    shaderString = LIQUID_REFRACTION_AGSL,
                    uniformShaderName = "inputShader",
                ) {
                    // 官方要求：像素空间 uniform 必须除以 downscaleFactor，
                    // 折射几何才会落在（降采样后的）图层范围内
                    val ds = downscaleFactor.toFloat()
                    setFloatUniform("uSize", size.width / ds, size.height / ds)
                    setFloatUniform("uRadius", with(density) { cornerDp.toPx() } / ds)
                    setFloatUniform("uBand", with(density) { 16.dp.toPx() } / ds)
                    setFloatUniform("uAmount", with(density) { 14.dp.toPx() } / ds)
                    setFloatUniform("uChroma", with(density) { 2.5f.dp.toPx() } / ds)
                }
            }
        },
        highlight = { highlight },
    )
}

/**
 * 玻璃面板（登录页表单卡等 Column 内容场景），显式传入背景记录器。
 *
 * @param backdrop [rememberDGlassBackdrop] 返回的背景记录器
 * @param tint    极薄中和层：只统一底色，通透感来自光学配方与折射
 * @param blurRadius 模糊半径（dp），官方悬浮栏 20 / 弹层 60
 * @param cornerDp 圆角半径（dp），折射 SDF 用，与传入 shape 的圆角保持一致
 * @param refraction 是否启用液态折射（大面积卡建议开，视觉最「液」）
 */
@Composable
fun DGlassPanel(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = Color.White.copy(alpha = 0.22f),
    blurRadius: Float = 24f,
    cornerDp: Dp = 12.dp,
    refraction: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isGlassSupported) {
        Box(
            modifier
                .clip(shape)
                .liquidGlassDraw(backdrop, shape, blurRadius, cornerDp, refraction, rememberDGlassHighlight())
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
 * 通用液态玻璃面（任意内容、跟随主题的自动退化）。
 *
 * 与 [DGlassPanel] 的差别：不需要显式传 backdrop——从 [LocalDGlassBackdrop] 取
 * 当前骨架记录的页面背景；取不到（或设备不支持）就退化为 [fallbackColor] 实体面，
 * 布局与内容零差异。浮层组件（结算栏 / 底部 Tab / Toast）统一走这里。
 *
 * tint 默认只有 10%：通透感来自光学配方、边缘光与液态折射，tint 只是极薄的中和层。
 * 矮表面（条状浮层）折射带宽 16dp 也只占小半高度，不会糊成一团。
 *
 * @param cornerDp 折射 SDF 的圆角半径，需与 shape 的实际圆角一致（胶囊/圆形传半径）
 */
@Composable
fun DGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = DColor.Card.copy(alpha = 0.10f),
    blurRadius: Float = 24f,
    cornerDp: Dp = 12.dp,
    refraction: Boolean = true,
    fallbackColor: Color = DColor.Card,
    fallbackBorderColor: Color = DColor.Line,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalDGlassBackdrop.current
    if (isGlassSupported && backdrop != null) {
        Box(
            modifier
                .clip(shape)
                .liquidGlassDraw(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = blurRadius,
                    cornerDp = cornerDp,
                    refraction = refraction,
                    highlight = rememberDGlassBarHighlight(),
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
