package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SitterCard
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch

@Composable
fun SittersPage(app: AppState) {
    var tab by remember { mutableStateOf("全部") }
    val tabs = listOf("全部", "家庭寄养", "门店寄养", "上门喂养", "其他")
    val sortOptions = listOf("综合排序", "评分优先", "价格从低到高", "价格从高到低")
    val priceOptions = listOf("全部价格", "¥100 以下", "¥100-200", "¥200 以上")
    var sortIndex by remember { mutableStateOf(0) }
    var priceIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (app.sitters.isEmpty() && !app.sittersLoading) app.loadSitters()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("找机构", "家庭寄养 / 门店寄养 / 上门喂养 / 其他") {
            // 只是按评分优先排序，不叫"智能推荐"，避免答辩时被追问向量召回
            Tag("评分优先", TagKind.Brand, onClick = {
                sortIndex = 1
                app.toast("已切换为按评分优先排序")
            })
        }

        Panel(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipRow(tabs, tab) { tab = it }
                Spacer(Modifier.weight(1f))
                GhostButton(sortOptions[sortIndex], {
                    sortIndex = (sortIndex + 1) % sortOptions.size
                }, height = 32.dp)
                Spacer(Modifier.width(8.dp))
                GhostButton(priceOptions[priceIndex], {
                    priceIndex = (priceIndex + 1) % priceOptions.size
                }, height = 32.dp)
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            app.sittersLoading && app.sitters.isEmpty() -> {
                Text("正在加载机构…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            app.sitters.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.sittersError ?: "暂无可浏览的机构",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("重新加载", {
                            app.scope.launch { app.loadSitters(force = true) }
                        }, height = 34.dp)
                    }
                }
            }
            else -> {
                var filtered = app.sitters.withIndex().filter { tab == "全部" || it.value.type == tab }
                val keyword = app.sitterSearch.trim()
                if (keyword.isNotEmpty()) {
                    filtered = filtered.filter {
                        it.value.name.contains(keyword, ignoreCase = true) ||
                            it.value.addr.contains(keyword, ignoreCase = true) ||
                            it.value.tags.contains(keyword, ignoreCase = true) ||
                            it.value.desc.contains(keyword, ignoreCase = true)
                    }
                }
                filtered = when (priceOptions[priceIndex]) {
                    "¥100 以下" -> filtered.filter { it.value.price < 100 }
                    "¥100-200" -> filtered.filter { it.value.price in 100..200 }
                    "¥200 以上" -> filtered.filter { it.value.price > 200 }
                    else -> filtered
                }
                filtered = when (sortOptions[sortIndex]) {
                    "评分优先" -> filtered.sortedByDescending { it.value.score }
                    "价格从低到高" -> filtered.sortedBy { it.value.price }
                    "价格从高到低" -> filtered.sortedByDescending { it.value.price }
                    else -> filtered
                }
                if (filtered.isEmpty()) {
                    Panel {
                        Text("该分类下暂无机构", color = PatNestColors.Ink2, fontSize = 13.sp)
                    }
                } else {
                    GridRows(filtered, columns = 3, gap = 16.dp) { iv ->
                        SitterCard(
                            iv.value,
                            onOpen = { app.openSitterDetail(iv.index) },
                            onBook = { app.openBooking(iv.index) },
                        )
                    }
                }
            }
        }
    }
}
