package team.wd.atnestapp.ui.pages.owner

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
fun OrdersPage(app: AppState) {
    var tab by remember { mutableStateOf("全部") }
    val tabs = listOf("全部", "待支付", "待确认", "已接单", "寄养中", "已结束")

    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.orders.isEmpty() && !app.ordersLoading) app.loadOrders()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("我的订单", "待支付 → 待确认 → 寄养中 → 已结束 → 已评价")

        Panel {
            ChipRow(tabs, tab) { tab = it }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                HCell("订单号", 1.5f)
                HCell("机构", 1f)
                HCell("宠物", 1.1f)
                HCell("区间", 1f)
                HCell("天数", 0.7f)
                HCell("金额", 0.8f)
                HCell("状态", 0.9f)
                HCell("操作", 1.9f)
            }
            ThinDivider()

            if (app.ordersLoading && app.orders.isEmpty()) {
                Text(
                    "正在加载订单…",
                    color = PatNestColors.Ink3, fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else if (app.orders.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        app.ordersError ?: "还没有订单，去预约一次寄养吧",
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton("重新加载", {
                        app.scope.launch { app.loadOrders(force = true) }
                    }, height = 32.dp)
                }
            }
            app.orders.withIndex()
                .filter { tab == "全部" || it.value.status == tab }
                .forEach { iv ->
                    val order = iv.value
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TCell(order.no, 1.5f, mono = true)
                        TCell(order.org, 1f)
                        TCell(order.pet, 1.1f)
                        TCell(order.range, 1f, dim = true)
                        TCell(order.days, 0.7f, dim = order.days.contains("次"))
                        TCell("¥${order.amount}", 0.8f, mono = true)
                        Row(Modifier.weight(0.9f)) {
                            Tag(
                                order.status,
                                when (order.status) {
                                    "寄养中" -> TagKind.Ok
                                    "待支付" -> TagKind.Warn
                                    "已结束" -> TagKind.Brand
                                    else -> TagKind.Soft
                                },
                                dot = order.status == "寄养中",
                            )
                        }
                        Row(
                            Modifier.weight(1.9f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LinkText("详情") { app.drawer = DrawerKind.OrderDetail(iv.index) }
                            // 按钮按后端原始状态出现，规则与服务端一致：
                            // 改期 pending_pay/confirmed/accepted；续住已支付且未结束；退款需已支付
                            when (order.rawStatus) {
                                "pending_pay" -> {
                                    PrimaryButton("去支付", { app.openPay(iv.index) }, height = 30.dp)
                                    LinkText("取消") { app.modal = ModalKind.CancelOrder(order.id, order.no) }
                                    LinkText("改期") {
                                        app.modal = ModalKind.Reschedule(order.id, order.no, order.startDate, order.endDate)
                                    }
                                }
                                "confirmed", "accepted" -> {
                                    LinkText("改期") {
                                        app.modal = ModalKind.Reschedule(order.id, order.no, order.startDate, order.endDate)
                                    }
                                    if (order.paid) {
                                        LinkText("续住") { app.modal = ModalKind.Renew(order.id, order.no, order.endDate, order.amount) }
                                        LinkText("退款") { app.modal = ModalKind.Refund(order.id, order.no, order.amount) }
                                    }
                                }
                                "staying" -> {
                                    if (order.paid) {
                                        LinkText("续住") { app.modal = ModalKind.Renew(order.id, order.no, order.endDate, order.amount) }
                                        LinkText("退款") { app.modal = ModalKind.Refund(order.id, order.no, order.amount) }
                                    }
                                }
                                "completed" -> PrimaryButton("去评价", { app.openReview(iv.index) }, height = 30.dp)
                            }
                        }
                    }
                    ThinDivider()
                }
        }
    }
}
