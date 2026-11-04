package team.wd.atnestapp.ui.mobile

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.SitterCouponPayload
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.ui.components.AppIcon
import java.time.LocalDate

/** 弹层宿主：由根组件在遮罩层调用，根据 state.sheet 渲染对应面板。key 保证换面板时内部状态重建。 */
@Composable
fun SheetHost(state: MobileState) {
    val current = state.sheet ?: return
    val close = { state.sheet = null }
    key(current) {
        when (current) {
            is MobileSheet.Message -> MessageSheet(current, close)
            is MobileSheet.Reason -> ReasonSheet(current, close)
            is MobileSheet.DateRange -> DateRangeSheet(current, close)
            is MobileSheet.DatePick -> DatePickSheet(current, close)
            is MobileSheet.Review -> ReviewSheet(current, close)
            is MobileSheet.Form -> FormSheet(current, close)
            is MobileSheet.CouponForm -> CouponFormSheet(state, current, close)
            is MobileSheet.AddonForm -> AddonFormSheet(state, current, close)
            is MobileSheet.PriceRuleForm -> PriceRuleSheet(state, current, close)
            is MobileSheet.CapacityForm -> CapacitySheet(state, current, close)
            is MobileSheet.ApplySitter -> ApplySitterSheet(state, close)
            is MobileSheet.Ticket -> TicketSheet(state, close)
            is MobileSheet.Photo -> PhotoSheet(current, close)
            is MobileSheet.CouponPicker -> CouponPickerSheet(state, close)
        }
    }
}

@Composable
private fun MessageSheet(sheet: MobileSheet.Message, close: () -> Unit) {
    SheetOverlay(title = sheet.title, onClose = close) {
        Text(sheet.body, color = MobileColors.Ink2, fontSize = 13.5.sp)
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = sheet.actionLabel,
            onClick = {
                sheet.onAction?.invoke()
                close()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton("取消", close, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReasonSheet(sheet: MobileSheet.Reason, close: () -> Unit) {
    var text by remember { mutableStateOf(sheet.initial) }
    SheetOverlay(title = sheet.title, onClose = close) {
        Text(sheet.hint, color = MobileColors.Ink3, fontSize = 12.sp)
        MsField(text, { text = it }, multiline = true, placeholder = sheet.hint)
        PrimaryButton(
            text = sheet.submitLabel,
            onClick = {
                sheet.onSubmit(text)
                close()
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FormSheet(sheet: MobileSheet.Form, close: () -> Unit) {
    val values = remember { sheet.fields.map { f -> mutableStateOf(f.initial) } }
    SheetOverlay(title = sheet.title, onClose = close) {
        sheet.fields.forEachIndexed { index, field ->
            MsField(
                value = values[index].value,
                onValueChange = { values[index].value = it },
                label = field.label,
                placeholder = field.placeholder,
                multiline = field.multiline,
                numeric = field.numeric,
                secret = field.secret,
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = sheet.submitLabel,
            onClick = {
                sheet.onSubmit(values.map { it.value })
            },
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton("取消", close, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DateRangeSheet(sheet: MobileSheet.DateRange, close: () -> Unit) {
    var start by remember { mutableStateOf(sheet.startDate.ifBlank { LocalDate.now().plusDays(1).toString() }) }
    var end by remember { mutableStateOf(sheet.endDate.ifBlank { LocalDate.now().plusDays(4).toString() }) }
    SheetOverlay(title = sheet.title, onClose = close) {
        if (sheet.note.isNotBlank()) Text(sheet.note, color = MobileColors.Ink3, fontSize = 12.sp)
        DateField("开始日期", start) { day ->
            start = day
            if (runCatching { LocalDate.parse(end) <= LocalDate.parse(day) }.getOrDefault(false)) {
                end = LocalDate.parse(day).plusDays(1).toString()
            }
        }
        DateField("结束日期", end, minValue = start) { day -> end = day }
        PrimaryButton(
            text = sheet.submitLabel,
            onClick = {
                sheet.onSubmit(start, end)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DatePickSheet(sheet: MobileSheet.DatePick, close: () -> Unit) {
    var day by remember { mutableStateOf(sheet.value.ifBlank { LocalDate.now().plusDays(1).toString() }) }
    SheetOverlay(title = sheet.title, onClose = close) {
        if (sheet.note.isNotBlank()) Text(sheet.note, color = MobileColors.Ink3, fontSize = 12.sp)
        DateField("目标日期", day, minValue = sheet.minValue) { day = it }
        PrimaryButton(sheet.submitLabel, { sheet.onSubmit(day) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReviewSheet(sheet: MobileSheet.Review, close: () -> Unit) {
    var overall by remember { mutableStateOf(5) }
    var service by remember { mutableStateOf(5) }
    var environment by remember { mutableStateOf(5) }
    var hygiene by remember { mutableStateOf(4) }
    var comment by remember { mutableStateOf("") }
    SheetOverlay(title = "评价 " + sheet.orderNo, onClose = close) {
        RateLine("总体满意", overall) { overall = it }
        RateLine("服务", service) { service = it }
        RateLine("环境", environment) { environment = it }
        RateLine("卫生", hygiene) { hygiene = it }
        MsField(comment, { comment = it }, placeholder = "这次寄养哪里好、哪里还能更好", multiline = true)
        PrimaryButton(
            text = "提交评价",
            onClick = {
                sheet.onSubmit(overall, service, environment, hygiene, comment)
                close()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RateLine(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MobileColors.Ink2, fontSize = 13.sp, modifier = Modifier.width(78.dp))
        StarRow(value, interactive = true, onChange = onChange, size = 20.dp)
    }
}


@Composable
private fun CouponFormSheet(state: MobileState, sheet: MobileSheet.CouponForm, close: () -> Unit) {
    val editing = sheet.coupon
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var typeLabel by remember { mutableStateOf(couponTypeLabel(editing?.type ?: "fixed")) }
    var value by remember { mutableStateOf(editing?.value?.toString() ?: "10") }
    var minAmount by remember { mutableStateOf((editing?.minAmount ?: 0.0).toString()) }
    var totalQty by remember { mutableStateOf((editing?.totalQty ?: 50).toString()) }
    var perUser by remember { mutableStateOf((editing?.perUser ?: 1).toString()) }
    var endsAt by remember { mutableStateOf(editing?.endsAt?.take(10) ?: LocalDate.now().plusDays(30).toString()) }
    val scope = rememberCoroutineScope()
    SheetOverlay(title = if (editing == null) "发布优惠券" else "编辑优惠券", onClose = close) {
        MsField(title, { title = it }, label = "券名称", placeholder = "例如：新客首单立减")
        Column {
            Text("优惠方式", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            ChipRow(COUPON_TYPES, typeLabel) { typeLabel = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                MsField(value, { value = it }, label = couponValueLabel(typeLabel), numeric = true)
            }
            Box(Modifier.weight(1f)) {
                MsField(minAmount, { minAmount = it }, label = "满减门槛", numeric = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                MsField(totalQty, { totalQty = it }, label = "发放总量", numeric = true)
            }
            Box(Modifier.weight(1f)) {
                MsField(perUser, { perUser = it }, label = "每人限领", numeric = true)
            }
        }
        DateField("有效期至", endsAt, minValue = LocalDate.now().toString()) { endsAt = it }
        PrimaryButton(
            text = if (editing == null) "确认发布" else "保存修改",
            onClick = {
                val payload = SitterCouponPayload(
                    title = title.ifBlank { "寄养优惠券" },
                    type = couponTypeCode(typeLabel),
                    value = value.toDoubleOrNull() ?: 0.0,
                    minAmount = minAmount.toDoubleOrNull() ?: 0.0,
                    totalQty = totalQty.toIntOrNull() ?: 0,
                    perUser = perUser.toIntOrNull() ?: 1,
                    enabled = editing?.enabled ?: true,
                    startsAt = editing?.startsAt.orEmpty(),
                    endsAt = endsAt,
                )
                scope.launch { state.saveSitterCoupon(editing, payload) }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val COUPON_TYPES = listOf("立减", "折扣", "满减")

fun couponTypeLabel(code: String): String = when (code) {
    "percent" -> "折扣"
    "min_amount" -> "满减"
    else -> "立减"
}

fun couponTypeCode(label: String): String = when (label) {
    "折扣" -> "percent"
    "满减" -> "min_amount"
    else -> "fixed"
}

fun couponValueLabel(typeLabel: String): String = when (typeLabel) {
    "折扣" -> "折扣率（0-9）"
    "满减" -> "减免金额"
    else -> "立减金额"
}

@Composable
private fun AddonFormSheet(state: MobileState, sheet: MobileSheet.AddonForm, close: () -> Unit) {
    val editing = sheet.addon
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var price by remember { mutableStateOf(editing?.price?.toString() ?: "") }
    var unit by remember { mutableStateOf(addonUnitLabel(editing?.unit ?: "per_order")) }
    val scope = rememberCoroutineScope()
    SheetOverlay(title = if (editing == null) "新增加购服务" else "编辑加购服务", onClose = close) {
        MsField(name, { name = it }, label = "服务名称", placeholder = "例如：洗澡、陪玩半小时、接送")
        MsField(price, { price = it }, label = "价格", numeric = true)
        Column {
            Text("计费方式", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            ChipRow(listOf("按订单", "按天", "按次"), unit) { unit = it }
        }
        if (editing != null) {
            Text("加购项改名会影响历史订单的显示，价格改动只对新订单生效。", color = MobileColors.Ink3, fontSize = 11.5.sp)
        }
        PrimaryButton(
            text = "保存",
            onClick = {
                scope.launch {
                    val code = when (unit) {
                        "按天" -> "per_day"
                        "按次" -> "per_time"
                        else -> "per_order"
                    }
                    val amount = price.toDoubleOrNull() ?: 0.0
                    if (editing == null) {
                        state.createAddon(name, amount, code)
                    } else {
                        state.updateAddon(editing, name, amount, code)
                    }
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun addonUnitLabel(code: String): String = when (code) {
    "per_day" -> "按天"
    "per_time" -> "按次"
    else -> "按订单"
}


@Composable
private fun CouponPickerSheet(state: MobileState, close: () -> Unit) {
    val usable = state.coupons?.items.orEmpty().filter { it.status == "unused" }
    val sitter = state.selectedSitter
    val localTotal = (sitter?.price?.toDouble() ?: 0.0) * state.bookingDays * state.bookingPetIds.size.coerceAtLeast(1)
    val subtotal = state.estimate?.subtotal ?: localTotal
    SheetOverlay(title = "选择优惠券", onClose = close) {
        if (usable.isEmpty()) {
            EmptyState("券包里暂无可用优惠券", "可以在优惠券页面领取平台或机构发的券")
        } else {
            Text("满足门槛、且适用于本机构的券才能抵扣，下单时只可用一张。", color = MobileColors.Ink3, fontSize = 12.sp)
            usable.forEach { item ->
                val chosen = state.bookingCoupon?.id == item.id
                val fits = subtotal >= item.minAmount
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MobileShape.Lg)
                        .background(if (chosen) MobileColors.Brand50 else MobileColors.PanelSoft)
                        .border(1.dp, if (chosen) MobileColors.Brand200 else MobileColors.Line, MobileShape.Lg)
                        .then(if (fits) Modifier.clickable { state.selectBookingCoupon(item); close() } else Modifier)
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = MobileColors.Ink, fontSize = 14.sp)
                        Text(
                            couponText(item.type, item.value) + if (item.minAmount > 0) "，满 " + state.money(item.minAmount) + " 可用" else "，无门槛",
                            color = if (fits) MobileColors.BrandDeep else MobileColors.Ink3,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            (if (item.issuer.isNotBlank()) item.issuer + " · " else "") + "有效期至 " + item.endsAt.take(10),
                            color = MobileColors.Ink3,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (!fits) {
                        StatusTag("未到门槛", TagTone.Neutral)
                    } else if (chosen) {
                        StatusTag("已选择", TagTone.Brand)
                    } else {
                        StatusTag("可使用", TagTone.Ok)
                    }
                }
            }
        }
        GhostButton(
            text = "不使用优惠券",
            onClick = { state.selectBookingCoupon(null); close() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhotoSheet(sheet: MobileSheet.Photo, close: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF21B1E23)).clickable(onClick = close),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MobileImage(
                url = sheet.url,
                modifier = Modifier.fillMaxWidth().height(400.dp),
                contentDescription = sheet.caption.ifBlank { "寄养动态图片" },
                contentScale = ContentScale.Fit,
            )
            if (sheet.caption.isNotBlank()) {
                Text(sheet.caption, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = close),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Close, Modifier.size(15.dp), Color.White)
            }
        }
    }
}

@Composable
private fun CapacitySheet(state: MobileState, sheet: MobileSheet.CapacityForm, close: () -> Unit) {
    val day = state.capacityItems.firstOrNull { it.date == sheet.date }
    val floor = day?.used ?: 0
    var value by remember(sheet.date, sheet.capacity) { mutableStateOf(sheet.capacity) }
    SheetOverlay(title = sheet.date + " 接待容量", onClose = close) {
        Text("容量是当天最多能接收的宠物数。已在住数量会自动占用，无法调到它以下。", color = MobileColors.Ink3, fontSize = 12.sp)
        if (day != null) {
            MsCard(padding = 12.dp) {
                LineRow("当日已在住", day.used.toString() + " 只")
                LineRow("当前可接待", day.available.toString() + " 只")
            }
        }
        StepperRow(
            label = "可接待数量",
            hint = "最少 " + floor + " 只，最多 200 只",
            value = value,
            minValue = floor,
            maxValue = 200,
            suffix = " 只",
        ) { value = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("当天暂停接单", { value = 0 }, modifier = Modifier.weight(1f))
            GhostButton("恢复当前值", { value = sheet.capacity }, modifier = Modifier.weight(1f))
        }
        if (value < floor) {
            Text("不能低于当日已在住的 " + floor + " 只。", color = MobileColors.Danger, fontSize = 12.sp)
        }
        PrimaryButton(
            text = if (value == 0) "保存并暂停当天接单" else "保存",
            onClick = { state.scope.launch { state.setCapacity(sheet.date, value.coerceAtLeast(floor)) } },
            enabled = value >= floor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PriceRuleSheet(state: MobileState, sheet: MobileSheet.PriceRuleForm, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var end by remember { mutableStateOf(LocalDate.now().plusDays(6).toString()) }
    var multiplier by remember { mutableStateOf("1.5") }
    SheetOverlay(title = "节假日定价", onClose = close) {
        if (sheet.rules.isEmpty()) {
            Text("还没有规则，平时按机构基础日价计费。", color = MobileColors.Ink3, fontSize = 12.5.sp)
        } else {
            Text("已有规则", color = MobileColors.Ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            sheet.rules.forEach { rule ->
                PriceRuleCell(rule, onDelete = { state.scope.launch { state.removePriceRule(rule) } })
            }
        }
        MsDivider()
        Text("新增规则", color = MobileColors.Ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        MsField(name, { name = it }, label = "规则名称", placeholder = "例如：国庆假期")
        DateField("开始日期", start) { day ->
            start = day
            if (runCatching { LocalDate.parse(end).isBefore(LocalDate.parse(day)) }.getOrDefault(false)) {
                end = day
            }
        }
        DateField("结束日期", end, minValue = start) { day -> end = day }
        MsField(
            multiplier,
            { multiplier = it },
            label = "价格倍率",
            placeholder = "1.5 表示按基础价的 1.5 倍收费",
            numeric = true,
        )
        PrimaryButton(
            text = "保存规则",
            onClick = {
                state.scope.launch {
                    state.createPriceRule(name, start, end, multiplier.toDoubleOrNull() ?: 0.0)
                }
            },
            enabled = name.isNotBlank() && (multiplier.toDoubleOrNull() ?: 0.0) > 0.0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val APPLY_TYPES = listOf("家庭寄养", "门店寄养", "上门喂养", "其他")

@Composable
private fun ApplySitterSheet(state: MobileState, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var typeLabel by remember { mutableStateOf("家庭寄养") }
    var contactPerson by remember { mutableStateOf(state.profile?.contactName ?: state.nickname) }
    var address by remember { mutableStateOf(state.profile?.address ?: "") }
    var description by remember { mutableStateOf("") }
    var licenseUrl by remember { mutableStateOf("") }
    var idCardUrl by remember { mutableStateOf("") }
    val pickLicense = rememberPhotoPicker { fileName, bytes ->
        if (fileName.isNotBlank()) {
            state.scope.launch { state.uploadPhoto(fileName, bytes)?.let { licenseUrl = it } }
        }
    }
    val pickIdCard = rememberPhotoPicker { fileName, bytes ->
        if (fileName.isNotBlank()) {
            state.scope.launch { state.uploadPhoto(fileName, bytes)?.let { idCardUrl = it } }
        }
    }
    SheetOverlay(title = "申请成为机构", onClose = close) {
        Text("需要上传营业执照等资质照片，平台人工审核通过后才能接单。审核结果会在消息里通知。", color = MobileColors.Ink3, fontSize = 12.sp)
        MsField(name, { name = it }, label = "机构名称", placeholder = "例如：小满家庭寄养")
        Column {
            Text("机构类型", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            ChipRow(APPLY_TYPES, typeLabel) { typeLabel = it }
        }
        MsField(contactPerson, { contactPerson = it }, label = "联系人", placeholder = "真实姓名")
        MsField(address, { address = it }, label = "机构地址", placeholder = "用于用户导航，尽量写详细")
        Text("资质证明", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UploadTile("营业执照", "必须", licenseUrl, pickLicense, { licenseUrl = "" }, Modifier.weight(1f))
            UploadTile("法人身份证", "选填", idCardUrl, pickIdCard, { idCardUrl = "" }, Modifier.weight(1f))
        }
        MsField(description, { description = it }, label = "机构介绍", placeholder = "环境、经验、可照料的身体情况", multiline = true, minLines = 3)
        PrimaryButton(
            text = "提交申请",
            onClick = {
                state.scope.launch {
                    state.applySitter(name, typeLabel, contactPerson, address, licenseUrl, idCardUrl, description)
                }
            },
            enabled = name.isNotBlank() && contactPerson.isNotBlank() && licenseUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton("取消", close, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UploadTile(
    label: String,
    requirement: String,
    url: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            Text(
                requirement,
                color = if (requirement == "必须") MobileColors.Danger else MobileColors.Ink3,
                fontSize = 11.5.sp,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(MobileShape.Md)
                .background(if (url.isBlank()) MobileColors.PanelSoft else MobileColors.Brand50)
                .border(1.dp, if (url.isBlank()) MobileColors.Line else MobileColors.Brand200, MobileShape.Md)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotBlank()) {
                MobileImage(url, Modifier.fillMaxSize(), contentDescription = label)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(MobileColors.Scrim).clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Close, Modifier.size(11.dp), Color.White)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(AppIcon.Plus, Modifier.size(19.dp), MobileColors.BrandDeep)
                    Text("点击上传", color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

private val TICKET_TYPES = listOf("订单问题", "机构投诉", "支付退款", "账号其他")

@Composable
private fun TicketSheet(state: MobileState, close: () -> Unit) {
    var category by remember { mutableStateOf("订单问题") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    SheetOverlay(title = "新建工单", onClose = close) {
        Column {
            Text("问题类型", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            ChipRow(TICKET_TYPES, category) { category = it }
        }
        MsField(title, { title = it }, label = "标题", placeholder = "一句话说明问题")
        MsField(
            content,
            { content = it },
            label = "详细描述",
            placeholder = "订单号、发生时间、希望怎么处理",
            multiline = true,
            minLines = 4,
        )
        PrimaryButton(
            text = "提交工单",
            onClick = { state.scope.launch { state.submitTicket(title, content, ticketCategoryCode(category)) } },
            enabled = title.isNotBlank() && content.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ticketCategoryCode(label: String): String = when (label) {
    "订单问题" -> "order"
    "机构投诉" -> "sitter"
    "支付退款" -> "refund"
    else -> "other"
}

/** 以下为订单详情调用的弹层入口，集中放在这里，避免和各页面互相引用。 */

fun openRescheduleSheet(state: MobileState, order: Order) {
    state.sheet = MobileSheet.DateRange(
        title = "改期 " + order.no,
        startDate = order.startDate,
        endDate = order.endDate,
        minStart = LocalDate.now().toString(),
        submitLabel = "确认改期",
        note = "改期由机构确认，费用按新日期重新计算，差价多退少补。",
    ) { start, end ->
        state.scope.launch { state.rescheduleOrder(order, start, end) }
    }
}

fun openRenewSheet(state: MobileState, order: Order) {
    val base = runCatching { LocalDate.parse(order.endDate) }.getOrElse { LocalDate.now() }
    state.sheet = MobileSheet.DatePick(
        title = "续住 " + order.no,
        value = base.plusDays(1).toString(),
        minValue = base.plusDays(1).toString(),
        submitLabel = "确认续住",
        note = "续住按机构日价乘以新增天数补收差价，机构有余位时自动生效。",
    ) { day ->
        state.scope.launch { state.renewOrder(order, day) }
    }
}

fun openReviewSheet(state: MobileState, order: Order) {
    state.sheet = MobileSheet.Review(order.no) { overall, service, environment, hygiene, comment ->
        state.scope.launch { state.submitReview(order, overall, service, environment, hygiene, comment) }
    }
}

fun openCancelSheet(state: MobileState, order: Order) {
    state.sheet = MobileSheet.Reason(
        title = "取消 " + order.no,
        hint = "填写取消原因，机构会看到这条说明",
        submitLabel = "确认取消",
        initial = "个人安排变化，暂时不需要寄养。",
    ) { text ->
        state.scope.launch { state.cancelOrder(order, text) }
    }
}

fun openRefundSheet(state: MobileState, order: Order) {
    state.sheet = MobileSheet.Reason(
        title = "申请退款 " + order.no,
        hint = "说明退款原因，平台会在 1 到 3 个工作日内处理",
        submitLabel = "提交申请",
    ) { text ->
        state.scope.launch { state.requestRefund(order, text) }
    }
}
