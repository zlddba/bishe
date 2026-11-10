package team.wd.atnestapp.ui.pages.sitter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.StatCard
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors

@Composable
fun SitterOverviewPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (!app.loggedIn) return@LaunchedEffect
        if (app.receivedOrders.isEmpty() && !app.receivedOrdersLoading) app.loadReceivedOrders()
        if (app.capacityItems.isEmpty() && !app.capacityLoading) app.loadCapacity()
        if (!app.earningsLoading) app.loadEarnings(force = true)
        if (app.myShop == null && !app.shopLoading) app.loadMyShop()
        if (app.sitterStats == null && !app.sitterStatsLoading) app.loadSitterStats()
    }

    val pending = app.sitterStats?.pending ?: app.receivedOrders.count { it.status == "待确认" }
    val active = app.sitterStats?.active
        ?: app.receivedOrders.count { it.status == "寄养中" || it.status == "已接单" }
    val totalUsed = app.capacityItems.sumOf { it.used }
    val totalCapacity = app.capacityItems.sumOf { it.capacity }
    val occupancy = if (totalCapacity > 0) totalUsed * 100 / totalCapacity else 0

    // 趋势优先用后端统计接口，拿不到时退回本地按订单日期统计
    val statsTrend = app.sitterStats?.trend.orEmpty()
    val trendDays = if (statsTrend.isNotEmpty()) {
        statsTrend.mapNotNull { runCatching { java.time.LocalDate.parse(it.date) }.getOrNull() }
    } else {
        (6 downTo 0).map { offset -> java.time.LocalDate.now().minusDays(offset.toLong()) }
    }
    val trendCounts = if (statsTrend.isNotEmpty()) {
        statsTrend.map { it.orders }
    } else {
        trendDays.map { day -> app.receivedOrders.count { it.createdAt.startsWith(day.toString()) } }
    }
    val trendMax = maxOf(1, trendCounts.maxOrNull() ?: 0)

    // 待办来自真实待确认订单、进行中订单和容量余量
    val fullDays = app.capacityItems.filter { it.full }
    val tightDays = app.capacityItems.filter { !it.full && it.available <= 1 }
    val todos = buildList {
        if (pending > 0) add("$pending 个订单待确认" to "warn")
        if (active > 0) add("$active 个寄养进行中" to "info")
        if (fullDays.isNotEmpty()) add("${fullDays.first().date} 等 ${fullDays.size} 天床位满员" to "danger")
        if (tightDays.isNotEmpty()) add("${tightDays.size} 天床位余量紧张" to "warn")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        val shopStatus = app.myShop?.status ?: "active"
        PageHeader("经营概览", "${app.myShop?.name?.takeIf { it.isNotBlank() } ?: "本机构"} · 本机构数据") {
            Tag(
                when (shopStatus) {
                    "active" -> "营业中"
                    "pending" -> "待审核"
                    "inactive" -> "已下架"
                    "banned" -> "已封禁"
                    else -> shopStatus
                },
                when (shopStatus) {
                    "active" -> TagKind.Ok
                    "pending" -> TagKind.Warn
                    else -> TagKind.Danger
                },
                dot = shopStatus == "active",
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("待确认订单", "$pending", "单", "需要尽快处理", modifier = Modifier.weight(1f), icon = AppIcon.Orders)
            StatCard("进行中寄养", "$active", "单", "已接单 + 寄养中", modifier = Modifier.weight(1f), icon = AppIcon.Paw)
            StatCard(
                // 收益页可以切区间，这里跟着显示的是同一个区间的数据，
                // 所以标题不能写死「近 30 天」
                "区间收入",
                "¥${money2(app.earnings?.summary?.grossAmount ?: 0.0)}",
                // 佣金已改成后台可配，不能再写死 10%
                trend = app.earnings?.summary?.let { "佣金 ${pctText(it.commissionRate)}%" } ?: "近 30 天",
                modifier = Modifier.weight(1f),
                icon = AppIcon.Bank,
            )
            StatCard("近 7 日入住率", "$occupancy%", trend = "已用 $totalUsed / 总 $totalCapacity", modifier = Modifier.weight(1f), icon = AppIcon.Home)
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(title = "近 7 日订单趋势", subtitle = "按订单创建日期统计", modifier = Modifier.weight(1.6f)) {
                Row(
                    Modifier.fillMaxWidth().height(180.dp).padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    trendDays.forEachIndexed { i, day ->
                        val isToday = i == trendDays.lastIndex
                        val ratio = (trendCounts[i].toFloat() / trendMax).coerceIn(0.06f, 1f)
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                "${trendCounts[i]}",
                                color = PatNestColors.Ink3, fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(ratio)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (isToday) {
                                            Brush.verticalGradient(listOf(PatNestColors.Brand, PatNestColors.Brand900))
                                        } else {
                                            Brush.verticalGradient(listOf(PatNestColors.BrandSoft, PatNestColors.BrandSoft))
                                        },
                                    ),
                            )
                            Text(
                                if (isToday) "今" else day.toString().substring(5).replace('-', '/'),
                                color = PatNestColors.Ink3, fontSize = 10.5.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(PatNestColors.Brand))
                    Text("订单数", color = PatNestColors.Ink3, fontSize = 11.5.sp)
                }
            }

            Panel(title = "待办事项", subtitle = "需要处理", modifier = Modifier.weight(1f)) {
                if (todos.isEmpty()) {
                    Text("暂无待办，一切正常", color = PatNestColors.Ink2, fontSize = 12.5.sp)
                } else {
                    todos.forEachIndexed { index, (text, kind) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (kind) {
                                            "warn" -> PatNestColors.Warn
                                            "info" -> PatNestColors.Info
                                            "danger" -> PatNestColors.Danger
                                            else -> PatNestColors.Ok
                                        },
                                    ),
                            )
                            Text(text, color = PatNestColors.Ink2, fontSize = 12.5.sp)
                        }
                        if (index < todos.lastIndex) ThinDivider()
                    }
                }
            }
        }
    }
}
