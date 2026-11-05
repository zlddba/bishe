package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.ClaimableCouponDto
import team.wd.atnestapp.data.api.UserCouponDto
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.CouponCard
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.data.Coupon
import team.wd.atnestapp.ui.theme.PatNestColors

@Composable
fun CouponsPage(app: AppState) {
    var tab by remember { mutableStateOf("可用") }
    val data = app.coupons

    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && data == null && !app.couponsLoading) app.loadCoupons()
    }

    val available = data?.items?.filter { it.status == "unused" }.orEmpty()
    val used = data?.items?.filter { it.status == "used" }.orEmpty()
    val expired = data?.items?.filter { it.status != "unused" && it.status != "used" }.orEmpty()
    val claimable = data?.claimable.orEmpty()
    val tabs = listOf(
        "可用（${available.size}）",
        "已使用（${used.size}）",
        "已过期（${expired.size}）",
        "可领取（${claimable.size}）",
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("优惠券", "下单时自动抵扣") {
            GhostButton("刷新", {
                app.scope.launch { app.loadCoupons(force = true) }
            })
        }

        ChipRow(tabs, tabs.firstOrNull { it.startsWith(tab) } ?: tabs[0]) { selected ->
            tab = selected.substringBefore("（")
        }

        Spacer(Modifier.height(16.dp))

        when {
            app.couponsLoading && data == null -> {
                Text("正在加载优惠券…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            data == null -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.couponsError ?: "暂时无法获取优惠券",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("重新加载", {
                            app.scope.launch { app.loadCoupons(force = true) }
                        }, height = 32.dp)
                    }
                }
            }
            else -> {
                val list: List<CouponRow> = when (tab) {
                    "可用" -> available.map { CouponRow(it.toCoupon(), null, it) }
                    "已使用" -> used.map { CouponRow(it.toCoupon(), null, null) }
                    "已过期" -> expired.map { CouponRow(it.toCoupon(), null, it) }
                    else -> claimable.map { CouponRow(it.toCoupon(), it.couponId, null) }
                }
                if (list.isEmpty()) {
                    Panel {
                        Text("这个分类下还没有优惠券", color = PatNestColors.Ink2, fontSize = 13.sp)
                    }
                } else {
                    list.forEach { row ->
                        CouponCard(row.coupon, onUse = {
                            when {
                                row.userCoupon != null -> {
                                    app.selectedCouponId = row.userCoupon.id
                                    app.selectedCouponTitle = row.userCoupon.title
                                    app.selectedCouponType = row.userCoupon.type
                                    app.selectedCouponValue = row.userCoupon.value
                                    app.go(AppPage.Booking)
                                }
                                row.claimId != null -> app.scope.launch { app.claimCoupon(row.claimId) }
                            }
                        })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

private fun UserCouponDto.toCoupon(): Coupon = Coupon(
    title = title.ifBlank { "优惠券" },
    value = if (type == "fixed") "¥${value.toInt()}" else "减免 ${value.toInt()}%",
    cond = "满 ¥${minAmount.toInt()} 可用",
    from = "",
    to = endsAt,
    state = when (status) {
        "unused" -> "可用"
        "used" -> "已使用"
        else -> "已过期"
    },
    issuer = if (sitterId != 0L) issuer else "",
)

private fun ClaimableCouponDto.toCoupon(): Coupon = Coupon(
    title = title.ifBlank { "优惠券" },
    value = if (type == "fixed") "¥${value.toInt()}" else "减免 ${value.toInt()}%",
    cond = "满 ¥${minAmount.toInt()} 可用",
    from = "",
    to = endsAt,
    state = "可领取",
    issuer = if (sitterId != 0L) issuer else "",
)

private data class CouponRow(
    val coupon: Coupon,
    val claimId: Long?,
    val userCoupon: UserCouponDto?,
)
