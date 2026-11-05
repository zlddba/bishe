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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patCard

@Composable
fun MessagesPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn) app.notificationsCenter.loadScopedNotifications(isSitter = app.isSitterAccount)
    }

    val scopeLabel = if (app.notificationsCenter.notificationScope == "business") "商家消息" else "个人消息"
    val items = app.notificationsCenter.visibleNotifications()
    val unread = items.count { !it.isRead }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader(
            "消息通知",
            "$scopeLabel · ${if (unread > 0) "$unread 条未读" else "全部已读"}",
            trailing = {
                GhostButton("全部已读", {
                    app.scope.launch { app.notificationsCenter.markAllScopedNotificationsRead() }
                }, height = 32.dp)
                PrimaryButton("刷新", {
                    app.scope.launch { app.notificationsCenter.loadScopedNotifications(force = true, isSitter = app.isSitterAccount) }
                }, height = 32.dp)
            },
        )

        ChipRow(
            options = listOf("个人消息", "商家消息"),
            selected = scopeLabel,
                onSelect = { label ->
                app.notificationsCenter.notificationScope = if (label == "商家消息") "business" else "personal"
            },
            modifier = Modifier.padding(bottom = 16.dp),
        )

        when {
            app.notificationsCenter.scopedNotificationsLoading && items.isEmpty() -> {
                Text("正在加载消息…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            items.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.notificationsCenter.scopedNotificationsError
                                ?: if (scopeLabel == "商家消息") "当前账号没有商家消息" else "暂时没有新消息",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("刷新", {
                            app.scope.launch { app.notificationsCenter.loadScopedNotifications(force = true, isSitter = app.isSitterAccount) }
                        }, height = 32.dp)
                    }
                }
            }
            else -> {
                items.forEach { message ->
                    val isOrder = message.type.startsWith("order_") || message.type == "refund_request"
                    val (bg, fg) = when {
                        isOrder -> PatNestColors.OkSoft to PatNestColors.Ok
                        message.type == "dynamics_published" -> PatNestColors.BrandSoft to PatNestColors.BrandInk
                        else -> PatNestColors.InfoSoft to PatNestColors.Info
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .patCard(PatNestShape.Md, 8.dp)
                            .clickable {
                                app.scope.launch { app.notificationsCenter.markScopedNotificationRead(message.id) }
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(PatNestShape.Md).background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(
                                when {
                                    isOrder -> AppIcon.Orders
                                    message.type == "dynamics_published" -> AppIcon.Image
                                    else -> AppIcon.CheckCircle
                                },
                                Modifier.size(19.dp),
                                fg,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                message.title.ifBlank { "系统通知" },
                                color = PatNestColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                formatTime(message.createdAt),
                                color = PatNestColors.Ink3, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                            Text(message.body, color = PatNestColors.Ink2, fontSize = 12.5.sp, lineHeight = 20.sp)
                        }
                        if (!message.isRead) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(PatNestColors.Danger),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun formatTime(raw: String): String {
    if (raw.length < 16) return raw
    val date = raw.substring(5, 10).replace('-', '/')
    return "$date ${raw.substring(11, 16)}"
}
