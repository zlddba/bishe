package team.wd.atnestapp.ui.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.WalletDto
import team.wd.atnestapp.data.api.WalletTransactionDto
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.app.MobileImage
import team.wd.atnestapp.ui.app.rememberPhotoPicker

/* ================================================================== *
 * 第 4 组：㉪~㉹ 屏。版式按 docs/KMP-Android端UI设计稿.html 还原，
 * 数据已接真实接口：统一从 DLocalStore.current（MobileState）读取。
 * 本组私有 helper 统一 D_ 前缀，避免与其它分组文件重名。
 * 注意：DFrame 的 content 已包在 verticalScroll 的 Column 里，
 * 因此不在滚动内容里使用 Modifier.weight；等宽布局改用
 * BoxWithConstraints 计算宽度 + Arrangement.SpaceBetween / SpaceAround。
 * ================================================================== */

private val D_HeroBottomShape = DShape.HeroBottom

// 钱包卡是「品牌面」，卡上永远是白字，所以渐变写死品牌色、不跟主题走。
private fun D_solid(color: Color): Brush = Brush.linearGradient(listOf(color, color))

/* ㉪ 机构入驻资料提交 */
@Composable
fun D46SitterOnboarding(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val types = listOf("家庭寄养", "门店寄养", "上门喂养")
    var type by remember { mutableStateOf(types.first()) }
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(store.pickedAddress) }
    var intro by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf("") }
    fun upload(fileName: String, bytes: ByteArray, target: String) {
        if (bytes.isEmpty()) {
            nav.toast("没有读取到图片")
            return
        }
        scope.launch {
            uploading = target
            val url = store.uploadPhoto(fileName, bytes)
            uploading = ""
            if (url != null) {
                if (target == "license") license = url else idCard = url
            }
        }
    }
    val pickLicense = rememberPhotoPicker { fileName, bytes -> upload(fileName, bytes, "license") }
    val pickIdCard = rememberPhotoPicker { fileName, bytes -> upload(fileName, bytes, "idcard") }
    DFrame(nav = nav, title = null) {
        DPad {
            Spacer(Modifier.height(8.dp))
            Text("机构入驻申请", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = DColor.Ink)
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            D_InfoCard(
                title = "提交资料后，平台将在 3 个工作日内完成审核",
                lines = listOf("审核通过后即可发布寄养服务并接单"),
                background = D_solid(DColor.BrandSoft),
            )
        }
        DPad {
            Spacer(Modifier.height(16.dp))
            DFieldInput("机构名称", name, { name = it }, placeholder = "例如：萌宠之家")
            D_SelectChipRow("机构类型", types, type) { type = it }
            DFieldInput("联系人", contact, { contact = it }, placeholder = "负责人姓名")
            DFieldInput("机构地址", address, { address = it }, placeholder = "省市区 + 街道门牌")
            DUploadRow(
                label = "营业执照 / 证件",
                url = license,
                uploading = uploading == "license",
                onPick = pickLicense,
            )
            DUploadRow(
                label = "身份证照片（选填）",
                url = idCard,
                uploading = uploading == "idcard",
                onPick = pickIdCard,
            )
            DFieldInput(
                label = "服务介绍",
                value = intro,
                onValueChange = { intro = it },
                placeholder = "自家小院，24 小时陪护…",
                multiline = true,
            )
        }
        DPad {
            Spacer(Modifier.height(6.dp))
            DButton(
                if (store.busy) "提交中…" else "提交审核",
                {
                    scope.launch {
                        store.applySitter(
                            name = name,
                            typeLabel = type,
                            contactPerson = contact,
                            address = address,
                            licenseUrl = license,
                            idCardUrl = idCard,
                            description = intro,
                        )
                    }
                },
                enabled = !store.busy,
            )
        }
    }
}

/** 单选胶囊行，用于机构类型这类枚举选择。 */
@Composable
private fun D_SelectChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Box(
                    Modifier.clip(DShape.Pill)
                        .background(if (option == selected) DColor.BrandSoft else DColor.NeutralSoft)
                        .border(1.dp, if (option == selected) DColor.Brand else DColor.Line, DShape.Pill)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        option,
                        fontSize = 12.sp,
                        color = if (option == selected) DColor.BrandInk else DColor.Ink2,
                    )
                }
            }
        }
    }
}

/** 证件上传行：已上传显示缩略图，否则显示上传入口。 */
@Composable
private fun DUploadRow(
    label: String,
    url: String,
    uploading: Boolean,
    onPick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(DShape.Md)
                .background(DColor.Bg)
                .border(1.dp, DColor.Line, DShape.Md)
                .clickable(enabled = !uploading) { onPick() },
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotBlank()) {
                MobileImage(
                    url = url,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    shape = DShape.Md,
                    placeholderSize = 22.dp,
                )
            } else {
                Text(
                    if (uploading) "上传中…" else "+ 上传照片",
                    fontSize = 12.5.sp,
                    color = DColor.Ink3,
                )
            }
        }
    }
}

/* ㉫ 用户协议 / 隐私政策 */
@Composable
fun D47Agreements(nav: DNav) {
    DFrame(nav = nav, title = "用户协议") {
        DPad {
            Spacer(Modifier.height(16.dp))
            D_AgreementSection(
                "一、服务说明",
                listOf("宠栖平台为宠物主人与寄养机构提供信息撮合服务，不直接提供寄养服务。寄养过程中发生的纠纷由双方协商解决。"),
            )
            D_AgreementSection(
                "二、订单规则",
                listOf(
                    "1. 下单后 30 分钟内未支付，订单自动取消；",
                    "2. 已支付订单在入住前 24 小时可免费取消；",
                    "3. 寄养期间改期/续费按差价结算。",
                ),
            )
            D_AgreementSection(
                "三、隐私保护",
                listOf("平台仅在订单履约必要范围内使用您的手机号与地址，不会向第三方披露。"),
            )
        }
    }
}

/* ㉬ 取消订单（原因 + 退款说明） */
@Composable
fun D48OrderCancel(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val order = store.selectedOrder
    val reasons = listOf("行程变更", "宠物身体不适", "找到更合适的机构", "其他原因")
    var reason by remember { mutableStateOf(reasons.first()) }
    var other by remember { mutableStateOf("") }

    if (order == null) {
        DFrame(nav = nav, title = "取消订单") {
            DEmpty("还没有选择订单", "查看订单") { nav.go(DScreen.Orders) }
        }
        return
    }
    DFrame(nav = nav, title = "取消订单") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                DKeyValue("机构", order.org)
                DKeyValue("订单号", order.no)
                DKeyValue("寄养日期", order.range)
            }
        }
        DSection("取消原因")
        DPad {
            DCard {
                reasons.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { reason = option }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option, fontSize = 13.sp, color = DColor.Ink, modifier = Modifier.weight(1f))
                        if (reason == option) Text("✓", fontSize = 14.sp, color = DColor.Brand)
                    }
                }
                if (reason == "其他原因") {
                    DFieldInput("补充说明", other, { other = it }, placeholder = "请简单说明原因")
                }
            }
        }
        DPad {
            Spacer(Modifier.height(10.dp))
            Text(
                "取消后已占用容量会立即释放；已支付订单按平台售后规则处理。",
                fontSize = 11.sp,
                color = DColor.Ink3,
            )
            Spacer(Modifier.height(14.dp))
            DButton(
                if (store.busy) "提交中…" else "确认取消订单",
                {
                    scope.launch {
                        store.cancelOrder(
                            order,
                            if (reason == "其他原因" && other.isNotBlank()) other.trim() else reason,
                        )
                        nav.go(DScreen.Orders)
                    }
                },
                style = DButtonStyle.Outline,
            )
        }
    }
}

@Composable
fun D49ReviewAppend(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val review = store.selectedReview
    val reviewId = review?.id ?: store.lastReviewId
    var content by remember { mutableStateOf("") }
    DFrame(
        nav = nav,
        title = "追评",
        actions = { DTag("24h 内", DTagTone.Brand) },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Text("原评价", fontSize = 12.5.sp, color = DColor.Ink3)
                Spacer(Modifier.height(8.dp))
                Text(
                    review?.ownerComment?.takeIf { it.isNotBlank() } ?: "（原评价没有文字内容）",
                    fontSize = 13.5.sp,
                    color = DColor.Ink,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(8.dp))
                DStars((review?.overallScore ?: 0).toDouble(), size = 14.sp)
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            if (reviewId <= 0L) {
                DNotice("未找到可追评的评价，请先从订单详情进入评价详情页。")
                Spacer(Modifier.height(10.dp))
                DButton("去我的订单", { nav.go(DScreen.Orders) }, style = DButtonStyle.Outline)
            } else {
                DFieldInput(
                    label = "追评内容",
                    value = content,
                    onValueChange = { content = it },
                    placeholder = "住后一周的感受、笼舍环境、沟通体验…",
                    multiline = true,
                )
                Text(
                    "追评提交后不可修改，仅支持文字。",
                    fontSize = 11.5.sp,
                    color = DColor.Ink3,
                )
                Spacer(Modifier.height(10.dp))
                DButton(
                    if (store.busy) "提交中…" else "提交追评",
                    {
                        scope.launch {
                            if (store.appendReview(reviewId, content)) {
                                content = ""
                                nav.back()
                            }
                        }
                    },
                    enabled = !store.busy,
                )
            }
        }
    }
}

/* ㉮ 邀请有礼（邀请码 + 积分） */
@Composable
fun D50Invite(nav: DNav) {
    val store = DLocalStore.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { store.loadProfile() }
    var copied by remember { mutableStateOf(false) }
    val userId = store.profile?.id ?: 0L
    val code = if (userId > 0L) "PET" + userId.toString().padStart(6, '0') else "PETNEST"
    val shareText = "我在宠栖 PetNest 给毛孩子找寄养，用我的邀请码 $code 注册，我们各得 100 积分。"
    DFrame(nav = nav, title = null) {
        D_HeroBlock(roundedBottom = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "邀请好友，各得 100 积分",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DColor.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "好友注册并完成首单，您和好友各得 100 积分",
                    fontSize = 12.5.sp,
                    color = DColor.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(DShape.Md)
                    .background(DColor.White.copy(alpha = 0.18f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("我的邀请码", fontSize = 11.sp, color = DColor.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        code,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.White,
                        letterSpacing = 4.sp,
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    D_Stat(
                        (store.profile?.points ?: store.pointsBalance).toString(),
                        "我的积分",
                        DColor.BrandInk,
                    )
                    D_Stat(store.money(store.pointsBalance / 100.0), "可抵扣")
                    D_Stat("100", "每邀请一位")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "邀请关系由平台在好友注册时按邀请码绑定，邀请记录暂未开放查询接口。",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
            )
        }
        DPad {
            Spacer(Modifier.height(18.dp))
            D_Equal2 { i, mod ->
                if (i == 0) {
                    DButton(
                        if (copied) "已复制" else "复制邀请码",
                        {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            manager?.setPrimaryClip(ClipData.newPlainText("邀请码", code))
                            copied = true
                            nav.toast("邀请码已复制")
                        },
                        mod,
                        DButtonStyle.Ghost,
                    )
                } else {
                    DButton(
                        "分享给好友",
                        {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(send, "分享邀请码"))
                        },
                        mod,
                    )
                }
            }
        }
    }
}

/* ㉯ 机构数据看板（趋势 + 近期订单） */
@Composable
fun D51SitterDashboard(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) {
        store.loadSitterStats()
        store.loadEarnings()
        store.loadReceivedOrders()
        if (store.myShop == null) store.loadMyShop()
    }
    val stats = store.sitterStats
    val trend = stats?.trend.orEmpty()
    val revenueValues = trend.map { it.revenue }
    DFrame(nav = nav, title = null) {
        D_HeroBlock(roundedBottom = false) {
            Text(
                store.myShop?.name?.takeIf { it.isNotBlank() } ?: "经营看板",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = DColor.White,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "待确认 ${stats?.pending ?: 0}",
                    fontSize = 12.sp,
                    color = DColor.White.copy(alpha = 0.9f),
                )
                Text(
                    "在住 ${stats?.active ?: 0}",
                    fontSize = 12.sp,
                    color = DColor.White.copy(alpha = 0.9f),
                )
                Text(
                    "今日 ${store.money(stats?.todayRevenue ?: 0.0)}",
                    fontSize = 12.sp,
                    color = DColor.White.copy(alpha = 0.9f),
                )
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    D_Stat(
                        store.money(store.earnings?.summary?.netAmount ?: 0.0),
                        "近 30 天净收入",
                        DColor.BrandInk,
                    )
                    D_Stat((store.earnings?.summary?.orderCount ?: 0).toString(), "结算订单")
                    D_Stat((stats?.trend?.sumOf { it.orders } ?: 0).toString(), "趋势订单")
                }
            }
        }
        DSection("每日收入趋势")
        DPad {
            DCard {
                D_TrendChart(revenueValues)
                Spacer(Modifier.height(6.dp))
                if (trend.size >= 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(trend.first(), trend[trend.size / 2], trend.last()).forEach { point ->
                            Text(
                                point.date.takeLast(5),
                                fontSize = 10.5.sp,
                                color = DColor.Ink3,
                            )
                        }
                    }
                }
            }
        }
        DSection("近期订单")
        DPad {
            DCard {
                val recent = store.receivedOrders.take(4)
                if (recent.isEmpty()) {
                    Text("还没有订单", fontSize = 12.sp, color = DColor.Ink3)
                } else {
                    recent.forEachIndexed { index, order ->
                        if (index > 0) DDivider()
                        D_ServiceRow(
                            "${order.ownerName.ifBlank { "主人" }} · ${order.petCount} 只",
                            "${store.money(order.totalAmount)} · ${store.sitterStatusText(order.status)}",
                        )
                    }
                }
            }
        }
    }
}

/* ㉰ 搜索历史 + 热门搜索 */
@Composable
fun D52SearchHistory(nav: DNav) {
    val store = DLocalStore.current
    var keyword by remember { mutableStateOf(store.sitterQuery) }
    // 后端没有搜索历史接口，历史只在本次会话内保留
    var history by remember {
        mutableStateOf(listOf("猫", "家庭寄养", "上门喂养", "萌宠之家", "有监控", "赛罕区"))
    }
    val hot = listOf("猫咪寄养", "上门喂养", "国庆寄养", "狗寄养", "24 小时监控")
    fun submit(word: String) {
        val trimmed = word.trim()
        if (trimmed.isBlank()) {
            nav.toast("请输入搜索关键词")
            return
        }
        store.sitterQuery = trimmed
        history = (listOf(trimmed) + history).distinct().take(12)
        nav.go(DScreen.SearchResults)
    }
    DFrame(nav = nav, title = null) {
        DPad {
            Spacer(Modifier.height(8.dp))
            DFieldInput(
                label = "搜索机构 / 服务",
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = "例如：家庭寄养、猫咪、赛罕区",
                onSubmit = { submit(keyword) },
            )
            DButton("搜索", { submit(keyword) }, height = 42.dp)
        }
        DPad {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("历史搜索", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Text(
                    "清空",
                    fontSize = 12.sp,
                    color = DColor.Ink3,
                    modifier = Modifier.clickable {
                        history = emptyList()
                        nav.toast("已清空历史搜索")
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) {
                Text("暂无历史搜索", fontSize = 12.5.sp, color = DColor.Ink3)
            } else {
                history.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { word ->
                            D_SearchChip(word) { submit(word) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("热门搜索", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            Spacer(Modifier.height(10.dp))
            hot.forEachIndexed { i, text ->
                D_HotRow(i + 1, text) { submit(text) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/* ㉱ 空状态合集（订单 / 宠物 / 收藏） */
@Composable
fun D53EmptyStates(nav: DNav) {
    DFrame(nav = nav, title = null) {
        DPad {
            Spacer(Modifier.height(8.dp))
            Text("空状态", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = DColor.Ink)
        }
        DPad {
            Spacer(Modifier.height(20.dp))
            D_EmptyCard(
                icon = AppIcon.OrdersPlain,
                title = "暂无订单",
                sub = "还没有寄养订单，去看看附近的机构吧",
                action = "去看看",
                onAction = { nav.tab(DTab.Sitters) },
            )
            D_EmptyCard(
                icon = AppIcon.Paw,
                title = "还没有宠物",
                sub = "添加毛孩子档案，预约时一键选择",
                action = "添加宠物",
                onAction = { nav.go(DScreen.PetAdd) },
            )
            D_EmptyCard(
                icon = AppIcon.Heart,
                title = "暂无收藏",
                sub = "看到喜欢的机构，点收藏方便下次找",
            )
        }
    }
}

/* ㉲ 积分明细（获取 / 消耗） */
@Composable
fun D54PointsLedger(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) { store.loadPoints() }
    DFrame(nav = nav, title = "积分明细") {
        DPad {
            Spacer(Modifier.height(14.dp))
            if (store.pointsHistory.isEmpty()) {
                DEmpty("暂无积分流水")
            } else {
                DCard {
                    store.pointsHistory.forEachIndexed { index, entry ->
                        if (index > 0) DDivider()
                        DKeyValue(
                            entry.reason.ifBlank { "积分变动" },
                            (if (entry.change > 0) "+" else "") + entry.change,
                            if (entry.change > 0) DColor.Ok else DColor.Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun D55CouponDetail(nav: DNav) {
    val store = DLocalStore.current
    val coupon = store.selectedCoupon
    if (coupon == null) {
        DFrame(nav = nav, title = "优惠券详情") {
            DEmpty("券信息不存在", "返回") { nav.back() }
        }
        return
    }
    DFrame(nav = nav, title = "优惠券详情") {
        DPad {
            Spacer(Modifier.height(18.dp))
            DCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (coupon.type == "percent") "${coupon.value.toInt()}折" else "¥${coupon.value.toInt()}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.BrandInk,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(coupon.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (coupon.minAmount > 0) "满 ${coupon.minAmount.toInt()} 可用" else "无门槛",
                        fontSize = 12.sp,
                        color = DColor.Ink3,
                    )
                }
            }
        }
        DSection("使用说明")
        DPad {
            DCard {
                DKeyValue("券码", coupon.code)
                DKeyValue("适用范围", if (coupon.sitterId > 0) "仅限 ${coupon.issuer.ifBlank { "发券机构" }}" else "全平台通用")
                DKeyValue("有效期", coupon.endsAt.ifBlank { "长期有效" })
                DKeyValue("状态", if (coupon.status == "unused") "未使用" else coupon.status)
            }
        }
        DPad {
            Spacer(Modifier.height(16.dp))
            DButton(
                if (coupon.status == "unused") "用于当前订单" else "该券已不可用",
                {
                    if (coupon.status == "unused") {
                        store.selectBookingCoupon(coupon)
                        nav.back()
                    }
                },
                enabled = coupon.status == "unused",
            )
        }
    }
}

@Composable
fun D56OrderReschedule(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val order = store.selectedOrder
    var startDate by remember(order?.id) { mutableStateOf(order?.startDate.orEmpty()) }
    var endDate by remember(order?.id) { mutableStateOf(order?.endDate.orEmpty()) }
    if (order == null) {
        DFrame(nav = nav, title = "订单改期") {
            DEmpty("还没有选择订单", "查看订单") { nav.go(DScreen.Orders) }
        }
        return
    }
    DFrame(nav = nav, title = "订单改期") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                DKeyValue("机构", order.org)
                DKeyValue("原日期", "${order.startDate} - ${order.endDate}")
                DKeyValue("订单号", order.no)
            }
        }
        DSection("新日期")
        DPad {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DDateStep("开始日期", startDate, Modifier.weight(1f),
                    onPrev = {
                        runCatching { java.time.LocalDate.parse(startDate).plusDays(-1) }
                            .getOrNull()?.toString()?.let { startDate = it }
                    },
                    onNext = {
                        runCatching { java.time.LocalDate.parse(startDate).plusDays(1) }
                            .getOrNull()?.toString()?.let {
                                startDate = it
                                if (endDate <= it) {
                                    endDate = runCatching { java.time.LocalDate.parse(it).plusDays(1) }
                                        .getOrNull()?.toString().orEmpty()
                                }
                            }
                    },
                )
                DDateStep("结束日期", endDate, Modifier.weight(1f),
                    onPrev = {
                        runCatching { java.time.LocalDate.parse(endDate).plusDays(-1) }
                            .getOrNull()?.toString()?.let { if (it > startDate) endDate = it }
                    },
                    onNext = {
                        runCatching { java.time.LocalDate.parse(endDate).plusDays(1) }
                            .getOrNull()?.toString()?.let { endDate = it }
                    },
                )
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DButton(
                if (store.busy) "提交中…" else "确认改期",
                {
                    if (endDate <= startDate) {
                        nav.toast("结束日期要晚于开始日期")
                    } else {
                        scope.launch {
                            store.rescheduleOrder(order, startDate, endDate)
                            nav.back()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DDateStep(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = DColor.Ink3)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.Card)
                .border(1.dp, DColor.Line, DShape.Md)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) { Text("−", fontSize = 16.sp, color = DColor.Ink2) }
            Text(
                value.takeLast(5).replace("-", "/"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DColor.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) { Text("+", fontSize = 16.sp, color = DColor.Ink2) }
        }
    }
}

@Composable
fun D57OrderExtend(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val order = store.selectedOrder
    var endDate by remember(order?.id) { mutableStateOf(order?.endDate.orEmpty()) }
    if (order == null) {
        DFrame(nav = nav, title = "订单续费") {
            DEmpty("还没有选择订单", "查看订单") { nav.go(DScreen.Orders) }
        }
        return
    }
    DFrame(nav = nav, title = "订单续费") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                DKeyValue("机构", order.org)
                DKeyValue("当前结束日期", order.endDate)
                DKeyValue("当前金额", order.fee)
            }
        }
        DSection("延长到")
        DPad {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(DColor.Card)
                        .border(1.dp, DColor.Line, CircleShape)
                        .clickable {
                            runCatching { java.time.LocalDate.parse(endDate).plusDays(-1) }
                                .getOrNull()?.let { if (it.toString() > order.startDate) endDate = it.toString() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("−", fontSize = 18.sp, color = DColor.Ink2)
                }
                Text(
                    endDate,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DColor.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(DColor.Card)
                        .border(1.dp, DColor.Line, CircleShape)
                        .clickable {
                            runCatching { java.time.LocalDate.parse(endDate).plusDays(1) }
                                .getOrNull()?.let { endDate = it.toString() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", fontSize = 18.sp, color = DColor.Ink2)
                }
            }
        }
        DPad {
            Spacer(Modifier.height(10.dp))
            Text(
                "续住按新增服务段立即收取差价，费用以服务端结算为准。",
                fontSize = 11.sp,
                color = DColor.Ink3,
            )
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DButton(
                if (store.busy) "提交中…" else "确认续费",
                {
                    scope.launch {
                        store.renewOrder(order, endDate)
                        nav.back()
                    }
                },
            )
        }
    }
}

@Composable
fun D58SitterGallery(nav: DNav) {
    val store = DLocalStore.current
    val sitter = store.selectedSitter
    val photos = buildList {
        sitter?.coverPhoto?.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(sitter?.photos.orEmpty().filter { it.isNotBlank() })
    }.distinct()
    DFrame(nav = nav, title = "${sitter?.name ?: "机构"} · 相册") {
        DPad {
            Spacer(Modifier.height(14.dp))
            if (photos.isEmpty()) {
                DEmpty("机构还没有上传照片", "返回详情") { nav.back() }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = (maxWidth - 16.dp) / 3f
                    Column {
                        photos.chunked(3).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { url ->
                                    Box(
                                        Modifier.width(cell).height(cell).clip(DShape.Md)
                                            .background(DColor.ImageGradient),
                                    ) {
                                        MobileImage(
                                            url = url,
                                            modifier = Modifier.width(cell).height(cell),
                                            shape = DShape.Md,
                                            placeholderSize = 20.dp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Text(
                    "共 ${photos.size} 张照片",
                    fontSize = 11.5.sp,
                    color = DColor.Ink3,
                )
            }
        }
    }
}

/* ㉷ 评价详情（机构回复） */
@Composable
fun D59ReviewDetail(nav: DNav) {
    val store = DLocalStore.current
    val review = store.selectedReview
        ?: store.detailReviews.firstOrNull()
    if (review == null) {
        DFrame(nav = nav, title = "评价详情") {
            DEmpty("还没有评价内容", "返回") { nav.back() }
        }
        return
    }
    DFrame(nav = nav, title = "评价详情") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DAvatar(dInitial(review.ownerName), size = 38.dp, rounded = false)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            review.ownerName.ifBlank { "匿名住客" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ink,
                        )
                        DStars(review.overallScore.toDouble())
                    }
                    Text(
                        review.createdAt.replace("T", " ").take(10),
                        fontSize = 11.sp,
                        color = DColor.Ink3,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    review.ownerComment.ifBlank { "该住客没有填写评价内容" },
                    fontSize = 13.sp,
                    color = DColor.Ink2,
                )
                if (review.replyText.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(DShape.Md)
                            .background(DColor.BrandSoft).padding(10.dp),
                    ) {
                        Text("机构回复", fontSize = 11.sp, color = DColor.BrandInk)
                        Spacer(Modifier.height(4.dp))
                        Text(review.replyText, fontSize = 12.5.sp, color = DColor.Ink2)
                    }
                }
            }
        }
        DSection("分项评分")
        DPad {
            DCard {
                DKeyValue("服务", "${review.serviceScore} 分")
                DKeyValue("环境", "${review.environmentScore} 分")
                DKeyValue("卫生", "${review.hygieneScore} 分")
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DButton(
                "写追评",
                { nav.go(DScreen.ReviewAppend) },
                style = DButtonStyle.Outline,
                height = 42.dp,
            )
        }
    }
}

@Composable
fun D60Wallet(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var wallet by remember { mutableStateOf<WalletDto?>(null) }
    var transactions by remember { mutableStateOf(listOf<WalletTransactionDto>()) }
    var showRecharge by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(100) }
    var custom by remember { mutableStateOf("") }
    var recharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        wallet = runCatching { store.api.walletOverview() }.getOrNull()
        transactions = runCatching { store.api.walletTransactions(1).items }.getOrDefault(emptyList())
    }

    // 手输优先于快捷档位；输入了非法内容就退回档位，别让「0 元充值单」打到后端
    val amount = custom.toDoubleOrNull()?.takeIf { it > 0 } ?: preset.toDouble()

    val submitRecharge: () -> Unit = {
        if (recharging) {
            Unit
        } else if (amount < 1.0) {
            nav.toast("充值金额至少 1 元")
        } else {
            scope.launch {
                recharging = true
                try {
                    val created = store.api.createRecharge(amount, "mock")
                    // 演示环境走 mock 网关：创建后立刻确认到账，不做轮询
                    val confirmed = store.api.confirmRecharge(created.rechargeId)
                    wallet = runCatching { store.api.walletOverview() }.getOrNull() ?: wallet
                    transactions = runCatching { store.api.walletTransactions(1).items }
                        .getOrDefault(transactions)
                    showRecharge = false
                    custom = ""
                    nav.toast("充值成功，余额 ${store.money(confirmed.balance)}")
                } catch (e: Exception) {
                    nav.toast(e.message ?: "充值失败")
                } finally {
                    recharging = false
                }
            }
        }
    }

    DFrame(nav = nav, title = "钱包余额") {
        DPad {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().clip(DShape.Lg).background(DWalletGradientFor(nav))
                    .padding(18.dp),
            ) {
                Text("可用余额", fontSize = 12.sp, color = DColor.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                Text(
                    store.money(wallet?.balance ?: 0.0),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DColor.White,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "累计充值 ${store.money(wallet?.totalRecharge ?: 0.0)}",
                        fontSize = 11.5.sp,
                        color = DColor.White.copy(alpha = 0.85f),
                    )
                    Text(
                        "累计消费 ${store.money(wallet?.totalConsume ?: 0.0)}",
                        fontSize = 11.5.sp,
                        color = DColor.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DButton(
                    if (showRecharge) "收起充值" else "充值",
                    { showRecharge = !showRecharge },
                    Modifier.weight(1f),
                    if (showRecharge) DButtonStyle.Ghost else DButtonStyle.Primary,
                    42.dp,
                )
                DButton(
                    "积分抵扣",
                    { nav.go(DScreen.Points) },
                    Modifier.weight(1f),
                    DButtonStyle.Ghost,
                    42.dp,
                )
            }
        }
        if (showRecharge) {
            DPad {
                Spacer(Modifier.height(10.dp))
                DCard {
                    Text("充值金额", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(50, 100, 200, 500).forEach { value ->
                            DChip(
                                text = "¥$value",
                                selected = custom.isBlank() && preset == value,
                                onClick = {
                                    preset = value
                                    custom = ""
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DFieldInput(
                        label = "自定义金额",
                        value = custom,
                        onValueChange = { input ->
                            // 只留数字和一个小数点，避免把「￥100 元」这类输入直接送后端
                            if (input.count { it == '.' } <= 1 && input.all { it.isDigit() || it == '.' }) {
                                custom = input
                            }
                        },
                        placeholder = "不填则用上面的档位",
                        numeric = true,
                    )
                    DNotice("演示环境未接入真实支付网关，确认后模拟到账；真实资金请走线下渠道。")
                    Spacer(Modifier.height(10.dp))
                    DButton(
                        if (recharging) "处理中…" else "确认充值 ${store.money(amount)}",
                        submitRecharge,
                        Modifier.fillMaxWidth(),
                        DButtonStyle.Primary,
                        44.dp,
                        !recharging,
                    )
                }
            }
        }
        DSection("钱包流水")
        DPad {
            DCard {
                if (transactions.isEmpty()) {
                    Text("暂无钱包流水", fontSize = 12.sp, color = DColor.Ink3)
                } else {
                    transactions.take(12).forEachIndexed { index, txn ->
                        if (index > 0) DDivider()
                        val positive = txn.type == "recharge" || txn.type == "refund"
                        DKeyValue(
                            txn.remark.ifBlank {
                                when (txn.type) {
                                    "recharge" -> "充值"
                                    "consume" -> "消费"
                                    "refund" -> "退款"
                                    "withdraw" -> "提现"
                                    else -> txn.type
                                }
                            },
                            (if (positive) "+" else "-") + store.money(txn.amount),
                            if (positive) DColor.Ok else DColor.Ink,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** 钱包卡渐变：品牌面永远是白字，深色主题下也不跟着翻转。 */
private fun DWalletGradientFor(nav: DNav) = Brush.linearGradient(
    listOf(Color(0xFF8E3B4E), Color(0xFFE0708A)),
)

@Composable
fun D61SettingsDetail(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var dynamicNotify by remember { mutableStateOf(true) }
    var orderNotify by remember { mutableStateOf(true) }
    var passwordOpen by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    DFrame(nav = nav, title = "账号与安全") {
        DPad {
            Spacer(Modifier.height(16.dp))
            DCard(padding = 0.dp) {
                D_SwitchRow("寄养动态通知", dynamicNotify) { dynamicNotify = !dynamicNotify }
                DDivider()
                D_SwitchRow("订单状态通知", orderNotify) { orderNotify = !orderNotify }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "通知开关仅保存在本机，用于前台提示；服务端推送策略暂未开放配置。",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
            )
            Spacer(Modifier.height(12.dp))
            DCard(padding = 0.dp) {
                DMenuRow(
                    "修改密码",
                    if (passwordOpen) "收起" else "",
                    onClick = { passwordOpen = !passwordOpen },
                )
                DDivider()
                DMenuRow("隐私政策", onClick = { nav.go(DScreen.Agreements) })
                DDivider()
                DMenuRow("用户协议", onClick = { nav.go(DScreen.Agreements) })
                DDivider()
                DInfoRow("关于宠栖", "v1.0.0")
            }
            if (passwordOpen) {
                Spacer(Modifier.height(12.dp))
                DCard {
                    DFieldInput(
                        "原密码", oldPassword, { oldPassword = it },
                        secret = true, placeholder = "当前登录密码",
                    )
                    DFieldInput(
                        "新密码", newPassword, { newPassword = it },
                        secret = true, placeholder = "至少 6 位",
                    )
                    DButton(
                        if (store.busy) "提交中…" else "保存新密码",
                        {
                            when {
                                oldPassword.isBlank() -> nav.toast("请填写原密码")
                                newPassword.length < 6 -> nav.toast("新密码至少 6 位")
                                else -> scope.launch {
                                    store.changePassword(oldPassword, newPassword)
                                    oldPassword = ""
                                    newPassword = ""
                                    passwordOpen = false
                                }
                            }
                        },
                        enabled = !store.busy,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            D_DangerOutlineButton("退出登录") {
                store.logout()
                nav.tab(DTab.Home)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "注销账号",
                    fontSize = 12.5.sp,
                    color = DColor.Ink3,
                    modifier = Modifier.clickable {
                        scope.launch {
                            store.requestAccountCancel("delay")
                            nav.tab(DTab.Home)
                        }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "注销后进入冷静期，期间重新登录可撤销；账号数据将在冷静期结束后清理。",
                fontSize = 11.sp,
                color = DColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 本组私有 helper：统一 D_ 前缀，避免与其它分组文件重名。
 * ------------------------------------------------------------------ */

/** 顶部粉色渐变 Hero，roundedBottom 控制是否带设计稿里的 26dp 底部圆角。 */
@Composable
private fun D_HeroBlock(
    roundedBottom: Boolean,
    background: Brush = DColor.HeroGradient,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = if (roundedBottom) {
        Modifier.fillMaxWidth().clip(D_HeroBottomShape)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        base.background(background).padding(horizontal = 18.dp, vertical = 22.dp),
        content = content,
    )
}

/** 两列等宽，宽度由 BoxWithConstraints 计算，不使用 weight。 */
@Composable
private fun D_Equal2(
    spacing: Dp = 10.dp,
    content: @Composable (Int, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - spacing) / 2f
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            content(0, Modifier.width(w))
            content(1, Modifier.width(w))
        }
    }
}

/** 加粗标题 + 若干浅色说明行的软底信息卡（审核说明 / 退款说明 / 寄养中）。 */
@Composable
private fun D_InfoCard(title: String, lines: List<String>, background: Brush) {
    Column(Modifier.fillMaxWidth().clip(DShape.Lg).background(background).padding(14.dp)) {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
        lines.forEach { line ->
            Spacer(Modifier.height(6.dp))
            Text(line, fontSize = 12.sp, color = DColor.Ink3, lineHeight = 18.sp)
        }
    }
}

/** 协议条款段落。 */
@Composable
private fun D_AgreementSection(title: String, lines: List<String>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
        Spacer(Modifier.height(6.dp))
        lines.forEach { line ->
            Text(line, fontSize = 13.sp, color = DColor.Ink2, lineHeight = 22.sp)
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** 危险操作描边按钮（退出登录）。 */
@Composable
private fun D_DangerOutlineButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(DShape.Md)
            .background(DColor.Card).border(1.dp, DColor.Danger, DShape.Md)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DColor.Danger)
    }
}

/** 每日收入趋势折线：按传入的实际数值归一化绘制。 */
@Composable
private fun D_TrendChart(values: List<Double>) {
    // Canvas 的绘制 lambda 不是 @Composable，颜色要在外面读好再带进去。
    val lineColor = DColor.Brand
    if (values.size < 2) {
        Box(
            Modifier.fillMaxWidth().height(90.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (values.isEmpty()) "暂无趋势数据" else "数据点太少，暂不绘制趋势",
                fontSize = 12.sp,
                color = DColor.Ink3,
            )
        }
        return
    }
    val peak = values.max().takeIf { it > 0.0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val stepX = size.width / (values.size - 1).toFloat()
        val usable = size.height * 0.84f
        val offsetY = size.height * 0.08f
        val pts = values.mapIndexed { index, value ->
            Offset(
                index * stepX,
                offsetY + usable * (1f - (value / peak).toFloat()),
            )
        }
        for (i in 0 until pts.lastIndex) {
            drawLine(
                color = lineColor,
                start = pts[i],
                end = pts[i + 1],
                strokeWidth = 2.5.dp.toPx(),
            )
        }
        pts.forEach { point ->
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = point,
            )
        }
    }
}

/** 数值行：名称深色、数值浅色。 */
@Composable
private fun D_ServiceRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = DColor.Ink)
        Text(value, fontSize = 12.5.sp, color = DColor.Ink3)
    }
}

/** 历史搜索胶囊标签。 */
@Composable
private fun D_SearchChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(DShape.Pill).background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = DColor.Ink2)
    }
}

/** 热门搜索带序号行（前三名高亮）。 */
@Composable
private fun D_HotRow(rank: Int, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = when (rank) {
                1 -> DColor.Danger
                2 -> DColor.Warn
                3 -> DColor.BrandInk
                else -> DColor.Ink3
            },
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.5.sp, color = DColor.Ink)
    }
}

/** 空状态卡片：图标 + 标题 + 说明 + 可选小按钮。 */
@Composable
private fun D_EmptyCard(
    icon: AppIcon,
    title: String,
    sub: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    DCard(modifier = Modifier.padding(bottom = 14.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(icon, Modifier.size(56.dp), DColor.Line2)
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 12.5.sp, color = DColor.Ink3, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                DSmallButton(action, { onAction?.invoke() }, filled = true)
            }
        }
    }
}

/** 设置开关行。 */
@Composable
private fun D_SwitchRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.5.sp, color = DColor.Ink)
        D_Switch(on, onToggle)
    }
}

@Composable
private fun D_Switch(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(40.dp).height(22.dp).clip(DShape.Pill)
            .background(if (on) DColor.Brand else DColor.Line2)
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(DColor.White))
    }
}

/** 大号统计（三列）。 */
@Composable
private fun D_Stat(value: String, label: String, valueColor: Color = DColor.Ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = DColor.Ink3)
    }
}
