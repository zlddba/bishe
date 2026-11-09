package team.wd.atnestapp.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import team.wd.atnestapp.data.api.DynamicCommentDto
import team.wd.atnestapp.ui.components.AppIcon

/* ================================================================== *
 * 第 3 组：㉛~㉩ 屏（1186-1663 行）。版式按
 * docs/KMP-Android端UI设计稿.html 还原，数据已接真实接口。
 * 本组私有数据与 helper 统一 C_ 前缀，避免与其它分组文件重名。
 * 注意：DFrame 的 content 已包在 verticalScroll 的 Column 里，
 * 因此本文件不在滚动内容里按权重分配剩余高度；等宽布局改用
 * BoxWithConstraints 计算宽度 + Arrangement.SpaceBetween / SpaceAround。
 * ================================================================== */

/* ㉛ 机构评价（查看 + 回复） */
@Composable
fun D31SitterReviews(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadSitterReviews() }
    val reviews = store.sitterReviews
    var replyFor by remember { mutableStateOf(0L) }
    var replyText by remember { mutableStateOf("") }
    val average = if (reviews.isEmpty()) 0.0 else reviews.map { it.overallScore }.average()
    // 保留一位小数，整数也补 .0，避免 4 和 4.3 混排
    val averageText = if (reviews.isEmpty()) {
        "—"
    } else {
        val rounded = (average * 10).roundToInt() / 10.0
        if (rounded % 1.0 == 0.0) "${rounded.toLong()}.0" else rounded.toString()
    }
    DFrame(nav = nav, title = "我的评价", backable = true) {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        averageText,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.BrandInk,
                    )
                    Spacer(Modifier.height(4.dp))
                    DStars(average, size = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("共 ${reviews.size} 条评价", fontSize = 12.sp, color = DColor.Ink3)
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            if (reviews.isEmpty()) {
                DEmpty("还没有主人评价")
            } else {
                DGrid(reviews, minItemWidth = 400.dp) { review ->
                    C_ReviewCard(
                        avatar = dInitial("主人"),
                        avatarBg = DColor.BrandSoft,
                        avatarFg = DColor.BrandInk,
                        name = "宠物主人",
                        stars = review.overallScore,
                        date = review.createdAt.take(10),
                        body = review.ownerComment?.takeIf { it.isNotBlank() } ?: "主人没有填写文字评价",
                        tags = listOf(
                            "服务 ${review.serviceScore}",
                            "环境 ${review.environmentScore}",
                            "卫生 ${review.hygieneScore}",
                        ),
                        reply = review.replyText?.takeIf { it.isNotBlank() }?.let { "机构回复：$it" },
                        footer = {
                            if (review.replyText.isNullOrBlank()) {
                                Spacer(Modifier.height(10.dp))
                                DSmallButton(
                                    if (replyFor == review.id) "取消回复" else "回复评价",
                                    { replyFor = if (replyFor == review.id) 0L else review.id },
                                )
                            }
                        },
                    )
                    if (replyFor == review.id) {
                        DCard {
                            DFieldInput(
                                label = "回复内容",
                                value = replyText,
                                onValueChange = { replyText = it },
                                placeholder = "感谢反馈，我们会继续改进…",
                                multiline = true,
                            )
                            DButton(
                                "提交回复",
                                {
                                    if (replyText.isBlank()) {
                                        nav.toast("回复内容不能为空")
                                    } else {
                                        scope.launch {
                                            store.replyReview(review.id, replyText.trim())
                                            replyText = ""
                                            replyFor = 0L
                                        }
                                    }
                                },
                                height = 40.dp,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/* ㉜ 动态详情（点赞 + 评论） */
@Composable
fun D32DynamicDetail(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val dynamic = store.selectedDynamic
    if (dynamic == null) {
        DFrame(nav = nav, title = "动态详情") {
            DEmpty("动态不存在或已删除", "返回") { nav.back() }
        }
        return
    }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(dynamic.id) { store.loadDynamicComments(dynamic.id) }
    val sendComment: () -> Unit = {
        scope.launch {
            if (store.sendDynamicComment(dynamic.id, draft)) draft = ""
        }
    }
    DFrame(nav = nav, title = "动态详情") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DAvatar(dInitial(dynamic.sitterName), size = 32.dp, rounded = false)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            dynamic.sitterName.ifBlank { "机构动态" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ink,
                        )
                        Text(
                            dynamic.createdAt.replace("T", " ").take(16),
                            fontSize = 11.sp,
                            color = DColor.Ink3,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (dynamic.photos.isNotEmpty()) {
                    DImageUrl(dynamic.photos.first(), "", height = 200.dp)
                    Spacer(Modifier.height(10.dp))
                }
                Text(dynamic.content, fontSize = 13.5.sp, color = DColor.Ink)
                if (dynamic.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        dynamic.tags.joinToString("  ") { "#$it" },
                        fontSize = 11.5.sp,
                        color = DColor.BrandInk,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val liked = store.likedDynamics.contains(dynamic.id)
                    Row(
                        Modifier.clickable { scope.launch { store.toggleLike(dynamic.id) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(AppIcon.Heart, Modifier.size(15.dp), if (liked) DColor.Brand else DColor.Ink3)
                        Spacer(Modifier.width(4.dp))
                        Text(if (liked) "已赞" else "点赞", fontSize = 12.sp, color = DColor.Ink3)
                    }
                    // 点赞总数来自留言接口的顺带返回（含别人点的），比本地「我赞过没」准
                    if (store.dynamicLikes > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text("${store.dynamicLikes} 人赞过", fontSize = 11.5.sp, color = DColor.Ink3)
                    }
                }
            }
        }
        DPad {
            Spacer(Modifier.height(10.dp))
            DCard {
                DKeyValue("关联订单", dynamic.orderNo.ifBlank { "—" })
            }
        }
        DSection(
            if (store.dynamicComments.isEmpty()) "留言"
            else "留言 ${store.dynamicComments.size}",
        )
        DPad {
            DCard {
                when {
                    store.commentsLoading && store.dynamicComments.isEmpty() ->
                        Text("留言加载中…", fontSize = 12.sp, color = DColor.Ink3)

                    store.dynamicComments.isEmpty() ->
                        Text("还没有人留言，来说第一句吧", fontSize = 12.sp, color = DColor.Ink3)

                    else -> store.dynamicComments.forEachIndexed { index, comment ->
                        if (index > 0) DDivider()
                        C_CommentRow(comment)
                    }
                }
            }
        }
        DPad {
            DCard {
                DFieldInput(
                    label = "写留言",
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "最多 500 字，聊聊寄养期间的见闻…",
                    multiline = true,
                    enabled = !store.commentSending,
                    onSubmit = sendComment,
                )
                DButton(
                    if (store.commentSending) "发送中…" else "发送留言",
                    sendComment,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun D33PetHistory(nav: DNav) {
    val store = DLocalStore.current
    val pet = store.viewingPet
    LaunchedEffect(pet?.id) { pet?.let { store.loadPetStays(it) } }
    DFrame(nav = nav, title = "${pet?.name ?: "宠物"} · 寄养记录") {
        DPad {
            Spacer(Modifier.height(14.dp))
            if (store.petStays.isEmpty()) {
                DEmpty("还没有寄养记录", "去预约") { nav.tab(DTab.Sitters) }
            } else {
                store.petStays.forEach { stay ->
                    DCard(modifier = Modifier.padding(bottom = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stay.sitterName.ifBlank { "未知机构" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            DTag(
                                when (stay.status) {
                                    "completed", "evaluated" -> "已完成"
                                    "staying" -> "寄养中"
                                    "cancelled" -> "已取消"
                                    else -> stay.status
                                },
                                dOrderTone(stay.status),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        DKeyValue("日期", "${stay.startDate} - ${stay.endDate}")
                        DKeyValue("天数", "${stay.totalDays} 天")
                        DKeyValue("金额", store.money(stay.totalAmount))
                        DKeyValue("订单号", stay.orderNo)
                    }
                }
            }
        }
    }
}

@Composable
fun D34RefundProgress(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadRefunds() }
    var expanded by remember { mutableStateOf(0L) }
    val first = store.refunds.firstOrNull()
    DFrame(nav = nav, title = "退款进度", backable = true) {
        DPad {
            Spacer(Modifier.height(14.dp))
            if (store.refunds.isEmpty()) {
                DEmpty("还没有退款申请", "去订单看看") { nav.go(DScreen.Orders) }
            } else {
                store.refunds.forEach { refund ->
                    val tone = when (refund.status) {
                        "paid", "approved", "refunded" -> DTagTone.Ok
                        "rejected", "failed" -> DTagTone.Danger
                        else -> DTagTone.Warn
                    }
                    val barColor = when (tone) {
                        DTagTone.Ok -> DColor.Ok
                        DTagTone.Danger -> DColor.Danger
                        else -> DColor.Warn
                    }
                    val open = expanded == refund.id
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            .clip(DShape.Lg).background(DColor.Card)
                            .border(1.dp, DColor.Line, DShape.Lg)
                            .clickable { expanded = if (open) 0L else refund.id }
                            .padding(14.dp),
                    ) {
                        C_SplitRow(
                            fixedWidth = 3.dp,
                            gap = 10.dp,
                            verticalAlignment = Alignment.Top,
                            fixed = {
                                Box(
                                    Modifier.width(3.dp).height(70.dp).clip(DShape.Pill)
                                        .background(barColor),
                                )
                            },
                            flexible = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        refund.orderNo.ifBlank { "订单 ${refund.orderId}" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DColor.Ink,
                                    )
                                    DTag(
                                        when (refund.status) {
                                            "paid", "refunded" -> "已退款"
                                            "approved" -> "已通过"
                                            "rejected" -> "已驳回"
                                            "failed" -> "退款失败"
                                            else -> "审核中"
                                        },
                                        tone,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "退款金额：${store.money(refund.amount)}",
                                    fontSize = 12.sp,
                                    color = DColor.Ink3,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "原因：${refund.reason.ifBlank { "未填写" }}",
                                    fontSize = 12.sp,
                                    color = DColor.Ink3,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "申请时间：${refund.createdAt.replace("T", " ").take(16)}",
                                    fontSize = 12.sp,
                                    color = DColor.Ink3,
                                )
                                if (open) {
                                    Spacer(Modifier.height(10.dp))
                                    DNotice("如机构 48 小时内未处理，平台将自动介入并原路退款。")
                                }
                            },
                        )
                    }
                }
            }
        }
        DSection("处理进度")
        DPad {
            DCard {
                DTimelineNode(
                    "提交申请",
                    first?.createdAt?.replace("T", " ")?.take(16).orEmpty(),
                    DNodeState.Done,
                )
                val pending = first != null && first.status !in setOf("paid", "refunded", "rejected", "failed")
                DTimelineNode(
                    if (pending) "机构审核中" else "机构已处理",
                    if (pending) "等待机构确认退款金额" else "机构已给出处理结果",
                    if (pending) DNodeState.Current else DNodeState.Done,
                )
                DTimelineNode(
                    "平台打款",
                    "预计 1-3 个工作日原路退回",
                    if (first?.status in setOf("paid", "refunded")) DNodeState.Done else DNodeState.Todo,
                )
                DTimelineNode("退款完成", "", DNodeState.Todo, last = true)
            }
            Spacer(Modifier.height(14.dp))
            DButton(
                "刷新进度",
                { scope.launch { store.loadRefunds() } },
                style = DButtonStyle.Outline,
                height = 42.dp,
            )
        }
    }
}

/* ㉟ 加购服务管理（增改 + 启停） */
@Composable
fun D35Addons(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadMyAddons() }
    // null = 收起表单；0 = 新建；>0 = 编辑对应服务
    var editing by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("次") }
    val editingAddon = store.myAddons.firstOrNull { it.id == editing }
    DFrame(
        nav = nav,
        title = "加购服务",
        backable = true,
        actions = {
            DSmallButton(
                if (editing == null) "+ 新建" else "取消",
                onClick = {
                    if (editing == null) {
                        editing = 0L
                        name = ""
                        price = ""
                        unit = "次"
                    } else {
                        editing = null
                    }
                },
            )
        },
    ) {
        if (editing != null) {
            DPad {
                Spacer(Modifier.height(14.dp))
                DCard {
                    DFieldInput("服务名称", name, { name = it }, placeholder = "例如：遛宠")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DFieldInput(
                            "价格（¥）", price, { price = it },
                            modifier = Modifier.weight(1f), numeric = true, placeholder = "20",
                        )
                        DFieldInput(
                            "计价单位", unit, { unit = it },
                            modifier = Modifier.weight(1f), placeholder = "次",
                        )
                    }
                    DButton(
                        if (store.busy) "保存中…" else if (editingAddon == null) "新增服务" else "保存修改",
                        {
                            val p = price.toDoubleOrNull() ?: 0.0
                            val target = store.myAddons.firstOrNull { it.id == editing }
                            when {
                                name.isBlank() -> nav.toast("请填写服务名称")
                                p <= 0.0 -> nav.toast("价格要大于 0")
                                target == null -> scope.launch {
                                    store.createAddon(name.trim(), p, unit.ifBlank { "次" })
                                    editing = null
                                }
                                else -> scope.launch {
                                    store.updateAddon(target, name.trim(), p, unit.ifBlank { "次" })
                                    editing = null
                                }
                            }
                        },
                        enabled = !store.busy,
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            if (store.myAddons.isEmpty()) {
                DEmpty("还没有加购服务", "新建服务") {
                    editing = 0L
                    name = ""
                    price = ""
                    unit = "次"
                }
            } else {
                store.myAddons.forEach { addon ->
                    DCard(modifier = Modifier.padding(bottom = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                addon.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            DTag(
                                if (addon.enabled) "已启用" else "已停用",
                                if (addon.enabled) DTagTone.Ok else DTagTone.Line,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${store.money(addon.price)} / ${addon.unit}",
                            fontSize = 12.sp,
                            color = DColor.Ink3,
                        )
                        Spacer(Modifier.height(8.dp))
                        C_Links(
                            listOf(
                                "编辑" to DColor.BrandInk,
                                (if (addon.enabled) "停用" else "启用") to
                                    (if (addon.enabled) DColor.Warn else DColor.Ok),
                            ),
                            onLink = { label ->
                                when (label) {
                                    "编辑" -> {
                                        editing = addon.id
                                        name = addon.name
                                        price = if (addon.price % 1.0 == 0.0) {
                                            addon.price.toLong().toString()
                                        } else {
                                            addon.price.toString()
                                        }
                                        unit = addon.unit
                                    }
                                    else -> scope.launch { store.toggleAddon(addon) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ㉠ 节假日定价规则 */
@Composable
fun D36HolidayRules(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.loadPriceRules()
        if (store.myShop == null) store.loadMyShop()
    }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf("1.2") }
    val basePrice = store.myShop?.dailyPrice ?: 0.0
    DFrame(
        nav = nav,
        title = "节假日定价",
        backable = true,
        actions = { DSmallButton(if (creating) "取消" else "+ 规则", { creating = !creating }) },
    ) {
        if (creating) {
            DPad {
                Spacer(Modifier.height(14.dp))
                DCard {
                    DFieldInput("规则名称", name, { name = it }, placeholder = "例如：国庆上浮")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DFieldInput(
                            "开始日期", startDate, { startDate = it },
                            modifier = Modifier.weight(1f), placeholder = "2026-10-01",
                        )
                        DFieldInput(
                            "结束日期", endDate, { endDate = it },
                            modifier = Modifier.weight(1f), placeholder = "2026-10-07",
                        )
                    }
                    DFieldInput(
                        "上浮倍数", multiplier, { multiplier = it },
                        numeric = true, placeholder = "1.2",
                    )
                    val m = multiplier.toDoubleOrNull()
                    if (basePrice > 0 && m != null && m > 0) {
                        Text(
                            "原价 ${store.money(basePrice)}/天 → 上浮后 ${store.money(basePrice * m)}/天",
                            fontSize = 11.5.sp,
                            color = DColor.Ink3,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    DButton(
                        if (store.busy) "保存中…" else "保存规则",
                        {
                            val mValue = multiplier.toDoubleOrNull() ?: 0.0
                            when {
                                name.isBlank() -> nav.toast("请填写规则名称")
                                startDate.length < 10 || endDate.length < 10 ->
                                    nav.toast("日期格式为 yyyy-MM-dd")
                                mValue <= 0.0 -> nav.toast("上浮倍数要大于 0")
                                else -> scope.launch {
                                    store.createPriceRule(name.trim(), startDate.trim(), endDate.trim(), mValue)
                                    creating = false
                                    name = ""
                                    startDate = ""
                                    endDate = ""
                                }
                            }
                        },
                        enabled = !store.busy,
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            if (store.priceRules.isEmpty()) {
                DEmpty("还没有定价规则", "新建规则") { creating = true }
            } else {
                store.priceRules.forEach { rule ->
                    DCard(modifier = Modifier.padding(bottom = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                rule.name.ifBlank { "未命名规则" },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            DTag("×${rule.multiplier}", DTagTone.Warn)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${rule.startDate} ~ ${rule.endDate}",
                            fontSize = 12.sp,
                            color = DColor.Ink3,
                        )
                        if (basePrice > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "原价 ${store.money(basePrice)} → 上浮后 ${store.money(basePrice * rule.multiplier)}/天",
                                fontSize = 12.sp,
                                color = DColor.Ink3,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        C_Links(listOf("删除" to DColor.Danger)) {
                            scope.launch { store.removePriceRule(rule) }
                        }
                    }
                }
            }
        }
    }
}

/* ㉡ 机构订单详情（履约 + 完成寄养） */
@Composable
fun D37SitterOrderDetail(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val order = store.selectedReceivedOrder
    if (order == null) {
        DFrame(nav = nav, title = "订单详情", backable = true) {
            DPad {
                // 这一页只会从机构工作台点进来，退回上一页就是工作台；
                // 用 go() 会再压一个新工作台，退两次都是工作台
                DEmpty("还没有选择订单", "去接单") { nav.back() }
            }
        }
        return
    }
    val finished = order.status == "completed"
    DFrame(
        nav = nav,
        title = "订单详情",
        backable = true,
        actions = {
            DTag(store.sitterStatusText(order.status), dOrderTone(order.status))
        },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        order.ownerName.ifBlank { "宠物主人" },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DColor.Ink,
                    )
                    DTag(if (order.status == "confirmed") "待接单" else "已确认", DTagTone.Ok)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "订单号 ${order.orderNo} · ${order.createdAt.replace("T", " ").take(16)} 创建",
                    fontSize = 12.sp,
                    color = DColor.Ink3,
                )
                Spacer(Modifier.height(10.dp))
                DDivider()
                Spacer(Modifier.height(10.dp))
                C_SplitRow(
                    fixedWidth = 36.dp,
                    gap = 10.dp,
                    fixed = {
                        Box(
                            Modifier.size(36.dp).clip(DShape.Md).background(DColor.ImageGradient),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(AppIcon.Paw, Modifier.size(18.dp), DColor.White)
                        }
                    },
                    flexible = {
                        Text(
                            "共 ${order.petCount} 只宠物",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "宠物档案由主人下单时提交，可在接单前确认健康情况",
                            fontSize = 11.5.sp,
                            color = DColor.Ink3,
                        )
                    },
                )
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                DKeyValue("寄养日期", "${order.startDate} - ${order.endDate}")
                DKeyValue("寄养天数", "${order.totalDays} 天")
                DKeyValue("订单金额", store.money(order.totalAmount))
                DDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("实收", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Text(
                        store.money(order.totalAmount),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DColor.BrandInk,
                    )
                }
            }
        }
        DSection("履约操作")
        DPad {
            C_EqualButtons(
                listOf(
                    C_Btn("发布动态", DButtonStyle.Ghost) {
                        store.selectedOrderIdForDynamic = order.id
                        nav.go(DScreen.PublishDynamic)
                    },
                    C_Btn("容量日历", DButtonStyle.Ghost) { nav.go(DScreen.Capacity) },
                ),
                height = 40.dp,
            )
            Spacer(Modifier.height(10.dp))
            if (order.status == "confirmed") {
                DButton("确认接单", {
                    scope.launch {
                        store.acceptOrder(order)
                        store.selectedReceivedOrder = null
                    }
                })
                Spacer(Modifier.height(10.dp))
            }
            DButton(
                if (finished) "订单已完成" else "完成寄养",
                {
                    if (finished) {
                        nav.toast("该订单已完成")
                    } else {
                        scope.launch { store.completeOrder(order) }
                    }
                },
                enabled = !finished,
            )
        }
    }
}

/* ㉢ 搜索结果（筛选 + 排序） */
@Composable
fun D38SearchResults(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) { store.loadSitters() }
    var chips by remember { mutableStateOf(setOf<String>()) }
    var sort by remember { mutableStateOf(0) }
    val sorts = listOf("推荐", "评分", "价格低")
    val chipLabels = listOf("家庭寄养", "门店寄养", "上门喂养", "4 分以上", "有封面")
    // 关键词 + 筛选 + 排序三步都缓存，输入框每敲一个字只重算一次。
    val results = remember(store.sitters, store.sitterQuery, chips, sort) {
        val filtered = store.sitters.filter { sitter ->
            val queryHit = store.sitterQuery.isBlank() ||
                sitter.name.contains(store.sitterQuery, ignoreCase = true) ||
                sitter.type.contains(store.sitterQuery, ignoreCase = true) ||
                sitter.desc.contains(store.sitterQuery, ignoreCase = true)
            val chipHit = chips.isEmpty() || chips.any { chip ->
                when (chip) {
                    "家庭寄养", "门店寄养", "上门喂养" -> sitter.type == chip
                    "4 分以上" -> sitter.score >= 4
                    "有封面" -> sitter.coverPhoto.isNotBlank()
                    else -> true
                }
            }
            queryHit && chipHit
        }
        when (sort) {
            1 -> filtered.sortedByDescending { it.score }
            2 -> filtered.sortedBy { it.price }
            else -> filtered
        }
    }
    DFrame(nav = nav, title = null, backable = false) {
        DPad {
            Spacer(Modifier.height(8.dp))
            C_SplitRow(
                fixedWidth = 36.dp,
                gap = 10.dp,
                fixed = {
                    Box(
                        Modifier.size(36.dp).clip(DShape.Md).background(DColor.Card)
                            .clickable { nav.back() },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Back, Modifier.size(18.dp), DColor.Ink)
                    }
                },
                flexible = {
                    Box(
                        Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.Bg)
                            .clickable { nav.go(DScreen.SearchHistory) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            store.sitterQuery.ifBlank { "搜索机构名称 / 类型" },
                            fontSize = 13.5.sp,
                            color = if (store.sitterQuery.isBlank()) DColor.Ink3 else DColor.Ink,
                        )
                    }
                },
            )
        }
        DPad {
            Spacer(Modifier.height(12.dp))
            chipLabels.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { label ->
                        DChip(label, label in chips) {
                            chips = if (label in chips) chips - label else chips + label
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        DPad {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("找到 ${results.size} 家机构", fontSize = 12.5.sp, color = DColor.Ink3)
                DDropdown(
                    options = sorts,
                    selectedIndex = sort,
                    onSelect = { sort = it },
                ) { expanded ->
                    Row(
                        // 文字本身只有十几 dp 高，不加内边距点击区域太窄
                        Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("排序：${sorts[sort]}", fontSize = 12.5.sp, color = DColor.Ink3)
                        Spacer(Modifier.width(3.dp))
                        Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = DColor.Ink3)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                store.sittersLoading && results.isEmpty() -> DLoading()
                store.sittersError != null && results.isEmpty() ->
                    DErrorBox(store.sittersError ?: "加载失败") { store.reloadSittersByType() }
                results.isEmpty() -> DEmpty("没有匹配的机构", "清空筛选") { chips = emptySet() }
                else -> DGrid(results, minItemWidth = 340.dp) { sitter ->
                    DCard(
                        modifier = Modifier.padding(bottom = 12.dp)
                            .clickable { nav.openSitter(sitter) },
                        padding = 12.dp,
                    ) {
                        C_SplitRow(
                            fixedWidth = 86.dp,
                            gap = 12.dp,
                            verticalAlignment = Alignment.Top,
                            fixed = {
                                DCoverImage(
                                    url = sitter.coverPhoto,
                                    fallback = dInitial(sitter.name),
                                    width = 86.dp,
                                    height = 86.dp,
                                )
                            },
                            flexible = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        sitter.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DColor.Ink,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    if (sitter.status == "active") DTag("已核验", DTagTone.Ok)
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    C_StarsText(sitter.score)
                                    Spacer(Modifier.width(5.dp))
                                    Text(sitter.dRatingText(), fontSize = 11.5.sp, color = DColor.Ink3)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    sitter.dSubtitle(),
                                    fontSize = 11.5.sp,
                                    color = DColor.Ink3,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        store.money(sitter.price.toDouble()),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = DColor.BrandInk,
                                    )
                                    Text("/${sitter.unit}", fontSize = 11.sp, color = DColor.Ink3)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ㉣ 平台公告（后端以 system_announcement 通知下发） */
@Composable
fun D39Announcements(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadNotifications() }
    var expanded by remember { mutableStateOf(-1L) }
    val announcements = store.visibleNotifications().filter { notice ->
        val type = notice.type.lowercase()
        type.contains("announce") || type.contains("notice") || type == "system"
    }
    DFrame(nav = nav, title = "平台公告", backable = false) {
        DPad {
            Spacer(Modifier.height(14.dp))
            if (announcements.isEmpty()) {
                DEmpty("暂无平台公告")
            } else {
                DGrid(announcements, minItemWidth = 360.dp) { notice ->
                    val open = expanded == notice.id
                    DCard(modifier = Modifier.padding(bottom = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                notice.title.ifBlank { "平台公告" },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            Text(
                                notice.createdAt.take(10),
                                fontSize = 11.sp,
                                color = DColor.Ink3,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            notice.body,
                            fontSize = 12.sp,
                            color = DColor.Ink3,
                            maxLines = if (open) Int.MAX_VALUE else 2,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (open) "收起 ›" else "查看全文 ›",
                            fontSize = 12.sp,
                            color = DColor.BrandInk,
                            modifier = Modifier.clickable {
                                if (!open) scope.launch { store.markRead(notice.id) }
                                expanded = if (open) -1L else notice.id
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ㉤ 注册（用户名 + 角色选择） */
@Composable
fun D40Register(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    val doRegister: () -> Unit = {
        when {
            account.trim().length < 3 -> nav.toast("用户名至少 3 个字符")
            account.trim().matches(Regex("^1[3-9]\\d{9}$")) -> nav.toast("用户名不能是手机号")
            password.length < 8 -> nav.toast("密码至少 8 位")
            !phone.trim().matches(Regex("^1[3-9]\\d{9}$")) -> nav.toast("请填写正确的手机号")
            else -> scope.launch {
                store.register(account.trim(), password, phone.trim())
                // 注册页是从登录页点进来的，成功后要连登录页一起换掉，不能只 back()
                if (store.loggedIn) nav.afterLogin()
            }
        }
    }

    DFrame(nav = nav, title = "注册") {
        DPad {
            Spacer(Modifier.height(14.dp))
            Text("创建宠物主人账号", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DColor.Ink)
            Spacer(Modifier.height(6.dp))
            Text("注册后即可预约寄养、管理宠物档案", fontSize = 12.sp, color = DColor.Ink3)
            Spacer(Modifier.height(18.dp))
            DFieldInput("用户名", account, { account = it }, placeholder = "3-50 个字符，不能是手机号")
            DFieldInput("密码", password, { password = it }, placeholder = "至少 8 位", secret = true)
            DFieldInput("手机号", phone, { phone = it }, placeholder = "中国大陆 11 位手机号", numeric = true)
            DFieldInput("昵称（可选）", nickname, { nickname = it }, placeholder = "不填则使用用户名")
            Spacer(Modifier.height(6.dp))
            DButton(if (store.busy) "注册中…" else "注册并登录", doRegister)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "已有账号？返回登录",
                    fontSize = 12.5.sp,
                    color = DColor.BrandInk,
                    modifier = Modifier.clickable { nav.back() },
                )
            }
        }
    }
}

@Composable
fun D41ProfileEdit(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(store.profile?.nickname.orEmpty()) }
    var phone by remember { mutableStateOf(store.profile?.phone.orEmpty()) }
    var address by remember { mutableStateOf(store.profile?.address.orEmpty()) }
    var contactName by remember { mutableStateOf(store.profile?.contactName.orEmpty()) }
    var contactPhone by remember { mutableStateOf(store.profile?.contactPhone.orEmpty()) }
    LaunchedEffect(store.profile) {
        val p = store.profile ?: return@LaunchedEffect
        nickname = p.nickname.orEmpty()
        phone = p.phone.orEmpty()
        address = p.address.orEmpty()
        contactName = p.contactName.orEmpty()
        contactPhone = p.contactPhone.orEmpty()
    }
    LaunchedEffect(Unit) { store.loadProfile() }
    DFrame(nav = nav, title = "个人资料") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DFieldInput("昵称", nickname, { nickname = it }, placeholder = "展示给机构的名字")
            DFieldInput("手机号", phone, { phone = it }, placeholder = "用于登录与联系", numeric = true)
            DFieldInput("地址", address, { address = it }, placeholder = "省市区 + 详细地址")
            DFieldInput("联系人", contactName, { contactName = it }, placeholder = "紧急联系人")
            DFieldInput("联系电话", contactPhone, { contactPhone = it }, placeholder = "紧急联系人电话", numeric = true)
        }
        DPad {
            Spacer(Modifier.height(8.dp))
            DButton(
                if (store.busy) "保存中…" else "保存资料",
                {
                    scope.launch {
                        store.saveProfile(nickname.trim(), phone.trim(), address.trim(), contactName.trim(), contactPhone.trim())
                        nav.back()
                    }
                },
            )
        }
    }
}

@Composable
fun D42PetDetail(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val pet = store.viewingPet
    if (pet == null) {
        DFrame(nav = nav, title = "宠物详情") {
            DEmpty("还没有选择宠物", "我的宠物") { nav.go(DScreen.Pets) }
        }
        return
    }
    DFrame(nav = nav, title = pet.name) {
        DPad {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pet.photos.isNotEmpty()) {
                    DImageUrl(pet.photos.first(), pet.name, height = 140.dp)
                } else {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(DColor.BrandSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            dInitial(pet.name),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DColor.BrandInk,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                DTag(pet.cert, if (pet.cert == "已核验") DTagTone.Ok else DTagTone.Warn)
            }
        }
        DSection("基本档案")
        DPad {
            DCard {
                DKeyValue("品种", pet.breed)
                DKeyValue("类型", pet.type)
                DKeyValue("年龄", pet.age)
                DKeyValue("体型", pet.size)
                DKeyValue("疫苗", pet.vaccine)
                DKeyValue("备注", pet.note)
            }
        }
        DSection("免疫证明")
        DPad {
            DCard {
                DKeyValue("核验状态", pet.cert)
                DKeyValue("证明图片", "${pet.vaccinePhotos.size} 张")
                if (pet.vaccineNote.isNotBlank()) DKeyValue("核验备注", pet.vaccineNote)
            }
        }
        DPad {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DButton(
                    "寄养历史",
                    { nav.openPet(pet, DScreen.PetHistory) },
                    Modifier.weight(1f),
                    DButtonStyle.Ghost,
                    42.dp,
                )
                DButton(
                    "编辑档案",
                    {
                        store.editingPet = pet
                        nav.go(DScreen.PetAdd, guard = false)
                    },
                    Modifier.weight(1f),
                    DButtonStyle.Ghost,
                    42.dp,
                )
            }
            Spacer(Modifier.height(10.dp))
            DButton(
                "删除宠物",
                {
                    scope.launch {
                        store.deletePet(pet)
                        store.viewingPet = null
                        nav.back()
                    }
                },
                style = DButtonStyle.Outline,
                height = 42.dp,
            )
        }
    }
}

@Composable
fun D43ClaimCenter(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadCoupons() }
    val claimable = store.coupons?.claimable.orEmpty()
    DFrame(nav = nav, title = "领券中心") {
        DPad {
            Spacer(Modifier.height(10.dp))
            Text("领取后可在下单时抵扣", fontSize = 11.5.sp, color = DColor.Ink3)
        }
        if (claimable.isEmpty()) {
            DPad { DEmpty("暂无可领取的优惠券") }
        } else {
            DPad {
                DGrid(claimable, minItemWidth = 320.dp) { coupon ->
                    C_CouponCard(
                        amount = if (coupon.type == "percent") "${coupon.value.toInt()}折" else "¥${coupon.value.toInt()}",
                        threshold = if (coupon.minAmount > 0) "满 ${coupon.minAmount.toInt()} 可用" else "无门槛",
                        title = coupon.title,
                        sub = if (coupon.sitterId > 0) "仅限 ${coupon.issuer.ifBlank { "发券机构" }}" else "全平台通用",
                        expiry = if (coupon.endsAt.isNotBlank()) "有效期至 ${coupon.endsAt.take(10)}" else "",
                        grayed = false,
                    ) {
                        scope.launch {
                            store.claimCoupon(coupon.couponId)
                            store.loadCoupons(force = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun D44AddressAdd(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadProfile() }
    val profile = store.profile
    var contact by remember(profile) { mutableStateOf(profile?.contactName.orEmpty()) }
    var phone by remember(profile) {
        mutableStateOf(profile?.contactPhone?.takeIf { it.isNotBlank() } ?: profile?.phone.orEmpty())
    }
    var address by remember(profile) {
        mutableStateOf(store.pickedAddress.ifBlank { profile?.address.orEmpty() })
    }
    DFrame(nav = nav, title = "新增地址", backable = true) {
        DPad {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(DShape.Lg).background(DColor.MapWash)
                    .border(1.dp, DColor.Line, DShape.Lg)
                    .clickable { nav.go(DScreen.MapPicker) }
                    .padding(14.dp),
            ) {
                C_SplitRow(
                    fixedWidth = 22.dp,
                    gap = 10.dp,
                    fixed = { AppIcon(AppIcon.Paw, Modifier.size(22.dp), DColor.Ok) },
                    flexible = {
                        Text(
                            "地图选点定位",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                store.pickedAddress.isNotBlank() -> store.pickedAddress
                                store.pickedLatitude != null -> "已解析坐标，点击可重新选择"
                                else -> "点击按地址解析坐标"
                            },
                            fontSize = 12.sp,
                            color = DColor.Ink3,
                        )
                    },
                )
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DFieldInput("联系人", contact, { contact = it }, placeholder = "收宠联系人姓名")
            DFieldInput("手机号", phone, { phone = it }, numeric = true, placeholder = "11 位手机号")
            DFieldInput(
                label = "详细地址",
                value = address,
                onValueChange = { address = it },
                placeholder = "省市区 + 街道门牌",
                multiline = true,
            )
            Text(
                "地址与联系人保存在个人资料里，下单时自动带上；后端暂无独立地址簿。",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
            )
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DButton(
                if (store.busy) "保存中…" else "保存地址",
                {
                    when {
                        contact.isBlank() -> nav.toast("请填写联系人")
                        phone.filter { it.isDigit() }.length < 7 -> nav.toast("手机号格式不对")
                        address.isBlank() -> nav.toast("请填写详细地址")
                        else -> scope.launch {
                            store.saveProfile(
                                nicknameInput = store.nickname.ifBlank { store.account },
                                phone = profile?.phone.orEmpty(),
                                address = address.trim(),
                                contactName = contact.trim(),
                                contactPhone = phone.trim(),
                            )
                            store.pickedAddress = ""
                            nav.back()
                        }
                    }
                },
                enabled = !store.busy,
            )
        }
    }
}

/* ㉩ 联系客服 + 常见问题 */
@Composable
fun D45Support(nav: DNav) {
    var expanded by remember { mutableStateOf(-1) }
    val faqs = listOf(
        "如何取消订单？" to "进入「我的订单」找到对应订单，点击「取消订单」，寄养开始前可全额退款。",
        "退款多久到账？" to "机构确认退款后 1-3 个工作日原路退回，可在「退款进度」查看处理进度。",
        "宠物疫苗没打全能寄养吗？" to "需完成狂犬疫苗与猫三联接种并上传疫苗本，通过核验后方可寄养。",
        "如何成为寄养机构？" to "在「角色切换」中选择机构端，提交店铺资料与资质，审核通过即可接单。",
    )
    DFrame(nav = nav, title = "联系客服", backable = true) {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(DColor.BrandSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.OrdersPlain, Modifier.size(26.dp), DColor.BrandInk)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("在线客服", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("9:00 - 21:00 · 工单 24 小时内回复", fontSize = 12.sp, color = DColor.Ink3)
                    Spacer(Modifier.height(14.dp))
                    DButton("提交工单咨询", { nav.go(DScreen.Tickets) })
                }
            }
        }
        DSection("常见问题")
        DPad {
            DCard(padding = 0.dp) {
                faqs.forEachIndexed { i, (question, answer) ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            expanded = if (expanded == i) -1 else i
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(question, fontSize = 13.5.sp, color = DColor.Ink)
                            Text(if (expanded == i) "⌄" else "›", fontSize = 16.sp, color = DColor.Ink3)
                        }
                        if (expanded == i) {
                            Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                                Text(answer, fontSize = 12.sp, color = DColor.Ink2)
                            }
                        }
                    }
                    if (i != faqs.lastIndex) DDivider()
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 本组私有 helper：统一 C_ 前缀，避免与其它分组文件重名。
 * ------------------------------------------------------------------ */

private class C_Btn(
    val text: String,
    val style: DButtonStyle,
    val onClick: () -> Unit,
)

/** 固定一侧 + 自适应另一侧（宽度由 BoxWithConstraints 计算，不按比例分配）。 */
@Composable
private fun C_SplitRow(
    fixedWidth: Dp,
    gap: Dp = 12.dp,
    fixedAtStart: Boolean = true,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    fixed: @Composable () -> Unit,
    flexible: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val flexW = maxWidth - fixedWidth - gap
        Row(Modifier.fillMaxWidth(), verticalAlignment = verticalAlignment) {
            if (fixedAtStart) {
                Box(Modifier.width(fixedWidth)) { fixed() }
                Spacer(Modifier.width(gap))
                Column(Modifier.width(flexW)) { flexible() }
            } else {
                Column(Modifier.width(flexW)) { flexible() }
                Spacer(Modifier.width(gap))
                Box(Modifier.width(fixedWidth)) { fixed() }
            }
        }
    }
}

/** 等宽按钮行。 */
@Composable
private fun C_EqualButtons(items: List<C_Btn>, height: Dp = 44.dp) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - 10.dp * (items.size - 1).toFloat()) / items.size.toFloat()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { b ->
                DButton(b.text, b.onClick, Modifier.width(w), b.style, height)
            }
        }
    }
}

@Composable
private fun C_StarsText(filled: Int) {
    Row {
        Text("\u2605".repeat(filled.coerceIn(0, 5)), fontSize = 12.sp, color = DColor.Star)
        Text("\u2605".repeat(5 - filled.coerceIn(0, 5)), fontSize = 12.sp, color = DColor.Ink3)
    }
}

@Composable
private fun C_Tags(tags: List<String>, tone: DTagTone = DTagTone.Line) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { DTag(it, tone) }
    }
}

@Composable
private fun C_Links(links: List<Pair<String, Color>>, onLink: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        links.forEach { (text, color) ->
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.clickable { onLink(text) },
            )
        }
    }
}

@Composable
private fun C_ReviewCard(
    avatar: String,
    avatarBg: Color,
    avatarFg: Color,
    name: String,
    stars: Int,
    date: String,
    body: String,
    tags: List<String>,
    reply: String? = null,
    onClick: () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    DCard(modifier = Modifier.padding(bottom = 12.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(avatarBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(avatar, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = avatarFg)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Spacer(Modifier.height(2.dp))
                    C_StarsText(stars)
                }
            }
            Text(date, fontSize = 11.sp, color = DColor.Ink3)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 13.sp, color = DColor.Ink)
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            C_Tags(tags)
        }
        if (reply != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.Bg).padding(10.dp),
            ) {
                Text(reply, fontSize = 12.5.sp, color = DColor.Ink2)
            }
        }
        footer()
    }
}

/**
 * 优惠券卡片：左侧色块放面额，右侧放标题、适用范围与有效期。
 * 领券中心与券包共用一套版式，[grayed] 用于已失效/不可领的弱化展示。
 */
@Composable
private fun C_CouponCard(
    amount: String,
    threshold: String,
    title: String,
    sub: String,
    expiry: String,
    grayed: Boolean,
    onClick: () -> Unit,
) {
    DCard(
        // 左右边距交给外层 DPad：两列时卡片自己再带一份 gutter，列间距会翻倍
        modifier = Modifier.padding(vertical = 6.dp).clickable(onClick = onClick),
        padding = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            Column(
                Modifier.width(110.dp).height(96.dp)
                    .background(if (grayed) DColor.Ink3 else DColor.Brand),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(amount, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = DColor.White)
                Text(threshold, fontSize = 10.sp, color = DColor.White.copy(alpha = 0.9f))
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Spacer(Modifier.height(4.dp))
                Text(sub, fontSize = 11.5.sp, color = DColor.Ink3)
                if (expiry.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(expiry, fontSize = 11.5.sp, color = DColor.Danger)
                }
            }
        }
    }
}

/** 动态下的一条留言：头像 + 昵称 + 时间 + 正文。 */
@Composable
private fun C_CommentRow(comment: DynamicCommentDto) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DAvatar(dInitial(comment.user), size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                comment.user.ifBlank { "用户" },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = DColor.Ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                comment.createdAt.replace("T", " ").take(16),
                fontSize = 11.sp,
                color = DColor.Ink3,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(comment.content, fontSize = 13.sp, color = DColor.Ink2)
    }
}
