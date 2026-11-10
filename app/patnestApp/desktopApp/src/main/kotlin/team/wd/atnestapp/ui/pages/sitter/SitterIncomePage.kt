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
import kotlin.math.roundToInt
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.ChipRow
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
        // 每次进页面都强制取一次：订单结束 / 主人评价都会改金额，缓存会显示旧值；
        // 结算记录同理——后台打款或驳回后，非 force 的加载会一直显示旧的 pending
        if (app.loggedIn) app.loadEarnings(force = true)
        if (app.loggedIn) app.loadSettlements(force = true)
        if (app.loggedIn) app.loadSettleWindow(force = true)
    }

    val summary = app.earnings?.summary
    val today = java.time.LocalDate.now()
    // 快捷区间：不想让机构自己输日期，这四个覆盖绝大多数场景
    val presets = listOf(
        "近 7 天" to (today.minusDays(6).toString() to today.toString()),
        "近 30 天" to (today.minusDays(29).toString() to today.toString()),
        "本月" to (today.withDayOfMonth(1).toString() to today.toString()),
        "上月" to (
            today.minusMonths(1).withDayOfMonth(1).toString() to
                today.withDayOfMonth(1).minusDays(1).toString()
        ),
    )
    // 空串表示走后端默认（近 30 天），此时也要把「近 30 天」点亮
    val activeLabel = presets.firstOrNull {
        it.second.first == app.earningsFrom && it.second.second == app.earningsTo
    }?.first ?: if (app.earningsFrom.isBlank()) "近 30 天" else ""

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        // 佣金比例已改成后台可配（全局默认 + 单机构覆盖），不能在这里写死 10%
        PageHeader(
            "收益结算",
            if (summary != null) {
                val p = app.earnings?.period
                "${p?.from.orEmpty()} ~ ${p?.to.orEmpty()} · 佣金 ${pctText(summary.commissionRate)}%"
            } else {
                "按完成订单统计"
            },
        ) {
            PrimaryButton("刷新", {
                app.scope.launch { app.loadEarnings(force = true) }
            }, height = 38.dp)
        }

        // 以前固定近 30 天，机构想查上个月或上一周都看不到，只能干等
        ChipRow(
            options = presets.map { it.first },
            selected = activeLabel,
            onSelect = { label ->
                val range = presets.first { it.first == label }.second
                app.scope.launch { app.loadEarningsRange(range.first, range.second) }
            },
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                "区间收入",
                "¥${money2(summary?.grossAmount ?: 0.0)}",
                trend = "订单 ${summary?.orderCount ?: 0} 笔",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "平台佣金",
                "¥${money2(summary?.commissionAmount ?: 0.0)}",
                trend = summary?.let { "佣金比例 ${pctText(it.commissionRate)}%" } ?: "加载中…",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "应得收入",
                "¥${money2(summary?.netAmount ?: 0.0)}",
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
                            app.earningsError ?: "该区间还没有已完成订单的收益",
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
                            TCell("¥${money2(item.amount)}", 0.9f, mono = true)
                        }
                        ThinDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Panel(
            title = "提现申请",
            subtitle = "区间由系统按上次结算之后自动计算",
        ) {
            val win = app.settleWindow
            if (win != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (win.orderCount > 0) {
                            "可结算 ${win.start} ~ ${win.end} · ${win.orderCount} 笔 · " +
                                "流水 ¥${money2(win.grossAmount)} · 应得 ¥${money2(win.netAmount)}"
                        } else {
                            "暂无待结算收益"
                        },
                        color = PatNestColors.Ink2, fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (win.hasPending) Tag("有待打款", TagKind.Warn)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // 漏掉的月份会被自动带进来，这里说清楚，免得机构以为只能领当月
                    "上次结算之后的收益会一起结算，断掉的月份不会丢",
                    color = PatNestColors.Ink3, fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("申请结算", {
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
                                "¥${money2(item.netAmount)}",
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

/** 佣金比例按百分比展示，最多一位小数：0.1 → 10，0.125 → 12.5 */
internal fun pctText(rate: Double): String {
    val pct = (rate * 1000).roundToInt() / 10.0
    return if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()
}

/**
 * 金额固定两位小数。
 * 原来用 toInt() 截断，99.9 元会显示成 99，机构会以为平台少算了钱。
 */
internal fun money2(amount: Double): String = String.format("%.2f", amount)
