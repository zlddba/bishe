package team.wd.atnestapp.ui.pages.sitter

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.DashedUploadBox
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.util.chooseImageFile

@Composable
fun SitterProfilePage(app: AppState) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("家庭寄养") }
    var address by remember { mutableStateOf("") }
    var capacityText by remember { mutableStateOf("") }
    var dailyPriceText by remember { mutableStateOf("") }
    var visitPriceText by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var detailUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.loadMyShop() }
    LaunchedEffect(app.myShop) {
        app.myShop?.let { shop ->
            name = shop.name
            type = when (shop.type) {
                "shop" -> "门店寄养"
                "visit" -> "上门喂养"
                else -> "家庭寄养"
            }
            address = shop.address.orEmpty()
            capacityText = shop.capacity.toString()
            dailyPriceText = shop.dailyPrice.toInt().toString()
            visitPriceText = shop.visitPrice.toInt().toString()
            desc = shop.description.orEmpty()
            coverUrl = shop.coverPhotoUrl.orEmpty()
            detailUrls = shop.detailPhotos
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("机构资料", "维护本机构信息与资质")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(title = "环境照片", subtitle = "用于机构展示", modifier = Modifier.weight(1f)) {
                DashedUploadBox(
                    if (detailUrls.isEmpty() && coverUrl.isBlank()) "点击上传环境照片" else "已上传 ${detailUrls.size + if (coverUrl.isBlank()) 0 else 1} 张，点击继续添加",
                    if (uploading) "正在上传…" else "建议 3-5 张，展示寄养区 / 院落 / 活动区",
                    Modifier.fillMaxWidth().height(180.dp),
                ) {
                    if (uploading) return@DashedUploadBox
                    val file = chooseImageFile() ?: return@DashedUploadBox
                    app.scope.launch {
                        uploading = true
                        try {
                            val url = app.uploadImage(file.name, file.readBytes())
                            if (url != null) {
                                if (coverUrl.isBlank()) coverUrl = url else detailUrls = detailUrls + url
                                app.toast("照片已上传")
                            }
                        } finally {
                            uploading = false
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "当前 ${detailUrls.size + if (coverUrl.isBlank()) 0 else 1} 张，建议 3-5 张",
                    color = PatNestColors.Ink3, fontSize = 12.sp,
                )
            }

            Panel(title = "基本信息", modifier = Modifier.weight(1.4f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("机构名称")
                        SoftTextField(name, { name = it }, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("类型")
                        ChipRow(listOf("家庭寄养", "门店寄养", "上门喂养"), type) { type = it }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("所在地址")
                SoftTextField(address, { address = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("同时接待容量")
                        SoftTextField(capacityText, { capacityText = it }, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("日单价（元）")
                        SoftTextField(dailyPriceText, { dailyPriceText = it }, Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("上门单价（元）")
                        SoftTextField(visitPriceText, { visitPriceText = it }, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("环境标签（展示用）")
                        SoftTextField(tags, { tags = it }, Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("机构简介")
                SoftTextField(desc, { desc = it }, Modifier.fillMaxWidth(), singleLine = false, minHeight = 72.dp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("保存资料", {
                        app.scope.launch {
                            app.saveMyShop(
                                name = name.trim(),
                                type = when (type) {
                                    "门店寄养" -> "shop"
                                    "上门喂养" -> "visit"
                                    else -> "home"
                                },
                                description = desc.trim(),
                                dailyPrice = dailyPriceText.toDoubleOrNull() ?: 0.0,
                                visitPrice = visitPriceText.toDoubleOrNull() ?: 0.0,
                                priceMode = if (type == "上门喂养") "per_visit" else "per_day",
                                capacity = capacityText.filter { it.isDigit() }.toIntOrNull() ?: 1,
                                address = address.trim(),
                                coverPhotoUrl = coverUrl,
                                detailPhotos = detailUrls,
                            )
                        }
                    })
                    GhostButton("退出登录", {
                        app.modal = ModalKind.Confirm("退出登录", "确定退出当前账号吗？") { app.logout() }
                    }, textColor = PatNestColors.Danger)
                }
            }
        }
    }
}
