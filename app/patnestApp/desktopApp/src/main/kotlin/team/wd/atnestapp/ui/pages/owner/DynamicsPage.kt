package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.DrawerKind
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.RemoteImage
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patCard

@Composable
fun DynamicsPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.ownerDynamics.isEmpty() && !app.dynamicsLoading) {
            app.loadOwnerDynamics()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("寄养动态", "全部寄养订单的动态更新") {
            // 订阅消息依赖小程序资质，桌面端做不到；这里只说明站内消息这条真实存在的通道
            Tag("新动态会进消息通知", TagKind.Warn)
        }

        when {
            app.dynamicsLoading && app.ownerDynamics.isEmpty() -> {
                Text("正在加载寄养动态…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            app.ownerDynamics.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.dynamicsError ?: "机构还没有发布寄养动态",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("刷新", {
                            app.scope.launch { app.loadOwnerDynamics(force = true) }
                        }, height = 32.dp)
                    }
                }
            }
            else -> {
                app.ownerDynamics.forEachIndexed { index, dynamic ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .patCard(PatNestShape.Md, 8.dp)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        val cover = dynamic.photos.firstOrNull().orEmpty()
                        if (cover.isNotBlank()) {
                            RemoteImage(
                                url = cover,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(90.dp)
                                    .clickable { app.modal = ModalKind.Photo(cover, "寄养动态") },
                                contentDescription = "动态配图",
                            )
                        } else {
                            Box(
                                Modifier
                                    .width(100.dp)
                                    .height(90.dp)
                                    .clip(PatNestShape.Md)
                                    .background(Brush.linearGradient(listOf(PatNestColors.Brand50, PatNestColors.BrandSoft))),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(AppIcon.Image, Modifier.size(32.dp), PatNestColors.BrandInk)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOf(dynamic.sitterName, dynamic.orderNo.takeIf { it.isNotBlank() }?.let { "订单 $it" })
                                    .filterNotNull()
                                    .joinToString(" · ")
                                    .ifBlank { "寄养动态" },
                                color = PatNestColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                formatDynamicTime(dynamic.createdAt) + " · 照片 ${dynamic.photos.size} 张",
                                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Text(
                                dynamic.content,
                                color = PatNestColors.Ink2, fontSize = 12.5.sp, lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LinkText("查看详情") { app.drawer = DrawerKind.DynDetail(index) }
                                LinkText(
                                    if (app.likedDynamics.contains(dynamic.id)) "已点赞" else "点赞",
                                ) {
                                    app.scope.launch { app.toggleDynamicLike(dynamic.id) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun formatDynamicTime(raw: String): String {
    if (raw.length < 16) return raw
    val date = raw.substring(5, 10).replace('-', '/')
    return "$date ${raw.substring(11, 16)}"
}
