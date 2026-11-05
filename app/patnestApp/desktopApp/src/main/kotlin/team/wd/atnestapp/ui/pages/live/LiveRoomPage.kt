package team.wd.atnestapp.ui.pages.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.VlcVideoPlayer
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape

/**
 * 直播间：左边画面，右边弹幕。
 *
 * 进房每 30 秒心跳一次（后端 90 秒窗口），弹幕每 3 秒增量拉一次。
 * 离开页面时补一次 leave，否则在线人数要等窗口过期才掉下去。
 *
 * 播放依赖本机 VLC（VLCJ）。没装时 libvlc 初始化失败，这里只提示不崩溃 ——
 * 直播是附加能力，不该把整个应用拖下水。
 */
@Composable
fun LiveRoomPage(app: AppState) {
    val room = app.viewingLive
    if (room == null) {
        Column(
            Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("直播间已关闭", color = PatNestColors.Ink2, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            GhostButton("返回直播广场", { app.go(AppPage.Live) })
        }
        return
    }

    val roomId = room.id
    var playerError by remember(roomId) { mutableStateOf<String?>(null) }
    var draft by remember(roomId) { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(roomId) {
        while (isActive) {
            app.joinLive(roomId)
            delay(30_000)
        }
    }
    LaunchedEffect(roomId) {
        while (isActive) {
            app.pollLiveMessages(roomId)
            delay(3_000)
        }
    }
    DisposableEffect(roomId) {
        onDispose { app.scope.launch { app.leaveLive(roomId) } }
    }
    // 新弹幕到了就滚到底。直播间里用户永远在看最新的几条。
    LaunchedEffect(app.liveMessages.size) {
        if (app.liveMessages.isNotEmpty()) {
            listState.animateScrollToItem(app.liveMessages.lastIndex)
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || app.liveMsgSending) return
        app.scope.launch {
            if (app.sendLiveMessage(roomId, text)) draft = ""
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(
                Modifier.fillMaxWidth().background(Color.Black).aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                val url = room.playUrl
                when {
                    !room.isLiving -> Text(
                        when {
                            room.endReason == "banned" || room.banReason != null -> "该直播已被平台封禁"
                            else -> "直播已结束"
                        },
                        color = Color(0xFFD7CFD2),
                        fontSize = 13.sp,
                    )

                    url == null -> Text("没有可用的播放地址", color = Color(0xFFD7CFD2), fontSize = 13.sp)

                    else -> VlcVideoPlayer(
                        url = url,
                        modifier = Modifier.fillMaxSize(),
                        onError = { message -> playerError = message },
                    )
                }
            }

            Column(Modifier.padding(horizontal = 26.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(PatNestColors.BrandSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            room.sitterName.take(1).ifBlank { "机" },
                            color = PatNestColors.BrandInk,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            room.title.ifBlank { "正在直播" },
                            color = PatNestColors.Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                room.sitterName.ifBlank { "寄养机构" },
                                liveStartedLabel(room.startedAt),
                                room.peakViewers.takeIf { it > 0 }?.let { "峰值 $it 人" },
                            ).joinToString(" · "),
                            color = PatNestColors.Ink3,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (room.isLiving) Tag("直播中", TagKind.Ok) else Tag("已下播", TagKind.Soft)
                }

                if (playerError != null) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(PatNestShape.Md)
                            .background(PatNestColors.WarnSoft)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "画面播放失败：${playerError}。桌面端播放 HLS 依赖本机安装 VLC（需 64 位），" +
                                "装好后重新进入本页即可。",
                            color = PatNestColors.Ink2,
                            fontSize = 11.5.sp,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("返回直播广场", { app.go(AppPage.Live) })
                    GhostButton("刷新画面", { app.scope.launch { app.openLive(room) } })
                }
            }
        }

        Column(
            Modifier
                .width(330.dp)
                .fillMaxHeight()
                .background(PatNestColors.Panel),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("弹幕", color = PatNestColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${app.liveViewerCount} 人在看",
                    color = PatNestColors.Ink3,
                    fontSize = 11.5.sp,
                )
            }

            if (app.liveMessages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (room.isLiving) "还没有人说话，来第一句" else "这场直播没有弹幕",
                        color = PatNestColors.Ink3,
                        fontSize = 12.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp, end = 18.dp, top = 2.dp, bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(app.liveMessages) { msg ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                msg.nickname.ifBlank { "观众" },
                                color = PatNestColors.BrandInk,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                msg.content,
                                color = PatNestColors.Ink2,
                                fontSize = 12.5.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SoftTextField(
                    value = draft,
                    onValueChange = { draft = it.take(200) },
                    placeholder = if (room.isLiving) "说点什么…" else "直播已结束",
                    modifier = Modifier.weight(1f),
                    minHeight = 38.dp,
                    onSubmit = { send() },
                )
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    if (app.liveMsgSending) "发送中" else "发送",
                    { send() },
                    height = 38.dp,
                )
            }
        }
    }
}
