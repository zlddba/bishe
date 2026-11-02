package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.DashedUploadBox
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.util.chooseImageFile

@Composable
fun PetEditPage(app: AppState) {
    var type by remember { mutableStateOf("猫") }
    var size by remember { mutableStateOf("小型") }
    var vaccine by remember { mutableStateOf("已接种") }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("返回", { app.go(AppPage.Pets) }, height = 34.dp, icon = AppIcon.Back)
            Spacer(Modifier.weight(1f))
            Text("新增宠物", color = PatNestColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(title = "宠物照片", subtitle = "用于机构识别与档案展示", modifier = Modifier.weight(1f)) {
                DashedUploadBox(
                    if (photoUrl.isBlank()) "点击上传宠物照片" else "照片已上传，点击可更换",
                    if (uploading) "正在上传…" else "支持 JPG / PNG，≤ 5MB",
                    Modifier.fillMaxWidth().height(220.dp),
                ) {
                    if (uploading) return@DashedUploadBox
                    val file = chooseImageFile() ?: return@DashedUploadBox
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
                FieldLabel("免疫证明（照片）")
                DashedUploadBox("保存后可到宠物管理上传接种证") { app.toast("请先保存宠物档案") }
                Spacer(Modifier.height(12.dp))
                FieldLabel("健康与行为备注")
                SoftTextField(
                    note, { note = it }, Modifier.fillMaxWidth(),
                    placeholder = "忌口、性格、过敏史、常用用品等，供机构照料参考",
                    singleLine = false, minHeight = 72.dp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("取消", { app.go(AppPage.Pets) }, Modifier.weight(1f))
                    PrimaryButton("保存宠物档案", {
                        if (name.isBlank()) {
                            app.toast("请输入宠物名字")
                        } else {
                            app.scope.launch {
                                app.createPet(name, type, size, age, vaccine == "已接种", note, photoUrl)
                            }
                        }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}
