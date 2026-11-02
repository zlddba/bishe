package team.wd.atnestapp.ui.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.utils.getAppVersionInfo

private val LaunchBackgroundTop = Color(0xFFFFFBF5)
private val LaunchBackgroundBottom = Color(0xFFFFE8D6)
private val LaunchTitleColor = Color(0xFF3D2B1F)
private val LaunchAccentColor = Color(0xFFB85C38)
private val LaunchVersionColor = Color(0xFF8A6F5C)

/**
 * 手机端启动页（Android / iOS）。桌面端使用 desktopApp 模块中的 DesktopSplash，
 * 两端视觉不同，互不影响。
 */
@Composable
@PreviewScreenSizes
fun Launch() {
    val appInfo = getAppVersionInfo()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LaunchBackgroundTop, LaunchBackgroundBottom),
                ),
            )
            .safeContentPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(34.dp),
                            ambientColor = Color(0x33B85C38),
                            spotColor = Color(0x33B85C38),
                        )
                        .clip(RoundedCornerShape(34.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(LaunchAccentColor, Color(0xFF8F4426)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            icon = AppIcon.Paw,
                            modifier = Modifier.fillMaxSize(0.62f),
                            tint = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))

                Text(
                    text = "宠栖 PetNest",
                    color = LaunchTitleColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "PET & HOME",
                    color = LaunchAccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp,
                )
            }

            Text(
                text = "版本 ${appInfo.displayVersion}",
                modifier = Modifier.padding(bottom = 4.dp),
                color = LaunchVersionColor,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}
