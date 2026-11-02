package team.wd.atnestapp.ui.pages.sitter

import androidx.compose.foundation.layout.Arrangement
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
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.CalDayCard
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SlotTag
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.ui.data.CalDay
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.ModalKind

@Composable
fun SitterCapacityPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.capacityItems.isEmpty() && !app.capacityLoading) app.loadCapacity()
        if (app.loggedIn && app.priceRules.isEmpty() && !app.priceRulesLoading) app.loadPriceRules()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("容量日历", "本机构床位排期与节假日定价") {
            GhostButton("节假日定价", { app.modal = ModalKind.PriceRule })
        }

        Panel(title = "近 7 日床位排期", subtitle = "家庭 / 门店") {
            if (app.capacityLoading && app.capacityItems.isEmpty()) {
                Text("正在加载容量…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            } else if (app.capacityItems.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.capacityError ?: "暂无容量数据",
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton("重新加载", {
                        app.scope.launch { app.loadCapacity(force = true) }
                    }, height = 32.dp)
                }
            } else {
                GridRows(app.capacityItems.map { it.toCalDay() }, columns = 7, gap = 10.dp) { day ->
                    CalDayCard(day)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot("空闲", "free", "余量充足")
                LegendDot("紧张", "busy", "余量 ≤ 1")
                LegendDot("满员", "full", "不可下单")
                Text("节假日", color = PatNestColors.Danger, fontSize = 12.5.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Panel(title = "容量与定价规则", subtitle = "家庭 3 只 / 门店 5 只") {
            if (app.priceRules.isNotEmpty()) {
                app.priceRules.forEach { rule ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${rule.name}（${rule.startDate} ~ ${rule.endDate}）",
                            color = PatNestColors.Ink2, fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "×${rule.multiplier}",
                            color = PatNestColors.BrandInk, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    ThinDivider()
                }
            }
            PriceRuleRow("家庭寄养 · 日单价", "¥130/天")
            ThinDivider()
            PriceRuleRow("门店寄养 · 日单价", "¥180/天")
            ThinDivider()
            PriceRuleRow("节假日上浮", "+20%")
            ThinDivider()
            PriceRuleRow("上门喂养 · 次单价", "¥60/次")
            Spacer(Modifier.height(14.dp))
            PrimaryButton("保存定价", { app.toast("定价已保存") })
        }
    }
}

private fun CapacityItemDto.toCalDay(): CalDay {
    val week = runCatching {
        when (java.time.LocalDate.parse(date).dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }.getOrDefault("")
    val kind = when {
        full -> "full"
        available <= 1 -> "busy"
        else -> "free"
    }
    return CalDay(
        date = if (date.length >= 10) date.substring(5).replace('-', '/') else date,
        week = week,
        slots = listOf("已用 $used/$capacity" to kind),
    )
}

@Composable
private fun LegendDot(label: String, kind: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SlotTag(label, kind)
        Text(desc, color = PatNestColors.Ink2, fontSize = 12.5.sp)
    }
}

@Composable
private fun PriceRuleRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PatNestColors.Ink2, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = PatNestColors.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
