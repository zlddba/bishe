package team.wd.atnestapp.ui.pages.sitter

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
import team.wd.atnestapp.ui.DrawerKind
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.HCell
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.TCell
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch

@Composable
fun SitterOrdersPage(app: AppState) {
    var tab by remember { mutableStateOf("全部") }
    val tabs = listOf("全部", "待确认", "寄养中", "已完成")

    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.receivedOrders.isEmpty() && !app.receivedOrdersLoading) {
            app.loadReceivedOrders()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("接单管理", "确认 / 拒绝预约，管理寄养进度") {
            Tag("待确认 ${app.receivedOrders.count { it.status == "待确认" }}", TagKind.Warn)
        }

        Panel {
            ChipRow(tabs, tab) { tab = it }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                HCell("订单号", 1.5f)
                HCell("主人", 0.9f)
                HCell("宠物", 1.1f)
                HCell("区间", 1f)
                HCell("天数", 0.7f)
                HCell("金额", 0.8f)
                HCell("状态", 0.9f)
                HCell("操作", 0.8f)
            }
            ThinDivider()

            if (app.receivedOrdersLoading && app.receivedOrders.isEmpty()) {
                Text(
                    "正在加载订单…",
                    color = PatNestColors.Ink3, fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else if (app.receivedOrders.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        app.receivedOrdersError ?: "还没有收到订单",
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton("重新加载", {
                        app.scope.launch { app.loadReceivedOrders(force = true) }
                    }, height = 32.dp)
                }
            }
            app.receivedOrders.withIndex()
                .filter { tab == "全部" || it.value.status == tab }
                .forEach { iv ->
                    val order = iv.value
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TCell(order.no, 1.5f, mono = true)
                        TCell(order.owner, 0.9f)
                        TCell(order.pet, 1.1f)
                        TCell(order.range, 1f, dim = true)
                        TCell(order.days, 0.7f)
                        TCell("¥${order.amount}", 0.8f, mono = true)
                        Row(Modifier.weight(0.9f)) {
                            Tag(
                                order.status,
                                when (order.status) {
                                    "寄养中" -> TagKind.Ok
                                    "待确认" -> TagKind.Warn
                                    else -> TagKind.Soft
                                },
                                dot = order.status == "寄养中",
                            )
                        }
                        Row(Modifier.weight(0.8f)) {
                            LinkText("详情") { app.drawer = DrawerKind.SbOrderDetail(iv.index) }
                        }
                    }
                    ThinDivider()
                }
        }
    }
}
