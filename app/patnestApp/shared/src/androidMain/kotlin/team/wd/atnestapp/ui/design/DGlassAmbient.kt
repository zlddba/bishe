package team.wd.atnestapp.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 全局环境玻璃底色。
 *
 * 内容流里的玻璃（卡片、输入框、hero 头）不能采样「页面自己所在的记录层」——
 * 上一版 dGlassPanelStyle 就是采样了正在绘制的图层，Adreno + MIUI 直接首帧 SIGSEGV。
 *
 * 这里在窗口最底层单独记录一层**装饰性背景**（品牌光斑 + 斜向光带），
 * 所有内容玻璃只采样这一层：它是页面内容的兄弟节点，永远不包含玻璃自身，
 * 因此不存在 GPU 反馈环路，也不受页面滚动影响。
 *
 * 代价是内容玻璃不模糊「正后方的文字/图片」，只折射这层环境光——
 * 对卡片、输入框这类需要保持文字可读性的表面反而更稳定；真正需要
 * 模糊实时页面内容的浮层（顶栏 / 底栏 / 结算栏 / Toast）继续走
 * [LocalDGlassBackdrop] 的页面级记录。
 */
val LocalDGlassAmbient: ProvidableCompositionLocal<LayerBackdrop?> =
    staticCompositionLocalOf { null }

/**
 * 被 [LocalDGlassAmbient] 记录的装饰背景。
 *
 * 用大半径径向渐变做柔光斑：玻璃的折射着色器需要边缘有可采样的色彩变化，
 * 纯色底折射出来是「一块死的白板」，看不出液态感。
 * 斜向光带提供一点高频细节，让边缘的透镜位移在视觉上能被读出来。
 */
@Composable
fun DGlassAmbientLayer(modifier: Modifier = Modifier) {
    val bg = DColor.Bg
    val brand = DColor.Brand
    val brandSoft = DColor.BrandSoft
    val brandStrong = DColor.BrandStrong
    val info = DColor.Info
    val white = DColor.White
    val dark = bg.luminance() < 0.5f

    Canvas(modifier.fillMaxSize()) {
        // 首帧/零尺寸容器下半径会是 0，radialGradient 不接受零半径
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        drawRect(bg)

        fun blob(color: Color, x: Float, y: Float, radiusRatio: Float, alpha: Float) {
            val center = Offset(size.width * x, size.height * y)
            val radius = size.width * radiusRatio
            drawCircle(
                brush = Brush.radialGradient(
                    // 用同色的 0 alpha 收边，不要用 Color.Transparent（透明黑）——
                    // 否则柔光边缘会混出一圈脏灰
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        // 左上品牌主光：首页 / 我的页 hero 正下方，最容易被玻璃采样到
        blob(brand, 0.10f, 0.08f, 1.05f, if (dark) 0.34f else 0.52f)
        // 右上粉白柔光：给顶栏和卡片右缘一点冷暖过渡
        blob(brandSoft, 0.92f, 0.04f, 0.72f, if (dark) 0.26f else 0.46f)
        // 屏幕中段偏右的白色高光：卡片的折射边缘主要靠它提亮
        blob(white, 0.88f, 0.48f, 0.78f, if (dark) 0.08f else 0.24f)
        // 左下深品牌色：和左上形成斜向明暗，玻璃厚度感更明显
        blob(brandStrong, 0.08f, 0.92f, 1.00f, if (dark) 0.30f else 0.34f)
        // 底部中间的冷色，避免整屏只有粉色
        blob(info, 0.55f, 1.05f, 0.82f, if (dark) 0.14f else 0.18f)

        // 两条低对比斜光带：给折射提供可读的线状细节
        rotate(degrees = -16f, pivot = center) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        white.copy(alpha = 0f),
                        white.copy(alpha = if (dark) 0.05f else 0.14f),
                        white.copy(alpha = 0f),
                    ),
                    start = Offset(0f, size.height * 0.22f),
                    end = Offset(size.width, size.height * 0.22f),
                ),
                topLeft = Offset(-size.width * 0.35f, size.height * 0.20f),
                size = Size(size.width * 1.70f, size.height * 0.055f),
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        brand.copy(alpha = 0f),
                        brand.copy(alpha = if (dark) 0.08f else 0.12f),
                        brand.copy(alpha = 0f),
                    ),
                    start = Offset(0f, size.height * 0.70f),
                    end = Offset(size.width, size.height * 0.70f),
                ),
                topLeft = Offset(-size.width * 0.35f, size.height * 0.66f),
                size = Size(size.width * 1.70f, size.height * 0.075f),
            )
        }
    }
}
