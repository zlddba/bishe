package team.wd.atnestapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 宠栖 PetNest 全局品牌令牌。
 * 品牌玫瑰粉色阶严格来自 docs/宠栖后台-UI配色设计图.html（500 主色 #E0708A），
 * 新视觉语言「现代柔和 · 有层次」只在中性色、圆角、阴影与排版上做增量。
 */
object PatNestColors {
    /* ---- 品牌玫瑰粉色阶（要求色板，勿改） ---- */
    val Brand50 = Color(0xFFFDF1F4)
    val BrandSoft = Color(0xFFFBE0E6)
    val Brand200 = Color(0xFFF6C2CE)
    val Brand300 = Color(0xFFEFA0B3)
    val Brand400 = Color(0xFFE87D97)
    val Brand = Color(0xFFE0708A)
    val Brand600 = Color(0xFFC85B74)
    val BrandInk = Color(0xFFAF4A62)
    val Brand800 = Color(0xFF8E3B4E)
    val Brand900 = Color(0xFF6F2A3B)

    /* ---- 中性与背景（暖灰粉基调） ---- */
    val Bg = Color(0xFFF7F4F5)
    val BgDeep = Color(0xFFF1EBED)
    val Panel = Color(0xFFFFFFFF)
    val PanelSoft = Color(0xFFFBF8F9)
    val Line = Color(0xFFEDE4E7)
    val Line2 = Color(0xFFF4ECEE)
    val Backdrop = Color(0xFFF1EBED)
    val BlushTop = Color(0xFFF7E9EC)
    val BlushBottom = Color(0xFFF2DCE3)

    /* ---- 文字层级 ---- */
    val Ink = Color(0xFF3A2A2F)
    val Ink2 = Color(0xFF7C6A6F)
    val Ink3 = Color(0xFFB2A0A5)

    /* ---- 语义色 ---- */
    val Danger = Color(0xFFC2456A)
    val Ok = Color(0xFF2F9E63)
    val Warn = Color(0xFFD97706)
    val Info = Color(0xFF3E7BFA)
    val Star = Color(0xFFEAB308)
    val Soft = Color(0xFFF3EFF0)
    val OkSoft = Color(0xFFE2F5EA)
    val WarnSoft = Color(0xFFFBF0DF)
    val DangerSoft = Color(0xFFF9E4E8)
    val InfoSoft = Color(0xFFE7F1FD)

    /* ---- 阴影（暖墨色，低透明度） ---- */
    val Shadow = Color(0x1F241A1E)
    val ShadowSoft = Color(0x14241A1E)
    val BrandGlow = Brand.copy(alpha = 0.32f)

    /* ---- 侧边导航（浅色） ---- */
    val Side = Color(0xFFFCF9FA)
    val SideActive = Brand50
    val SideInk = Ink2
    val SideInkActive = BrandInk
    val SideBadge = Color(0xFF6E5F64)

    /* ---- 历史深色导航令牌（保留以免旧引用失效） ---- */
    val Rail = Color(0xFF2C2326)
    val RailInk = Color(0xFFC9BCC0)
    val RailActive = Color(0xFFE0708A)
}

/** 圆角尺度：小控件 / 常规卡片 / 大卡片 / 全屏面板 / 胶囊。 */
object PatNestShape {
    val Sm = RoundedCornerShape(10.dp)
    val Md = RoundedCornerShape(14.dp)
    val Lg = RoundedCornerShape(18.dp)
    val Xl = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

/** 暖墨色柔和投影：卡片浮起感的核心。 */
fun Modifier.patShadow(elevation: Dp = 10.dp, shape: Shape = PatNestShape.Lg): Modifier =
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = PatNestColors.Shadow,
        spotColor = PatNestColors.Shadow,
    )

/** 更轻的投影，用于下拉、胶囊、嵌套浮层。 */
fun Modifier.patShadowSoft(elevation: Dp = 6.dp, shape: Shape = PatNestShape.Md): Modifier =
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = PatNestColors.ShadowSoft,
        spotColor = PatNestColors.ShadowSoft,
    )

/** 品牌粉光晕，用于主按钮等强调元素。 */
fun Modifier.brandGlow(elevation: Dp = 9.dp, shape: Shape = PatNestShape.Pill): Modifier =
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = PatNestColors.BrandGlow,
        spotColor = PatNestColors.Brand.copy(alpha = 0.26f),
    )

/** 白色浮起卡片：柔和阴影 + 大圆角，不描边。 */
fun Modifier.patCard(shape: Shape = PatNestShape.Lg, elevation: Dp = 10.dp): Modifier =
    patShadow(elevation, shape).clip(shape).background(PatNestColors.Panel)

/** 页面底：暖灰粉基底 + 两团极淡的腮红光斑。 */
@Composable
fun PatNestPageBackground(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(PatNestColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val glowRadius = maxOf(w, h) * 0.62f

        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(PatNestColors.BlushTop.copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.02f),
                    radius = glowRadius,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(PatNestColors.BlushBottom.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * 0.94f, h * 0.95f),
                    radius = glowRadius * 0.8f,
                ),
            ),
        )
        content()
    }
}

/**
 * 启动页、登录页等全屏页面共用的暖色底（与旧版一致的深一点的腮红底）。
 */
@Composable
fun PatNestBlushBackground(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(PatNestColors.Backdrop),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val glowRadius = maxOf(w, h) * 0.68f

        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(PatNestColors.BlushTop, Color.Transparent),
                    center = Offset(w * 0.14f, h * 0.08f),
                    radius = glowRadius,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(PatNestColors.BlushBottom, Color.Transparent),
                    center = Offset(w * 0.9f, h * 0.86f),
                    radius = glowRadius * 0.82f,
                ),
            ),
        )
        content()
    }
}

@Composable
fun PatNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PatNestColors.Brand,
            onPrimary = Color.White,
            primaryContainer = PatNestColors.BrandSoft,
            onPrimaryContainer = PatNestColors.BrandInk,
            background = PatNestColors.Bg,
            onBackground = PatNestColors.Ink,
            surface = PatNestColors.Panel,
            onSurface = PatNestColors.Ink,
            surfaceVariant = PatNestColors.Line2,
            onSurfaceVariant = PatNestColors.Ink2,
            error = PatNestColors.Danger,
            onError = Color.White,
        ),
        content = content,
    )
}
