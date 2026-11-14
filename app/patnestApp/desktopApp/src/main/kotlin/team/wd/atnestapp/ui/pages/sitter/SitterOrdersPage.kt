package team.wd.atnestapp.ui.pages.sitter

import team.wd.atnestapp.utils.formatMoney
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
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.DangerLinkText
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

    // 按后端原始状态过滤，别拿中文展示文案当条件：文案一改筛选就整体失效
    fun matches(rawStatus: String): Boolean = when (tab) {
        "待确认" -> rawStatus == "confirmed"
        "寄养中" -> rawStatus == "accepted" || rawStatus == "staying"
        "已完成" -> rawStatus == "completed" || rawStatus == "evaluated"
        else -> true
    }

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
            Tag("待确认 ${app.receivedOrders.count { it.rawStatus == "confirmed" }}", TagKind.Warn)
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
                HCell("操作", 1.7f)
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
                .filter { matches(it.value.rawStatus) }
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
                        TCell("¥${formatMoney(order.amount)}", 0.8f, mono = true)
                        Row(Modifier.weight(0.9f)) {
                            Tag(
                                order.status,
                                when (order.rawStatus) {
                                    "staying", "accepted" -> TagKind.Ok
                                    "confirmed" -> TagKind.Warn
                                    else -> TagKind.Soft
                                },
                                dot = order.rawStatus == "staying",
                            )
                        }
                        Row(
                            Modifier.weight(1.7f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinkText("详情") { app.drawer = DrawerKind.SbOrderDetail(iv.index) }
                            if (order.rawStatus == "confirmed") {
                                Spacer(Modifier.width(14.dp))
                                LinkText("接单") {
                                    app.modal = ModalKind.Confirm(
                                        "确认接单",
                                        "确认接受订单 ${order.no}？接单后将占用对应日期的接待容量。",
                                    ) {
                                        app.scope.launch { app.acceptReceivedOrder(order.id) }
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                DangerLinkText("拒绝") {
                                    app.modal = ModalKind.Confirm(
                                        "拒绝订单",
                                        "确认拒绝订单 ${order.no}？已支付金额将原路退回，占用的容量会释放。",
                                    ) {
                                        app.scope.launch { app.rejectReceivedOrder(order.id) }
                                    }
                                }
                            }
                        }
                    }
                    ThinDivider()
                }
            if (app.receivedOrders.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                if (app.receivedOrdersHasMore) {
                    PrimaryButton(
                        if (app.receivedOrdersLoadingMore) "正在加载…" else "加载更多订单",
                        { app.scope.launch { app.loadMoreReceivedOrders() } },
                        height = 34.dp,
                    )
                } else {
                    Text("没有更多订单了", color = PatNestColors.Ink3, fontSize = 11.5.sp)
                }
            }
        }
    }
}
