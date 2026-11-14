package team.wd.atnestapp.ui.pages.sitter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.DangerLinkText
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.RemoteImage
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patCard
import kotlinx.coroutines.launch

@Composable
fun SitterPublishPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.sitterDynamics.isEmpty() && !app.sitterDynamicsLoading) {
            app.loadSitterDynamics()
        }
        if (app.loggedIn && app.receivedOrders.isEmpty() && !app.receivedOrdersLoading) {
            app.loadReceivedOrders()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("寄养动态发布", "记录宠物近况，审核后对主人可见") {
            PrimaryButton("发布动态", { app.modal = ModalKind.Publish }, height = 38.dp, icon = AppIcon.Plus)
        }

        if (app.sitterDynamicsLoading && app.sitterDynamics.isEmpty()) {
            Text("正在加载动态…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
        } else if (app.sitterDynamics.isEmpty()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.sitterDynamicsError ?: "还没有发布过寄养动态",
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton("重新加载", {
                        app.scope.launch { app.loadSitterDynamics(force = true) }
                    }, height = 34.dp)
                }
            }
        }
        app.sitterDynamics.forEach { dyn ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .patCard(PatNestShape.Md, 8.dp)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                // 有真实照片就显示首图（原先一律画占位图标，机构没法确认图片是否上传成功）
                RemoteImage(
                    url = dyn.photos.firstOrNull().orEmpty(),
                    modifier = Modifier.width(100.dp).height(90.dp),
                    shape = PatNestShape.Md,
                    contentDescription = "动态照片",
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("寄养动态", color = PatNestColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Tag("已发布", TagKind.Ok)
                    }
                    Text(
                        "${dyn.createdAt.replace('T', ' ').take(16)} · 照片 ${dyn.photos.size} 张",
                        color = PatNestColors.Ink3, fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        dyn.content,
                        color = PatNestColors.Ink2, fontSize = 12.5.sp, lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DangerLinkText("删除") {
                            app.scope.launch { app.deleteSitterDynamic(dyn.id) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
