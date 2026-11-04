package team.wd.atnestapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
 * 手机端配色令牌，取值严格对应 docs/宠栖后台-UI配色设计图.html。
 * 品牌粉只用在关键动作与选中态，中性灰承担信息层级，语义色只在反馈时出现。
 */
object MobileColors {
    /* 品牌玫瑰粉色阶 50-900 */
    val Brand50 = Color(0xFFFDF1F4)
    val Brand100 = Color(0xFFFBE0E6)
    val Brand200 = Color(0xFFF6C2CE)
    val Brand300 = Color(0xFFEFA0B3)
    val Brand400 = Color(0xFFE87D97)
    val Brand = Color(0xFFE0708A)
    val Brand600 = Color(0xFFC85B74)
    val BrandDeep = Color(0xFFAF4A62)
    val Brand800 = Color(0xFF8E3B4E)
    val Brand900 = Color(0xFF6F2A3B)

    /* 中性与背景 */
    val Bg = Color(0xFFFAF5F6)
    val Panel = Color(0xFFFFFFFF)
    val PanelSoft = Color(0xFFFBF7F8)
    val Line = Color(0xFFE7E9EC)
    val Line2 = Color(0xFFF1EEEF)
    val Ink = Color(0xFF1B1E23)
    val Ink2 = Color(0xFF5C6470)
    val Ink3 = Color(0xFF9AA3AF)

    /* 语义色：主色 + 约 90% 明度浅底 + 同色相深字 */
    val Ok = Color(0xFF2F9E63)
    val OkSoft = Color(0xFFEAF6EF)
    val Warn = Color(0xFFD97706)
    val WarnInk = Color(0xFFB45309)
    val WarnSoft = Color(0xFFFBF2E4)
    val Danger = Color(0xFFC2456A)
    val DangerSoft = Color(0xFFFAE7EC)
    val Info = Color(0xFF3E7BFA)
    val InfoSoft = Color(0xFFE7F1FD)
    val Amber = Color(0xFFEAB308)
    val Mint = Color(0xFFF2AEBE)
    val NeutralSoft = Color(0xFFEFF1F3)

    /* 导航选中态（设计稿 .navi.on） */
    val NavActiveBg = Color(0xFFFBE7EC)

    val Scrim = Color(0x66241A1E)
}

/** 圆角尺度：卡片 12、控件 10、小元素 6，与设计稿一致。 */
object MobileShape {
    val Sm = RoundedCornerShape(6.dp)
    val Md = RoundedCornerShape(10.dp)
    val Lg = RoundedCornerShape(12.dp)
    val Xl = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(50)
}

/** 卡片浮起：手机端阴影比桌面端更轻，主要靠 1px 描边分层。 */
fun Modifier.mobileCard(
    shape: Shape = MobileShape.Lg,
    fill: Color = MobileColors.Panel,
    elevated: Boolean = true,
): Modifier {
    val base = if (elevated) shadow(6.dp, shape, clip = false, ambientColor = MobileColors.Line, spotColor = MobileColors.Line) else Modifier
    return base.clip(shape).background(fill).border(1.dp, MobileColors.Line, shape)
}

/** 主按钮的品牌粉渐变（设计稿 .btn-p / .btn-pd）。 */
val PrimaryGradient = Brush.linearGradient(listOf(MobileColors.Brand, MobileColors.BrandDeep))

/** 页面底：暖粉白底 + 顶部一团极淡的品牌色，避免大面积纯色显得生硬。 */
@Composable
fun MobilePageBackground(content: @Composable BoxScope.() -> Unit) {
    BoxWithGradient(content = content)
}

@Composable
private fun BoxWithGradient(content: @Composable BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MobileColors.Bg),
        contentAlignment = Alignment.TopStart,
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        MobileColors.Brand50,
                        MobileColors.Bg.copy(alpha = 0.0f),
                        MobileColors.Bg,
                    ),
                ),
            ),
        )
        // 左上角一团极淡的腮红，与桌面端同一套视觉语言
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(MobileColors.Brand100.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * 0.05f, h * 0.02f),
                    radius = maxOf(w, h) * 0.55f,
                ),
            ),
        )
        content()
    }
}

@Composable
fun PatNestMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MobileColors.Brand,
            onPrimary = Color.White,
            primaryContainer = MobileColors.Brand100,
            onPrimaryContainer = MobileColors.BrandDeep,
            background = MobileColors.Bg,
            onBackground = MobileColors.Ink,
            surface = MobileColors.Panel,
            onSurface = MobileColors.Ink,
            surfaceVariant = MobileColors.Line2,
            onSurfaceVariant = MobileColors.Ink2,
            error = MobileColors.Danger,
            onError = Color.White,
        ),
        content = content,
    )
}
