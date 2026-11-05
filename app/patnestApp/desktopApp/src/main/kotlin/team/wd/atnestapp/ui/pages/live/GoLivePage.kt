package team.wd.atnestapp.ui.pages.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.CameraBroadcastPanel
import team.wd.atnestapp.ui.components.DashedUploadBox
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.RemoteImage
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.util.chooseImageFile

/**
 * 开播（机构端）。
 *
 * 流程按 docs/18：`POST /sitter/live` 拿到 `push_url` → 本机摄像头 + FFmpeg 推 RTMP →
 * 结束推流后调 `PUT /sitter/live/:id/stop`。两件事要分开做：
 * 停推只是断流，直播间仍然存在，不调 stop 会一直挂在「直播中」列表里。
 */
@Composable
fun GoLivePage(app: AppState) {
    LaunchedEffect(Unit) { app.loadMyLive() }

    var title by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    // 摄像头设备名（Windows 的 dshow 名 / mac 的索引 / Linux 的 /dev/videoN）
    var cameraInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val broadcasting = app.broadcastRoom
    val pushUrl = broadcasting?.pushUrl?.takeIf { it.isNotBlank() }
    val banned = app.myLive?.liveBanned == true
    val staleRoom = app.myLive?.current?.takeIf { it.id != broadcasting?.id }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("开播", "本机摄像头 + 麦克风直接推 RTMP，不需要额外装 OBS") {
            when {
                pushUrl != null -> Tag("直播中", TagKind.Ok)
                banned -> Tag("已封禁", TagKind.Danger)
                else -> Tag("机构功能", TagKind.Brand)
            }
        }

        when {
            pushUrl != null && broadcasting != null -> {
                Panel(
                    title = "直播预览",
                    subtitle = "预览即推流画面；结束后记得点「结束直播」",
                ) {
                    CameraBroadcastPanel(
                        rtmpUrl = pushUrl,
                        cameraInput = cameraInput,
                        previewHeight = 380.dp,
                        onStatus = { app.liveStreamStatus = it },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Panel(title = "推流信息", subtitle = broadcasting.title.ifBlank { "未命名直播" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("状态", color = PatNestColors.Ink3, fontSize = 12.sp, modifier = Modifier.padding(end = 14.dp))
                        Text(
                            app.liveStreamStatus.ifBlank { "准备推流…" },
                            color = PatNestColors.Ink,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("推流地址（外部工具推流时可复制使用）", color = PatNestColors.Ink3, fontSize = 11.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(PatNestColors.PanelSoft)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            pushUrl,
                            color = PatNestColors.Ink2,
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("复制地址", {
                            clipboard.setText(AnnotatedString(pushUrl))
                            app.toast("推流地址已复制")
                        })
                        GhostButton("看直播广场", { app.go(AppPage.Live) })
                        PrimaryButton(
                            if (app.liveBusy) "正在结束…" else "结束直播",
                            { app.scope.launch { app.stopLive(broadcasting.id) } },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "观众大约 4~10 秒后能看到画面（HLS 切片 2 秒）。离开本页会断开推流，" +
                            "但直播间仍然存在，需要回来结束它才会真正下播。",
                        color = PatNestColors.Ink3,
                        fontSize = 11.sp,
                    )
                }
            }

            banned -> {
                Panel(title = "直播权限已被封禁") {
                    Text(
                        app.myLive?.banReason?.takeIf { it.isNotBlank() } ?: "请联系平台客服了解封禁原因",
                        color = PatNestColors.Ink2,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "封禁通常由内容违规或多次投诉触发，解封后这里会恢复开播按钮。",
                        color = PatNestColors.Ink3,
                        fontSize = 11.5.sp,
                    )
                }
            }

            staleRoom != null -> {
                Panel(title = "还有一场直播在进行") {
                    Text(
                        "「${staleRoom.title.ifBlank { "未命名直播" }}」还没有结束。同一机构只能同时开一场，" +
                            "先结束它才能重新开播。",
                        color = PatNestColors.Ink2,
                        fontSize = 12.5.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            if (app.liveBusy) "正在结束…" else "结束那场直播",
                            { app.scope.launch { app.stopLive(staleRoom.id) } },
                        )
                        GhostButton("去直播广场", { app.go(AppPage.Live) })
                    }
                }
            }

            else -> {
                Panel(title = "创建直播间", subtitle = "标题会显示在直播广场，封面可选") {
                    FieldLabel("直播标题")
                    Spacer(Modifier.height(6.dp))
                    SoftTextField(
                        value = title,
                        onValueChange = { title = it.take(40) },
                        placeholder = "例如：带大家看看今天的寄养房间",
                    )
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("封面（可选）")
                    Spacer(Modifier.height(6.dp))
                    if (cover.isBlank()) {
                        DashedUploadBox(
                            "点击选择封面图",
                            if (uploading) "正在上传…" else "不上传时会用机构封面兜底",
                            Modifier.fillMaxWidth().height(170.dp),
                        ) {
                            if (uploading) return@DashedUploadBox
                            val file = chooseImageFile() ?: return@DashedUploadBox
                            app.scope.launch {
                                uploading = true
                                try {
                                    val url = app.uploadImage(file.name, file.readBytes())
                                    if (url != null) {
                                        cover = url
                                        app.toast("封面上传成功")
                                    }
                                } finally {
                                    uploading = false
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RemoteImage(
                                url = cover,
                                modifier = Modifier.height(110.dp).fillMaxWidth(0.42f),
                                contentDescription = "直播封面",
                                shape = RoundedCornerShape(10.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            GhostButton("重新选择", { cover = "" })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("摄像头设备（留空使用默认）")
                    Spacer(Modifier.height(6.dp))
                    SoftTextField(
                        value = cameraInput,
                        onValueChange = { cameraInput = it },
                        placeholder = "Windows: video=Integrated Camera · macOS: 0 · Linux: /dev/video0",
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            if (app.liveBusy) "正在创建…" else "开始直播",
                            { app.scope.launch { app.startLive(title, cover) } },
                        )
                        GhostButton("去直播广场", { app.go(AppPage.Live) })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "首次推流时若提示「开播失败」，多半是摄像头设备名不对：在系统「相机」里确认设备名" +
                            "（Windows）后填到上面再试；macOS 用 0、Linux 用 /dev/video0。",
                        color = PatNestColors.Ink3,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
