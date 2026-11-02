package team.wd.atnestapp.ui.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.theme.PatNestBlushBackground
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.utils.getAppVersionInfo

/**
 * 应用启动页：品牌徽标 + 应用名 + 版本号，保持与登录页同一套暖色底。
 */
@Composable
fun DesktopSplash() {
    val appInfo = getAppVersionInfo()

    PatNestBlushBackground {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(150.dp)
                .clip(CircleShape)
                .border(1.5.dp, PatNestColors.BrandSoft, CircleShape),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .shadow(26.dp, CircleShape, ambientColor = PatNestColors.Brand.copy(alpha = 0.30f), spotColor = PatNestColors.Brand.copy(alpha = 0.24f))
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(PatNestColors.Brand, PatNestColors.BrandInk),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "宠",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            Text(
                text = "宠栖 PetNest",
                color = PatNestColors.Ink,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "宠物寄养服务预约 · Desktop",
                color = PatNestColors.Ink2,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.height(28.dp))

            Row3Dots()
        }

        Text(
            text = "版本 ${appInfo.displayVersion}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            color = PatNestColors.Ink3,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun Row3Dots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(PatNestColors.Brand),
        )
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(PatNestColors.Brand300),
        )
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(PatNestColors.Brand200),
        )
    }
}
