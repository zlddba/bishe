package team.wd.atnestapp.ui.pages.live

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.LiveRoomDto
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.RemoteImage
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patCard

/**
 * 直播广场（docs/18-商家直播功能.md）。
 *
 * 接口是 `GET /api/v1/live`，只回「直播中」的房间，`play_hls/play_flv` 由后端拼好，
 * 客户端不拼地址 —— 换 SRS 域名时只要改后端环境变量。
 */
@Composable
fun LiveListPage(app: AppState) {
    LaunchedEffect(Unit) { app.loadLiveRooms() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("直播广场", "机构正在直播的实景，进直播间可以发弹幕") {
            if (app.isSitterAccount) {
                PrimaryButton("我要开播", { app.go(AppPage.GoLive) }, height = 34.dp)
            } else {
                Tag("机构账号可开播", TagKind.Warn)
            }
        }

        when {
            app.liveRoomsLoading && app.liveRooms.isEmpty() -> {
                Text("正在加载直播…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }

            app.liveRooms.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.liveRoomsError ?: "现在还没有机构在直播",
                            color = PatNestColors.Ink2,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            "刷新",
                            { app.scope.launch { app.loadLiveRooms(force = true) } },
                            height = 32.dp,
                        )
                    }
                }
            }

            else -> {
                // 桌面窗口宽，两列刚好：一列会把 16:9 的封面拉得过高
                app.liveRooms.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        row.forEach { room ->
                            LiveCard(app, room, Modifier.weight(1f))
                        }
                        // 奇数个时补一个空位，最后一张不会横跨整行
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveCard(app: AppState, room: LiveRoomDto, modifier: Modifier = Modifier) {
    Column(
        modifier
            .patCard(PatNestShape.Md, 8.dp)
            .clickable { app.scope.launch { app.openLive(room) } },
    ) {
        Box(Modifier.fillMaxWidth().height(172.dp)) {
            RemoteImage(
                url = room.coverUrl?.takeIf { it.isNotBlank() } ?: room.sitterCover.orEmpty(),
                modifier = Modifier.fillMaxSize(),
                contentDescription = room.title,
                // 外层卡片已经裁过圆角，图用直角，免得两层圆角差出白边
                shape = RectangleShape,
            )
            Box(
                Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC1C1618)),
                        ),
                    ),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clip(PatNestShape.Pill).background(PatNestColors.Danger)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White),
                )
                Spacer(Modifier.width(4.dp))
                Text("直播中", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(AppIcon.User, Modifier.size(13.dp), Color.White)
                Spacer(Modifier.width(4.dp))
                Text(
                    room.sitterName.ifBlank { "寄养机构" },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("${room.viewerCount} 人在看", color = Color(0xFFEDE7E9), fontSize = 11.5.sp)
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                room.title.ifBlank { "正在直播" },
                color = PatNestColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                liveStartedLabel(room.startedAt) + " · 累计 ${room.totalViews} 次观看",
                color = PatNestColors.Ink3,
                fontSize = 11.5.sp,
            )
        }
    }
}

/** "2026-11-05T16:01:30+08:00" → "16:01 开播"；格式对不上就原样返回，不猜。 */
internal fun liveStartedLabel(raw: String): String =
    if (raw.length >= 16) raw.substring(11, 16) + " 开播" else raw
