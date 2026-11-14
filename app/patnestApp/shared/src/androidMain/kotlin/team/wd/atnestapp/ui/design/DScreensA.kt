package team.wd.atnestapp.ui.design

import team.wd.atnestapp.utils.formatScore
import team.wd.atnestapp.utils.formatMoney
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.ui.app.MapNavigation
import team.wd.atnestapp.ui.components.AppIcon
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.text.input.ImeAction
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar

/* ================================================================== *
 * 第 1 组：①~⑮ 屏。版式按 docs/KMP-Android端UI设计稿.html 还原，
 * 数据已接真实接口：统一从 DLocalStore.current（MobileState）读取。
 * ================================================================== */

@Composable
fun D01Home(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.loadSitters()
        store.loadPets()
        store.loadNotifications()
    }
    DMiuixScrollScaffold(
        nav = nav,
        title = null,
        activeTab = DTab.Home,
        backable = false,
        // 首页头部是整块粉色渐变，直接顶到状态栏下才是设计稿的观感。
        immersiveTop = true,
    ) {
        DPaleStatusBar()
        DHero(
            "${store.nickname.ifBlank { "你好" }}，欢迎回来",
            "今天给宠物安排好寄养了吗？",
            insetTop = true,
            trailing = {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(DColor.White.copy(alpha = 0.25f))
                        .clickable { nav.go(DScreen.Messages) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Bell, Modifier.size(18.dp), DColor.White)
                    if (store.unreadCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                                .background(DColor.Danger),
                        )
                    }
                }
            },
        )
        DSearchBar("搜索机构 / 宠物 / 服务", onClick = { nav.go(DScreen.SearchResults) })
        DPad {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                A_QuickEntry(AppIcon.Home, "家庭寄养", Modifier.weight(1f)) { nav.tab(DTab.Sitters) }
                A_QuickEntry(AppIcon.Shop, "门店寄养", Modifier.weight(1f)) { nav.tab(DTab.Sitters) }
                A_QuickEntry(AppIcon.Paw, "上门喂养", Modifier.weight(1f)) { nav.tab(DTab.Sitters) }
                A_QuickEntry(AppIcon.Calendar, "节假日", Modifier.weight(1f)) { nav.tab(DTab.Sitters) }
            }
        }
        DSection("我的宠物", more = "管理 ›", onMore = { nav.go(DScreen.Pets) })
        DPad {
            when {
                store.petsLoading && store.pets.isEmpty() -> DLoading("正在加载宠物档案…")
                store.pets.isEmpty() -> DCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "还没有宠物档案，先添加一只吧",
                            fontSize = 12.5.sp,
                            color = DColor.Ink3,
                            modifier = Modifier.weight(1f),
                        )
                        DSmallButton("添加", { nav.go(DScreen.PetAdd) })
                    }
                }
                else -> {
                    store.pets.take(2).forEach { pet ->
                        DCard(padding = 12.dp, modifier = Modifier.padding(bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DAvatar(dInitial(pet.name), size = 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            pet.name,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DColor.Ink,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        DTag(
                                            pet.cert,
                                            if (pet.cert == "已核验") DTagTone.Ok else DTagTone.Warn,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(pet.dPetInfo(), fontSize = 11.5.sp, color = DColor.Ink3)
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.clickable { nav.tab(DTab.Sitters) }) {
                                    DTag("去预约", DTagTone.Line)
                                }
                            }
                        }
                    }
                }
            }
        }
        DSection("推荐寄养机构", more = "更多 ›", onMore = { nav.tab(DTab.Sitters) })
        DPad {
            when {
                store.sittersLoading && store.sitters.isEmpty() -> DLoading("正在加载机构…")
                store.sitters.isEmpty() && store.sittersError != null ->
                    DErrorBox(store.sittersError ?: "机构加载失败") {
                        scope.launch { store.loadSitters(force = true) }
                    }
                store.sitters.isEmpty() -> DEmpty("附近还没有入驻机构", "刷新") {
                    scope.launch { store.loadSitters(force = true) }
                }
                else -> store.sitters.take(3).forEach { sitter ->
                    A_SitterCard(sitter.toACard()) { nav.openSitter(sitter) }
                }
            }
        }
    }
}

@Composable
fun D02FindSitters(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var chip by remember { mutableStateOf(0) }
    val chips = listOf("全部", "家庭寄养", "门店", "上门喂养", "评分优先", "有地图坐标")
    // 列数在组合上下文里先算好：DLazyFrame 的 content 是普通 DSL lambda，
    // 里面读不了 CompositionLocal。
    val columns = dWindow().columns(320.dp)
    LaunchedEffect(Unit) { store.loadSitters() }
    // 筛选 / 排序结果按（数据, 选中项）缓存：否则每次重组都会重新过滤一遍列表，
    // 列表滚动、输入框联动时这些重复计算会直接反映成掉帧。
    val shown = remember(store.sitters, chip) {
        when (chip) {
            1 -> store.sitters.filter { it.type == "家庭寄养" }
            2 -> store.sitters.filter { it.type == "门店寄养" }
            3 -> store.sitters.filter { it.type == "上门喂养" }
            4 -> store.sitters.sortedByDescending { it.score }
            5 -> store.sitters.sortedByDescending { it.latitude != null }
            else -> store.sitters
        }
    }
    // 机构一次最多返回 50 家，用惰性骨架：首屏只组合可见的几张卡片。
    DMiuixListScaffold(
        nav = nav,
        title = null,
        activeTab = DTab.Sitters,
        backable = false,
        refreshing = store.sittersLoading,
        onRefresh = { scope.launch { store.loadSitters(force = true) } },
    ) {
        item { DSearchBar("搜索机构", onClick = { nav.go(DScreen.SearchResults) }) }
        item { A_ChipRow(chips, chip) { chip = it } }
        item {
            DPad {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DSmallButton("对比前 3 家", { nav.go(DScreen.SitterCompare) })
                }
            }
        }
        when {
            store.sittersLoading && store.sitters.isEmpty() ->
                item { DPad { DLoading("正在加载机构…") } }

            store.sitters.isEmpty() && store.sittersError != null ->
                item {
                    DPad {
                        DErrorBox(store.sittersError ?: "机构加载失败") {
                            scope.launch { store.loadSitters(force = true) }
                        }
                    }
                }

            shown.isEmpty() ->
                item { DPad { DEmpty("没有符合条件的机构", "查看全部") { chip = 0 } } }

            // 宽屏按行分组：仍走 LazyColumn，只是每行放 2~3 张卡，
            // 首屏组合量不变；chunked 之后 key 取每行第一张卡的 id，仍能稳定复用。
            else -> items(shown.chunked(columns), key = { row -> row.first().id }) { row ->
                DPad {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { sitter ->
                            Box(Modifier.weight(1f)) {
                                A_SitterCard(sitter.toACard()) { nav.openSitter(sitter) }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun D03SitterDetail(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sitter = store.selectedSitter
    LaunchedEffect(sitter?.id) { sitter?.let { store.loadSitterExtras(it.id) } }

    if (sitter == null) {
        DMiuixScrollScaffold(nav = nav, title = "机构详情") {
            DEmpty("还没有选择机构", "去找机构") { nav.tab(DTab.Sitters) }
        }
        return
    }
    val fav = store.isFavorite(sitter.id)
    DMiuixScrollScaffold(
        nav = nav,
        title = null,
        backable = false,
        bottomBar = {
            DFixBar(
                "¥${formatMoney(sitter.price)}",
                "¥${formatMoney(sitter.price)}/${sitter.unit}",
                "立即预约",
            ) { nav.startBooking(sitter) }
        },
    ) {
        Box(Modifier.fillMaxWidth().height(180.dp)) {
            DImageUrl(sitter.coverPhoto, sitter.name, height = 180.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = LocalDWindow.current.gutter, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(DColor.Scrim)
                        .clickable { nav.back() },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Back, Modifier.size(18.dp), DColor.White)
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(DColor.Scrim)
                        .clickable { scope.launch { store.toggleFavorite(sitter) } },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Heart, Modifier.size(18.dp), if (fav) DColor.Brand else DColor.White)
                }
            }
        }
        DPad {
            Spacer(Modifier.height(12.dp))
            DCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        sitter.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    DTag(
                        if (sitter.status == "active") "已认证" else "暂停接单",
                        if (sitter.status == "active") DTagTone.Ok else DTagTone.Warn,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DStars(sitter.score)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${formatScore(sitter.score)} · ${sitter.dRatingText()}",
                        fontSize = 11.5.sp,
                        color = DColor.Ink3,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(sitter.addr, fontSize = 11.5.sp, color = DColor.Ink3)
                Spacer(Modifier.height(8.dp))
                A_Tags(
                    listOfNotNull(
                        sitter.type to DTagTone.Brand,
                        "容量 ${sitter.cap}" to DTagTone.Ok,
                        if (sitter.price > 0) "¥${formatMoney(sitter.price)}/${sitter.unit}" to DTagTone.Line else null,
                    ),
                )
            }
        }
        // 位置：只展示，不内置导航。路径规划和实时路况交给手机里装的地图 App，
        // 应用自己画一条线只会更难用。
        sitter.latitude?.let { lat ->
            sitter.longitude?.let { lng ->
                DSection("位置")
                DPad {
                    // 170dp 只够看个大概：点一下进全屏地图页（能拖、能缩放）
                    Box(Modifier.fillMaxWidth().height(170.dp).clip(DShape.Lg)) {
                        DMapView(
                            latitude = lat,
                            longitude = lng,
                            modifier = Modifier.fillMaxSize(),
                            zoom = 15,
                        )
                        // 透明点击层：高德 SDK 版地图是原生 View，触摸会被它自己消费掉，
                        // 挂在外层 Modifier 上的 clickable 根本收不到事件（瓦片版是 Compose Canvas，
                        // 不消费事件，所以只有配了 Key 的机器上"点不动"）。
                        // 这一页的地图只展示（interactive = false），盖一层不影响任何操作。
                        Box(
                            Modifier.fillMaxSize().clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { nav.go(DScreen.SitterMap) },
                        )
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                .clip(DShape.Pill).background(DColor.Scrim)
                                .clickable { nav.go(DScreen.SitterMap) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text("查看大图", fontSize = 10.5.sp, color = DColor.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    DCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sitter.addr,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DColor.Ink,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "点地图可全屏查看",
                                    fontSize = 10.5.sp,
                                    color = DColor.Ink3,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            DSmallButton("导航", {
                                if (!MapNavigation.navigate(context, lat, lng, sitter.name)) {
                                    nav.toast("这台设备上没有可用的地图应用")
                                }
                            }, filled = true)
                        }
                    }
                }
            }
        }
        DSection("寄养信息")
        DPad {
            DCard {
                DKeyValue("寄养类型", sitter.type)
                DKeyValue("可同时容量", sitter.cap)
                DKeyValue("价格", "¥${formatMoney(sitter.price)}/${sitter.unit}")
                DKeyValue("简介", sitter.desc)
            }
        }
        val gallery = (listOf(sitter.coverPhoto) + sitter.photos).filter { it.isNotBlank() }
        if (gallery.isNotEmpty()) {
            DSection("机构相册", more = "全部 ›", onMore = { nav.go(DScreen.SitterGallery) })
            DPad {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gallery.take(3).forEach { url ->
                        DCoverImage(
                            url = url,
                            fallback = sitter.name,
                            width = 96.dp,
                            height = 72.dp,
                        )
                    }
                }
            }
        }
        if (store.detailAddons.isNotEmpty()) {
            DSection("附加服务")
            DPad {
                DCard {
                    store.detailAddons.forEach { addon ->
                        DKeyValue(addon.name, "¥${addon.price.toInt()}/${addon.unit}")
                    }
                }
            }
        }
        DSection("近 7 日容量", more = "余位")
        DPad {
            DCard {
                if (store.detailCapacity.isEmpty()) {
                    Text("暂无容量数据", fontSize = 11.5.sp, color = DColor.Ink3)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        store.detailCapacity.take(7).forEach { cap ->
                            val tone = when {
                                cap.full -> DTagTone.Danger
                                cap.available <= 1 -> DTagTone.Warn
                                else -> DTagTone.Ok
                            }
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().height(44.dp).clip(DShape.Sm)
                                        .background(A_capBg(tone)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (cap.full) "满" else "${cap.available}/${cap.capacity}",
                                        fontSize = 10.5.sp,
                                        color = A_capFg(tone),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    cap.date.takeLast(5).replace("-", "/"),
                                    fontSize = 10.5.sp,
                                    color = DColor.Ink3,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (store.detailReviews.isNotEmpty()) {
            DSection("住客评价", more = "全部 ›") {
                store.selectedReview = store.detailReviews.first()
                nav.go(DScreen.ReviewDetail)
            }
            DPad {
                store.detailReviews.take(2).forEach { review ->
                    DCard(
                        modifier = Modifier.padding(bottom = 10.dp).clickable {
                            store.selectedReview = review
                            nav.go(DScreen.ReviewDetail)
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DAvatar(dInitial(review.ownerName), size = 30.dp, rounded = false)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    review.ownerName.ifBlank { "匿名住客" },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DColor.Ink,
                                )
                                DStars(review.overallScore.toDouble(), size = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            review.ownerComment.ifBlank { "该住客没有填写评价内容" },
                            fontSize = 12.5.sp,
                            color = DColor.Ink2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun D04Booking(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val sitter = store.selectedSitter
    LaunchedEffect(sitter?.id) {
        if (sitter != null) {
            store.loadPets()
            store.loadCoupons()
            store.loadPoints()
            store.loadSitterAddons()
            store.refreshEstimate()
        }
    }
    if (sitter == null) {
        DMiuixScrollScaffold(nav = nav, title = "预约下单") {
            DEmpty("还没有选择机构", "去找机构") { nav.tab(DTab.Sitters) }
        }
        return
    }
    val days = store.bookingDays
    val total = store.estimate?.totalAmount

    DMiuixScrollScaffold(nav = nav, title = "预约下单") {
        DStepHeader(1, "选择寄养日期")
        DPad {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                A_DateStepper(
                    label = "开始日期",
                    value = store.bookingStart,
                    modifier = Modifier.weight(1f),
                    onPrev = { store.shiftStart(-1) },
                    onNext = { store.shiftStart(1) },
                )
                A_DateStepper(
                    label = "结束日期",
                    value = store.bookingEnd,
                    modifier = Modifier.weight(1f),
                    onPrev = { store.shiftEnd(-1) },
                    onNext = { store.shiftEnd(1) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (days > 0) "共 $days 晚（含首不含尾）· 节假日已自动加价" else "结束日期需要晚于开始日期",
                fontSize = 11.5.sp,
                color = if (days > 0) DColor.Ink3 else DColor.Danger,
            )
        }
        DStepHeader(2, "选择宠物")
        DPad {
            if (store.petsLoading && store.pets.isEmpty()) {
                DLoading("正在加载宠物档案…")
            } else if (store.pets.isEmpty()) {
                DCard(padding = 12.dp) {
                    Text("还没有宠物档案，先添加一只", fontSize = 12.5.sp, color = DColor.Ink3)
                }
            } else {
                store.pets.forEach { pet ->
                    A_PetOption(
                        avatar = dInitial(pet.name),
                        name = pet.name,
                        sub = pet.dPetInfo(),
                        badge = pet.cert,
                        selected = store.bookingPetIds.contains(pet.id),
                    ) { store.toggleBookingPet(pet.id) }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            DButton("+ 添加新宠物", { nav.go(DScreen.PetAdd) }, style = DButtonStyle.Outline, height = 40.dp)
        }
        if (store.sitterAddons.isNotEmpty()) {
            DStepHeader(3, "加购服务")
            DPad {
                store.sitterAddons.filter { it.enabled }.forEach { addon ->
                    val qty = store.bookingAddonQty[addon.id] ?: 0
                    DCard(modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    addon.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DColor.Ink,
                                )
                                Text(
                                    "¥${formatMoney(addon.price)} / ${dAddonUnit(addon.unit)}",
                                    fontSize = 11.5.sp,
                                    color = DColor.Ink3,
                                )
                            }
                            DSmallButton("−", { store.setBookingAddonQty(addon.id, qty - 1) })
                            Text(
                                "$qty",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            DSmallButton("+", { store.setBookingAddonQty(addon.id, qty + 1) }, filled = qty > 0)
                        }
                    }
                }
            }
        }
        DStepHeader(if (store.sitterAddons.isEmpty()) 3 else 4, "费用确认")
        DPad {
            DCard {
                val estimate = store.estimate
                if (estimate == null) {
                    Text(
                        if (store.estimating) "正在计算费用…" else "选好日期和宠物后自动计算费用",
                        fontSize = 12.sp,
                        color = DColor.Ink3,
                    )
                } else {
                    estimate.breakdown.forEach { line ->
                        DFeeLine(line.label, store.money(line.amount))
                    }
                    DDivider()
                    DFeeLine("合计", store.money(estimate.totalAmount), total = true)
                }
            }
        }
        DPad {
            Spacer(Modifier.height(10.dp))
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("积分抵扣", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DColor.Ink)
                        Text(
                            "可用 ${store.pointsBalance} 分 · 100 分抵 1 元",
                            fontSize = 11.5.sp,
                            color = DColor.Ink3,
                        )
                    }
                    if (store.bookingPoints > 0) {
                        Text(
                            "−" + store.money(store.bookingPoints / 100.0),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ok,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DSmallButton("−100", { store.selectBookingPoints(store.bookingPoints - 100) })
                    Text(
                        "${store.bookingPoints} 分",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DColor.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    DSmallButton("+100", { store.selectBookingPoints(store.bookingPoints + 100) })
                    DSmallButton(
                        "全部",
                        { store.selectBookingPoints(store.maxUsablePoints()) },
                        filled = true,
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(12.dp))
            DCard(padding = 0.dp) {
                DMenuRow(
                    text = store.bookingCoupon?.let { "优惠券：${it.title}" } ?: "选择优惠券（可选）",
                    onClick = { nav.go(DScreen.Coupons) },
                )
            }
        }
        DPad {
            Spacer(Modifier.height(12.dp))
            DButton(
                if (total != null) "提交预约 · 实付 ${store.money(total)}" else "提交预约",
                {
                    scope.launch {
                        if (store.createOrder()) {
                            // 优先按刚创建订单的 id 精确匹配；拿不到再退回待支付列表
                            store.selectedOrder = store.orders.firstOrNull { it.id == store.lastCreatedOrderId }
                                ?: store.orders.firstOrNull { it.rawStatus == "pending_pay" }
                                ?: store.orders.firstOrNull()
                            nav.go(DScreen.Checkout)
                        }
                    }
                },
                // 下单请求在飞行中时禁用，防止连点创建多笔订单
                enabled = !store.busy,
            )
        }
    }
}

@Composable
fun D05Orders(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val labels = listOf("全部", "待支付", "寄养中", "已完成", "已评价")
    // 订单卡右侧有金额和按钮，比机构卡更吃宽度，最小列宽取 360dp
    val columns = dWindow().columns(360.dp)
    LaunchedEffect(Unit) { store.loadOrders() }
    val statuses = remember(tab) {
        when (tab) {
            1 -> setOf("pending_pay")
            2 -> setOf("accepted", "staying")
            3 -> setOf("completed")
            4 -> setOf("evaluated")
            else -> emptySet()
        }
    }
    val shown = remember(store.orders, statuses) {
        if (statuses.isEmpty()) store.orders else store.orders.filter { it.rawStatus in statuses }
    }
    // 订单也可能一次返回几十条，同样用惰性骨架。
    DMiuixListScaffold(nav = nav, title = "我的订单", backable = false, refreshing = store.ordersLoading, onRefresh = { scope.launch { store.loadOrders(force = true) } }) {
        item { DTabLine(labels, tab) { tab = it } }
        item { Spacer(Modifier.height(10.dp)) }
        when {
            store.ordersLoading && store.orders.isEmpty() ->
                item { DPad { DLoading("正在加载订单…") } }

            store.orders.isEmpty() && store.ordersError != null ->
                item {
                    DPad {
                        DErrorBox(store.ordersError ?: "订单加载失败") {
                            scope.launch { store.loadOrders(force = true) }
                        }
                    }
                }

            shown.isEmpty() ->
                item {
                    DPad {
                        DEmpty("暂无${labels[tab]}订单", "去逛逛") { nav.tab(DTab.Sitters) }
                    }
                }

            else -> items(shown.chunked(columns), key = { row -> row.first().id }) { row ->
                DPad {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { order ->
                            val card = order.toAOrder()
                            Box(Modifier.weight(1f)) {
                                A_OrderCard(
                                    info = card,
                                    onCard = { nav.openOrder(order) },
                                    onAction = {
                                        store.selectedOrder = order
                                        nav.go(card.actionScreen)
                                    },
                                )
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (shown.isNotEmpty()) {
            item {
                DPad {
                    Spacer(Modifier.height(4.dp))
                    if (store.ordersHasMore) {
                        DButton(
                            if (store.ordersLoadingMore) "正在加载…" else "加载更多订单",
                            { scope.launch { store.loadMoreOrders() } },
                            style = DButtonStyle.Outline,
                            height = 40.dp,
                            enabled = !store.ordersLoadingMore,
                        )
                    } else {
                        Text(
                            "没有更多订单了",
                            fontSize = 11.5.sp,
                            color = DColor.Ink3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun D06Dynamics(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadOwnerDynamics() }
    DFrame(
        nav = nav,
        title = "寄养动态",
        activeTab = DTab.Dynamics,
        backable = false,
        refreshing = store.dynamicsLoading,
        onRefresh = { scope.launch { store.loadOwnerDynamics(force = true) } },
    ) {
        when {
            store.dynamicsLoading && store.ownerDynamics.isEmpty() -> DPad { DLoading("正在加载动态…") }
            store.ownerDynamics.isEmpty() -> DPad {
                Spacer(Modifier.height(24.dp))
                DEmpty("还没有寄养动态", "去预约") { nav.tab(DTab.Sitters) }
            }
            // 动态卡带大图，列宽给足：380dp 以下正文会缩成三四个字一行
            else -> DPad {
                DGrid(store.ownerDynamics, minItemWidth = 380.dp) { dynamic ->
                    val liked = store.likedDynamics.contains(dynamic.id)
                    val openDetail = {
                        store.selectedDynamic = dynamic
                        nav.go(DScreen.DynamicDetail)
                    }
                    A_DynamicCard(
                        avatar = dInitial(dynamic.sitterName),
                        org = dynamic.sitterName.ifBlank { "机构动态" },
                        time = dynamic.createdAt.replace("T", " ").take(16),
                        title = dynamic.orderNo.ifBlank { "订单动态" },
                        body = dynamic.content,
                        // 之前这里写死 DImageBlock("")，机构上传的照片在列表里永远看不到
                        photoUrl = dynamic.photos.firstOrNull().orEmpty(),
                        liked = liked,
                        onLike = { scope.launch { store.toggleLike(dynamic.id) } },
                        onComment = openDetail,
                        onClick = openDetail,
                    )
                }
            }
        }
    }
}

@Composable
fun D07Profile(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) {
        store.loadProfile()
        store.loadOrders()
        store.loadPets()
    }
    val staying = store.orders.count { it.rawStatus == "accepted" || it.rawStatus == "staying" }
    DFrame(
        nav = nav,
        title = null,
        activeTab = DTab.Profile,
        backable = false,
    ) {
        Column(
            Modifier.fillMaxWidth().background(DColor.HeroGradient)
                .padding(horizontal = LocalDWindow.current.gutter, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(DColor.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dInitial(store.nickname),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.BrandInk,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (store.loggedIn) store.nickname.ifBlank { store.account } else "未登录",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.White,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (store.loggedIn) dMaskPhone(store.profile?.phone) else "登录后查看订单与宠物档案",
                        fontSize = 12.sp,
                        color = DColor.White.copy(alpha = 0.9f),
                    )
                }
                Box(
                    Modifier.clip(DShape.Md).background(DColor.White.copy(alpha = 0.2f))
                        .clickable { nav.go(DScreen.Settings) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text("设置", fontSize = 12.sp, color = DColor.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                A_Stat(staying.toString(), "寄养中", Modifier.weight(1f))
                A_Stat(store.orders.size.toString(), "累计订单", Modifier.weight(1f))
                A_Stat(store.pets.size.toString(), "我的宠物", Modifier.weight(1f))
            }
        }
        // 历史/后台导入账号可能没有手机号：后端允许登录但会回 phone_bound=false，
        // 不给入口的话用户根本不知道这事，等到要联系客服时才发现号码是空的。
        if (store.loggedIn && store.needsPhoneBinding) {
            DPad {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(DShape.Md)
                        .background(DColor.WarnSoft)
                        .border(1.dp, DColor.Warn, DShape.Md)
                        .clickable { nav.go(DScreen.ProfileEdit) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "请绑定手机号",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "接单联系、退款与售后都靠它，绑完才能正常收通知",
                                fontSize = 11.5.sp,
                                color = DColor.Ink2,
                            )
                        }
                        Text("去绑定 ›", fontSize = 12.sp, color = DColor.BrandInk)
                    }
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("我的订单", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Text(
                        "全部 ›",
                        fontSize = 11.5.sp,
                        color = DColor.Ink3,
                        modifier = Modifier.clickable { nav.go(DScreen.Orders) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    A_EntryIcon(AppIcon.Card, "待支付", Modifier.weight(1f)) { nav.go(DScreen.Orders) }
                    A_EntryIcon(AppIcon.Home, "寄养中", Modifier.weight(1f)) { nav.go(DScreen.Orders) }
                    A_EntryIcon(AppIcon.CheckCircle, "已完成", Modifier.weight(1f)) { nav.go(DScreen.Orders) }
                    A_EntryIcon(AppIcon.Star, "待评价", Modifier.weight(1f)) { nav.go(DScreen.Orders) }
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                val entries = listOf(
                    Triple(AppIcon.OrdersPlain, "我的订单", DScreen.Orders),
                    Triple(AppIcon.Paw, "我的宠物", DScreen.Pets),
                    Triple(AppIcon.Heart, "我的收藏", DScreen.Favorites),
                    Triple(AppIcon.Ticket, "优惠券", DScreen.Coupons),
                    Triple(AppIcon.Bell, "消息", DScreen.Messages),
                    Triple(AppIcon.Shop, "地址", DScreen.Addresses),
                    Triple(AppIcon.User, "设置", DScreen.Settings),
                    Triple(AppIcon.CheckCircle, "帮助", DScreen.Support),
                )
                entries.chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth()) {
                        rowItems.forEach { (icon, label, screen) ->
                            A_GridEntry(icon, label, Modifier.weight(1f)) { nav.go(screen) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        DPad {
            Spacer(Modifier.height(4.dp))
            DCard(padding = 0.dp) {
                DMenuRow("钱包余额", onClick = { nav.go(DScreen.Wallet) })
                DDivider()
                DMenuRow("积分中心", onClick = { nav.go(DScreen.Points) })
                DDivider()
                DMenuRow("售后工单", onClick = { nav.go(DScreen.Tickets) })
                DDivider()
                DMenuRow("平台公告", onClick = { nav.go(DScreen.Announcements) })
                DDivider()
                DMenuRow("邀请有礼", "各得 100 积分", onClick = { nav.go(DScreen.Invite) })
                DDivider()
                DMenuRow(
                    "切换身份",
                    // 显示「当前正在用」的身份，不是账号注册的角色
                    store.identityLabel,
                    onClick = { nav.go(DScreen.RoleSwitch) },
                )
            }
        }
    }
}

@Composable
fun D08Pets(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadPets() }
    DMiuixScrollScaffold(
        nav = nav,
        title = "我的宠物",
        actions = { DSmallButton("+ 添加", { nav.go(DScreen.PetAdd) }) },
        refreshing = store.petsLoading,
        onRefresh = { scope.launch { store.loadPets(force = true) } },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            when {
                store.petsLoading && store.pets.isEmpty() -> DLoading("正在加载宠物档案…")
                store.pets.isEmpty() -> {
                    DEmpty("还没有宠物档案", "添加宠物") { nav.go(DScreen.PetAdd) }
                }
                else -> {
                    DGrid(store.pets, minItemWidth = 340.dp) { pet ->
                        A_PetCard(
                            avatar = dInitial(pet.name),
                            name = pet.name,
                            tone = when (pet.cert) {
                                "已核验" -> DTagTone.Ok
                                "待核验" -> DTagTone.Warn
                                "未通过" -> DTagTone.Danger
                                else -> DTagTone.Line
                            },
                            badge = pet.cert,
                            info1 = pet.dPetInfo(),
                            info2 = pet.dPetNote(),
                            actions = listOf("预约常用" to DTagTone.Line, "编辑" to DTagTone.Line),
                            onAction = { label ->
                                when (label) {
                                    "预约常用" -> nav.tab(DTab.Sitters)
                                    else -> nav.openPet(pet)
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (store.petsHasMore) {
                        DButton(
                            if (store.petsLoadingMore) "正在加载…" else "加载更多宠物",
                            { scope.launch { store.loadMorePets() } },
                            style = DButtonStyle.Outline,
                            height = 40.dp,
                            enabled = !store.petsLoadingMore,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    DButton(
                        "+ 继续添加宠物",
                        { nav.go(DScreen.PetAdd) },
                        style = DButtonStyle.Outline,
                        height = 42.dp,
                    )
                }
            }
        }
    }
}

@Composable
fun D09Login(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(store.account) }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val gutter = LocalDWindow.current.gutter

    val accountState = rememberTextFieldState(store.account)
    val passwordState = rememberTextFieldState()
    LaunchedEffect(Unit) {
        snapshotFlow { accountState.text.toString() }.collect { account = it }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { passwordState.text.toString() }.collect { password = it }
    }

    val doLogin: () -> Unit = {
        when {
            account.isBlank() -> nav.toast("请填写账号")
            password.isBlank() -> nav.toast("请填写密码")
            store.busy -> Unit
            else -> scope.launch {
                store.login(account.trim(), password)
                if (store.loggedIn) nav.afterLogin()
            }
        }
    }

    // 玻璃质感（miuix-blur）：两层模型——
    // ① 背景层（页面底色 + 品牌渐变 hero + 装饰圆）记录进 backdrop；
    // ② 前景滚动列里，登录表单卡是 DGlassPanel，真实模糊它身后骑跨的渐变。
    //    Android 13 以下 DGlassPanel 自动退化为实体卡，布局不变。
    val backdrop = rememberDGlassBackdrop()

    Box(Modifier.fillMaxSize()) {
        // ① 被玻璃模糊的背景层（不透明内容才值得记录）
        Box(Modifier.matchParentSize().dGlassBackdropSource(backdrop)) {
            Column(Modifier.fillMaxSize().background(DColor.Bg)) {
                Box(Modifier.fillMaxWidth().height(250.dp).background(DColor.HeroGradient)) {
                    // 装饰圆：给模糊提供明暗变化，玻璃上才看得出「后面有东西在动」的层次
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 24.dp, end = gutter)
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(DColor.White.copy(alpha = 0.10f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = gutter + 30.dp)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(DColor.White.copy(alpha = 0.08f)),
                    )
                }
            }
        }

        // ② 返回钮（压在渐变上，图标用素材白）
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = gutter, top = 9.dp),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(DColor.Scrim)
                    .clickable { nav.back() },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Back, Modifier.size(18.dp), DColor.White)
            }
        }

        // ③ 前景滚动列：渐变上的欢迎文案（白）+ 骑跨渐变下缘的玻璃表单卡
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(42.dp))
            Column(Modifier.padding(horizontal = gutter)) {
                Text("欢迎使用宠栖", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = DColor.White)
                Spacer(Modifier.height(8.dp))
                Text("为宠物找个温暖的家，节假日寄养不再愁。", fontSize = 13.5.sp, color = DColor.White.copy(alpha = 0.88f))
            }
            // 玻璃卡上缘压进渐变 46dp，模糊里同时包含「渐变 + 页面底」，质感最明显
            Spacer(Modifier.height(46.dp))
            DGlassPanel(
                backdrop = backdrop,
                modifier = Modifier.padding(horizontal = gutter).fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                    TextField(
                        state = accountState,
                        label = "账号",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        state = passwordState,
                        label = "密码",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                        outputTransformation = if (!reveal) DPasswordMask else null,
                        trailingIcon = {
                            Text(
                                if (reveal) "隐藏" else "显示",
                                fontSize = 12.sp,
                                color = DColor.BrandInk,
                                modifier = Modifier.clickable { reveal = !reveal }.padding(horizontal = 6.dp),
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onKeyboardAction = KeyboardActionHandler { performDefaultAction ->
                            doLogin()
                            performDefaultAction()
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = doLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !store.busy,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (store.busy) "登录中…" else "登录")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "还没有账号？去注册",
                    fontSize = 12.5.sp,
                    color = DColor.BrandInk,
                    modifier = Modifier.clickable { nav.go(DScreen.Register) },
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "登录即代表同意《用户协议》与《隐私政策》",
                    fontSize = 11.sp,
                    color = DColor.Ink3,
                    modifier = Modifier.clickable { nav.go(DScreen.Agreements) },
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun D10Checkout(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val order = store.selectedOrder
    var pay by remember { mutableStateOf(0) }
    var balance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        balance = runCatching { store.api.walletOverview().balance }.getOrNull()
    }

    if (order == null) {
        DMiuixScrollScaffold(nav = nav, title = "收银台") {
            DEmpty("没有待支付的订单", "查看订单") { nav.go(DScreen.Orders) }
        }
        return
    }

    val method = when (pay) {
        0 -> "wechat"
        1 -> "balance"
        else -> "mock"
    }
    DMiuixScrollScaffold(
        nav = nav,
        title = "收银台",
        bottomBar = {
            DFixBar("实付", order.fee, "确认支付") {
                scope.launch {
                    try {
                        store.api.payOrder(order.id, method)
                        store.loadOrders(force = true)
                        store.selectedOrder = store.orders.firstOrNull { it.id == order.id } ?: order
                        // 支付成功是流程终点：把收银台 / 预约页整条链清掉，
                        // 否则从成功页按返回会回到一个已经付过款、按钮还能再点的收银台
                        nav.resetTo(DScreen.PaySuccess)
                    } catch (e: Exception) {
                        nav.toast(e.message ?: "支付失败")
                    }
                }
            }
        },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Text("订单信息", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Spacer(Modifier.height(10.dp))
                DKeyValue("寄养机构", order.org)
                DKeyValue("宠物数量", order.pet)
                DKeyValue("寄养日期", order.range)
                DKeyValue("订单号", order.no)
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                DFeeLine("订单金额", order.fee)
                DDivider()
                DFeeLine("实付金额", order.fee, total = true)
            }
        }
        DSection("选择支付方式")
        DPad {
            A_PayOption("微信支付", "确认后立即完成支付（暂未接网关）", pay == 0, { pay = 0 }, AppIcon.Paw, DColor.Ok, DColor.White)
            A_PayOption(
                "余额支付",
                balance?.let { "可用余额 ${store.money(it)}" } ?: "余额查询失败",
                pay == 1,
                { pay = 1 },
                AppIcon.Card,
                DColor.BrandSoft,
                DColor.BrandInk,
            )
            A_PayOption("模拟支付", "用于本地联调，不扣真实余额", pay == 2, { pay = 2 }, AppIcon.Bank, DColor.Info, DColor.White)
        }
        DPad {
            Spacer(Modifier.height(8.dp))
            Text(
                "订单创建后 30 分钟内完成支付，超时容量会自动释放。",
                fontSize = 11.sp,
                color = DColor.Ink3,
            )
        }
    }
}

@SuppressLint("AutoboxingStateCreation")
@Composable
fun D11ReviewWrite(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val order = store.selectedOrder
    var overall by remember { mutableIntStateOf(5) }
    var service by remember { mutableStateOf(5) }
    var environment by remember { mutableStateOf(5) }
    var hygiene by remember { mutableStateOf(5) }
    var content by remember { mutableStateOf("") }

    if (order == null) {
        DMiuixScrollScaffold(nav = nav, title = "写评价") {
            DEmpty("没有可评价的订单", "查看订单") { nav.go(DScreen.Orders) }
        }
        return
    }
    val average = (overall + service + environment + hygiene) / 4.0

    DMiuixScrollScaffold(nav = nav, title = "评价 · ${order.org}") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("综合评分", fontSize = 11.5.sp, color = DColor.Ink3)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        String.format("%.1f", average),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.BrandInk,
                    )
                    Spacer(Modifier.height(8.dp))
                    DStars(average, size = 24.sp)
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Text("分项打分", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Spacer(Modifier.height(14.dp))
                A_StarPicker("整体体验", overall) { overall = it }
                Spacer(Modifier.height(10.dp))
                A_StarPicker("服务态度", service) { service = it }
                Spacer(Modifier.height(10.dp))
                A_StarPicker("环境卫生", environment) { environment = it }
                Spacer(Modifier.height(10.dp))
                A_StarPicker("安全可靠", hygiene) { hygiene = it }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DFieldInput(
                label = "评价内容",
                value = content,
                onValueChange = { content = it },
                placeholder = "说说这次寄养的体验（1-512 字）",
                multiline = true,
            )
        }
        DPad {
            Spacer(Modifier.height(8.dp))
            DButton(
                if (store.busy) "提交中…" else "提交评价",
                {
                    if (content.isBlank()) {
                        nav.toast("评价内容不能为空")
                    } else {
                        scope.launch {
                            store.submitReview(order, overall, service, environment, hygiene, content.trim())
                            nav.back()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun D12PetAdd(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val editing = store.editingPet
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var type by remember(editing?.id) {
        mutableStateOf(editing?.type?.takeIf { it.isNotBlank() } ?: "猫")
    }
    var size by remember(editing?.id) {
        mutableStateOf(editing?.size?.takeIf { it.isNotBlank() && it != "—" } ?: "小型")
    }
    var ageMonth by remember(editing?.id) { mutableStateOf(dAgeToMonth(editing?.age)) }
    var vaccinated by remember(editing?.id) { mutableStateOf(editing?.vaccine != "待接种") }
    var note by remember(editing?.id) {
        mutableStateOf(editing?.note?.takeIf { it.isNotBlank() && it != "暂无健康备注" } ?: "")
    }

    DMiuixFormScaffold(nav = nav, title = if (editing == null) "新增宠物" else "编辑宠物") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(DColor.BrandSoft)
                    .clickable { nav.toast("照片请在宠物详情页上传") },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Paw, Modifier.size(34.dp), DColor.BrandInk)
            }
            Spacer(Modifier.height(8.dp))
            Text("照片可在详情页补充", fontSize = 12.5.sp, color = DColor.Ink3)
        }
        Spacer(Modifier.height(16.dp))
        DMiuixField("宠物名字", name, { name = it }, placeholder = "例如：豆包")
        A_SelectField("宠物类型", type, listOf("猫", "狗", "其他")) { type = it }
        A_SelectField("体型", size, listOf("迷你", "小型", "中型", "大型", "超大型")) { size = it }
        DMiuixField("月龄（月）", ageMonth, { ageMonth = it }, placeholder = "例如：18", numeric = true)
        DField("疫苗情况", if (vaccinated) "已接种" else "待接种", onClick = { vaccinated = !vaccinated })
        DMiuixField(
            label = "健康备注",
            value = note,
            onValueChange = { note = it },
            placeholder = "绝育 / 慢性病 / 饮食习惯…",
            multiline = true,
        )
        Spacer(Modifier.height(18.dp))
        DMiuixPrimaryButton(
            if (store.busy) "保存中…" else "保存宠物",
            {
                scope.launch {
                    val ok = store.savePet(
                        name = name,
                        typeLabel = type,
                        sizeLabel = size,
                        ageText = ageMonth,
                        vaccinated = vaccinated,
                        note = note,
                        avatarUrl = "",
                    )
                    if (ok) nav.back()
                }
            },
        )
    }
}

@Composable
fun D13Favorites(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadFavorites() }
    DMiuixScrollScaffold(nav = nav, title = "我的收藏", backable = false, refreshing = store.favoritesLoading,
        onRefresh = { scope.launch { store.loadFavorites(force = true) } }) {
        DPad {
            Spacer(Modifier.height(14.dp))
            when {
                store.favoritesLoading && store.favorites.isEmpty() -> DLoading("正在加载收藏…")
                store.favorites.isEmpty() -> DEmpty("还没有收藏的机构", "去找机构") { nav.tab(DTab.Sitters) }
                else -> DGrid(store.favorites) { sitter ->
                    A_SitterCard(sitter.toACard()) { nav.openSitter(sitter) }
                }
            }
        }
    }
}

@Composable
fun D14Coupons(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { store.loadCoupons() }
    val items = store.coupons?.items.orEmpty()
    val usable = items.filter { it.status == "unused" }
    val used = items.filter { it.status == "used" }
    val expired = items.filter { it.status == "expired" }
    DMiuixScrollScaffold(
        nav = nav,
        title = "优惠券",
        actions = { DSmallButton("领券中心", { nav.go(DScreen.ClaimCenter) }) },
        refreshing = store.couponsLoading,
        onRefresh = { scope.launch { store.loadCoupons(force = true) } },
    ) {
        DTabLine(listOf("可用 (${usable.size})", "已使用", "已过期"), tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        val shown = when (tab) {
            0 -> usable
            1 -> used
            else -> expired
        }
        if (shown.isEmpty()) {
            DPad {
                DEmpty(
                    when (tab) {
                        0 -> "暂无可用优惠券"
                        1 -> "还没有使用过优惠券"
                        else -> "暂无已过期优惠券"
                    },
                    "去领券中心",
                ) { nav.go(DScreen.ClaimCenter) }
            }
        } else {
            DPad {
                DGrid(shown, minItemWidth = 320.dp) { coupon ->
                    A_CouponCard(
                        // 后端 percent 券的 value 是「减免百分比」（order_pricing.go:190：subtotal*value/100），
                        // 写成「X 折」会把减 10% 显示成「10 折」（= 原价），语义正好相反。
                        amount = if (coupon.type == "percent") "减${coupon.value.toInt()}%" else store.money(coupon.value),
                        threshold = if (coupon.minAmount > 0) "满 ${store.money(coupon.minAmount)} 可用" else "无门槛",
                        title = coupon.title,
                        sub = if (coupon.sitterId > 0) "仅限 ${coupon.issuer.ifBlank { "发券机构" }}" else "全平台通用",
                        expiry = if (coupon.endsAt.isNotBlank()) "有效期至 ${coupon.endsAt.take(10)}" else "",
                        grayed = coupon.status != "unused",
                    ) {
                        if (coupon.status == "unused" && store.selectedSitter != null) {
                            store.selectBookingCoupon(coupon)
                            nav.back()
                        } else {
                            nav.go(DScreen.CouponDetail)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun D15Messages(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val labels = listOf("全部", "订单", "动态", "系统")
    LaunchedEffect(Unit) { store.loadScopedNotifications(true) }
    DMiuixScrollScaffold(
        nav = nav,
        title = "消息通知",
        backable = false,
        refreshing = store.notificationsCenter.notificationsLoading,
        onRefresh = { scope.launch { store.loadScopedNotifications(true) } },
        actions = {
            DSmallButton("全部已读", { scope.launch { store.markAllScopedRead() } })
        },
    ) {
        // 机构账号同一个人会收到两类消息：个人侧（自己下的单）与商家侧（接单/评价）。
        // 之前 Android 端没有任何地方能切到 business，商家消息永远渲染不出来。
        if (store.isSitterMode) {
            val business = store.notificationsCenter.notificationScope == "business"
            DTabLine(listOf("个人消息", "商家消息"), if (business) 1 else 0) { idx ->
                store.notificationsCenter.notificationScope = if (idx == 1) "business" else "personal"
            }
            Spacer(Modifier.height(10.dp))
        }
        DTabLine(labels, tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        val all = store.visibleNotifications()
        val shown = if (tab == 0) all else all.filter { it.dCategory() == labels[tab] }
        DPad {
            if (shown.isEmpty()) {
                DEmpty("暂无${labels[tab]}消息")
            } else {
                DGrid(shown, minItemWidth = 340.dp) { notice ->
                    A_MessageCard(notice.toAMessage()) {
                        scope.launch {
                            store.markScopedRead(notice.id)
                            nav.go(notice.dTarget())
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 本组私有数据与 helper：统一 A_ 前缀，避免与其它分组文件重名。
 * ------------------------------------------------------------------ */

private data class A_SitterInfo(
    val name: String,
    val cover: String,
    val rating: Double,
    val ratingText: String,
    val desc: String,
    val tags: List<Pair<String, DTagTone>>,
    val price: String? = null,
    val unit: String? = null,
    val type: String = "",
)

/** 真实机构 → 设计稿卡片模型（封面 URL 之外的字段都在这里对齐）。 */
/** 加购服务计价单位（后端值：per_order / per_day / per_visit） */
internal fun dAddonUnit(unit: String): String = when (unit) {
    "per_day" -> "每天"
    "per_visit" -> "每次"
    else -> "每单"
}

private fun Sitter.toACard(): A_SitterInfo {
    val area = addr.substringBefore("区").takeIf { it.length in 2..8 }?.plus("区")
    return A_SitterInfo(
        name = name,
        cover = name,
        rating = score,
        ratingText = dRatingText(),
        desc = listOfNotNull(type, area, "容量 $cap").joinToString(" · "),
        tags = buildList {
            add(type to DTagTone.Brand)
            if (price > 0) add("¥${formatMoney(price)}/$unit 起" to DTagTone.Ok)
            if (status != "active") add("暂停接单" to DTagTone.Warn)
        },
        price = if (price > 0) "¥${formatMoney(price)}" else null,
        unit = if (price > 0) "/$unit" else null,
        type = type,
    )
}

private data class A_OrderInfo(
    val no: String,
    val status: String,
    val tone: DTagTone,
    val cover: String,
    val name: String,
    val detail: String,
    val amount: String,
    val action: String,
    val actionScreen: DScreen,
    val category: String,
)

private data class A_MessageInfo(
    val icon: AppIcon,
    val bg: Color,
    val tint: Color,
    val title: String,
    val sub: String,
    val time: String,
    val category: String,
    val screen: DScreen,
)

// 主题令牌是 @Composable 取值（深色下要换一套），所以这几个取色 helper 也得是 @Composable。
@Composable
private fun A_capBg(tone: DTagTone): Color = when (tone) {
    DTagTone.Ok -> DColor.OkSoft
    DTagTone.Warn -> DColor.WarnSoft
    DTagTone.Danger -> DColor.DangerSoft
    else -> DColor.NeutralSoft
}

@Composable
private fun A_capFg(tone: DTagTone): Color = when (tone) {
    DTagTone.Ok -> DColor.Ok
    DTagTone.Warn -> DColor.Warn
    DTagTone.Danger -> DColor.Danger
    else -> DColor.Ink2
}

@Composable
private fun A_QuickEntry(icon: AppIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .clip(DShape.Md).background(DColor.Card)
            .border(1.dp, DColor.Line, DShape.Md)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            A_IconBox(icon, DColor.BrandSoft, DColor.BrandInk, 38.dp, 20.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.5.sp, color = DColor.Ink2)
        }
    }
}

@Composable
private fun A_IconBox(
    icon: AppIcon,
    bg: Color,
    tint: Color,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    rounded: Boolean = true,
) {
    Box(
        Modifier.size(boxSize).clip(if (rounded) DShape.Md else CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, Modifier.size(iconSize), tint)
    }
}

@Composable
private fun A_Tags(tags: List<Pair<String, DTagTone>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { (text, tone) ->
            DTag(text, tone)
            Spacer(Modifier.width(5.dp))
        }
    }
}

@Composable
private fun A_SitterCard(info: A_SitterInfo, onClick: () -> Unit) {
    DCard(
        modifier = Modifier.padding(bottom = 12.dp).clickable(onClick = onClick),
        padding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            DCover(info.cover, width = 86.dp, height = 86.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DStars(info.rating)
                    Spacer(Modifier.width(5.dp))
                    Text(info.ratingText, fontSize = 11.5.sp, color = DColor.Ink3)
                }
                Spacer(Modifier.height(3.dp))
                Text(info.desc, fontSize = 11.5.sp, color = DColor.Ink3)
                if (info.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    A_Tags(info.tags)
                }
                if (info.price != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(info.price, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = DColor.BrandInk)
                        if (info.unit != null) Text(info.unit, fontSize = 11.sp, color = DColor.Ink3)
                    }
                }
            }
        }
    }
}

@Composable
private fun A_ChipRow(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = LocalDWindow.current.gutter, vertical = 8.dp),
    ) {
        items.forEachIndexed { i, text ->
            DChip(text, i == selected) { onSelect(i) }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun A_PetOption(
    avatar: String,
    name: String,
    sub: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(DShape.Md)
            .background(if (selected) DColor.BrandSoft else DColor.Card)
            .border(1.5.dp, if (selected) DColor.Brand else DColor.Line, DShape.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .border(2.dp, if (selected) DColor.Brand else DColor.Line2, CircleShape)
                .background(if (selected) DColor.Brand else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        DAvatar(avatar, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            Text(sub, fontSize = 11.sp, color = DColor.Ink3)
        }
        if (badge != null) DTag(badge, DTagTone.Ok)
    }
}

@Composable
private fun A_OrderCard(info: A_OrderInfo, onCard: () -> Unit, onAction: () -> Unit) {
    DCard(
        modifier = Modifier.padding(bottom = 12.dp).clickable(onClick = onCard),
        padding = 13.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(info.no, fontSize = 11.5.sp, color = DColor.Ink3)
            DTag(info.status, info.tone)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DCover(info.cover, width = 56.dp, height = 56.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Text(info.detail, fontSize = 11.5.sp, color = DColor.Ink3)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(info.amount, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = DColor.BrandInk)
                Spacer(Modifier.height(4.dp))
                DSmallButton(info.action, onAction, filled = info.action == "去支付")
            }
        }
    }
}

@Composable
private fun A_DynamicCard(
    avatar: String,
    org: String,
    time: String,
    title: String,
    body: String,
    photoUrl: String,
    liked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onClick: () -> Unit,
) {
    DCard(
        // 左右边距交给外层 DPad：两列时卡片自己再带一份 gutter，列间距会翻倍
        modifier = Modifier.padding(vertical = 6.dp).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAvatar(avatar, size = 28.dp, rounded = false)
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(org, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Text(" · $time", fontSize = 12.sp, color = DColor.Ink3)
                }
                Text(title, fontSize = 11.5.sp, color = DColor.Ink3)
            }
        }
        Spacer(Modifier.height(10.dp))
        DImageUrl(photoUrl, "", height = 130.dp)
        Spacer(Modifier.height(10.dp))
        Text(body, fontSize = 13.sp, color = DColor.Ink)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 列表接口不带点赞数/留言数（只有详情接口带），所以不做数字展示：
            // 之前按 tags.size 编出来的「留言数」跟真实数据毫无关系，纯属误导。
            Row(Modifier.clickable(onClick = onLike), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    AppIcon.Heart,
                    Modifier.size(15.dp),
                    if (liked) DColor.Brand else DColor.Ink3,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (liked) "已赞" else "点赞", fontSize = 12.sp, color = DColor.Ink3)
            }
            Spacer(Modifier.width(16.dp))
            Row(Modifier.clickable(onClick = onComment), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcon.OrdersPlain, Modifier.size(15.dp), DColor.Ink3)
                Spacer(Modifier.width(4.dp))
                Text("留言", fontSize = 12.sp, color = DColor.Ink3)
            }
        }
    }
}

@Composable
private fun A_Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DColor.White)
        Text(label, fontSize = 11.sp, color = DColor.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun A_EntryIcon(icon: AppIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(icon, Modifier.size(24.dp), DColor.BrandInk)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.5.sp, color = DColor.Ink3)
    }
}

@Composable
private fun A_GridEntry(icon: AppIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        A_IconBox(icon, DColor.BrandSoft, DColor.BrandInk, 42.dp, 21.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.5.sp, color = DColor.Ink2)
    }
}

@Composable
private fun A_PetCard(
    avatar: String,
    name: String,
    tone: DTagTone,
    badge: String,
    info1: String,
    info2: String,
    actions: List<Pair<String, DTagTone>>,
    onAction: (String) -> Unit,
) {
    DCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAvatar(avatar, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Spacer(Modifier.width(6.dp))
                    DTag(badge, tone)
                }
                Spacer(Modifier.height(3.dp))
                Text(info1, fontSize = 11.5.sp, color = DColor.Ink3)
                Text(info2, fontSize = 11.5.sp, color = DColor.Ink3)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            actions.forEach { (text, t) ->
                Box(Modifier.clickable { onAction(text) }) { DTag(text, t) }
                Spacer(Modifier.width(5.dp))
            }
        }
    }
}

@Composable
private fun A_PayOption(
    title: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: AppIcon,
    iconBg: Color,
    iconTint: Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(DShape.Md)
            .background(if (selected) DColor.BrandSoft else DColor.Card)
            .border(1.5.dp, if (selected) DColor.Brand else DColor.Line, DShape.Md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .border(2.dp, if (selected) DColor.Brand else DColor.Line2, CircleShape)
                .background(if (selected) DColor.Brand else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        A_IconBox(icon, iconBg, iconTint, 30.dp, 16.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DColor.Ink)
            Text(sub, fontSize = 11.sp, color = DColor.Ink3)
        }
    }
}

@Composable
private fun A_StarPicker(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.5.sp, color = DColor.Ink)
        Row {
            for (i in 1..5) {
                Text(
                    if (i <= value) "★" else "☆",
                    fontSize = 16.sp,
                    color = if (i <= value) DColor.Star else DColor.Ink3,
                    modifier = Modifier.clickable { onValueChange(i) }.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun A_SelectField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    dropdown: Boolean = true,
    onChange: (String) -> Unit,
) {
    Column(modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        DDropdown(
            options = options,
            selectedIndex = options.indexOf(value).coerceAtLeast(0),
            onSelect = { onChange(options[it]) },
            modifier = Modifier.fillMaxWidth(),
        ) { expanded ->
            Row(
                Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.Card)
                    .border(1.dp, DColor.Line, DShape.Md)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, fontSize = 13.sp, color = DColor.Ink, modifier = Modifier.weight(1f))
                if (dropdown) {
                    Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = DColor.Ink3)
                }
            }
        }
    }
}

@Composable
private fun A_CouponCard(
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

@Composable
private fun A_MessageCard(info: A_MessageInfo, onClick: () -> Unit) {
    DCard(
        modifier = Modifier.padding(bottom = 12.dp).clickable(onClick = onClick),
        padding = 13.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            A_IconBox(info.icon, info.bg, info.tint, 38.dp, 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Text(info.sub, fontSize = 11.5.sp, color = DColor.Ink3)
            }
            Spacer(Modifier.width(8.dp))
            Text(info.time, fontSize = 10.5.sp, color = DColor.Ink3)
        }
    }
}


/** 日期加减：直接点两侧按钮，避免在手机上弹日期键盘。 */
@Composable
private fun A_DateStepper(
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
            ) {
                Text("−", fontSize = 16.sp, color = DColor.Ink2)
            }
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
            ) {
                Text("+", fontSize = 16.sp, color = DColor.Ink2)
            }
        }
    }
}

/** 真实订单 → 设计稿订单卡片模型。 */
private fun team.wd.atnestapp.data.model.Order.toAOrder(): A_OrderInfo {
    val (action, screen) = dOrderAction(this)
    return A_OrderInfo(
        no = no,
        status = status,
        tone = dOrderTone(rawStatus),
        cover = org,
        name = org,
        detail = listOf(pet, range, days).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
        amount = fee,
        action = action,
        actionScreen = screen,
        category = status,
    )
}


/** 通知归类：订单 / 动态 / 系统。 */
private fun team.wd.atnestapp.data.api.NotificationDto.dCategory(): String = when {
    type.contains("order") -> "订单"
    type.contains("dynamic") -> "动态"
    type.contains("review") -> "动态"
    else -> "系统"
}

/** 通知点击后落到哪个页面。 */
private fun team.wd.atnestapp.data.api.NotificationDto.dTarget(): DScreen = when {
    type.contains("order") -> DScreen.Orders
    type.contains("dynamic") || type.contains("review") -> DScreen.Dynamics
    else -> DScreen.Announcements
}

/** 真实通知 → 设计稿消息卡片模型。 */
@Composable
private fun team.wd.atnestapp.data.api.NotificationDto.toAMessage(): A_MessageInfo {
    val (icon, bg, tint) = when (dCategory()) {
        "订单" -> Triple(AppIcon.Card, DColor.BrandSoft, DColor.BrandInk)
        "动态" -> Triple(AppIcon.Image, DColor.OkSoft, DColor.Ok)
        else -> Triple(AppIcon.Bell, DColor.WarnSoft, DColor.Warn)
    }
    return A_MessageInfo(
        icon = icon,
        bg = bg,
        tint = tint,
        title = title.ifBlank { "系统通知" },
        sub = body,
        time = createdAt.replace("T", " ").take(16),
        category = dCategory(),
        screen = dTarget(),
    )
}


/** "2 岁" / "6 个月" → 月龄输入框的字符串。 */
private fun dAgeToMonth(age: String?): String {
    val text = age.orEmpty()
    val number = Regex("[0-9]+").find(text)?.value ?: return ""
    return if (text.contains("岁")) (number.toInt() * 12).toString() else number
}
