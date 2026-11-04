package team.wd.atnestapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.data.api.UserCouponDto
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.components.AppIcon

/** 首页：待办 + 快捷入口 + 推荐机构 + 最近动态，一屏之内把主人最常做的四件事摆出来 */
@Composable
fun HomeScreen(
    state: MobileState,
    onBell: () -> Unit,
    onQuick: (MobileScreen) -> Unit,
) {
    val greeting = when (java.time.LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
    val pending = state.orders.filter { it.rawStatus == "pending_pay" }
    val running = state.orders.filter { it.rawStatus in listOf("accepted", "staying") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting + "，" + state.nickname, color = MobileColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.loggedIn) "今天也想给毛孩子找个好去处吗" else "登录后即可预约寄养",
                        color = MobileColors.Ink2,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(MobileColors.Panel).border(1.dp, MobileColors.Line, CircleShape).clickable(onClick = onBell),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        AppIcon(AppIcon.Bell, Modifier.size(19.dp), MobileColors.Ink)
                        if (state.unreadCount > 0) {
                            CountBadge(state.unreadCount, modifier = Modifier.offset(x = 9.dp, y = (-5).dp))
                        }
                    }
                }
            }
        }

        if (pending.isNotEmpty()) {
            item {
                ActionBanner(
                    tone = TagTone.Warn,
                    icon = AppIcon.Card,
                    title = "有 " + pending.size + " 个订单待支付",
                    body = "30 分钟内未支付会自动取消并释放容量",
                    action = "去支付",
                    onClick = { state.openOrder(pending.first()) },
                )
            }
        }

        if (running.isNotEmpty()) {
            item {
                ActionBanner(
                    tone = TagTone.Brand,
                    icon = AppIcon.Paw,
                    title = "有 " + running.size + " 只宠物正在寄养",
                    body = "机构会不定时更新照片和状态",
                    action = "看动态",
                    onClick = { state.go(MobileScreen.Dynamics) },
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickEntry(AppIcon.Paw, "我的宠物", onQuick = { onQuick(MobileScreen.Pets) })
                QuickEntry(AppIcon.Orders, "我的订单", onQuick = { onQuick(MobileScreen.Orders) })
                QuickEntry(AppIcon.Heart, "我的收藏", onQuick = { onQuick(MobileScreen.Favorites) })
                QuickEntry(AppIcon.Ticket, "优惠券", onQuick = { onQuick(MobileScreen.Coupons) })
            }
        }

        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("为你推荐", modifier = Modifier.weight(1f));
                LinkButton("全部机构", onClick = { state.go(MobileScreen.Sitters) })
            }
        }

        if (state.sittersLoading && state.sitters.isEmpty()) {
            item { LoadingBars("正在加载机构") }
        } else if (state.sittersError != null) {
            item { InlineError(state.sittersError ?: "") { state.scope.launch { state.loadSitters(force = true) } } }
        } else {
            items(state.sitters.take(4)) { sitter ->
                SitterCard(sitter = sitter, onClick = { state.openSitter(sitter) })
            }
        }

        if (state.loggedIn && state.ownerDynamics.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionTitle("最近动态", modifier = Modifier.weight(1f));
                    LinkButton("查看全部", onClick = { state.go(MobileScreen.Dynamics) })
                }
            }
            items(state.ownerDynamics.take(2)) { dynamic ->
                DynamicCard(
                    dynamic = dynamic,
                    liked = state.likedDynamics.contains(dynamic.id),
                    onLike = { state.scope.launch { state.toggleLike(dynamic.id) } },
                    onPhoto = { state.sheet = MobileSheet.Photo(it, dynamic.sitterName) },
                )
            }
        }
    }
}

@Composable
private fun ActionBanner(
    tone: TagTone,
    icon: AppIcon,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    val (bg, fg) = tagColors(tone)
    Row(
        Modifier.fillMaxWidth().clip(MobileShape.Lg).background(bg).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, Modifier.size(19.dp), fg)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = fg.copy(alpha = 0.78f),
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            Modifier.clip(MobileShape.Pill).background(MobileColors.Panel).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
        ) {
            Text(action, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RowScope.QuickEntry(icon: AppIcon, label: String, onQuick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).clickable(onClick = onQuick).padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MobileColors.Brand50),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(17.dp), MobileColors.BrandDeep)
        }
        Text(label, color = MobileColors.Ink2, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.padding(top = 7.dp))
    }
}

/** 找机构：搜索 + 类型筛选，结果用懒加载列表，滚动不卡顿 */
@Composable
fun SittersScreen(state: MobileState) {
    val list = state.visibleSitters;
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    MsField(
                        value = state.sitterQuery,
                        onValueChange = { state.sitterQuery = it },
                        placeholder = "搜索机构名称或地址",
                    )
                }
                Box(
                    Modifier.size(46.dp).clip(MobileShape.Md).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Md).clickable { state.scope.launch { state.loadSitters(force = true) } },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Search, Modifier.size(18.dp), MobileColors.BrandDeep)
                }
            }
        }
        item {
            ChipRow(SITTER_TYPES, state.sitterType) { picked ->
                state.sitterType = picked
                state.reloadSittersByType()
            }
        }
        if (state.sittersLoading && state.sitters.isEmpty()) {
            item { LoadingBars("正在加载机构") }
        } else if (state.sittersError != null) {
            item { InlineError(state.sittersError ?: "") { state.scope.launch { state.loadSitters(force = true) } } }
        } else if (list.isEmpty()) {
            item { EmptyState("没有符合条件的机构", "换个关键词或类型试试") }
        } else {
            items(list) { sitter ->
                SitterCard(sitter = sitter, onClick = { state.openSitter(sitter) })
            }
        }
    }
}

private val SITTER_TYPES = listOf("全部", "家庭寄养", "门店寄养", "上门喂养", "其他")


/** 机构详情：封面、价格、地址导航、可预订容量、加购、真实评价 */
@Composable
fun SitterDetailScreen(state: MobileState) {
    val sitter = state.selectedSitter
    if (sitter == null) {
        EmptyState("机构信息已失效", "回到列表重新选择")
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val favorited = state.isFavorite(sitter.id)
    val latitude = sitter.latitude
    val longitude = sitter.longitude
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                CoverBox(sitter.coverPhoto, Modifier.fillMaxSize(), iconSize = 34)
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(MobileColors.Brand900.copy(alpha = 0.24f), MobileColors.Scrim.copy(alpha = 0.0f)),
                        ),
                    ),
                )
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sitter.name, color = MobileColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusTag(sitter.type, TagTone.Neutral)
                    }
                    Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StarRow(sitter.score, size = 14.dp)
                        Text(sitter.score.toString() + " 分", color = MobileColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(sitter.tags, color = MobileColors.Ink3, fontSize = 12.sp)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Brand50).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("¥" + sitter.price, color = MobileColors.BrandDeep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(" / " + sitter.unit, color = MobileColors.Ink2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp, start = 2.dp))
                        }
                        Text("当前可接待 " + sitter.cap, color = MobileColors.Ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    GhostButton("联系机构", { openContactSheet(state, sitter) }, height = 40.dp)
                }

                InfoBlock("机构简介", sitter.desc)
                Row(
                    Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(AppIcon.Shop, Modifier.size(17.dp), MobileColors.BrandDeep)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("所在位置", color = MobileColors.Ink2, fontSize = 11.5.sp)
                        Text(sitter.addr, color = MobileColors.Ink, fontSize = 13.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (latitude != null && longitude != null) {
                        TonalButton("导航", onClick = {
                            openNavigation(context, latitude, longitude, sitter.name)
                        })
                    }
                }

                if (sitter.photos.isNotEmpty()) {
                    Column {
                        SectionTitle("环境实拍")
                        Spacer(Modifier.height(9.dp))
                        PhotoGrid(sitter.photos, size = 108.dp, onClick = { state.sheet = MobileSheet.Photo(it, sitter.name) })
                    }
                }

                if (state.detailAddons.isNotEmpty()) {
                    Column {
                        SectionTitle("可选加购")
                        Spacer(Modifier.height(9.dp))
                        MsCard {
                            state.detailAddons.forEach { addon ->
                                LineRow(addon.name, "¥" + addon.price + " " + addonUnitText(addon.unit))
                            }
                        }
                    }
                }

                Column {
                    SectionTitle("近两周可预订", subtitle = "数字为当日剩余名额")
                    Spacer(Modifier.height(9.dp))
                    if (state.detailCapacity.isEmpty()) {
                        Text("机构暂未开放日历，可直接下单由机构确认", color = MobileColors.Ink3, fontSize = 12.5.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.detailCapacity.take(7).forEach { day ->
                                CapacityPill(day)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.detailCapacity.drop(7).take(7).forEach { day ->
                                CapacityPill(day)
                            }
                        }
                    }
                }

                Column {
                    SectionTitle("真实评价", subtitle = if (state.detailReviews.isEmpty()) "还没有人评价过" else "共 " + state.detailReviews.size + " 条")
                    Spacer(Modifier.height(9.dp))
                    if (state.detailReviews.isEmpty()) {
                        Text("期待你成为第一个留下评价的人", color = MobileColors.Ink3, fontSize = 12.5.sp)
                    } else {
                        state.detailReviews.take(3).forEach { review ->
                            PublicReviewCell(review = review, onPhoto = { state.sheet = MobileSheet.Photo(it, "评价图片") })
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(MobileColors.Panel)
                .border(1.dp, MobileColors.Line2, RoundedCornerShape(0.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(width = 58.dp, height = 48.dp)
                    .clip(MobileShape.Md)
                    .background(if (favorited) MobileColors.Brand100 else MobileColors.PanelSoft)
                    .clickable { state.scope.launch { state.toggleFavorite(sitter) } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(AppIcon.Heart, Modifier.size(17.dp), if (favorited) MobileColors.BrandDeep else MobileColors.Ink3)
                    Text(if (favorited) "已收藏" else "收藏", color = if (favorited) MobileColors.BrandDeep else MobileColors.Ink3, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            PrimaryButton("立即预约", { state.startBooking(sitter) }, modifier = Modifier.weight(1f))
        }
    }
}

private fun openContactSheet(state: MobileState, sitter: Sitter) {
    state.sheet = MobileSheet.Reason(
        title = "联系 " + sitter.name,
        hint = "说点什么，机构会在消息里回复你",
        submitLabel = "发送",
        initial = "您好，我想咨询一下寄养服务。",
    ) { text ->
        state.scope.launch { state.contactSitter(sitter, text) }
    }
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(14.dp),
    ) {
        Text(title, color = MobileColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = MobileColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RowScope.CapacityPill(day: team.wd.atnestapp.data.api.CapacityItemDto) {
    val bg = when {
        day.full -> MobileColors.DangerSoft
        day.available <= 1 -> MobileColors.WarnSoft
        else -> MobileColors.OkSoft
    }
    val fg = when {
        day.full -> MobileColors.Danger
        day.available <= 1 -> MobileColors.WarnInk
        else -> MobileColors.Ok
    }
    Column(
        modifier = Modifier.weight(1f).clip(MobileShape.Md).background(bg).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day.date.takeLast(5), color = MobileColors.Ink, fontSize = 11.5.sp)
        Text(if (day.full) "满" else day.available.toString(), color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * 预约下单：日期、宠物、加购、优惠券、积分五步收在一页。
 * 金额优先用后端估价，估价还没回来时退回本地估算，避免用户在空白数字前犹豫。
 */
@Composable
fun BookingScreen(state: MobileState) {
    val sitter = state.selectedSitter
    if (sitter == null) {
        EmptyState("缺少机构信息", "回到列表重新选择")
        return
    }
    val estimate = state.estimate
    val days = state.bookingDays
    val coupon = state.bookingCoupon
    val localTotal = sitter.price.toDouble() * days * state.bookingPetIds.size.coerceAtLeast(1)
    val total = estimate?.totalAmount ?: localTotal
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SitterCard(sitter = sitter, onClick = { state.openSitter(sitter) })

            Column {
                SectionTitle("寄养日期")
                Spacer(Modifier.height(10.dp))
                MsCard {
                    DateField("开始日期", state.bookingStart, onChange = { state.pickBookingStart(it) })
                    Spacer(Modifier.height(10.dp))
                    MsDivider()
                    Spacer(Modifier.height(10.dp))
                    DateField("结束日期", state.bookingEnd, minValue = state.bookingStart, onChange = { state.pickBookingEnd(it) })
                    Spacer(Modifier.height(10.dp))
                    LineRow("共计", days.toString() + " 晚")
                }
            }

            Column {
                SectionTitle("选择宠物", subtitle = "可多选，按只计价")
                Spacer(Modifier.height(10.dp))
                if (state.pets.isEmpty()) {
                    EmptyState("还没有宠物档案", "先去添加一个，再回来下单", action = { state.go(MobileScreen.PetEdit) })
                } else {
                    MsCard {
                        state.pets.forEach { pet ->
                            val picked = state.bookingPetIds.contains(pet.id)
                            Row(
                                Modifier.fillMaxWidth().clickable { state.toggleBookingPet(pet.id) }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MsCheckbox(picked, onChange = { state.toggleBookingPet(pet.id) })
                                Spacer(Modifier.width(11.dp))
                                CoverBox(pet.photos.firstOrNull().orEmpty(), Modifier.size(38.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pet.name, color = MobileColors.Ink, fontSize = 14.sp)
                                    Text(pet.type + " · " + pet.size + " · " + pet.age, color = MobileColors.Ink3, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (state.detailAddons.isNotEmpty()) {
                Column {
                    SectionTitle("加购服务")
                    Spacer(Modifier.height(10.dp))
                    CheckChips(state.detailAddons.map { it.name }, state.bookingAddons.toSet()) { name ->
                        state.toggleBookingAddon(name)
                    }
                }
            }

            Column {
                SectionTitle("优惠与积分")
                Spacer(Modifier.height(10.dp))
                MsCard {
                    val usable = state.coupons?.items.orEmpty().filter { it.status == "unused" }
                    Row(
                        Modifier.fillMaxWidth().clickable { state.sheet = MobileSheet.CouponPicker }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("优惠券", color = MobileColors.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            coupon?.title ?: if (usable.isEmpty()) "暂无可用" else usable.size.toString() + " 张可用",
                            color = if (coupon != null) MobileColors.BrandDeep else MobileColors.Ink3,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        AppIcon(AppIcon.Back, Modifier.size(14.dp).rotate(180f), MobileColors.Ink3)
                    }
                    Spacer(Modifier.height(8.dp))
                    MsDivider()
                    Spacer(Modifier.height(10.dp))
                    StepperRow(
                        label = "使用积分",
                        hint = "余额 " + state.pointsBalance + " 分，" + (estimate?.pointsDiscount ?: 0.0) + " 元已抵扣",
                        value = state.bookingPoints,
                        step = 10,
                        maxValue = state.pointsBalance,
                        onChange = { state.selectBookingPoints(it) },
                    )
                }
            }

            Column {
                SectionTitle("备注")
                Spacer(Modifier.height(8.dp))
                MsField(state.bookingRemark, { state.bookingRemark = it }, placeholder = "喂食习惯、用药提醒、性格特点都可以写在这里", multiline = true)
            }

            Column {
                SectionTitle("费用明细")
                Spacer(Modifier.height(9.dp))
                MsCard {
                    if (estimate != null) {
                        estimate.breakdown.forEach { line ->
                            LineRow(line.label, state.money(line.amount), tone = if (line.amount < 0) MobileColors.Ok else MobileColors.Ink2)
                        }
                    } else {
                        LineRow("寄养费 " + days + " 晚 × ¥" + sitter.price, "¥" + (sitter.price * days))
                        if (state.estimating) LineRow("正在向服务端估价", "…")
                    }
                    MsDivider(modifier = Modifier.padding(vertical = 6.dp))
                    LineRow("预计支付", state.money(total), tone = MobileColors.BrandDeep, bold = true)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(MobileColors.Panel).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("合计", color = MobileColors.Ink2, fontSize = 11.5.sp)
                Text(state.money(total), color = MobileColors.BrandDeep, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            PrimaryButton(
                text = if (state.busy) "提交中" else "提交订单",
                onClick = { state.scope.launch { state.createOrder() } },
                enabled = !state.busy && state.bookingPetIds.isNotEmpty() && days > 0,
                modifier = Modifier.width(150.dp),
            )
        }
    }
}

/** 我的订单：按状态分栏，避免用户在长列表里翻找 */
@Composable
fun OrdersScreen(state: MobileState) {
    var tab by remember { mutableStateOf("全部") }
    val all = state.orders;
    val list = when (tab) {
        "待支付" -> all.filter { it.rawStatus == "pending_pay" }
        "进行中" -> all.filter { it.rawStatus in listOf("confirmed", "accepted", "staying") }
        "待评价" -> all.filter { it.rawStatus == "completed" }
        "已完成" -> all.filter { it.rawStatus in listOf("completed", "evaluated") }
        else -> all
    }
    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf("全部", "待支付", "进行中", "待评价", "已完成"), tab, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            tab = it
        }
        if (state.ordersLoading && all.isEmpty()) {
            LoadingBars("正在加载订单")
        } else if (state.ordersError != null) {
            Box(Modifier.fillMaxSize().padding(16.dp)) { InlineError(state.ordersError ?: "") { state.scope.launch { state.loadOrders(force = true) } } }
        } else if (list.isEmpty()) {
            EmptyState("这里还没有订单", "去挑一家合适的机构开始预约", action = { state.go(MobileScreen.Sitters) })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            ) {
                items(list) { order ->
                    OrderCard(order = order, onClick = { state.openOrder(order) })
                }
            }
        }
    }
}

/** 订单详情：状态说明 + 明细 + 当前能做的操作，按钮显隐完全按后端原始状态判断 */
@Composable
fun OrderDetailScreen(state: MobileState) {
    val order = state.selectedOrder;
    if (order == null) {
        EmptyState("订单不存在", "回到列表重新打开")
        return
    }
    val canPay = order.rawStatus == "pending_pay"
    val canCancel = order.rawStatus in listOf("pending_pay", "confirmed")
    val canReschedule = order.rawStatus in listOf("pending_pay", "confirmed", "accepted")
    val canRenew = order.paid && order.rawStatus in listOf("accepted", "staying")
    val canRefund = order.paid && order.rawStatus in listOf("confirmed", "accepted", "staying", "completed")
    val canReview = order.rawStatus == "completed"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusTag(order.status, statusTone(order.rawStatus), dot = true)
                    Spacer(Modifier.width(9.dp))
                    Text(order.no, color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    order.org,
                    color = MobileColors.Ink,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(order.range + " 共 " + order.days + " · " + order.pet, color = MobileColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                Spacer(Modifier.height(12.dp))
                MsDivider()
                Spacer(Modifier.height(10.dp))
                LineRow("下单时间", order.made)
                LineRow("支付状态", if (order.paid) "已支付" else "未支付", tone = if (order.paid) MobileColors.Ok else MobileColors.Warn)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("订单金额", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Text(order.fee, color = MobileColors.BrandDeep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("可以做的事")
        Spacer(Modifier.height(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canPay) {
                PrimaryButton("去支付", { state.openPay(order) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canReschedule) {
                    GhostButton("改期", { openRescheduleSheet(state, order) }, modifier = Modifier.weight(1f))
                }
                if (canRenew) {
                    GhostButton("续住", { openRenewSheet(state, order) }, modifier = Modifier.weight(1f))
                }
                if (canReview) {
                    GhostButton("评价", { openReviewSheet(state, order) }, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("寄养动态", { state.scope.launch { state.loadOrderDynamics(order) } }, modifier = Modifier.weight(1f), icon = AppIcon.Paw)
                GhostButton("申请退款", { openRefundSheet(state, order) }, modifier = Modifier.weight(1f), textColor = MobileColors.Danger)
            }
            if (canCancel) {
                GhostButton("取消订单", { openCancelSheet(state, order) }, modifier = Modifier.fillMaxWidth(), textColor = MobileColors.Danger)
            }
        }

        Spacer(Modifier.height(16.dp))
        if (state.orderDynamics.isNotEmpty()) {
            SectionTitle("这次寄养的动态");
            Spacer(Modifier.height(9.dp))
            state.orderDynamics.forEach { dynamic ->
                DynamicCard(
                    dynamic = dynamic,
                    liked = state.likedDynamics.contains(dynamic.id),
                    onLike = { state.scope.launch { state.toggleLike(dynamic.id) } },
                    onPhoto = { state.sheet = MobileSheet.Photo(it, dynamic.sitterName) },
                )
                Spacer(Modifier.height(10.dp))
            }
        } else if (state.orderDynamicsLoading) {
            LoadingBars("正在加载动态")
        }

        val mine = state.refunds.filter { it.orderId == order.id }
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("退款进度")
            Spacer(Modifier.height(9.dp))
            mine.forEach { refund ->
                SimpleRow(
                    title = state.money(refund.amount),
                    subtitle = refund.reason.ifBlank { "未填写原因" } + " · " + refund.createdAt.take(16),
                    trailingTag = refundStatusText(refund.status),
                    tone = statusTone(refund.status),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

fun refundStatusText(status: String): String = when (status) {
    "pending" -> "审核中"
    "approved" -> "已通过"
    "rejected" -> "已驳回"
    "refunded" -> "已退款"
    else -> status
}

/** 收银台：手机端不接真实支付通道，这里保持和桌面端一致的模拟支付，但把金额、时限、支付方式说清楚 */
@Composable
fun PayScreen(state: MobileState) {
    val order = state.selectedOrder;
    if (order == null) {
        EmptyState("没有待支付订单")
        return
    }
    var method by remember { mutableStateOf("微信支付") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("应付金额", color = MobileColors.Ink2, fontSize = 12.5.sp)
                    Text(order.fee, color = MobileColors.BrandDeep, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Text(order.no, color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
                    Box(
                        Modifier.padding(top = 12.dp).clip(MobileShape.Pill).background(MobileColors.WarnSoft).padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text("下单后 30 分钟内需完成支付", color = MobileColors.WarnInk, fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("支付方式")
            Spacer(Modifier.height(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("微信支付", "支付宝", "账户余额").forEach { name ->
                    PayMethodRow(name, method == name) { method = name }
                }
            }
            Spacer(Modifier.height(14.dp))
            LineRow("机构", order.org)
            LineRow("寄养时间", order.range)
            LineRow("宠物数量", order.pet)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (state.busy) "支付中" else "确认支付 " + order.fee,
            onClick = { state.scope.launch { state.payOrder(order) } },
            enabled = !state.busy && order.rawStatus == "pending_pay",
            modifier = Modifier.fillMaxWidth(),
        )
        if (order.rawStatus != "pending_pay") {
            Text("该订单当前不可支付", color = MobileColors.Danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
        }
        LinkButton("暂不支付，返回订单", onClick = { state.go(MobileScreen.Orders, keepCurrent = false) })    }
}

@Composable
private fun PayMethodRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(if (selected) MobileColors.Brand50 else MobileColors.Panel)
            .border(1.dp, if (selected) MobileColors.Brand200 else MobileColors.Line, MobileShape.Lg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) MobileColors.Brand else MobileColors.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(MobileColors.Brand))
        }
        Spacer(Modifier.width(11.dp))
        Text(name, color = MobileColors.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        val icon = when (name) {
            "微信支付" -> AppIcon.Card
            "支付宝" -> AppIcon.Card
            else -> AppIcon.Bank
        }
        AppIcon(icon, Modifier.size(17.dp), if (selected) MobileColors.BrandDeep else MobileColors.Ink3)
    }
}

/** 我的宠物 */
@Composable
fun PetsScreen(state: MobileState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("宠物档案", subtitle = "下单时从这里选择", modifier = Modifier.weight(1f))
                LinkButton("新增", onClick = {
                    state.editingPet = null
                    state.go(MobileScreen.PetEdit)
                })
            }
        }
        if (state.petsLoading && state.pets.isEmpty()) {
            item { LoadingBars("正在加载档案") }
        } else if (state.petsError != null) {
            item { InlineError(state.petsError ?: "") { state.scope.launch { state.loadPets(force = true) } } }
        } else if (state.pets.isEmpty()) {
            item {
                EmptyState("还没有宠物档案", "先建好档案才能预约寄养", action = {
                    state.editingPet = null
                    state.go(MobileScreen.PetEdit)
                })
            }
        } else {
            items(state.pets) { pet ->
                PetCard(pet = pet, onClick = {
                    state.viewingPet = pet
                    state.scope.launch { state.loadPetStays(pet) }
                    state.go(MobileScreen.Stays)
                })
            }
        }
    }
}

/** 宠物详情与寄养记录 */
@Composable
fun StaysScreen(state: MobileState) {
    val pet = state.viewingPet;
    if (pet == null) {
        EmptyState("宠物信息已失效")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverBox(pet.photos.firstOrNull().orEmpty(), Modifier.size(64.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(pet.name, color = MobileColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(pet.type + " · " + pet.breed + " · " + pet.size, color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusTag(pet.cert, certTone(pet.cert))
                        StatusTag(pet.vaccine, if (pet.vaccine == "已接种") TagTone.Ok else TagTone.Warn)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(16.dp),
        ) {
            Column {
                KeyValueRow("年龄", pet.age)
                KeyValueRow("健康备注", pet.note)
                if (pet.vaccinePhotos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Eyebrow("免疫证明")
                    PhotoGrid(pet.vaccinePhotos, Modifier.padding(top = 8.dp), size = 72.dp, onClick = { state.sheet = MobileSheet.Photo(it, "免疫证明") })
                }
                if (pet.vaccineNote.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("核验意见：" + pet.vaccineNote, color = MobileColors.Danger, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("编辑档案", {
                        state.editingPet = pet
                        state.go(MobileScreen.PetEdit)
                    }, modifier = Modifier.weight(1f))
                    GhostButton("删除", { openDeletePetSheet(state, pet) }, modifier = Modifier.width(84.dp), textColor = MobileColors.Danger)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("寄养记录", subtitle = "共 " + state.petStays.size + " 条")
        Spacer(Modifier.height(9.dp))
        if (state.petStays.isEmpty()) {
            Text("这只宝贝还没有寄养记录", color = MobileColors.Ink3, fontSize = 12.5.sp)
        } else {
            state.petStays.forEach { stay ->
                PetStayCell(stay)
                Spacer(Modifier.height(9.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun openDeletePetSheet(state: MobileState, pet: Pet) {
    state.sheet = MobileSheet.Message(
        title = "删除 " + pet.name,
        body = "删除后档案不再出现在下单列表，历史订单不受影响。",
        actionLabel = "确认删除",
        onAction = { state.scope.launch { state.deletePet(pet) } },
    )
}

/** 新增 / 编辑宠物档案 */
@Composable
fun PetEditScreen(state: MobileState) {
    val editing = state.editingPet;
    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var typeLabel by remember(editing?.id) { mutableStateOf(editing?.type ?: "猫") }
    var sizeLabel by remember(editing?.id) { mutableStateOf(editing?.size ?: "中型") }
    var ageText by remember(editing?.id) { mutableStateOf(editing?.age?.filter { ch -> ch.isDigit() }.orEmpty()) }
    var vaccinated by remember(editing?.id) { mutableStateOf(editing == null || editing.vaccine == "已接种") }
    var note by remember(editing?.id) { mutableStateOf(if (editing?.note == "暂无健康备注") "" else editing?.note ?: "") }
    var avatarUrl by remember(editing?.id) { mutableStateOf(editing?.photos?.firstOrNull().orEmpty()) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pick = rememberPhotoPicker { fileName, bytes ->
        if (fileName.isEmpty() || bytes.isEmpty()) {
            state.toast("没有读到这张图片")
            return@rememberPhotoPicker
        }
        uploading = true
        scope.launch {
            val url = state.uploadPhoto(fileName, bytes)
            if (url != null) {
                avatarUrl = url
                state.toast("照片已上传")
            }
            uploading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CoverBox(avatarUrl, Modifier.size(78.dp), iconSize = 26)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (editing == null) "给宠物照张相" else "更换头像", color = MobileColors.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                    Text("选一张清晰的正脸照，机构更容易判断体型", color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
                    Spacer(Modifier.height(9.dp))
                    GhostButton(
                        text = if (uploading) "上传中" else "从相册选择",
                        onClick = { if (!uploading) pick() },
                        height = 38.dp,
                        icon = AppIcon.Image,
                    )
                }
            }
            MsField(name, { name = it }, label = "名字", placeholder = "例如：糯米")
            Column {
                Text("种类", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
                ChipRow(listOf("猫", "狗", "其他"), typeLabel) { typeLabel = it }
            }
            Column {
                Text("体型", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
                ChipRow(listOf("迷你", "小型", "中型", "大型", "超大型"), sizeLabel) { sizeLabel = it }
            }
            MsField(ageText, { ageText = it }, label = "年龄（月）", placeholder = "例如：18", numeric = true)
            Row(
                Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("已接种疫苗", color = MobileColors.Ink, fontSize = 14.sp)
                    Text("机构接待时会核对免疫证明", color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                MsSwitch(vaccinated, onChange = { vaccinated = it })
            }
            MsField(note, { note = it }, label = "健康与性格备注", placeholder = "慢性病、用药、怕生、挑食都可以写", multiline = true)
            if (editing != null && editing.vaccinePhotos.isEmpty() && editing.cert != "已核验") {
                Box(
                    Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.WarnSoft).padding(13.dp),
                ) {
                    Text("还没有上传免疫证明，机构可能无法接待。可在档案页补充上传。", color = MobileColors.WarnInk, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(MobileColors.Panel).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("取消", { state.back() }, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = if (editing == null) "创建档案" else "保存修改",
                onClick = {
                    state.scope.launch { state.savePet(name, typeLabel, sizeLabel, ageText, vaccinated, note, avatarUrl) }
                },
                modifier = Modifier.weight(1.6f),
            )
        }
    }
}

/** 优惠券：上半部分是券包，下半部分是可以领取的新券 */
@Composable
fun CouponsScreen(state: MobileState) {
    val data = state.coupons;
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (state.couponsLoading && data == null) {
            item { LoadingBars("正在加载券包") }
        } else {
            item { SectionTitle("我的券包", subtitle = "下单页可以直接选用") }
            val mine = data?.items.orEmpty();
            if (mine.isEmpty()) {
                item { Text("券包还是空的，先去领一张吧", color = MobileColors.Ink3, fontSize = 12.5.sp) }
            } else {
                items(mine) { coupon ->
                    CouponCell(
                        title = coupon.title,
                        amount = couponText(coupon.type, coupon.value),
                        rule = if (coupon.minAmount > 0) "满 " + coupon.minAmount.toInt() + " 可用" else "无门槛",
                        status = userCouponStatus(coupon.status),
                        tone = statusTone(coupon.status),
                        onDelete = if (coupon.status == "unused") { { state.scope.launch { state.deleteMyCoupon(coupon) } } } else null,
                    )
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
            item { SectionTitle("可领取", subtitle = "平台与机构发放") }
            val claimable = data?.claimable.orEmpty();
            if (claimable.isEmpty()) {
                item { Text("暂时没有可领取的优惠券", color = MobileColors.Ink3, fontSize = 12.5.sp) }
            } else {
                items(claimable) { coupon ->
                    CouponCell(
                        title = coupon.title + " · " + coupon.issuer,
                        amount = couponText(coupon.type, coupon.value),
                        rule = if (coupon.minAmount > 0) "满 " + coupon.minAmount.toInt() + " 可用" else "无门槛",
                        status = "有效期至 " + coupon.endsAt.take(10),
                        tone = TagTone.Info,
                        action = "领取",
                        onAction = { state.scope.launch { state.claimCoupon(coupon.couponId) } },
                    )
                }
            }
        }
    }
}

fun couponText(type: String, value: Double): String = when (type) {
    "percent" -> value.toInt().toString() + "%";
    "discount" -> value.toInt().toString() + " 折";
    else -> "¥" + value.toInt()
}

fun userCouponStatus(status: String): String = when (status) {
    "unused" -> "未使用"
    "used" -> "已使用"
    "expired" -> "已过期"
    "locked" -> "下单锁定"
    else -> status
}

/** 消息通知：个人消息 / 商家消息两套独立接口，已读互不影响 */
@Composable
fun MessagesScreen(state: MobileState) {
    val scopeLabel = if (state.notificationScope == "business") "商家消息" else "个人消息"
    val messages = state.visibleNotifications()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("消息通知", subtitle = messages.count { !it.isRead }.toString() + " 条未读", modifier = Modifier.weight(1f))
                LinkButton("全部已读", onClick = { state.scope.launch { state.markAllScopedRead() } })
            }
        }
        item {
            ChipRow(
                options = listOf("个人消息", "商家消息"),
                selected = scopeLabel,
                onSelect = { label ->
                    state.notificationScope = if (label == "商家消息") "business" else "personal"
                },
            )
        }
        if (state.scopedNotificationsLoading && messages.isEmpty()) {
            item { LoadingBars("正在加载消息") }
        } else if (messages.isEmpty()) {
            item {
                EmptyState(
                    if (scopeLabel == "商家消息") "当前账号没有商家消息" else "暂时没有新消息",
                    "订单状态变化会第一时间通知你",
                )
            }
        } else {
            items(messages) { item ->
                NotificationCell(item) { state.scope.launch { state.markScopedRead(item.id) } }
            }
        }
    }
}

/** 我的收藏 */
@Composable
fun FavoritesScreen(state: MobileState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item { SectionTitle("我的收藏", subtitle = state.favorites.size.toString() + " 家机构") }
        if (state.favoritesLoading && state.favorites.isEmpty()) {
            item { LoadingBars("正在加载收藏") }
        } else if (state.favorites.isEmpty()) {
            item { EmptyState("还没有收藏的机构", "在详情页点右上角的爱心就能收藏", action = { state.go(MobileScreen.Sitters) })}
        } else {
            items(state.favorites) { sitter ->
                SitterCard(
                    sitter = sitter,
                    onClick = { state.openSitter(sitter) },
                    onFavorite = { state.scope.launch { state.toggleFavorite(sitter) } },
                )
            }
        }
    }
}

/** 寄养动态流 */
@Composable
fun DynamicsScreen(state: MobileState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item { SectionTitle("寄养动态", subtitle = "机构发来的实时记录") }
        if (state.dynamicsLoading && state.ownerDynamics.isEmpty()) {
            item { LoadingBars("正在加载动态") }
        } else if (state.ownerDynamics.isEmpty()) {
            item { EmptyState("还没有动态", "宠物入住后机构会更新照片和状态") }
        } else {
            items(state.ownerDynamics) { dynamic ->
                DynamicCard(
                    dynamic = dynamic,
                    liked = state.likedDynamics.contains(dynamic.id),
                    onLike = { state.scope.launch { state.toggleLike(dynamic.id) } },
                    onPhoto = { state.sheet = MobileSheet.Photo(it, dynamic.sitterName) },
                )
            }
        }
    }
}

/** 我的：把账号相关的一切都收在一页，底部导航的第 4 个入口 */
@Composable
fun ProfileScreen(state: MobileState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(MobileColors.Brand50),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.User, Modifier.size(25.dp), MobileColors.Brand300)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(if (state.loggedIn) state.nickname else "未登录", color = MobileColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (!state.loggedIn) "登录后同步你的宠物和订单" else "@" + state.account,
                    color = MobileColors.Ink3,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            StatusTag(
                when {
                    !state.loggedIn -> "游客"
                    state.isSitterAccount -> "机构账号"
                    else -> "主人账号"
                },
                if (state.isSitterAccount) TagTone.Brand else TagTone.Neutral,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("订单", state.orders.size.toString(), hint = "全部")
            MetricCard("宠物", state.pets.size.toString(), hint = "档案")
            MetricCard("收藏", state.favorites.size.toString(), hint = "机构")
            MetricCard("积分", state.pointsBalance.toString(), hint = "可抵现", tone = MobileColors.Amber)
        }

        if (state.isSitterAccount) {
            MsCard(padding = 0.dp) {
                CellRow(AppIcon.Shop, "机构工作台", "接单、容量、收益都在那里", onClick = { state.switchToSitter() })
                MsDivider()
            }
        } else {
            val app = state.profile?.sitterApplication
            if (app != null && app.status.isNotBlank()) {
                MsCard(padding = 0.dp) {
                CellRow(
                        icon = AppIcon.Shop,
                        title = "机构入驻审核中",
                        subtitle = app.reviewNote ?: "平台正在核验你提交的资质材料",
                        trailing = applicationStatusText(app.status),
                    )
                }
            } else {
                MsCard(padding = 0.dp) {
                    CellRow(AppIcon.Shop, "申请成为机构", "上传营业执照即可提交入驻申请", onClick = { state.sheet = MobileSheet.ApplySitter })
                }
            }
        }

        Eyebrow("常用")
        MsCard(padding = 0.dp) {
            CellRow(AppIcon.User, "我的资料", onClick = { openProfileSheet(state) })
            MsDivider()
            CellRow(AppIcon.Paw, "我的宠物", state.pets.size.toString() + " 只", onClick = { state.go(MobileScreen.Pets) })
            MsDivider()
            CellRow(AppIcon.Heart, "我的收藏", state.favorites.size.toString() + " 家", onClick = { state.go(MobileScreen.Favorites) })
            MsDivider()
            CellRow(AppIcon.Ticket, "优惠券", onClick = { state.go(MobileScreen.Coupons) })
            MsDivider()
            CellRow(AppIcon.Card, "我的积分", state.pointsBalance.toString() + " 分", onClick = { state.scope.launch { state.loadPoints(force = true); state.go(MobileScreen.Points) } })
        }

        Eyebrow("帮助与安全")
        MsCard(padding = 0.dp) {
            CellRow(AppIcon.Bell, "消息通知", badge = state.unreadCount, onClick = { state.go(MobileScreen.Messages) })
            MsDivider()
            CellRow(AppIcon.Orders, "退款记录", state.refunds.size.toString() + " 条", onClick = { state.scope.launch { state.loadRefunds(); state.go(MobileScreen.Tickets) } })
            MsDivider()
            CellRow(AppIcon.Card, "修改密码", onClick = { state.sheet = passwordSheet(state) })
            MsDivider()
            CellRow(AppIcon.Logout, "注销账号", "提交后立即退出登录，3 天内可撤销", destructive = true, onClick = { openCancelAccountSheet(state) })
        }

        if (state.loggedIn) {
            GhostButton("退出登录", { state.logout() }, modifier = Modifier.fillMaxWidth(), textColor = MobileColors.Danger, icon = AppIcon.Logout)
        }
        Spacer(Modifier.height(20.dp))
    }
}

fun applicationStatusText(status: String): String = when (status) {
    "pending" -> "审核中"
    "approved" -> "已通过"
    "rejected" -> "已驳回"
    else -> status
}

private fun openProfileSheet(state: MobileState) {
    val me = state.profile
    state.sheet = MobileSheet.Form(
        title = "我的资料",
        submitLabel = "保存",
        fields = listOf(
            FormField("昵称", initial = me?.nickname ?: state.nickname),
            FormField("手机号", initial = me?.phone ?: "", numeric = true),
            FormField("常用地址", initial = me?.address ?: ""),
            FormField("联系人", initial = me?.contactName ?: ""),
            FormField("联系电话", initial = me?.contactPhone ?: "", numeric = true),
        ),
    ) { values ->
        state.scope.launch {
            state.saveProfile(values.getOrElse(0) { "" }, values.getOrElse(1) { "" }, values.getOrElse(2) { "" }, values.getOrElse(3) { "" }, values.getOrElse(4) { "" })
        }
    }
}

private fun passwordSheet(state: MobileState): MobileSheet = MobileSheet.Form(
    title = "修改密码",
    submitLabel = "确认修改",
    fields = listOf(
        FormField("当前密码", secret = true),
        FormField("新密码", placeholder = "至少 6 位", secret = true),
        FormField("确认新密码", secret = true),
    ),
) { values ->
    val old = values.getOrElse(0) { "" }
    val fresh = values.getOrElse(1) { "" }
    val again = values.getOrElse(2) { "" }
    when {
        old.isBlank() || fresh.isBlank() -> state.toast("请填写完整")
        fresh != again -> state.toast("两次输入的新密码不一致")
        fresh.length < 6 -> state.toast("新密码至少 6 位")
        else -> state.scope.launch { state.changePassword(old, fresh) }
    }
}

private fun openCancelAccountSheet(state: MobileState) {
    state.sheet = MobileSheet.Message(
        title = "申请注销账号",
        body = "提交后会立即退出登录。3 天内重新登录可以撤销；超过 3 天账号与数据将被清除。",
        actionLabel = "确认注销",
        onAction = { state.scope.launch { state.requestAccountCancel("account") } },
    )
}

/** 积分明细 */
@Composable
fun PointsScreen(state: MobileState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(MobileShape.Lg).background(PrimaryGradient).padding(20.dp),
            ) {
                Column {
                    Text("当前可用积分", color = MobileColors.Panel.copy(alpha = 0.85f), fontSize = 12.sp)
                    Text(state.pointsBalance.toString(), color = MobileColors.Panel, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("下单时可按比例抵扣现金", color = MobileColors.Panel.copy(alpha = 0.8f), fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item { SectionTitle("积分明细") }
        if (state.pointsHistory.isEmpty()) {
            item { Text("还没有积分记录", color = MobileColors.Ink3, fontSize = 12.5.sp) }
        } else {
            items(state.pointsHistory) { entry ->
                PointsCell(entry)
            }
        }
    }
}

/** 工单与退款进度 */
@Composable
fun TicketsScreen(state: MobileState) {
    var tab by remember { mutableStateOf("工单") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ChipRow(listOf("工单", "退款"), tab) {
                        tab = it
                    }
                }
                if (tab == "工单") TonalButton("新建", onClick = { state.sheet = MobileSheet.Ticket })
            }
        }
        if (tab == "工单") {
            if (state.tickets.isEmpty()) {
                item { EmptyState("还没有工单", "遇到问题随时找平台") }
            } else {
                items(state.tickets) { ticket ->
                    TicketCell(ticket)
                }
            }
        } else {
            if (state.refunds.isEmpty()) {
                item { EmptyState("还没有退款申请", "在订单详情里可以发起") }
            } else {
                items(state.refunds) { refund ->
                    SimpleRow(
                        title = refund.orderNo,
                        subtitle = refund.reason.ifBlank { "未填写原因" } + " · " + refund.createdAt.take(16),
                        trailing = state.money(refund.amount),
                        trailingTag = refundStatusText(refund.status),
                        tone = statusTone(refund.status),
                    )
                }
            }
        }
    }
}
