package team.wd.atnestapp.ui.pages.sitter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import team.wd.atnestapp.data.api.SitterCouponDto
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.HCell
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.TCell
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors

@Composable
fun SitterCouponsPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn) app.loadSitterCoupons(force = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("优惠券", "发布本店专属券，主人领取后在本店下单自动抵扣") {
            PrimaryButton("发布优惠券", {
                app.modal = ModalKind.CouponForm(null)
            }, height = 38.dp)
        }

        Panel(title = "本机构的优惠券") {
            if (app.sitterCouponsLoading && app.sitterCoupons.isEmpty()) {
                Text("正在加载优惠券…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            } else if (app.sitterCoupons.isEmpty()) {
                Text(
                    "还没有发布过优惠券，点击右上角发布第一张",
                    color = PatNestColors.Ink2, fontSize = 13.sp,
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    HCell("券名称", 1.8f)
                    HCell("面值", 0.9f)
                    HCell("领取/核销", 1f)
                    HCell("有效期至", 1f)
                    HCell("状态", 0.8f)
                    HCell("操作", 1.6f)
                }
                ThinDivider()
                app.sitterCoupons.forEach { coupon ->
                    CouponAdminRow(app, coupon)
                    ThinDivider()
                }
            }
        }
    }
}

@Composable
private fun CouponAdminRow(app: AppState, coupon: SitterCouponDto) {
    val face = if (coupon.type == "percent") "${coupon.value.toInt()}%" else "¥${coupon.value.toInt()}"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TCell(coupon.title, 1.8f)
        TCell(face, 0.9f, mono = true)
        TCell("${coupon.claimedQty} / ${coupon.usedQty}", 1f, mono = true)
        TCell(if (coupon.endsAt.isBlank()) "长期" else coupon.endsAt, 1f, dim = true)
        Tag(
            if (coupon.enabled) "启用中" else "已停用",
            if (coupon.enabled) TagKind.Ok else TagKind.Soft,
        )
        Spacer(Modifier.width(10.dp))
        GhostButton("编辑", { app.modal = ModalKind.CouponForm(coupon) }, height = 30.dp)
        Spacer(Modifier.width(8.dp))
        GhostButton(
            if (coupon.enabled) "停用" else "启用",
            { app.scope.launch { app.toggleSitterCoupon(coupon) } },
            height = 30.dp,
        )
        Spacer(Modifier.width(8.dp))
        GhostButton("删除", {
            app.modal = ModalKind.Confirm(
                "删除优惠券",
                "删除「${coupon.title}」后，主人已领取但未使用的券将同时从券包移除；已被订单核销的券无法删除。",
            ) { app.scope.launch { app.deleteSitterCoupon(coupon) } }
        }, height = 30.dp, textColor = PatNestColors.Danger)
    }
}
