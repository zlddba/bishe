package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.DrawerKind
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.HCell
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PetCard
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.TCell
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.util.chooseImageFile

@Composable
fun PetsPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.pets.isEmpty() && !app.petsLoading) app.loadPets()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("我的宠物", "宠物档案 · 免疫证明 · 健康备注") {
            PrimaryButton("新增宠物", { app.go(AppPage.PetEdit) }, height = 38.dp, icon = AppIcon.Plus)
        }

        when {
            app.petsLoading && app.pets.isEmpty() -> {
                Text("正在加载宠物档案…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            app.pets.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.petsError ?: "还没有宠物档案，先添加一只吧",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("新增宠物", { app.go(AppPage.PetEdit) }, height = 34.dp)
                        if (app.petsError != null) {
                            Spacer(Modifier.width(8.dp))
                            PrimaryButton("重试", {
                                app.scope.launch { app.loadPets(force = true) }
                            }, height = 34.dp)
                        }
                    }
                }
            }
            else -> {
                GridRows(app.pets.withIndex().toList(), columns = 4, gap = 14.dp) { iv ->
                    PetCard(
                        iv.value,
                        onDetail = { app.drawer = DrawerKind.PetDetail(iv.index) },
                        onBook = { app.go(AppPage.Booking) },
                        onDelete = {
                            val pet = iv.value
                            app.modal = ModalKind.Confirm(
                                "删除宠物档案",
                                "确定删除「${pet.name}」吗？删除后该宠物不会再出现在预约列表里。",
                            ) {
                                app.scope.launch { app.deletePet(pet.id) }
                            }
                        },
                    )
                }
            }
        }

        Panel(
            title = "免疫证明核验状态",
            subtitle = "平台审核通过后可在预约时正常使用",
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                HCell("宠物", 1.2f)
                HCell("疫苗情况", 1f)
                HCell("证明", 1f)
                HCell("状态", 1f)
                HCell("操作", 1f)
            }
            ThinDivider()
            app.pets.forEach { pet ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TCell(pet.name, 1.2f)
                    TCell(pet.vaccine, 1f)
                    TCell(pet.cert, 1f, dim = true)
                    Row(Modifier.weight(1f)) {
                        Tag(
                            pet.cert,
                            if (pet.cert == "已核验") TagKind.Ok else TagKind.Warn,
                        )
                    }
                    Row(Modifier.weight(1f)) {
                        LinkText("上传证明") {
                            val file = chooseImageFile() ?: return@LinkText
                            app.scope.launch {
                                val url = app.uploadImage(file.name, file.readBytes()) ?: return@launch
                                app.updatePetVaccine(pet.id, listOf(url))
                            }
                        }
                    }
                }
                ThinDivider()
            }
        }
    }
}
