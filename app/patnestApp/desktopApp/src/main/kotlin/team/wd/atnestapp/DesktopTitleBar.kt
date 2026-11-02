package team.wd.atnestapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Frame
import team.wd.atnestapp.ui.theme.PatNestColors

/**
 * 桌面端自定义标题栏：浅色顶栏 + 左侧品牌圆点，中间区域可拖拽窗口，右侧窗口控制按钮。
 */
@Composable
fun FrameWindowScope.DesktopTitleBar(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(PatNestColors.Side)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxHeight().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(9.dp).clip(CircleShape).background(PatNestColors.Brand),
                    )
                    Box(
                        Modifier.size(9.dp).clip(CircleShape).background(PatNestColors.Brand300),
                    )
                    Box(
                        Modifier.size(9.dp).clip(CircleShape).background(PatNestColors.Brand200),
                    )
                    Text(
                        "宠栖 PetNest · Desktop",
                        color = PatNestColors.Ink2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            TitleBarButton("—") {
                window.extendedState = Frame.ICONIFIED
            }
            TitleBarButton("□") {
                window.extendedState =
                    if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) Frame.NORMAL else Frame.MAXIMIZED_BOTH
            }
            TitleBarButton("✕", close = true) { onClose() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PatNestColors.Line))
    }
}

@Composable
private fun TitleBarButton(symbol: String, close: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .width(42.dp)
            .height(39.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = if (close) PatNestColors.Danger else PatNestColors.Ink3,
            fontSize = 13.sp,
        )
    }
}
