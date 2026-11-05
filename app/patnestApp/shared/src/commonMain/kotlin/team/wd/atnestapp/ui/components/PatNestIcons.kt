package team.wd.atnestapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * 图标统一按 JetBrains ExpUI 规格绘制，桌面端与手机端共用：
 * 16x16 视口、发丝描边（1.15/16）、主色 + 低透明度次要细节的双色层次。
 * 颜色由 tint 控制，形状随组件尺寸等比缩放。
 */
enum class AppIcon {
    Home, Shop, Paw, Calendar, Orders, OrdersPlain, Image, User,
    Heart, Ticket, Bell, Search, Plus, Back, Check, CheckCircle,
    Star, Close, Card, Bank, Logout, Eye, EyeOff, Camera,
}

/**
 * 图标默认色板。取值与桌面端主题令牌 PatNestColors 的 Ink2 / Star 对齐，
 * 手机端未接入该主题时也能直接使用带默认色的图标。
 */
object PetIconPalette {
    val Ink2 = Color(0xFF7C6A6F)
    val Star = Color(0xFFEAB308)
}

/** ExpUI 画布边长。 */
private const val GRID = 16f

/** 次要细节的透明度（ExpUI 双色层次）。 */
private const val SECONDARY_ALPHA = 0.42f

@Composable
fun AppIcon(icon: AppIcon, modifier: Modifier = Modifier, tint: Color = PetIconPalette.Ink2) {
    Canvas(modifier) {
        val s = size.minDimension / GRID
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawAppIcon(icon, tint)
        }
    }
}

private fun hairline(width: Float = 1.15f) =
    Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun Path.polyline(vararg points: Float): Path {
    points.toList().chunked(2).forEachIndexed { index, (x, y) ->
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    return this
}

private fun DrawScope.drawAppIcon(icon: AppIcon, tint: Color) {
    val sub = tint.copy(alpha = tint.alpha * SECONDARY_ALPHA)
    when (icon) {
        AppIcon.Home -> {
            drawPath(Path().polyline(2.2f, 7.8f, 8f, 2.7f, 13.8f, 7.8f), tint, style = hairline())
            drawPath(
                Path().polyline(4.2f, 7.0f, 4.2f, 13.3f, 11.8f, 13.3f, 11.8f, 7.0f),
                tint, style = hairline(),
            )
            drawPath(
                Path().polyline(6.7f, 13.3f, 6.7f, 9.7f, 9.3f, 9.7f, 9.3f, 13.3f),
                sub, style = hairline(),
            )
        }

        AppIcon.Shop -> {
            drawRoundRect(
                tint, topLeft = Offset(2.2f, 4.4f), size = Size(11.6f, 9f),
                cornerRadius = CornerRadius(1.6f, 1.6f), style = hairline(),
            )
            drawLine(tint, Offset(2.2f, 7.1f), Offset(13.8f, 7.1f), strokeWidth = 1.15f)
            drawPath(
                Path().polyline(6.3f, 13.4f, 6.3f, 9.6f, 9.7f, 9.6f, 9.7f, 13.4f),
                sub, style = hairline(),
            )
        }

        AppIcon.Paw -> {
            drawOval(sub, topLeft = Offset(4.5f, 7.2f), size = Size(7f, 5.2f))
            drawCircle(tint, radius = 1.2f, center = Offset(4.9f, 5.1f))
            drawCircle(tint, radius = 1.2f, center = Offset(7.0f, 3.8f))
            drawCircle(tint, radius = 1.2f, center = Offset(9.0f, 3.8f))
            drawCircle(tint, radius = 1.2f, center = Offset(11.1f, 5.1f))
        }

        AppIcon.Calendar -> {
            drawRoundRect(
                tint, topLeft = Offset(2.2f, 3.6f), size = Size(11.6f, 9.8f),
                cornerRadius = CornerRadius(1.6f, 1.6f), style = hairline(),
            )
            drawLine(tint, Offset(5.4f, 1.9f), Offset(5.4f, 4.2f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(tint, Offset(10.6f, 1.9f), Offset(10.6f, 4.2f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(2.2f, 6.5f), Offset(13.8f, 6.5f), strokeWidth = 1.15f)
            drawRoundRect(
                sub, topLeft = Offset(4.4f, 8.4f), size = Size(3f, 2.4f),
                cornerRadius = CornerRadius(0.6f, 0.6f),
            )
        }

        AppIcon.Orders -> {
            drawLine(sub, Offset(2.4f, 4.4f), Offset(9.2f, 4.4f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(2.4f, 8f), Offset(9.2f, 8f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(2.4f, 11.6f), Offset(7.2f, 11.6f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawPath(
                Path().polyline(9.8f, 10.4f, 11.6f, 12.2f, 13.9f, 8.2f),
                tint, style = hairline(1.5f),
            )
        }

        AppIcon.OrdersPlain -> {
            drawLine(tint, Offset(2.4f, 4.4f), Offset(13.6f, 4.4f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(tint, Offset(2.4f, 8f), Offset(13.6f, 8f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(2.4f, 11.6f), Offset(9.2f, 11.6f), strokeWidth = 1.15f, cap = StrokeCap.Round)
        }

        AppIcon.Image -> {
            drawRoundRect(
                tint, topLeft = Offset(2.2f, 3.6f), size = Size(11.6f, 8.8f),
                cornerRadius = CornerRadius(1.6f, 1.6f), style = hairline(),
            )
            drawCircle(sub, radius = 1.1f, center = Offset(5.8f, 6.3f))
            val hills = Path().apply {
                moveTo(2.8f, 11.9f)
                lineTo(6.4f, 8.3f)
                lineTo(8.9f, 10.8f)
                lineTo(10.6f, 9.1f)
                lineTo(13.4f, 11.9f)
                close()
            }
            drawPath(hills, sub)
        }

        AppIcon.Camera -> {
            // 机身 + 顶部取景凸起 + 镜头，和同组图标的发丝描边保持一致
            drawRoundRect(
                tint, topLeft = Offset(2.2f, 5.3f), size = Size(11.6f, 7.4f),
                cornerRadius = CornerRadius(1.6f, 1.6f), style = hairline(),
            )
            drawPath(
                Path().polyline(5.4f, 5.3f, 6.2f, 3.7f, 9.8f, 3.7f, 10.6f, 5.3f),
                tint, style = hairline(),
            )
            drawCircle(tint, radius = 2.15f, center = Offset(8f, 9f), style = hairline())
            drawCircle(sub, radius = 0.8f, center = Offset(8f, 9f))
        }

        AppIcon.User -> {
            drawCircle(tint, radius = 2.5f, center = Offset(8f, 5.4f), style = hairline())
            val shoulders = Path().apply {
                moveTo(2.8f, 13.6f)
                cubicTo(2.8f, 10.4f, 5.2f, 9.0f, 8f, 9.0f)
                cubicTo(10.8f, 9.0f, 13.2f, 10.4f, 13.2f, 13.6f)
                close()
            }
            drawPath(shoulders, sub)
        }

        AppIcon.Heart -> {
            val heart = Path().apply {
                moveTo(8f, 13.6f)
                cubicTo(2.4f, 9.9f, 1.3f, 6.3f, 2.7f, 4.5f)
                cubicTo(4.0f, 2.8f, 6.4f, 3.1f, 8f, 5.1f)
                cubicTo(9.6f, 3.1f, 12.0f, 2.8f, 13.3f, 4.5f)
                cubicTo(14.7f, 6.3f, 13.6f, 9.9f, 8f, 13.6f)
                close()
            }
            drawPath(heart, tint, style = hairline())
        }

        AppIcon.Ticket -> {
            drawRoundRect(
                tint, topLeft = Offset(2.2f, 4.8f), size = Size(11.6f, 6.4f),
                cornerRadius = CornerRadius(1.4f, 1.4f), style = hairline(),
            )
            drawLine(sub, Offset(9.8f, 5.6f), Offset(9.8f, 6.9f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(9.8f, 7.7f), Offset(9.8f, 8.9f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(9.8f, 9.7f), Offset(9.8f, 10.4f), strokeWidth = 1.15f, cap = StrokeCap.Round)
        }

        AppIcon.Bell -> {
            val body = Path().apply {
                moveTo(3.4f, 10.9f)
                lineTo(4.9f, 9.4f)
                lineTo(4.9f, 7.2f)
                cubicTo(4.9f, 5.0f, 6.2f, 3.5f, 8f, 3.5f)
                cubicTo(9.8f, 3.5f, 11.1f, 5.0f, 11.1f, 7.2f)
                lineTo(11.1f, 9.4f)
                lineTo(12.6f, 10.9f)
                close()
            }
            drawPath(body, tint, style = hairline())
            drawPath(
                Path().polyline(6.7f, 12.6f, 7.3f, 14.1f, 8.7f, 14.1f, 9.3f, 12.6f),
                sub, style = hairline(),
            )
        }

        AppIcon.Search -> {
            drawCircle(tint, radius = 4.2f, center = Offset(7f, 7f), style = hairline())
            drawLine(tint, Offset(10.2f, 10.2f), Offset(13.6f, 13.6f), strokeWidth = 1.15f, cap = StrokeCap.Round)
        }

        AppIcon.Plus -> {
            drawLine(tint, Offset(8f, 2.8f), Offset(8f, 13.2f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawLine(tint, Offset(2.8f, 8f), Offset(13.2f, 8f), strokeWidth = 1.3f, cap = StrokeCap.Round)
        }

        AppIcon.Back -> {
            drawPath(Path().polyline(10.2f, 3.2f, 5.4f, 8f, 10.2f, 12.8f), tint, style = hairline(1.3f))
        }

        AppIcon.Check -> {
            drawPath(Path().polyline(3.2f, 8.4f, 6.6f, 11.8f, 12.8f, 4.6f), tint, style = hairline(1.7f))
        }

        AppIcon.CheckCircle -> {
            drawCircle(tint, radius = 5.8f, center = Offset(8f, 8f), style = hairline())
            drawPath(Path().polyline(5.4f, 8.2f, 7.3f, 10.1f, 10.8f, 6.2f), tint, style = hairline(1.3f))
        }

        AppIcon.Star -> {
            drawPath(starPath(), tint, style = hairline(1.1f))
        }

        AppIcon.Close -> {
            drawLine(tint, Offset(4.4f, 4.4f), Offset(11.6f, 11.6f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawLine(tint, Offset(11.6f, 4.4f), Offset(4.4f, 11.6f), strokeWidth = 1.3f, cap = StrokeCap.Round)
        }

        AppIcon.Card -> {
            drawRoundRect(
                tint, topLeft = Offset(2.2f, 4.3f), size = Size(11.6f, 7.4f),
                cornerRadius = CornerRadius(1.4f, 1.4f), style = hairline(),
            )
            drawLine(sub, Offset(2.2f, 6.6f), Offset(13.8f, 6.6f), strokeWidth = 1.15f)
            drawLine(tint, Offset(4.4f, 9.4f), Offset(7.2f, 9.4f), strokeWidth = 1.15f, cap = StrokeCap.Round)
        }

        AppIcon.Bank -> {
            drawPath(Path().polyline(2.2f, 5.9f, 8f, 2.6f, 13.8f, 5.9f), tint, style = hairline())
            drawLine(tint, Offset(2.6f, 13.3f), Offset(13.4f, 13.3f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(4.7f, 7.3f), Offset(4.7f, 12.2f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(8f, 7.3f), Offset(8f, 12.2f), strokeWidth = 1.15f, cap = StrokeCap.Round)
            drawLine(sub, Offset(11.3f, 7.3f), Offset(11.3f, 12.2f), strokeWidth = 1.15f, cap = StrokeCap.Round)
        }

        AppIcon.Logout -> {
            drawPath(
                Path().polyline(8.2f, 2.6f, 3f, 2.6f, 3f, 13.4f, 8.2f, 13.4f),
                sub, style = hairline(),
            )
            drawLine(tint, Offset(7.2f, 8f), Offset(13.2f, 8f), strokeWidth = 1.25f, cap = StrokeCap.Round)
            drawPath(Path().polyline(10.5f, 5.4f, 13.2f, 8f, 10.5f, 10.6f), tint, style = hairline(1.25f))
        }

        AppIcon.Eye -> {
            drawPath(eyePath(), tint, style = hairline())
            drawCircle(tint, radius = 1.9f, center = Offset(8f, 8f), style = hairline())
        }

        AppIcon.EyeOff -> {
            drawPath(eyePath(), sub, style = hairline())
            drawCircle(sub, radius = 1.9f, center = Offset(8f, 8f), style = hairline())
            drawLine(tint, Offset(2.8f, 2.8f), Offset(13.2f, 13.2f), strokeWidth = 1.25f, cap = StrokeCap.Round)
        }
    }
}

private fun eyePath(): Path = Path().apply {
    moveTo(1.8f, 8f)
    cubicTo(4.0f, 4.4f, 6.0f, 3.2f, 8f, 3.2f)
    cubicTo(10.0f, 3.2f, 12.0f, 4.4f, 14.2f, 8f)
    cubicTo(12.0f, 11.6f, 10.0f, 12.8f, 8f, 12.8f)
    cubicTo(6.0f, 12.8f, 4.0f, 11.6f, 1.8f, 8f)
    close()
}

private fun starPath(): Path = Path().apply {
    moveTo(8f, 2.4f)
    lineTo(9.53f, 6.30f)
    lineTo(13.71f, 6.55f)
    lineTo(10.47f, 9.20f)
    lineTo(11.53f, 13.25f)
    lineTo(8f, 11.0f)
    lineTo(4.47f, 13.25f)
    lineTo(5.53f, 9.20f)
    lineTo(2.29f, 6.55f)
    lineTo(6.47f, 6.30f)
    close()
}

/** 五角星：filled 为实心，否则描边。 */
@Composable
fun StarIcon(modifier: Modifier = Modifier, filled: Boolean = true, tint: Color = PetIconPalette.Star) {
    Canvas(modifier) {
        val s = size.minDimension / GRID
        withTransform({ scale(s, s, Offset.Zero) }) {
            val path = starPath()
            if (filled) drawPath(path, tint) else drawPath(path, tint, style = hairline(1.1f))
        }
    }
}
