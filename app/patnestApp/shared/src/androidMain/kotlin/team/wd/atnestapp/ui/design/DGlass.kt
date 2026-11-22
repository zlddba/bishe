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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blendColors
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
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
    uniform float in_dispersion;  // 每通道折射偏移（降采样 px），0 = 关闭

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

    // 向白提亮而不裁剪：增益围绕亮度相关的支点，亮像素少动、暗像素多动
    // （官方 liftToward）——玻璃边缘因曲率聚光永远比中间亮，没有它边缘就发暗成黑框
    float3 liftToward(float3 color, float amount) {
        float v = lumaOf(color);
        float w = smoothstep(0.0, 0.5, v);
        float k = mix(1.0 - v, v, w);
        float gain = 1.0 + smoothstep(0.0, 1.0, amount) * mix(0.75, 0.4, w);
        return (color + k) * gain - k;
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
        // 官方 kMaxBevelSlope=1.7：闭式导数在轮廓处趋于数千，不封顶法线会立起来，
        // 折射在边缘产生极端采样——这就是外圈环带异常的来源之一
        float slope = min(edgeCurveSlope(depth, 2.0), 1.7);
        float3 normal = (shaped >= 1.0)
            ? float3(0.0, 0.0, 1.0)
            : normalize(float3(gradient * slope, 1.0));
        float2 refractUv = local;
        if (shaped < 1.0) {
            float3 bent = refract(float3(0.0, 0.0, -1.0), normal, 1.0 / max(in_glass.w, 1.0));
            refractUv += bent.xy * mix((in_glass.z - edge) * 2.0, in_glass.z * 2.0, shaped);
        }
        // 边缘色散：红/蓝通道沿法线反向偏移，绿通道居中。
        // 这是「液态玻璃」和普通磨砂最直观的区别；只在边缘带内、强度很低，
        // 避免整圈出现彩虹边。
        float4 col;
        if (shaped < 1.0 && in_dispersion > 0.01) {
            float2 dispersionOffset = gradient * (in_dispersion * (1.0 - shaped));
            float4 cr = sampleBackdrop(refractUv + dispersionOffset);
            float4 cg = sampleBackdrop(refractUv);
            float4 cb = sampleBackdrop(refractUv - dispersionOffset);
            col = float4(cr.r, cg.g, cb.b, cg.a);
        } else {
            col = sampleBackdrop(refractUv);
        }
        float l = lumaOf(col.rgb);
        col.rgb = mix(float3(l), col.rgb, in_satBri.x) + float3(in_satBri.y * col.a);
        // 固定方向的高光：法线在边缘立起来时才会点亮，中心平面几乎为零。
        // 比整圈 highlight 环带自然——只出现受光侧，不会在暗部镀一圈灰边。
        float3 lightDir = normalize(float3(-0.5, -0.8, 0.6));
        float spec = pow(max(dot(normal, lightDir), 0.0), 24.0);
        col.rgb += spec * mix(0.03, 0.10, 1.0 - shaped);
        // 底缘极淡的加性辉光：玻璃厚度在底部的受光感（官方 inner bottom）
        float bottom = smoothstep(1.0, 0.0, 1.0 - local.y / max(in_size.y, 1.0));
        col.rgb += bottom * bottom * 0.03;
        // 边缘聚光提亮：越贴边越亮（官方 rim lighting 的核心感知），
        // 真玻璃的边缘永远比中间亮——这是「液态」观感的关键，也杜绝暗边黑框
        col.rgb = liftToward(col.rgb, 0.5 * (1.0 - shaped));
        return col;
    }

    half4 main(float2 coord) {
        float2 local = coord - in_pad;
        float d = silhouetteSdf(local);
        // 轮廓在着色器内做 1.5px 平滑淡出：既不在降采样图层上硬切（那会放大成
        // 楼梯锯齿），也不让边缘材质溢出成暗环——淡出的同时边缘材质已被
        // liftToward 提亮，轮廓是亮边过渡而非黑边
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
        float edgeFade = clamp(1.0 - d / 1.5, 0.0, 1.0);
        float alpha = in_glass.x * edgeFade;
        return half4(half3(col.rgb * alpha), half(alpha));
    }
"""

private const val LIQUID_SHADER_KEY = "patnest_liquid_refraction_v4"

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
    highlight: Highlight?,
    surfaceAlpha: Float = 1f,
    edgeDp: Dp = 10.dp,
    dispersionDp: Dp = 0.8.dp,
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
                    with(density) { edgeDp.toPx() } / ds,
                    with(density) { 12.dp.toPx() } / ds,
                    1.45f,
                )
                setFloatUniform("in_satBri", 1.45f, 0.04f)
                setFloatUniform("in_dispersion", with(density) { dispersionDp.toPx() } / ds)
            }
        }
    },
    highlight = { highlight },
)

/**
 * 挂一层液态玻璃的底层修饰符（内部使用）。
 *
 * 与 [dGlassSurface] 的区别是只负责光学层，不带 tint / fallback，
 * 调用方自己决定玻璃上面叠什么颜色。所有 backdrop 都必须由调用方显式传入，
 * 避免再次出现「玻璃在记录层内采样自己」的首帧崩溃。
 */
internal fun Modifier.dLiquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    blurRadius: Float = 14f,
    cornerDp: Dp = 12.dp,
    surfaceAlpha: Float = 1f,
    refraction: Boolean = true,
    edgeDp: Dp = 10.dp,
    dispersionDp: Dp = 0.8.dp,
): Modifier = liquidGlassDraw(
    backdrop = backdrop,
    shape = shape,
    blurRadius = blurRadius,
    cornerDp = cornerDp,
    refraction = refraction,
    highlight = null,
    surfaceAlpha = surfaceAlpha,
    edgeDp = edgeDp,
    dispersionDp = dispersionDp,
)

/**
 * 卡片表面的反光（镜面高光）。
 *
 * 画在玻璃底色之上、内容之下：
 * - 左上角一团很柔的白色 bloom，模拟光源在玻璃左上；
 * - 一条 -16° 的斜向反光带，模拟整块玻璃的镜面反射；
 * - 顶部一条 1px 亮边，圆角处由外层 clip 收掉。
 *
 * [strength] 控制强度；深色模式下也保留，玻璃在暗底上的反光本来就更明显。
 */
internal fun Modifier.dGlassSheen(
    strength: Float = 1f,
    topEdge: Boolean = true,
): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind

    // 左上柔光：半径取长边，边缘用同色 0 alpha，避免灰圈
    val bloomCenter = Offset(w * 0.16f, h * 0.02f)
    val bloomRadius = max(w, h) * 0.86f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                // 近白卡上纯白高光几乎不可见，bloom 用极淡的品牌粉
                Color(0xFFFFF2F7).copy(alpha = 0.22f * strength),
                Color(0xFFFFF2F7).copy(alpha = 0.07f * strength),
                Color(0xFFFFF2F7).copy(alpha = 0f),
            ),
            center = bloomCenter,
            radius = bloomRadius,
        ),
        radius = bloomRadius,
        center = bloomCenter,
    )

    // 斜向反光带：只在卡片上半部扫过，不压暗文字区域
    rotate(degrees = -16f, pivot = Offset(w * 0.5f, h * 0.5f)) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    // 白卡上的反光必须有极淡的品牌粉，否则纯白高光在近白卡上
                    // 几乎不可见；中间保留一段白色作为镜面核心
                    Color(0xFFFFDCE8).copy(alpha = 0f),
                    Color(0xFFFFDCE8).copy(alpha = 0.26f * strength),
                    Color.White.copy(alpha = 0.10f * strength),
                    Color(0xFFFFDCE8).copy(alpha = 0f),
                ),
                start = Offset(0f, h * 0.08f),
                end = Offset(w, h * 0.30f),
            ),
            topLeft = Offset(-w * 0.10f, h * 0.04f),
            size = Size(w * 1.20f, h * 0.22f),
        )
    }

    if (topEdge) {
        drawLine(
            color = Color(0xFFFFE8F0).copy(alpha = 0.68f * strength),
            start = Offset(w * 0.08f, 0.5f),
            end = Offset(w * 0.92f, 0.5f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/**
 * 透明浮层上的文字阴影：浅色主题用白色光晕把深色文字从地图/图片上「托」起来，
 * 深色主题用黑色阴影托浅色文字。透明导航栏、地图信息栏共用。
 */
@Composable
fun dGlassTextShadow(): Shadow {
    val dark = DColor.Bg.luminance() < 0.5f
    return if (dark) {
        Shadow(color = Color.Black.copy(alpha = 0.70f), blurRadius = 8f)
    } else {
        Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 8f)
    }
}

/**
 * 内容流玻璃的标准入口：卡片、输入框、搜索条、chip 这类嵌在页面里的表面。
 *
 * 默认采样 [LocalDGlassAmbient]（窗口底层的装饰背景），而不是页面自己的
 * [LocalDGlassBackdrop]——后者包含当前内容层，嵌在里面的玻璃采样它就是
 * GPU 反馈环路，Adreno + MIUI 会直接原生段错误。内容玻璃永远只采环境层。
 *
 * 支持玻璃时画光学层 + [tint]；不支持（API < 33 / 没有环境记录）时
 * 退化为 [fallbackColor] 实体面。两种分支的布局完全一致。
 */
@Composable
fun Modifier.dGlassSurface(
    backdrop: LayerBackdrop? = LocalDGlassAmbient.current,
    shape: Shape = DShape.Lg,
    tint: Color = DColor.Card.copy(alpha = 0.70f),
    blurRadius: Float = 16f,
    cornerDp: Dp = 12.dp,
    surfaceAlpha: Float = 1f,
    refraction: Boolean = true,
    // 小控件（chip / 小按钮 / 28dp 圆钮）必须收窄折射带，否则上下两条
    // 边缘折射会互相覆盖，整块看起来像糊了一层白雾
    edgeDp: Dp = 10.dp,
    // 卡片类表面可开反光；小控件默认关，避免高光压过内容
    sheen: Boolean = false,
    fallbackColor: Color = DColor.Card,
): Modifier {
    val sheenModifier = if (sheen) Modifier.dGlassSheen(strength = 0.85f) else Modifier
    if (!isGlassSupported || backdrop == null) {
        return this.clip(shape).background(fallbackColor).then(sheenModifier)
    }
    return this
        .clip(shape)
        .dLiquidGlass(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            cornerDp = cornerDp,
            surfaceAlpha = surfaceAlpha,
            refraction = refraction,
            edgeDp = edgeDp,
        )
        .background(tint)
        .then(sheenModifier)
}

/**
 * 品牌渐变 hero 头的玻璃版本。
 *
 * hero 位于滚动内容里，绝不能采样页面记录层；这里固定走环境玻璃，
 * 上面再叠 68% 品牌渐变——玻璃负责折射与边缘光，渐变负责品牌色与白字可读性。
 *
 * 不支持玻璃时就是原来的实色品牌渐变，布局零差异。
 *
 * @param shape 与 hero 实际裁切形状一致（我的页矩形 / 积分页底部大圆角）
 * @param cornerDp 折射 SDF 的圆角；[shape] 的圆角与它不必逐角相同，
 *   SDF 只影响边缘折射带，最终仍由 [shape] 裁切
 */
@Composable
fun Modifier.dGlassHero(
    shape: Shape = RectangleShape,
    cornerDp: Dp = 0.dp,
    // 76% 品牌渐变：低于这个值时，玻璃把环境光提亮后白字对比度会不够
    gradientAlpha: Float = 0.76f,
    edgeDp: Dp = 12.dp,
): Modifier {
    val ambient = LocalDGlassAmbient.current
    // hero 顶部压一层极淡的暗色渐变：白字直接落在品牌亮粉上时对比度不够，
    // 这层 scrim 只影响标题区，不改变玻璃的折射与边缘
    val textScrim = Brush.verticalGradient(
        listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent),
    )
    if (!isGlassSupported || ambient == null) {
        return this.clip(shape)
            .background(DColor.HeroGradient)
            .dGlassSheen(strength = 0.65f)
            .background(textScrim)
    }
    return this
        .clip(shape)
        .dLiquidGlass(
            backdrop = ambient,
            shape = shape,
            blurRadius = 20f,
            cornerDp = cornerDp,
            edgeDp = edgeDp,
        )
        .background(DColor.HeroGradient, alpha = gradientAlpha)
        .dGlassSheen(strength = 0.65f)
        .background(textScrim)
}

/**
 * 任意渐变面板的玻璃版本：钱包卡、订单机构头、审核说明这类「已经有品牌渐变、
 * 但还想要玻璃折射」的表面。
 *
 * 与 [dGlassHero] 的区别是渐变由调用方传入（颜色可能按身份 / 状态变化），
 * 玻璃仍然只采环境层，因此一样可以安全地放在滚动内容里。
 */
@Composable
fun Modifier.dGlassBrush(
    brush: Brush,
    alpha: Float = 0.86f,
    shape: Shape = DShape.Lg,
    cornerDp: Dp = 12.dp,
    blurRadius: Float = 18f,
    edgeDp: Dp = 12.dp,
    sheen: Boolean = true,
): Modifier {
    val ambient = LocalDGlassAmbient.current
    val sheenModifier = if (sheen) Modifier.dGlassSheen(strength = 0.8f) else Modifier
    if (!isGlassSupported || ambient == null) {
        return this.clip(shape).background(brush).then(sheenModifier)
    }
    return this
        .clip(shape)
        .dLiquidGlass(
            backdrop = ambient,
            shape = shape,
            blurRadius = blurRadius,
            cornerDp = cornerDp,
            edgeDp = edgeDp,
        )
        .background(brush, alpha = alpha)
        .then(sheenModifier)
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
    // 登录表单卡上有 label / 输入框 / 按钮，tint 不能再按「纯装饰玻璃」的 10%；
    // 48% 主题卡底色保证浅色/深色下正文都清楚，同时保留玻璃边缘
    tint: Color = DColor.Card.copy(alpha = 0.68f),
    blurRadius: Float = 12f,
    cornerDp: Dp = 12.dp,
    refraction: Boolean = true,
    edgeDp: Dp = 10.dp,
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
                    highlight = null,
                    edgeDp = edgeDp,
                )
                .background(tint)
                .dGlassSheen(strength = 0.85f),
        ) {
            androidx.compose.foundation.layout.Column(content = content)
        }
    } else {
        Box(
            modifier
                .clip(shape)
                .background(DColor.Card)
                .dGlassSheen(strength = 0.55f)
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
    // 默认值按「有文字」处理：3% 的极限通透只适合纯装饰面，
    // 任何忘记传 tint 的文本表面都会变得不可读
    tint: Color = DColor.Card.copy(alpha = 0.70f),
    blurRadius: Float = 12f,
    cornerDp: Dp = 12.dp,
    refraction: Boolean = true,
    // 玻璃面自身不透明度：1 = 完全由模糊背景构成（页面玻璃）；
    // <1 时让真实背景直接透出一部分——用于采样内容贫瘠的表面（如地图 SDK 模式），
    // 模糊层给质感、透出的真实内容给细节，两种信息源互补
    surfaceAlpha: Float = 1f,
    edgeDp: Dp = 10.dp,
    // 卡片类表面传 true：左上 bloom + 斜向反光；浮层/小圆钮保持 false
    sheen: Boolean = false,
    fallbackColor: Color = DColor.Card,
    fallbackBorderColor: Color = DColor.Line,
    // 默认采样页面级记录（浮层：顶栏 / 底栏 / 结算栏 / Toast）；
    // 内容流里的玻璃必须显式传 LocalDGlassAmbient，不能沿用默认值，
    // 否则又会在记录层内部采样自己。见 dGlassSurface 的注释。
    backdrop: LayerBackdrop? = LocalDGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) {
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
                    highlight = null,
                    surfaceAlpha = surfaceAlpha,
                    edgeDp = edgeDp,
                )
                .background(tint)
                .then(if (sheen) Modifier.dGlassSheen(strength = 0.85f) else Modifier),
            content = content,
        )
    } else {
        Box(
            modifier
                .clip(shape)
                .background(fallbackColor)
                .then(if (sheen) Modifier.dGlassSheen(strength = 0.55f) else Modifier)
                .border(1.dp, fallbackBorderColor, shape),
            content = content,
        )
    }
}
