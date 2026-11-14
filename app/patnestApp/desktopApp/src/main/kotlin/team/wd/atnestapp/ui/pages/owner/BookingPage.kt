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
import androidx.compose.runtime.LaunchedEffect
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
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.KeyValueRow
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PetAvatar
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.RadioDot
import team.wd.atnestapp.ui.components.SitterCard
import team.wd.atnestapp.ui.components.SoftCheckbox
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patCard
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun BookingPage(app: AppState) {
    var step by remember { mutableStateOf(1) }
    var selectedSitter by remember { mutableStateOf(app.sitterIndex) }
    var startDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(1).toString()) }
    var endDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(4).toString()) }
    var petIndex by remember { mutableStateOf(0) }
    var agreed by remember { mutableStateOf(false) }
    // 积分输入框的文本态；真正参与下单的是 app.selectedPoints
    var pointsText by remember { mutableStateOf("0") }
    // 支付方式：balance=钱包余额（优先推荐），mock=模拟支付通道
    var payMethod by remember { mutableStateOf("balance") }

    val sitter = app.sitters.getOrNull(selectedSitter)
    if (sitter == null) {
        Column(Modifier.fillMaxSize().padding(26.dp)) {
            PageHeader("预约下单", "正在加载机构信息")
            Text(
                app.sittersError ?: "请稍候…",
                color = PatNestColors.Ink2, fontSize = 13.sp,
            )
        }
        return
    }
    val days = remember(startDate, endDate) { calcDays(startDate, endDate) }
    val subtotal = sitter.price * days
    val couponDiscount = when (app.selectedCouponType) {
        "fixed" -> app.selectedCouponValue
        "percent" -> subtotal * app.selectedCouponValue / 100.0
        else -> 0.0
    }
    val payable = (subtotal - couponDiscount).coerceAtLeast(0.0)
    val pet = app.pets.getOrNull(petIndex)

    LaunchedEffect(app.pets.size) {
        if (petIndex > app.pets.lastIndex) petIndex = 0
    }

    // 进页面先取积分余额与钱包余额，再让后端按同一套口径算一遍价（含节假日上浮、券、积分）
    LaunchedEffect(Unit) {
        app.loadPoints()
        app.loadWallet()
    }
    LaunchedEffect(startDate, endDate, pet?.id, app.selectedCouponId, app.selectedPoints) {
        val chosen = pet
        if (chosen == null || sitter.id <= 0) {
            app.estimate = null
        } else {
            app.refreshEstimate(sitter.id, startDate, endDate, listOf(chosen.id))
        }
    }
    val quote = app.estimate

    // 余额不足以钱包支付时，自动切回模拟支付，避免提交时才报错
    LaunchedEffect(app.walletBalance, quote, payable) {
        val need = quote?.totalAmount ?: payable
        if (app.walletBalance < need && payMethod == "balance") payMethod = "mock"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("预约下单", "选机构 → 选日期与宠物 → 确认费用")

        BookingStepper(step)
        Spacer(Modifier.height(20.dp))
        when (step) {
            1 -> {
                when {
                    app.sittersLoading && app.sitters.isEmpty() -> {
                        Text("正在加载机构…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
                    }
                    app.sitters.isEmpty() -> {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    app.sittersError ?: "暂无可预约的机构",
                                    color = PatNestColors.Ink2, fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                PrimaryButton("重新加载", {
                                    app.scope.launch { app.loadSitters(force = true) }
                                }, height = 32.dp)
                            }
                        }
                    }
                    else -> {
                        GridRows(app.sitters.withIndex().toList(), columns = 2, gap = 16.dp) { iv ->
                            Box {
                                SitterCard(
                                    iv.value,
                                    selectable = true,
                                    onOpen = {
                                        selectedSitter = iv.index
                                        step = 2
                                    },
                                    onBook = {
                                        selectedSitter = iv.index
                                        step = 2
                                    },
                                )
                                if (iv.index == selectedSitter) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .border(1.5.dp, PatNestColors.Brand, PatNestShape.Lg),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel(modifier = Modifier.weight(1.3f)) {
                        PanelHeaderInPage("选择寄养日期", "天数 = 结束 − 开始（含首不含尾）")
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                FieldLabel("开始日期")
                                SoftTextField(startDate, { startDate = it }, Modifier.fillMaxWidth())
                            }
                            Column(Modifier.weight(1f)) {
                                FieldLabel("结束日期")
                                SoftTextField(endDate, { endDate = it }, Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tag("可同时容纳 ${sitter.cap}", TagKind.Ok)
                            Tag("¥${formatMoney(sitter.price)}/${sitter.unit}", TagKind.Brand)
                        }
                        Spacer(Modifier.height(18.dp))
                        PanelHeaderInPage("选择宠物", "单选")
                        if (app.pets.isEmpty()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (app.petsLoading) "正在加载宠物档案…" else "还没有宠物档案，先去添加一只吧",
                                    color = PatNestColors.Ink2, fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                PrimaryButton("新增宠物", { app.go(AppPage.PetEdit) }, height = 34.dp)
                            }
                        } else {
                        GridRows(app.pets.withIndex().toList(), columns = 2, gap = 10.dp) { iv ->
                            val selected = iv.index == petIndex
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(PatNestShape.Md)
                                    .background(if (selected) PatNestColors.Brand50 else Color.White)
                                    .border(
                                        1.5.dp,
                                        if (selected) PatNestColors.Brand else PatNestColors.Line,
                                        PatNestShape.Md,
                                    )
                                    .clickable { petIndex = iv.index }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PetAvatar(34.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(iv.value.name, color = PatNestColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${iv.value.breed} · ${iv.value.vaccine}",
                                        color = PatNestColors.Ink3, fontSize = 11.5.sp,
                                    )
                                }
                                Text(
                                    if (selected) "已选" else "选择",
                                    color = PatNestColors.BrandInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        }
                    }
                    Panel(title = "费用预估", subtitle = "实时计算", modifier = Modifier.weight(1f)) {
                        KeyValueRow("机构", sitter.name)
                        KeyValueRow("寄养天数", "$days 天")
                        KeyValueRow("日单价", "¥${formatMoney(sitter.price)}")
                        if (quote != null) {
                            // 服务端明细：节假日上浮、券、积分都算进去了
                            quote.breakdown.forEach { line ->
                                val abs = if (line.amount < 0) -line.amount else line.amount
                                KeyValueRow(
                                    line.label.ifBlank { if (line.type == "base") "寄养费用" else line.type },
                                    (if (line.amount < 0) "-¥" else "¥") + moneyOf(abs),
                                    valueColor = if (line.amount < 0) PatNestColors.Ok else PatNestColors.Ink,
                                )
                            }
                            KeyValueRow("小计", "¥${moneyOf(quote.subtotal)}")
                            if (app.selectedCouponId != 0L) {
                                LinkText("不使用优惠券") {
                                    app.selectedCouponId = 0L
                                    app.selectedCouponTitle = ""
                                    app.selectedCouponType = ""
                                    app.selectedCouponValue = 0.0
                                }
                            }
                        } else {
                            KeyValueRow("小计", "¥${formatMoney(subtotal)}")
                            if (app.selectedCouponId != 0L) {
                                KeyValueRow(
                                    "优惠券（${app.selectedCouponTitle}）",
                                    "-¥${couponDiscount.toInt()}",
                                    valueColor = PatNestColors.Ok,
                                )
                                LinkText("不使用优惠券") {
                                    app.selectedCouponId = 0L
                                    app.selectedCouponTitle = ""
                                    app.selectedCouponType = ""
                                    app.selectedCouponValue = 0.0
                                }
                            }
                            Text(
                                if (app.estimating) "正在向服务端取报价…" else "本地估算，提交时以服务端报价为准",
                                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        // 积分抵扣：最多用掉当前余额，且不超过应付金额
                        if (app.pointsBalance > 0) {
                            ThinDivider(Modifier.padding(top = 10.dp))
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("积分抵扣", color = PatNestColors.Ink2, fontSize = 12.5.sp)
                                Spacer(Modifier.weight(1f))
                                SoftTextField(
                                    pointsText,
                                    { text ->
                                        pointsText = text.filter { it.isDigit() }.take(7)
                                        app.selectedPoints = (pointsText.toIntOrNull() ?: 0).coerceAtMost(app.pointsBalance)
                                        pointsText = app.selectedPoints.toString()
                                    },
                                    Modifier.width(96.dp),
                                    placeholder = "0",
                                )
                                Text("/ ${app.pointsBalance} 分", color = PatNestColors.Ink3, fontSize = 12.sp)
                            }
                        }
                        ThinDivider(Modifier.padding(top = 10.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("应付", color = PatNestColors.Ink2, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "¥${moneyOf(quote?.totalAmount ?: payable)}",
                                color = PatNestColors.BrandInk, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        PrimaryButton("下一步：确认订单", {
                            val start = runCatching { LocalDate.parse(startDate) }.getOrNull()
                            val end = runCatching { LocalDate.parse(endDate) }.getOrNull()
                            when {
                                start == null || end == null -> app.toast("请填写正确的日期（YYYY-MM-DD）")
                                !end.isAfter(start) -> app.toast("结束日期需晚于开始日期")
                                else -> step = 3
                            }
                        }, Modifier.fillMaxWidth().padding(top = 14.dp), height = 44.dp)
                    }
                }
            }
            else -> {
                Box(Modifier.width(560.dp)) {
                    Panel(title = "确认订单") {
                        KeyValueRow("机构", sitter.name)
                        KeyValueRow("寄养区间", "${displayDate(startDate)} - ${displayDate(endDate)}")
                        KeyValueRow("宠物", pet?.let { "${it.name}（${it.breed}）" } ?: "未选择宠物")
                        if (quote != null) {
                            // 与下单接口同一口径：基础费用直接取服务端 breakdown 的 base 行。
                            // 本地 sitter.price 是取整后的展示价，拿它乘天数会和报价对不上。
                            val base = quote.breakdown.firstOrNull { it.type == "base" }?.amount
                                ?: subtotal.toDouble()
                            KeyValueRow("寄养费用", "¥${moneyOf(base)}")
                            quote.breakdown.filter { it.type != "base" }.forEach { line ->
                                val abs = if (line.amount < 0) -line.amount else line.amount
                                KeyValueRow(
                                    line.label.ifBlank { if (line.type == "points") "积分抵扣" else "其他" },
                                    (if (line.amount < 0) "-¥" else "¥") + moneyOf(abs),
                                    valueColor = if (line.amount < 0) PatNestColors.Ok else PatNestColors.Ink,
                                )
                            }
                            KeyValueRow("应付", "¥${moneyOf(quote.totalAmount)}", valueColor = PatNestColors.BrandInk, bold = true)
                        } else {
                            KeyValueRow("费用构成", "${formatMoney(sitter.price)} × $days 天 = ¥${formatMoney(subtotal)}")
                            if (app.selectedCouponId != 0L) {
                                KeyValueRow(
                                    "优惠",
                                    "-¥${couponDiscount.toInt()}（${app.selectedCouponTitle}）",
                                    valueColor = PatNestColors.Ok,
                                )
                            }
                            KeyValueRow("应付", "¥${formatMoney(payable)}", valueColor = PatNestColors.BrandInk, bold = true)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SoftCheckbox(agreed, { agreed = it })
                            Text("我已阅读并同意《寄养服务协议》与《宠物健康告知书》", color = PatNestColors.Ink2, fontSize = 12.5.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("上一步", { step = 2 }, Modifier.weight(1f))
                            PrimaryButton("提交订单", {
                                when {
                                    !agreed -> app.toast("请先阅读并同意服务协议")
                                    pet == null -> app.toast("请选择寄养宠物")
                                    else -> app.scope.launch {
                                        val orderId = app.createOrderRaw(sitter.id, startDate, endDate, pet.id) ?: return@launch
                                        if (payMethod == "balance") {
                                            val ok = app.payOrder(orderId, "balance")
                                            if (ok) app.go(AppPage.Orders)
                                        } else {
                                            app.toast("订单已创建，请在 30 分钟内完成支付")
                                            app.go(AppPage.Pay)
                                        }
                                    }
                                }
                            }, Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 支付方式：钱包余额优先，余额不足时禁用并提示充值
                val payableAmount = quote?.totalAmount ?: payable
                val balanceEnough = app.walletBalance >= payableAmount
                Panel(title = "支付方式", subtitle = "可用钱包余额直接支付订单") {
                    PayOptionRow(
                        title = "钱包余额",
                        sub = if (balanceEnough) "可用余额 ¥${moneyOf(app.walletBalance)}" else "余额不足，请先充值",
                        icon = AppIcon.Card,
                        selected = payMethod == "balance",
                        enabled = balanceEnough,
                        onClick = { if (balanceEnough) payMethod = "balance" else app.go(AppPage.Wallet) },
                    )
                    Spacer(Modifier.height(10.dp))
                    PayOptionRow(
                        title = "模拟支付通道",
                        sub = "当前为模拟支付（微信支付资质待申请）",
                        icon = AppIcon.CheckCircle,
                        selected = payMethod == "mock",
                        enabled = true,
                        onClick = { payMethod = "mock" },
                    )
                }
            }
        }
    }
}

/** 支付方式单选行：选中态用品牌粉描边，禁用态置灰 */
@Composable
private fun PayOptionRow(
    title: String,
    sub: String,
    icon: AppIcon,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PatNestColors.Brand50 else Color.White
    val border = if (selected) PatNestColors.Brand else PatNestColors.Line
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PatNestShape.Md)
            .background(if (enabled) bg else PatNestColors.Soft)
            .border(1.5.dp, if (enabled) border else PatNestColors.Line, PatNestShape.Md)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
                .background(if (enabled) PatNestColors.Brand50 else PatNestColors.Line2),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(18.dp), if (enabled) PatNestColors.Brand else PatNestColors.Ink3)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) PatNestColors.Ink else PatNestColors.Ink3, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = PatNestColors.Ink3, fontSize = 11.5.sp)
        }
        // 复用全局单选圆点
        RadioDot(selected)
    }
}

@Composable
private fun PanelHeaderInPage(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(title, color = PatNestColors.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = PatNestColors.Ink3, fontSize = 12.sp)
    }
}

@Composable
private fun BookingStepper(step: Int) {
    val steps = listOf("选择机构", "日期与宠物", "确认与提交")
    Row(
        Modifier
            .fillMaxWidth()
            .patCard(PatNestShape.Lg, 9.dp)
            .padding(horizontal = 26.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { i, label ->
            val n = i + 1
            val done = n < step
            val active = n <= step
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> PatNestColors.Brand50
                                active -> PatNestColors.Brand
                                else -> PatNestColors.Soft
                            },
                        )
                        .border(
                            1.dp,
                            when {
                                done -> PatNestColors.Brand200
                                active -> PatNestColors.Brand
                                else -> PatNestColors.Line
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        AppIcon(AppIcon.Check, Modifier.size(13.dp), PatNestColors.BrandInk)
                    } else {
                        Text(
                            "$n",
                            color = if (active) Color.White else PatNestColors.Ink3,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    label,
                    color = if (active) PatNestColors.BrandInk else PatNestColors.Ink3,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (n < steps.size) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (n < step) PatNestColors.Brand200 else PatNestColors.Line),
                )
            }
        }
    }
}

private fun calcDays(start: String, end: String): Int = runCatching {
    val startDate = LocalDate.parse(start)
    val endDate = LocalDate.parse(end)
    ChronoUnit.DAYS.between(startDate, endDate).toInt()
}.getOrDefault(0).coerceAtLeast(1)

private fun displayDate(value: String): String =
    if (value.length >= 10) value.substring(5).replace('-', '/') else value

/** 金额展示：整钱不带小数，小数最多两位；不用 String.format 以免跟随系统 locale */
private fun moneyOf(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
