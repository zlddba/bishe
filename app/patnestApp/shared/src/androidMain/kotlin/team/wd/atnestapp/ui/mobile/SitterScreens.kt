package team.wd.atnestapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.SitterCouponDto
import team.wd.atnestapp.data.api.SitterCouponPayload
import team.wd.atnestapp.ui.components.AppIcon
import java.time.LocalDate

/** 机构端概览：先把今天要处理的事摊开，再给近 7 天的趋势 */
@Composable
fun SbOverviewScreen(state: MobileState, onQuick: (MobileScreen) -> Unit) {
    val shop = state.myShop;
    val stats = state.sitterStats;
    val pending = state.receivedOrders.filter { it.status == "confirmed" }
    val active = state.receivedOrders.filter { it.status in listOf("accepted", "staying") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(shop?.name ?: "我的机构", color = MobileColors.Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                if (shop == null) "正在读取机构资料" else shopTypeText(shop.type) + " · " + shop.address.orEmpty(),
                color = MobileColors.Ink2,
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (pending.isNotEmpty()) {
            ActionRowBanner(
                tone = TagTone.Warn,
                title = "有 " + pending.size + " 个订单等你确认",
                body = "超时未确认会影响主人的寄养安排",
                action = "去接单",
                onClick = { onQuick(MobileScreen.SbOrders) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("待确认", pending.size.toString(), hint = "需要接单")
            MetricCard("在住", active.size.toString(), hint = "正在寄养")
            MetricCard("今日流水", state.money(stats?.todayRevenue ?: 0.0), hint = "含未结算", tone = MobileColors.Ok)
        }

        Column {
            SectionTitle("近 7 天订单")
            Spacer(Modifier.height(9.dp))
            MsCard {
                TrendBars(stats?.trend.orEmpty().map { it.orders to it.date })
            }
        }

        Eyebrow("快捷入口")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickRow(AppIcon.Orders, "接单管理", "确认、拒绝、办理离店", onClick = { onQuick(MobileScreen.SbOrders) })
            QuickRow(AppIcon.Calendar, "容量日历", "按天调整可接待数量", onClick = { onQuick(MobileScreen.SbCapacity) })
            QuickRow(AppIcon.Shop, "机构资料", "价格、地址、封面与加购服务", onClick = { onQuick(MobileScreen.SbShop) })
        }

        Spacer(Modifier.height(18.dp))
    }
}

fun shopTypeText(type: String): String = when (type) {
    "home" -> "家庭寄养"
    "shop" -> "门店寄养"
    "visit" -> "上门喂养"
    else -> "其他"
}

@Composable
private fun ActionRowBanner(tone: TagTone, title: String, body: String, action: String, onClick: () -> Unit) {
    val (bg, fg) = tagColors(tone)
    Row(
        Modifier.fillMaxWidth().clip(MobileShape.Lg).background(bg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = fg.copy(alpha = 0.78f), fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.clip(MobileShape.Pill).background(MobileColors.Panel).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
        ) {
            Text(action, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 用等宽柱子画一周订单量，不需要引入图表库 */
@Composable
private fun TrendBars(points: List<Pair<Int, String>>) {
    if (points.isEmpty()) {
        Text("暂无统计数据", color = MobileColors.Ink3, fontSize = 12.5.sp)
        return
    }
    val maxOrders = points.maxOf { it.first }.coerceAtLeast(1);
    Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        points.forEach { (orders, day) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(orders.toString(), color = MobileColors.BrandDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                val ratio = orders.toFloat() / maxOrders.toFloat()
                Box(
                    Modifier.fillMaxWidth().height((14 + ratio * 62).dp).clip(MobileShape.Sm).background(
                        if (orders == 0) MobileColors.NeutralSoft else MobileColors.Brand300,
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(day.takeLast(5).replace("-", "/"), color = MobileColors.Ink3, fontSize = 9.5.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun QuickRow(icon: AppIcon, title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(MobileShape.Lg).background(MobileColors.Panel).border(1.dp, MobileColors.Line, MobileShape.Lg),
    ) {
        CellRow(icon, title, subtitle, onClick = onClick)
    }
}

/** 接单管理 */
@Composable
fun SbOrdersScreen(state: MobileState) {
    var tab by remember { mutableStateOf("待确认") }
    val all = state.receivedOrders
    val list = when (tab) {
        "待确认" -> all.filter { it.status == "confirmed" }
        "进行中" -> all.filter { it.status in listOf("accepted", "staying") }
        "已结束" -> all.filter { it.status in listOf("completed", "evaluated") }
        else -> all
    }
    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf("全部", "待确认", "进行中", "已结束"), tab, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { tab = it }
        if (state.receivedOrdersLoading && all.isEmpty()) {
            LoadingBars("正在加载订单")
        } else if (list.isEmpty()) {
            EmptyState("这个分类下暂时没有订单")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            ) {
                items(list) { order ->
                    ReceivedOrderCell(
                        order = order,
                        statusText = state.sitterStatusText(order.status),
                        money = { state.money(it) },
                        onAccept = { state.scope.launch { state.acceptOrder(order) } },
                        onReject = { openRejectSheet(state, order) },
                        onComplete = { state.scope.launch { state.completeOrder(order) } },
                    )
                }
            }
        }
    }
}

private fun openRejectSheet(state: MobileState, order: team.wd.atnestapp.data.api.ReceivedOrderDto) {
    state.sheet = MobileSheet.Reason(
        title = "拒绝订单 " + order.orderNo,
        hint = "写清楚原因，主人可以及时改选别的机构",
        submitLabel = "确认拒绝",
        initial = "机构暂时无法接待",
    ) { reason ->
        state.scope.launch { state.rejectOrder(order, reason) }
    }
}

/** 容量日历：默认按店铺容量，逐日覆盖 */
@Composable
fun SbCapacityScreen(state: MobileState) {
    val shop = state.myShop
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().padding(16.dp).clip(MobileShape.Lg).background(MobileColors.Brand50).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcon.Calendar, Modifier.size(18.dp), MobileColors.BrandDeep)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("默认容量 " + (shop?.capacity ?: 0) + " 只/晚", color = MobileColors.Brand900, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    Text("点某一天可以单独改那天的接待数，节假日建议提前收紧", color = MobileColors.BrandDeep, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                LinkButton("去改默认值", onClick = { state.go(MobileScreen.SbShop) })
            }
        }
        if (state.capacityLoading && state.capacityItems.isEmpty()) {
            LoadingBars("正在加载容量")
        } else if (state.capacityItems.isEmpty()) {
            EmptyState("暂时读不到容量数据", action = { state.scope.launch { state.loadCapacity(force = true) } })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            ) {
                items(state.capacityItems) { day ->
                    CapacityCell(day) {
                        state.sheet = MobileSheet.CapacityForm(day.date, day.capacity)
                    }
                }
            }
        }
    }
}

/** 收益与结算 */
@Composable
fun SbIncomeScreen(state: MobileState) {
    val data = state.earnings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    ) {
        if (state.earningsLoading && data == null) {
            item { LoadingBars("正在统计收益") }
        } else {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(MobileShape.Lg).background(PrimaryGradient).padding(18.dp),
                ) {
                    Column {
                        Text("近 30 天可到账", color = MobileColors.Panel.copy(alpha = 0.85f), fontSize = 12.sp)
                        Text(state.money(data?.summary?.netAmount ?: 0.0), color = MobileColors.Panel, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SmallLightMetric("订单", (data?.summary?.orderCount ?: 0).toString())
                            SmallLightMetric("流水", state.money(data?.summary?.grossAmount ?: 0.0))
                            SmallLightMetric("服务费", state.money(data?.summary?.commissionAmount ?: 0.0))
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionTitle("结算申请", modifier = Modifier.weight(1f))
                    LinkButton("本月结算", onClick = { state.scope.launch { state.applySettlement() } })
                }
            }
            if (state.settlements.isEmpty()) {
                item { Text("还没有结算记录", color = MobileColors.Ink3, fontSize = 12.5.sp) }
            } else {
                items(state.settlements) { item ->
                    SettlementCell(item = item, money = { state.money(it) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionTitle("收入明细", modifier = Modifier.weight(1f))
                    Text("共 " + (data?.items?.size ?: 0) + " 笔", color = MobileColors.Ink3, fontSize = 11.5.sp)
                }
            }
            val rows = data?.items.orEmpty()
            items(rows) { entry ->
                EarningsCell(entry) { state.money(it) }
            }
            if (rows.isEmpty()) {
                item { Text("这段时间还没有已完成的订单", color = MobileColors.Ink3, fontSize = 12.5.sp) }
            }
        }
    }
}

@Composable
private fun SmallLightMetric(label: String, value: String) {
    Column {
        Text(label, color = MobileColors.Panel.copy(alpha = 0.75f), fontSize = 10.5.sp)
        Text(value, color = MobileColors.Panel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 评价管理：看差评、回评价 */
@Composable
fun SbReviewsScreen(state: MobileState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            SectionTitle("评价管理", subtitle = "共 " + state.sitterReviews.size + " 条")
        }
        if (state.sitterReviews.isEmpty()) {
            item { EmptyState("还没有收到评价", "订单完成并评价后会出现在这里") }
        } else {
            items(state.sitterReviews) { review ->
                ReviewCell(review = review, onReply = { openReplySheet(state, review) })
            }
        }
    }
}

private fun openReplySheet(state: MobileState, review: team.wd.atnestapp.data.api.SitterReviewDto) {
    state.sheet = MobileSheet.Reason(
        title = "回复这条 " + review.overallScore + " 星评价",
        hint = "回复会公开展示在机构详情页",
        submitLabel = "发送回复",
        initial = "感谢你的反馈，",
    ) { text ->
        state.scope.launch { state.replyReview(review.id, text) }
    }
}

/** 动态发布 */
@Composable
fun SbDynamicsScreen(state: MobileState) {
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var orderIdx by remember { mutableStateOf(0) }
    var photos by remember { mutableStateOf(listOf<String>()) }
    var uploading by remember { mutableStateOf(false) }
    val pick = rememberPhotoPicker { fileName, bytes ->
        if (fileName.isEmpty()) return@rememberPhotoPicker
        uploading = true
        scope.launch {
            val url = state.uploadPhoto(fileName, bytes)
            if (url != null) photos = photos + url
            uploading = false
        }
    }
    val candidates = state.receivedOrders.filter { it.status in listOf("accepted", "staying", "completed") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SectionTitle("发布动态", subtitle = "主人会在订单和动态流里看到")
        MsCard {
            if (candidates.isEmpty()) {
                Text("当前没有可以发布动态的订单", color = MobileColors.Ink3, fontSize = 12.5.sp)
            } else {
                Text("关联订单", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 6.dp))
                ChipRow(candidates.map { it.orderNo.takeLast(6) }, candidates.getOrElse(orderIdx) { candidates.first() }.orderNo.takeLast(6)) { picked ->
                    orderIdx = candidates.indexOfFirst { it.orderNo.takeLast(6) == picked }.coerceAtLeast(0)
                }
                Spacer(Modifier.height(11.dp))
                MsField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = "今天吃了多少、精神状态、遛弯时长都可以写",
                    multiline = true,
                )
                if (photos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    PhotoGrid(photos, size = 70.dp, onClick = { state.sheet = MobileSheet.Photo(it, "动态图片") })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = if (uploading) "上传中" else "添加图片",
                        onClick = { if (!uploading) pick() },
                        modifier = Modifier.weight(1f),
                        icon = AppIcon.Image,
                    )
                    PrimaryButton(
                        text = "发布",
                        onClick = {
                            val target = candidates.getOrElse(orderIdx) { candidates.first() }
                            scope.launch { state.publishDynamic(target.id, content, photos) }
                            content = ""
                            photos = emptyList()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        SectionTitle("已发布", subtitle = state.sitterDynamics.size.toString() + " 条")
        if (state.sitterDynamics.isEmpty()) {
            Text("还没有发布过动态", color = MobileColors.Ink3, fontSize = 12.5.sp)
        } else {
            state.sitterDynamics.forEach { dynamic ->
                Column {
                    DynamicCard(
                        dynamic = dynamic,
                        liked = false,
                        onLike = {},
                        onPhoto = { state.sheet = MobileSheet.Photo(it, dynamic.sitterName) },
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        LinkButton("删除这条动态", { state.scope.launch { state.deleteDynamic(dynamic) } }, color = MobileColors.Danger)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 机构优惠券管理 */
@Composable
fun SbCouponsScreen(state: MobileState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                SectionTitle("优惠券", modifier = Modifier.weight(1f))
                LinkButton("新建", onClick = { state.sheet = MobileSheet.CouponForm(null) })
            }
        }
        if (state.sitterCoupons.isEmpty()) {
            item { EmptyState("还没有发过优惠券", "新店可以用券吸引第一波主人") }
        } else {
            items(state.sitterCoupons) { coupon ->
                CouponCell(
                    title = coupon.title,
                    amount = couponText(coupon.type, coupon.value),
                    rule = "已领 " + coupon.claimedQty + "/" + coupon.totalQty,
                    status = if (coupon.enabled) "领取中" else "已停用",
                    tone = if (coupon.enabled) TagTone.Ok else TagTone.Danger,
                    action = if (coupon.enabled) "停用" else "启用",
                    onAction = { state.scope.launch { state.toggleSitterCoupon(coupon) } },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkButton("编辑", onClick = { state.sheet = MobileSheet.CouponForm(coupon) })
                    LinkButton("删除", onClick = { openDeleteCouponSheet(state, coupon) }, color = MobileColors.Danger)
                }
            }
        }
    }
}

private fun openDeleteCouponSheet(state: MobileState, coupon: SitterCouponDto) {
    state.sheet = MobileSheet.Message(
        title = "删除优惠券",
        body = coupon.title + " 将被删除，已领取的券也会失效。",
        actionLabel = "确认删除",
        onAction = { state.scope.launch { state.deleteSitterCoupon(coupon) } },
    )
}

/**
 * 机构资料：一次编辑价格、容量、地址与实拍，下面顺手管理加购与节假日定价。
 * 手机端没有内嵌地图，坐标由后端地理编码补齐，用户只需要把地址写清楚。
 */
@Composable
fun SbShopScreen(state: MobileState) {
    val shop = state.myShop
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (shop == null) {
        if (state.shopLoading) {
            LoadingBars("正在读取机构资料")
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                SectionTitle("机构资料还没准备好")
                Spacer(Modifier.height(10.dp))
                Text(state.shopError ?: "如果账号还没通过入驻审核，资料会在审核通过后出现。", color = MobileColors.Ink2, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("重新加载", { state.scope.launch { state.loadMyShop(force = true) } }, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    var name by remember(shop.id) { mutableStateOf(shop.name) }
    var typeLabel by remember(shop.id) { mutableStateOf(shopTypeText(shop.type)) }
    var description by remember(shop.id) { mutableStateOf(shop.description ?: "") }
    var priceMode by remember(shop.id) { mutableStateOf(if (shop.priceMode == "per_visit") "按次上门" else "按天寄养") }
    var dailyPrice by remember(shop.id) { mutableStateOf(shop.dailyPrice.toInt().toString()) }
    var visitPrice by remember(shop.id) { mutableStateOf(shop.visitPrice.toInt().toString()) }
    var capacity by remember(shop.id) { mutableStateOf(shop.capacity) }
    var address by remember(shop.id) { mutableStateOf(shop.address ?: "") }
    var coverUrl by remember(shop.id) { mutableStateOf(shop.coverPhotoUrl.orEmpty()) }
    var detailPhotos by remember(shop.id) { mutableStateOf(shop.detailPhotos) }
    var latitude by remember(shop.id) { mutableStateOf(shop.latitude) }
    var longitude by remember(shop.id) { mutableStateOf(shop.longitude) }
    var resolvedNote by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf("cover") }
    val pick = rememberPhotoPicker { fileName, bytes ->
        if (fileName.isEmpty()) return@rememberPhotoPicker
        uploading = true
        scope.launch {
            val url = state.uploadPhoto(fileName, bytes) ?: return@launch
            if (photoTarget == "cover") coverUrl = url else detailPhotos = detailPhotos + url
            uploading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SectionTitle("对外展示")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                CoverBox(coverUrl, Modifier.fillMaxWidth().height(132.dp), iconSize = 30)
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = if (uploading) "上传中" else "更换封面",
                    onClick = {
                        photoTarget = "cover"
                        if (!uploading) pick()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcon.Image,
                    height = 38.dp,
                )
            }
        }
        if (detailPhotos.isNotEmpty()) {
            PhotoGrid(detailPhotos, size = 76.dp, onClick = { state.sheet = MobileSheet.Photo(it, "实拍图") })
        }
        GhostButton(
            text = if (uploading) "上传中" else "添加实拍图（" + detailPhotos.size + "）",
            onClick = {
                photoTarget = "detail"
                if (!uploading) pick()
            },
            modifier = Modifier.fillMaxWidth(),
            height = 38.dp,
        )

        MsField(name, { name = it }, label = "机构名称")
        Column {
            Text("经营类型", color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            ChipRow(listOf("家庭寄养", "门店寄养", "上门喂养", "其他"), typeLabel) { typeLabel = it }
        }
        MsField(description, { description = it }, label = "机构简介", placeholder = "场地面积、陪伴时间、接送范围", multiline = true)

        SectionTitle("价格与接待")
        ChipRow(listOf("按天寄养", "按次上门"), priceMode) { priceMode = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                MsField(dailyPrice, { dailyPrice = it }, label = "每天价格", numeric = true)
            }
            Box(Modifier.weight(1f)) {
                MsField(visitPrice, { visitPrice = it }, label = "每次价格", numeric = true)
            }
        }
        StepperRow(
            label = "默认接待容量",
            hint = "每晚最多能接多少只，单日可在容量日历里单独调整",
            value = capacity,
            minValue = 1,
            maxValue = 200,
            suffix = " 只",
            onChange = { capacity = it },
        )
        if (capacity != shop.capacity) {
            Text("调小容量只影响之后的日期，已在住的宠物不会被清退", color = MobileColors.Ink3, fontSize = 11.5.sp)
        }

        SectionTitle("位置", subtitle = "主人可以在详情页一键导航")
        MsField(address, { address = it }, label = "所在地址", placeholder = "省市 + 详细街道门牌")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                text = "解析坐标",
                onClick = {
                    scope.launch {
                        val geo = state.resolveAddress(address, "")
                        if (geo != null) {
                            latitude = geo.latitude
                            longitude = geo.longitude
                            resolvedNote = geo.formattedAddress
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                height = 38.dp,
            )
            val mapLat = latitude
            val mapLng = longitude
            if (mapLat != null && mapLng != null) {
                GhostButton("在地图查看", {
                    openNavigation(context, mapLat, mapLng, name)
                }, modifier = Modifier.weight(1f), height = 38.dp)
            }
        }
        Text(
            if (resolvedNote.isNotBlank()) resolvedNote else if (latitude != null) "已定位：" + latitude + ", " + longitude else "还没有坐标，主人只能看文字地址",
            color = MobileColors.Ink3,
            fontSize = 11.5.sp,
        )

        PrimaryButton(
            text = if (state.busy) "保存中" else "保存机构资料",
            onClick = {
                scope.launch {
                    state.saveMyShop(
                        name = name,
                        type = shopTypeCode(typeLabel),
                        description = description,
                        dailyPrice = dailyPrice.toDoubleOrNull() ?: shop.dailyPrice,
                        visitPrice = visitPrice.toDoubleOrNull() ?: shop.visitPrice,
                        priceMode = if (priceMode == "按次上门") "per_visit" else "per_day",
                        capacity = capacity,
                        address = address,
                        coverPhotoUrl = coverUrl,
                        detailPhotos = detailPhotos,
                        latitude = latitude,
                        longitude = longitude,
                    )
                }
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle("加购服务", modifier = Modifier.weight(1f))
            LinkButton("新增", onClick = { state.sheet = MobileSheet.AddonForm(null) })
        }
        if (state.myAddons.isEmpty()) {
            Text("还没有加购服务，可以加洗澡、陪玩、接送这类项目", color = MobileColors.Ink3, fontSize = 12.sp)
        } else {
            state.myAddons.forEach { addon ->
                AddonCell(
                    addon = addon,
                    price = state.money(addon.price),
                    onToggle = { state.scope.launch { state.toggleAddon(addon) } },
                    onEdit = { state.sheet = MobileSheet.AddonForm(addon) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle("节假日定价", modifier = Modifier.weight(1f))
            LinkButton("新增", onClick = { state.sheet = MobileSheet.PriceRuleForm(state.priceRules) })
        }
        if (state.priceRules.isEmpty()) {
            Text("没有节假日规则，春节、国庆这类高峰可以提前设倍率", color = MobileColors.Ink3, fontSize = 12.sp)
        } else {
            state.priceRules.forEach { rule ->
                PriceRuleCell(rule) { state.scope.launch { state.removePriceRule(rule) } }
                Spacer(Modifier.height(8.dp))
            }
        }
        SectionTitle("账号")
        Text("这个账号同时是机构管理员，切回主人端可以下单、管理宠物和优惠券。", color = MobileColors.Ink3, fontSize = 12.sp)
        CellRow(AppIcon.User, "返回主人端", "下单、宠物、订单都在这里", onClick = { state.switchToOwner() })

        Spacer(Modifier.height(20.dp))
    }
}

fun shopTypeCode(label: String): String = when (label) {
    "家庭寄养" -> "home"
    "门店寄养" -> "shop"
    "上门喂养" -> "visit"
    else -> "other"
}
