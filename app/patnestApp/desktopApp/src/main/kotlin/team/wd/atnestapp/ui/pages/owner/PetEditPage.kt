package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.DashedUploadBox
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.RemoteImage
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.util.chooseImageFile

@Composable
fun PetEditPage(app: AppState) {
    // editingPetId > 0 表示这是编辑已有档案，页面复用同一套表单
    val editing = app.pets.firstOrNull { it.id != 0L && it.id == app.editingPetId }
    var type by remember { mutableStateOf(editing?.type ?: "猫") }
    var size by remember { mutableStateOf(editing?.size ?: "小型") }
    var vaccine by remember { mutableStateOf(editing?.vaccine ?: "已接种") }
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var age by remember { mutableStateOf(editing?.age ?: "") }
    var note by remember { mutableStateOf(if (editing?.note == "暂无健康备注") "" else editing?.note.orEmpty()) }
    var photoUrl by remember { mutableStateOf(editing?.photos?.firstOrNull().orEmpty()) }
    var uploading by remember { mutableStateOf(false) }
    var certPhotos by remember { mutableStateOf(editing?.vaccinePhotos ?: emptyList<String>()) }
    var uploadingCert by remember { mutableStateOf(false) }

    // 直接进页面（没有编辑对象）时清掉残留 id，避免下次误进编辑态
    LaunchedEffect(Unit) {
        if (editing == null && app.editingPetId != 0L) app.editingPetId = 0L
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("返回", {
                app.editingPetId = 0L
                app.go(AppPage.Pets)
            }, height = 34.dp, icon = AppIcon.Back)
            Spacer(Modifier.weight(1f))
            Text(
                if (editing == null) "新增宠物" else "编辑宠物",
                color = PatNestColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(title = "宠物照片", subtitle = "用于机构识别与档案展示", modifier = Modifier.weight(1f)) {
                fun pickAndUpload() {
                    if (uploading) return
                    val file = chooseImageFile() ?: return
                    app.scope.launch {
                        uploading = true
                        try {
                            val url = app.uploadImage(file.name, file.readBytes())
                            if (url != null) {
                                photoUrl = url
                                app.toast("照片已上传")
                            }
                        } finally {
                            uploading = false
                        }
                    }
                }
                if (photoUrl.isBlank()) {
                    DashedUploadBox("点击上传宠物照片", "支持 JPG / PNG，≤ 5MB", Modifier.fillMaxWidth().height(220.dp)) {
                        pickAndUpload()
                    }
                } else {
                    // 选完立刻能看到，不然用户不知道传的是哪张
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(PatNestShape.Md)
                            .clickable { pickAndUpload() },
                    ) {
                        RemoteImage(
                            url = photoUrl,
                            modifier = Modifier.matchParentSize(),
                            contentDescription = "宠物照片",
                            shape = PatNestShape.Md,
                        )
                        Text(
                            if (uploading) "正在上传…" else "点击可更换照片",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("已上传后将用于机构确认与寄养动态", color = PatNestColors.Ink3, fontSize = 12.sp)
            }

            Panel(title = "基本信息", modifier = Modifier.weight(1.4f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("宠物类型")
                        ChipRow(listOf("猫", "狗", "其他"), type) { type = it }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("宠物名字")
                        SoftTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "如：豆包")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("体型")
                        ChipRow(listOf("小型", "中型", "大型"), size) { size = it }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("年龄")
                        SoftTextField(age, { age = it }, Modifier.fillMaxWidth(), placeholder = "如：2 岁")
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("疫苗情况")
                ChipRow(listOf("已接种", "待补种", "不适用"), vaccine) { vaccine = it }
                Spacer(Modifier.height(12.dp))
                if (editing == null) {
                    FieldLabel("免疫证明（照片）")
                    DashedUploadBox("保存档案后可在宠物管理里上传接种证") {
                        app.toast("请先保存宠物档案，再上传接种证")
                    }
                } else {
                    FieldLabel("免疫证明（照片）")
                    if (certPhotos.isEmpty()) {
                        DashedUploadBox("点击上传接种证照片", "机构预约时会看到核验状态", Modifier.fillMaxWidth().height(96.dp)) {
                            if (uploadingCert) return@DashedUploadBox
                            val file = chooseImageFile() ?: return@DashedUploadBox
                            app.scope.launch {
                                uploadingCert = true
                                val url = app.uploadImage(file.name, file.readBytes())
                                uploadingCert = false
                                if (url != null) {
                                    certPhotos = certPhotos + url
                                    app.updatePetVaccine(editing.id, certPhotos)
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            certPhotos.forEach { url ->
                                Box {
                                    RemoteImage(
                                        url = url,
                                        modifier = Modifier
                                            .size(92.dp, 68.dp)
                                            .clickable { app.modal = ModalKind.Photo(url, "${editing.name} 免疫证明") },
                                        contentDescription = "免疫证明",
                                    )
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(17.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .clickable {
                                                val rest = certPhotos - url
                                                certPhotos = rest
                                                app.scope.launch { app.updatePetVaccine(editing.id, rest) }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("×", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                            if (!uploadingCert) {
                                DashedUploadBox("继续上传", modifier = Modifier.size(92.dp, 68.dp)) {
                                    val file = chooseImageFile() ?: return@DashedUploadBox
                                    app.scope.launch {
                                        uploadingCert = true
                                        val url = app.uploadImage(file.name, file.readBytes())
                                        uploadingCert = false
                                        if (url != null) {
                                            certPhotos = certPhotos + url
                                            app.updatePetVaccine(editing.id, certPhotos)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("健康与行为备注")
                SoftTextField(
                    note, { note = it }, Modifier.fillMaxWidth(),
                    placeholder = "忌口、性格、过敏史、常用用品等，供机构照料参考",
                    singleLine = false, minHeight = 72.dp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("取消", {
                        app.editingPetId = 0L
                        app.go(AppPage.Pets)
                    }, Modifier.weight(1f))
                    PrimaryButton(if (editing == null) "保存宠物档案" else "保存修改", {
                        if (name.isBlank()) {
                            app.toast("请输入宠物名字")
                        } else {
                            app.scope.launch {
                                if (editing != null) {
                                    app.updatePet(editing.id, name, type, size, age, vaccine == "已接种", note, photoUrl)
                                } else {
                                    app.createPet(name, type, size, age, vaccine == "已接种", note, photoUrl)
                                }
                            }
                        }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}
