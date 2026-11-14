package team.wd.atnestapp.ui.pages.owner

import team.wd.atnestapp.utils.formatMoney
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.KeyValueRow
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.RadioDot
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import kotlinx.coroutines.launch

@Composable
fun PaymentPage(app: AppState) {
    val order = app.orders.getOrNull(app.orderIndex)
    if (order == null) {
        Column(Modifier.fillMaxSize().padding(26.dp)) {
            PageHeader("收银台", "订单数据加载中")
            Text(app.ordersError ?: "订单信息加载中…", color = PatNestColors.Ink2, fontSize = 13.sp)
        }
        return
    }
    // 从 0 开始：methods 只有一项（index 0），原先初值 1 会让唯一一行永远显示「未选中」
    var method by remember { mutableStateOf(0) }
    var paid by remember { mutableStateOf(false) }

    val methods = listOf(
        PayMethod("模拟支付通道", "当前为模拟支付（微信支付资质待申请）", AppIcon.CheckCircle, PatNestColors.InfoSoft, PatNestColors.Info),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("收银台", "确认订单后完成支付") {
            LinkText("返回订单") { app.go(AppPage.Orders) }
        }

        Column(Modifier.width(560.dp)) {
            Panel(title = "订单摘要") {
                KeyValueRow("订单号", order.no)
                KeyValueRow("机构", order.org)
                KeyValueRow("宠物", order.pet)
                KeyValueRow("寄养区间", order.range)
                KeyValueRow("费用构成", order.fee)
            }

            Spacer(Modifier.height(14.dp))

            Panel(title = "支付方式") {
                methods.forEachIndexed { index, m ->
                    val selected = index == method
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(PatNestShape.Md)
                            .background(if (selected) PatNestColors.Brand50 else Color.White)
                            .border(1.5.dp, if (selected) PatNestColors.Brand else PatNestColors.Line, PatNestShape.Md)
                            .clickable { method = index }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(m.bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(m.icon, Modifier.size(18.dp), m.fg)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = PatNestColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(m.sub, color = PatNestColors.Ink3, fontSize = 11.5.sp)
                        }
                        RadioDot(selected)
                    }
                    if (index < methods.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("应付金额", color = PatNestColors.Ink3, fontSize = 12.sp)
                        Text(
                            "¥${formatMoney(order.amount)}",
                            color = PatNestColors.BrandInk, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    PrimaryButton("确认支付", {
                        app.scope.launch { if (app.payOrder(order.id)) paid = true }
                    }, height = 46.dp)
                }
            }

            if (paid) {
                Spacer(Modifier.height(14.dp))
                Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 34.dp)) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(PatNestColors.OkSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(AppIcon.Check, Modifier.size(26.dp), PatNestColors.Ok)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("支付成功", color = PatNestColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "订单已进入「待确认」状态，等待机构确认后开始寄养",
                            color = PatNestColors.Ink2, fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(
                            Modifier.padding(top = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            GhostButton("查看订单", { app.go(AppPage.Orders) })
                            PrimaryButton("订阅动态提醒", { app.go(AppPage.Dynamics) })
                        }
                    }
                }
            }
        }
    }
}

private data class PayMethod(
    val title: String, val sub: String,
    val icon: AppIcon, val bg: Color, val fg: Color,
)
