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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.HCell
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.StatCard
import team.wd.atnestapp.ui.components.TCell
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors

@Composable
fun SitterIncomePage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.earnings == null && !app.earningsLoading) app.loadEarnings()
        if (app.loggedIn && app.settlements.isEmpty() && !app.settlementsLoading) app.loadSettlements()
    }

    val summary = app.earnings?.summary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("收益结算", "近 30 天收入明细（平台佣金 10%）") {
            PrimaryButton("刷新", {
                app.scope.launch { app.loadEarnings(force = true) }
            }, height = 38.dp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                "近 30 天收入",
                "¥${summary?.grossAmount?.toInt() ?: 0}",
                trend = "订单 ${summary?.orderCount ?: 0} 笔",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "平台佣金",
                "¥${summary?.commissionAmount?.toInt() ?: 0}",
                trend = "佣金比例 ${((summary?.commissionRate ?: 0.1) * 100).toInt()}%",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "可结算金额",
                "¥${summary?.netAmount?.toInt() ?: 0}",
                trend = "已完成订单",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        Panel(title = "结算明细", subtitle = "按完成订单") {
            when {
                app.earningsLoading && app.earnings == null -> {
                    Text("正在加载收益…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
                }
                app.earnings == null || app.earnings!!.items.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.earningsError ?: "近 30 天还没有已完成订单的收益",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("重新加载", {
                            app.scope.launch { app.loadEarnings(force = true) }
                        }, height = 32.dp)
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        HCell("订单号", 1.6f)
                        HCell("完成日期", 1f)
                        HCell("主人", 1f)
                        HCell("金额", 0.9f)
                    }
                    ThinDivider()
                    app.earnings!!.items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TCell(item.orderNo, 1.6f, mono = true)
                            TCell(item.date, 1f, dim = true)
                            TCell(item.owner, 1f)
                            TCell("¥${item.amount.toInt()}", 0.9f, mono = true)
                        }
                        ThinDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Panel(
            title = "提现申请",
            subtitle = "按本月周期申请结算",
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                PrimaryButton("申请提现", {
                    app.scope.launch { app.applySettlement() }
                }, height = 34.dp)
            }
            when {
                app.settlementsLoading && app.settlements.isEmpty() -> {
                    Text("正在加载结算记录…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
                }
                app.settlements.isEmpty() -> {
                    Text(
                        app.settlementsError ?: "还没有提现记录",
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                    )
                }
                else -> {
                    app.settlements.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${item.periodStart} - ${item.periodEnd}",
                                color = PatNestColors.Ink2, fontSize = 12.5.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "¥${item.netAmount.toInt()}",
                                color = PatNestColors.Ink, fontSize = 12.5.sp,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Tag(
                                when (item.status) {
                                    "pending" -> "待打款"
                                    "paid" -> "已打款"
                                    "rejected" -> "已驳回"
                                    else -> item.status
                                },
                                when (item.status) {
                                    "paid" -> TagKind.Ok
                                    "rejected" -> TagKind.Danger
                                    else -> TagKind.Warn
                                },
                            )
                        }
                        ThinDivider()
                    }
                }
            }
        }
    }
}
