package team.wd.atnestapp.ui.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.LiveRoomDto
import team.wd.atnestapp.ui.app.LiveBroadcaster
import team.wd.atnestapp.ui.app.LivePlayer
import team.wd.atnestapp.ui.app.MobileImage
import team.wd.atnestapp.ui.app.rememberPhotoPicker
import team.wd.atnestapp.ui.components.AppIcon

/* ================================================================== *
 * 第 5 组：商家直播（docs/18-商家直播功能.md）
 *
 * 三屏：直播广场 → 直播间（看+弹幕）／开播（机构推流）。
 * 画面分别交给 LivePlayer（ExoPlayer 播 HLS）和 LiveBroadcaster（RootEncoder 推 RTMP），
 * 房间、人数、弹幕、心跳走 MobileState，和后端 /live 系列接口一一对应。
 * ================================================================== */

/* ㉖ 直播广场 */
@Composable
fun D62LiveList(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadLiveRooms() }

    DFrame(
        nav = nav,
        title = "直播广场",
        actions = {
            // 机构账号才有「开播」这件事；主人端看到这个按钮只会点进一个没有权限的页面
            if (store.canUseSitter) {
                DSmallButton("我要开播", { nav.go(DScreen.GoLive) })
            }
        },
    ) {
        when {
            store.liveRoomsLoading && store.liveRooms.isEmpty() ->
                DPad { Spacer(Modifier.height(14.dp)); DLoading("正在加载直播…") }

            store.liveRooms.isEmpty() && store.liveRoomsError != null -> DPad {
                Spacer(Modifier.height(14.dp))
                DErrorBox(store.liveRoomsError ?: "直播列表加载失败") {
                    scope.launch { store.loadLiveRooms(force = true) }
                }
            }

            store.liveRooms.isEmpty() -> DPad {
                Spacer(Modifier.height(14.dp))
                DEmpty("现在还没有机构在直播", "刷新") {
                    scope.launch { store.loadLiveRooms(force = true) }
                }
            }

            else -> {
                DPad {
                    Spacer(Modifier.height(12.dp))
                    DNotice("直播由入驻机构自己开播，画面来自门店实景；进直播间可以发弹幕聊天。")
                    Spacer(Modifier.height(14.dp))
                }
                store.liveRooms.forEach { room ->
                    DPad {
                        E_LiveCard(room) { nav.openLive(room) }
                    }
                }
                DPad {
                    DSmallButton("刷新列表", { scope.launch { store.loadLiveRooms(force = true) } })
                }
            }
        }
    }
}

/** 一张直播卡：封面 + LIVE 角标 + 标题 / 机构 / 在线人数。 */
@Composable
private fun E_LiveCard(room: LiveRoomDto, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(DShape.Lg)
            .background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Lg)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(176.dp)) {
            // 外层已经裁圆角，图自己用直角，避免两层圆角差出 4dp 的白边
            MobileImage(
                url = room.coverUrl?.takeIf { it.isNotBlank() }
                    ?: room.sitterCover?.takeIf { it.isNotBlank() }.orEmpty(),
                modifier = Modifier.fillMaxWidth().height(176.dp),
                shape = RectangleShape,
                placeholderSize = 30.dp,
            )
            // 底部压一层渐变，白字才压得住浅色封面
            Box(
                Modifier.fillMaxWidth().height(76.dp).align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, DColor.Ink.copy(alpha = 0.74f)),
                        ),
                    ),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clip(DShape.Pill).background(DColor.Danger)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(DColor.White))
                Spacer(Modifier.width(4.dp))
                Text("直播中", fontSize = 10.sp, color = DColor.White, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(AppIcon.User, Modifier.size(13.dp), DColor.White.copy(alpha = 0.9f))
                Spacer(Modifier.width(4.dp))
                Text(
                    room.sitterName.ifBlank { "寄养机构" },
                    fontSize = 12.sp,
                    color = DColor.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${room.viewerCount} 人在看",
                    fontSize = 11.5.sp,
                    color = DColor.White.copy(alpha = 0.92f),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(
                room.title.ifBlank { "正在直播" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DColor.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                E_startedLabel(room.startedAt) + " · 累计 ${room.totalViews} 次观看",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
            )
        }
    }
}

/* ㉗ 直播间：看画面 + 发弹幕 */
@Composable
fun D63LiveRoom(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val room = store.liveRoom

    if (room == null) {
        DFrame(nav = nav, title = "直播间") {
            DPad { DEmpty("直播间不存在或已关闭", "返回") { nav.back() } }
        }
        return
    }

    val roomId = room.id
    // 进房 + 心跳：后端按 90 秒窗口算在线，30 秒一次留三倍余量
    LaunchedEffect(roomId) {
        while (isActive) {
            store.joinLive(roomId)
            delay(30_000)
        }
    }
    // 弹幕轮询：3 秒一轮增量拉，间隔比心跳短，聊天才有「在聊」的感觉
    LaunchedEffect(roomId) {
        while (isActive) {
            store.pollLiveMessages(roomId)
            delay(3_000)
        }
    }
    // 退出时告诉后端「我走了」，否则在线人数要等窗口过期才掉。
    // 用 store.scope 而不是页面 scope：onDispose 时页面自己的 scope 已经取消。
    DisposableEffect(roomId) {
        onDispose { store.scope.launch { store.leaveLive(roomId) } }
    }

    val living = room.isLiving
    var draft by remember(roomId) { mutableStateOf("") }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || store.liveMsgSending) return
        scope.launch {
            if (store.sendLiveMessage(roomId, text)) draft = ""
        }
    }

    DFrame(
        nav = nav,
        title = room.title.ifBlank { "直播间" },
        actions = {
            DTag(
                if (living) "${store.liveViewerCount} 人在看" else "已结束",
                if (living) DTagTone.Ok else DTagTone.Line,
            )
        },
        bottomBar = if (living) {
            {
                E_MessageBar(
                    value = draft,
                    onValueChange = { draft = it.take(200) },
                    enabled = !store.liveMsgSending,
                    onSend = { send() },
                )
            }
        } else null,
    ) {
        Box(Modifier.fillMaxWidth().background(Color.Black)) {
            val url = room.playUrl
            if (url != null && living) {
                LivePlayer(url = url, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (living) "正在连接画面…" else "直播已结束",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DAvatar(dInitial(room.sitterName.ifBlank { "机构" }), size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        room.sitterName.ifBlank { "寄养机构" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DColor.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            E_startedLabel(room.startedAt),
                            room.peakViewers.takeIf { it > 0 }?.let { "峰值 $it 人" },
                        ).joinToString(" · "),
                        fontSize = 11.5.sp,
                        color = DColor.Ink3,
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    room.endReason == "banned" || room.banReason != null -> DTag("已被封禁", DTagTone.Warn)
                    living -> DTag("直播中", DTagTone.Ok)
                    else -> DTag("已下播", DTagTone.Line)
                }
            }
        }
        DSection("聊天")
        DPad {
            val messages = store.liveMessages
            if (messages.isEmpty()) {
                Text(
                    if (living) "还没有人说话，来第一句吧" else "这场直播没有留下弹幕",
                    fontSize = 12.sp,
                    color = DColor.Ink3,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                // 只渲染最近 40 条：直播间的弹幕不需要往回翻，全量列出来反而更卡
                messages.takeLast(40).forEach { msg ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            msg.nickname.ifBlank { "观众" },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DColor.BrandInk,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            msg.content,
                            fontSize = 12.5.sp,
                            color = DColor.Ink2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (!living) {
            DPad {
                Spacer(Modifier.height(6.dp))
                DButton("返回直播广场", { nav.back() }, style = DButtonStyle.Outline)
            }
        }
        DPad { Spacer(Modifier.height(8.dp)) }
    }
}

/** 直播间底部输入栏：自绘是因为 DFieldInput 带 label 和 12dp 下边距，放固定底栏太高。 */
@Composable
private fun E_MessageBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(DColor.Card)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).clip(DShape.Pill).background(DColor.NeutralSoft)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text("说点什么…", fontSize = 13.sp, color = DColor.Ink3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(color = DColor.Ink, fontSize = 13.sp),
                cursorBrush = SolidColor(DColor.Brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(DShape.Pill)
                .background(if (value.isBlank() || !enabled) DColor.Line2 else DColor.Brand)
                .clickable(enabled = value.isNotBlank() && enabled) { onSend() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text("发送", fontSize = 12.5.sp, color = DColor.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ㉘ 开播（机构端） */
@Composable
fun D64GoLive(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 从别的页面回来时，先让用户选「继续上一场 / 结束它」，不自动接着推
    var resumeConfirmed by remember { mutableStateOf(false) }

    // 每次进页面都跟服务端对一次账：上一场已经被下播的话，把本地这个幽灵房间清掉
    LaunchedEffect(Unit) { store.reconcileLive() }
    // 这一场收尾后清空表单，别把上一场的标题/封面带进下一场
    LaunchedEffect(store.broadcastRoom?.id) {
        if (store.broadcastRoom == null) {
            title = ""
            cover = ""
        }
    }

    val pickCover = rememberPhotoPicker { fileName, bytes ->
        if (bytes.isEmpty()) {
            nav.toast("没有读取到图片")
        } else {
            scope.launch {
                uploading = true
                val url = store.uploadPhoto(fileName, bytes)
                uploading = false
                if (url != null) cover = url
            }
        }
    }

    // 只有带推流地址的才是「本机手上这一场」；没有地址的连不上，只能结束
    val localRoom = store.broadcastRoom
    val broadcasting = localRoom?.takeIf { !it.pushUrl.isNullOrBlank() }
    val myLive = store.myLive
    val banned = myLive?.liveBanned == true
    // 服务端还有一场 living，但不是本机手上这一场（换了设备 / 上次离开过）：
    // 这里拿不到 push_url，所以只能提示先下播，不能假装能接着推
    val staleRoom = (myLive?.current?.takeIf { it.isLiving } ?: localRoom)
        ?.takeIf { it.id != broadcasting?.id }

    DFrame(
        nav = nav,
        title = "开播",
        actions = {
            if (broadcasting != null && resumeConfirmed) DTag("直播中", DTagTone.Ok)
        },
    ) {
        when {
            // 上次离开页面时推流断了，但直播间还开着：给两个明确出口，不静默复用旧房间
            broadcasting != null && !resumeConfirmed -> E_ResumeLive(
                room = broadcasting,
                busy = store.liveBusy,
                onResume = { resumeConfirmed = true },
                onStop = { scope.launch { store.stopLive(broadcasting.id) } },
            )

            broadcasting != null -> {
                E_Broadcasting(
                    room = broadcasting,
                    status = store.liveStreamStatus,
                    onStatus = { store.liveStreamStatus = it },
                    busy = store.liveBusy,
                    onCopy = {
                        val manager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        manager?.setPrimaryClip(
                            ClipData.newPlainText("推流地址", broadcasting.pushUrl.orEmpty()),
                        )
                        nav.toast("推流地址已复制")
                    },
                    onStop = { scope.launch { store.stopLive(broadcasting.id) } },
                )
            }

            banned -> DPad {
                Spacer(Modifier.height(14.dp))
                DNotice("直播权限已被封禁：${myLive.banReason?.takeIf { it.isNotBlank() } ?: "请联系平台客服了解原因"}")
                Spacer(Modifier.height(14.dp))
                Text(
                    "解封后这里会恢复开播按钮。封禁通常是内容违规或多次投诉，申诉请走「联系客服」。",
                    fontSize = 11.5.sp,
                    color = DColor.Ink3,
                )
            }

            // 主人端没有开播入口，但深链/误点不该看到一个「点了会 403」的按钮
            !store.canUseSitter -> DPad {
                Spacer(Modifier.height(14.dp))
                DNotice("当前账号还不是机构，不能开播。入驻审核通过后，同一个账号就能直接开播。")
                Spacer(Modifier.height(12.dp))
                DButton("去入驻机构", { nav.go(DScreen.SitterOnboarding) })
            }

            staleRoom != null -> DPad {
                Spacer(Modifier.height(14.dp))
                DNotice("你还有一场直播在进行：「${staleRoom.title.ifBlank { "未命名直播" }}」，先结束它才能重新开播。")
                Spacer(Modifier.height(14.dp))
                DButton(
                    if (store.liveBusy) "正在结束…" else "结束那场直播",
                    { scope.launch { store.stopLive(staleRoom.id) } },
                    enabled = !store.liveBusy,
                )
            }

            else -> {
                DPad {
                    Spacer(Modifier.height(14.dp))
                    DNotice("开播后手机摄像头与麦克风的画面会实时推给观众；离开这一页会停止推流。")
                    Spacer(Modifier.height(14.dp))
                    DFieldInput(
                        label = "直播标题",
                        value = title,
                        onValueChange = { title = it.take(40) },
                        placeholder = "例如：带大家看看今天的寄养房间",
                    )
                    Text("封面（可选）", fontSize = 12.sp, color = DColor.Ink2)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(140.dp).clip(DShape.Md)
                            .background(DColor.Card)
                            .border(1.dp, DColor.Line, DShape.Md)
                            .clickable(enabled = !uploading) { pickCover() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover.isNotBlank()) {
                            MobileImage(
                                url = cover,
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                shape = DShape.Md,
                                placeholderSize = 26.dp,
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(AppIcon.Camera, Modifier.size(24.dp), DColor.Ink3)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (uploading) "上传中…" else "＋ 拍照 / 选图",
                                    fontSize = 11.5.sp,
                                    color = DColor.Ink3,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    DButton(
                        if (store.liveBusy) "正在创建直播间…" else "开始直播",
                        {
                            scope.launch {
                                // 本页开的这一场直接进推流界面，不用再确认「是否继续」
                                if (store.startLive(title, cover) != null) resumeConfirmed = true
                            }
                        },
                        enabled = !store.liveBusy && title.isNotBlank(),
                    )
                    Spacer(Modifier.height(10.dp))
                    DSmallButton("去直播广场看看", { nav.go(DScreen.LiveList) })
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "一台设备同一时间只能开一场。中途退出页面会断开推流，但直播间仍然存在，" +
                            "需要回来「结束那场直播」才会真正下播。",
                        fontSize = 11.sp,
                        color = DColor.Ink3,
                    )
                }
            }
        }
    }
}

/** 推流中：预览 + 状态 + 推流地址 + 结束按钮。 */
@Composable
private fun E_Broadcasting(
    room: LiveRoomDto,
    status: String,
    onStatus: (String) -> Unit,
    busy: Boolean,
    onCopy: () -> Unit,
    onStop: () -> Unit,
) {
    Column {
        DPad {
            Spacer(Modifier.height(12.dp))
            // SurfaceView 由相机直接把画面贴到窗口上，Clip 对它是无效的，
            // 所以这里用直角黑底而不是圆角 —— 免得四角露出「圆角里面还有画面」的怪相。
            Box(Modifier.fillMaxWidth().background(Color.Black)) {
                LiveBroadcaster(
                    rtmpUrl = room.pushUrl.orEmpty(),
                    onState = onStatus,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DTag(status.ifBlank { "准备推流…" }, DTagTone.Ok)
                Spacer(Modifier.width(8.dp))
                Text(
                    room.title.ifBlank { "未命名直播" },
                    fontSize = 12.5.sp,
                    color = DColor.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            Text("推流地址（备用：外部工具推流用）", fontSize = 12.sp, color = DColor.Ink2)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.NeutralSoft)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    room.pushUrl.orEmpty(),
                    fontSize = 11.sp,
                    color = DColor.Ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DSmallButton("复制地址", onCopy)
            }
            Spacer(Modifier.height(18.dp))
            DButton(
                if (busy) "正在结束…" else "结束直播",
                onStop,
                style = DButtonStyle.Outline,
                enabled = !busy,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "观众大约 4~10 秒后能看到画面（HLS 切片 2 秒）。离开这一页会断开推流，" +
                    "但直播间仍然存在 —— 再进来时需要选「继续推流」或「结束直播」。",
                fontSize = 11.sp,
                color = DColor.Ink3,
            )
        }
    }
}

/**
 * 上次离开页面时推流断了、直播间还开着：给「继续 / 结束」两个明确出口。
 *
 * 不在进页面时自动续推 —— 那样用户会以为这是一场新直播，实际接的还是上一场
 * （标题、观看人数、弹幕都还是旧的）。
 */
@Composable
private fun E_ResumeLive(
    room: LiveRoomDto,
    busy: Boolean,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    DPad {
        Spacer(Modifier.height(14.dp))
        DNotice("你上次的直播「${room.title.ifBlank { "未命名直播" }}」还在进行，但画面已经断了。")
        Spacer(Modifier.height(10.dp))
        Text(
            "离开开播页会停止推流，直播间本身不会自动结束。继续推流会接着这一场" +
                "（观众不用重新进房）；结束则彻底下播。",
            fontSize = 11.5.sp,
            color = DColor.Ink3,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(E_startedLabel(room.startedAt), "房间号 ${room.id}").joinToString(" · "),
            fontSize = 11.sp,
            color = DColor.Ink3,
        )
        Spacer(Modifier.height(16.dp))
        DButton(if (busy) "处理中…" else "继续推流", onResume, enabled = !busy)
        Spacer(Modifier.height(10.dp))
        DButton(
            if (busy) "处理中…" else "结束这场直播",
            onStop,
            style = DButtonStyle.Outline,
            enabled = !busy,
        )
    }
}

/** "2026-11-05T16:01:30+08:00" → "16:01 开播"；格式对不上时原样返回，不猜。 */
private fun E_startedLabel(raw: String): String =
    if (raw.length >= 16) raw.substring(11, 16) + " 开播" else raw
