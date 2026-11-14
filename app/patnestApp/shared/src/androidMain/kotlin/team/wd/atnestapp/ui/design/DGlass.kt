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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blendColors
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.noiseDither
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/**
 * 液态玻璃（基于已发布的 miuix-blur 0.9.4，官方未发布的 miuix-glass 不引入）。
 *
 * 与普通「磨砂」的本质区别是**边缘折射**：真实玻璃不是均匀介质，光线穿过边缘时
 * 会向内弯折。折射着色器按官方 miuix-glass 的 GLASS_SHADER 思路实现
 * （Apache-2.0， portions derived from compose-miuix-ui/miuix）：
 *
 * 1. 圆角矩形 SDF 给出像素到边缘的有符号距离；`edgeCurve`（五次光滑曲线）
 *    定义边缘→平坦的深度剖面，`edgeCurveSlope` 闭式求导得到真实的表面法线
 *    （逐像素差分会把斜率读错数倍——官方注释原话）；
 * 2. 用 GLSL 内建 `refract()` 按斯涅尔定律弯折视线（ior 玻璃折射率），
 *    折射量随玻璃厚度与边缘深度插值——这是「液」的物理来源；
 * 3. 采样出界（alpha<0.02）时回退到表面内的最近采样点，防止透明黑在边缘
 *    镀出黑环（官方 sampleBackdrop 的防脏环处理）；
 * 4. 边缘带内沿梯度 3 tap（0.25/0.5/0.25）积分，抗锯齿。
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

/**
 * 液态折射 AGSL 着色器——官方 GLASS_SHADER 的简化忠实移植。
 * 保留：SDF 轮廓、闭式导数法线、物理折射、透明黑回退、边缘积分、底部微辉光。
 * 省略：镜像反射、Vogel 螺旋宽采样、亮度曲线（边缘光交给 highlight 参数，
 * 色调交给 colorControls/blendColors 效果）。
 * 所有像素空间 uniform 都是「降采样后图层」坐标，块内必须除以 downscaleFactor
 * （官方 runtimeShaderEffect 文档的硬性要求，否则采样全部落在图层外）。
 */
private const val LIQUID_REFRACTION_AGSL = """
    uniform shader child;
    uniform float2 in_size;       // 组件尺寸（降采样 px）
    uniform float2 in_pad;        // 组件在含 blur 出血图层内的原点（降采样 px，对称 padding）
    uniform float2 in_maxCoord;   // 图层采样上限（降采样 px）
    uniform float in_radius;      // 圆角半径（降采样 px）
    uniform float4 in_glass;      // alpha, 折射带宽, 玻璃厚度, 折射率
    uniform float2 in_satBri;     // 饱和度倍数, 亮度增量

    const float3 kLumaW = float3(0.2126, 0.7152, 0.0722);

    float lumaOf(float3 c) { return dot(c, kLumaW); }

    // 圆角矩形 SDF：负值在形状内（官方 roundedBoxSdf）
    float roundedBoxSdf(float2 p, float2 b, float r) {
        float2 d = p - b + r;
        return min(max(d.x, d.y), 0.0) + length(max(d, float2(0.0))) - r;
    }

    float silhouetteSdf(float2 local) {
        float2 b = in_size * 0.5;
        return roundedBoxSdf(abs(local - b), b, min(in_radius, min(b.x, b.y)));
    }

    // 五次光滑边缘剖面：0 在轮廓上，1 处表面平坦（官方 edgeCurve / edgeCurveSlope）
    float smooth5Map(float t) {
        t = clamp(mix(0.5, 1.0, t), 0.0, 1.0);
        t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        return (t - 0.5) * 2.0;
    }

    float edgeCurve(float x, float p) {
        if (x >= 0.85) return 1.0;
        float c = smooth5Map(sqrt(clamp(x, 0.0, 1.0)));
        return 1.0 - pow(1.0 - c, p);
    }

    // 剖面的闭式导数：逐像素差分会把斜率读错数倍，法线必须用解析值（官方注释）
    float edgeCurveSlope(float x, float p) {
        if (x >= 0.85) return 0.0;
        float t = sqrt(clamp(x, 0.000001, 1.0));
        float u = 0.5 + 0.5 * t;
        float um = u - 1.0;
        float c = smooth5Map(t);
        float slope = 15.0 * u * u * um * um / t;
        return p * pow(max(1.0 - c, 0.0), p - 1.0) * slope;
    }

    // SDF 梯度方向（指向形状外），半像素中心差分在局部平面场上是精确的（官方 sdfGradient）
    float2 sdfGradient(float2 local) {
        float dx = silhouetteSdf(local + float2(0.5, 0.0)) - silhouetteSdf(local - float2(0.5, 0.0));
        float dy = silhouetteSdf(local + float2(0.0, 0.5)) - silhouetteSdf(local - float2(0.0, 0.5));
        float2 g = float2(dx, dy);
        float l = length(g);
        return (l < 0.0001) ? float2(0.0) : g / l;
    }

    float4 tapLayer(float2 local) {
        return child.eval(clamp(local + in_pad, float2(0.5), in_maxCoord));
    }

    // 折射光线可能伸出记录范围；出界（透明黑）回退到表面内最近点，防黑环（官方 sampleBackdrop）
    float4 sampleBackdrop(float2 local) {
        float4 col = tapLayer(local);
        if (col.a < 0.02) col = tapLayer(clamp(local, float2(0.5), in_size - 0.5));
        if (col.a > 0.004) col = float4(col.rgb / col.a, 1.0);
        return col;
    }

    // 单点材质：法线 → 物理折射 → 采样 → 色彩分级（官方 shadeMaterial 简化版）
    float4 shade(float2 local, float d, float2 gradient, float edge) {
        float depth = clamp(-d / edge, 0.0, 1.0);
        float shaped = edgeCurve(depth, 2.0);
        float3 normal = (shaped >= 1.0)
            ? float3(0.0, 0.0, 1.0)
            : normalize(float3(gradient * edgeCurveSlope(depth, 2.0), 1.0));
        float2 refractUv = local;
        if (shaped < 1.0) {
            float3 bent = refract(float3(0.0, 0.0, -1.0), normal, 1.0 / max(in_glass.w, 1.0));
            refractUv += bent.xy * mix((in_glass.z - edge) * 2.0, in_glass.z * 2.0, shaped);
        }
        float4 col = sampleBackdrop(refractUv);
        float l = lumaOf(col.rgb);
        col.rgb = mix(float3(l), col.rgb, in_satBri.x) + float3(in_satBri.y * col.a);
        // 底缘极淡的加性辉光：玻璃厚度在底部的受光感（官方 inner bottom）
        float bottom = smoothstep(1.0, 0.0, 1.0 - local.y / max(in_size.y, 1.0));
        col.rgb += bottom * bottom * 0.03;
        return col;
    }

    // 轮廓外溢：着色器只在远超轮廓处才切透明；真正的边缘由全分辨率
    // 抗锯齿裁切收边。若在这里按 d>0 硬切，切口发生在降采样图层上，
    // 放大回屏幕就是一圈楼梯锯齿（官方注释：the mask pass trims the edge
    // at full resolution, so this one only has to reach past it）。
    const float kOverspill = 4.0;

    half4 main(float2 coord) {
        float2 local = coord - in_pad;
        float d = silhouetteSdf(local);
        if (d >= kOverspill) return half4(0.0);
        float edge = max(in_glass.y, 1.0);
        float2 gradient = sdfGradient(local);
        float4 col;
        // 边缘带内沿梯度 3 tap 积分：受光弧只有几个显示像素宽，
        // 单采样会锯齿成楼梯（官方的抗锯齿积分）
        if (-d < edge && length(gradient) > 0.5) {
            col = shade(local - gradient * 0.375, d - 0.375, gradient, edge) * 0.25
                + shade(local, d, gradient, edge) * 0.5
                + shade(local + gradient * 0.375, d + 0.375, gradient, edge) * 0.25;
        } else {
            col = shade(local, d, gradient, edge);
        }
        float alpha = in_glass.x;
        return half4(half3(col.rgb * alpha), half(alpha));
    }
"""

private const val LIQUID_SHADER_KEY = "patnest_liquid_refraction_v2"

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
 * 液态玻璃效果链（官方 textureBlurEffect 顺序 + 折射）：
 * noiseDither → colorControls(提饱和提亮) → blur → blendColors(Screen/Overlay) → 折射着色器。
 * 抽出来让 [DGlassPanel] 和 [DGlassSurface] 共用同一套光学。
 */
private fun Modifier.liquidGlassDraw(
    backdrop: LayerBackdrop,
    shape: Shape,
    blurRadius: Float,
    cornerDp: Dp,
    refraction: Boolean,
    highlight: Highlight,
    surfaceAlpha: Float = 1f,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        noiseDither(BlurDefaults.NoiseCoefficient)
        colorControls(brightness = 0.04f, contrast = 1.08f, saturation = 1.45f)
        blur(with(density) { blurRadius.dp.toPx() })
        blendColors(
            BlurColors(
                blendColors = listOf(
                    // Screen 混合：提亮但不遮底（对比 SrcOver 的奶白效果）
                    BlendColorEntry(Color.White.copy(alpha = 0.06f), BlurBlendMode.Screen),
                    // Overlay 压暗：模糊内容过曝时保住对比
                    BlendColorEntry(Color.Black.copy(alpha = 0.03f), BlurBlendMode.Overlay),
                ),
            ),
        )
        if (refraction) {
            runtimeShaderEffect(
                key = LIQUID_SHADER_KEY,
                shaderString = LIQUID_REFRACTION_AGSL,
                uniformShaderName = "child",
            ) {
                // 官方硬性要求：像素空间 uniform 必须除以 downscaleFactor，
                // 折射几何才会落在（降采样后的）图层范围内
                val ds = downscaleFactor.toFloat()
                setFloatUniform("in_size", size.width / ds, size.height / ds)
                setFloatUniform("in_pad", padding / ds, padding / ds)
                setFloatUniform(
                    "in_maxCoord",
                    (size.width + padding * 2f) / ds - 0.5f,
                    (size.height + padding * 2f) / ds - 0.5f,
                )
                setFloatUniform("in_radius", with(density) { cornerDp.toPx() } / ds)
                setFloatUniform(
                    "in_glass",
                    surfaceAlpha,
                    with(density) { 10.dp.toPx() } / ds,
                    with(density) { 12.dp.toPx() } / ds,
                    1.45f,
                )
                setFloatUniform("in_satBri", 1.45f, 0.04f)
            }
        }
    },
    highlight = { highlight },
)

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
    tint: Color = Color.White.copy(alpha = 0.10f),
    blurRadius: Float = 12f,
    cornerDp: Dp = 12.dp,
    refraction: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isGlassSupported) {
        Box(
            modifier
                .clip(shape)
                .liquidGlassDraw(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = blurRadius,
                    cornerDp = cornerDp,
                    refraction = refraction,
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
 * 通用液态玻璃面（任意内容、跟随主题的自动退化）。
 *
 * 与 [DGlassPanel] 的差别：不需要显式传 backdrop——从 [LocalDGlassBackdrop] 取
 * 当前骨架记录的页面背景；取不到（或设备不支持）就退化为 [fallbackColor] 实体面，
 * 布局与内容零差异。浮层组件（结算栏 / 底部 Tab / Toast）统一走这里。
 *
 * tint 默认只有 10%：通透感来自光学配方、边缘光与液态折射，tint 只是极薄的中和层。
 *
 * @param cornerDp 折射 SDF 的圆角半径，需与 shape 的实际圆角一致（胶囊/圆形传半径）
 */
@Composable
fun DGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DShape.Lg,
    tint: Color = DColor.Card.copy(alpha = 0.03f),
    blurRadius: Float = 12f,
    cornerDp: Dp = 12.dp,
    refraction: Boolean = true,
    // 玻璃面自身不透明度：1 = 完全由模糊背景构成（页面玻璃）；
    // <1 时让真实背景直接透出一部分——用于采样内容贫瘠的表面（如地图 SDK 模式），
    // 模糊层给质感、透出的真实内容给细节，两种信息源互补
    surfaceAlpha: Float = 1f,
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
                    surfaceAlpha = surfaceAlpha,
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
