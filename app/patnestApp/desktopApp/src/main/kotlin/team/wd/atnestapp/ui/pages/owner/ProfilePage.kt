package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.StatCard
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.utils.PhoneValidator

private data class StatItem(val label: String, val value: String, val unit: String, val icon: AppIcon)

@Composable
fun ProfilePage(app: AppState) {
    // 昵称空时退回账号名，写死的假名字在答辩时容易被追问
    var nickname by remember { mutableStateOf(app.nickname.ifBlank { app.account }) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { app.loadProfile() }
    LaunchedEffect(app.profile) {
        app.profile?.let { profile ->
            nickname = profile.nickname?.takeIf { it.isNotBlank() } ?: nickname
            phone = profile.phone.orEmpty()
            address = profile.address.orEmpty()
            contactName = profile.contactName.orEmpty()
            contactPhone = profile.contactPhone.orEmpty()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader(
            "个人中心",
            "账号：${app.account.ifBlank { "-" }} · ${if (app.accountRole == "sitter") "机构账号" else "主人"}",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(title = "我的资料", subtitle = "账号：${app.account.ifBlank { "-" }}", modifier = Modifier.weight(1.3f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("昵称")
                        SoftTextField(nickname, { nickname = it }, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("手机号")
                        SoftTextField(phone, { phone = it }, Modifier.fillMaxWidth(), placeholder = "如 13800000000")
                    }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("常用地址")
                SoftTextField(address, { address = it }, Modifier.fillMaxWidth(), placeholder = "如 呼和浩特 · 赛罕区")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("紧急联系人")
                        SoftTextField(contactName, { contactName = it }, Modifier.fillMaxWidth(), placeholder = "如 家人")
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("联系人电话")
                        SoftTextField(contactPhone, { contactPhone = it }, Modifier.fillMaxWidth(), placeholder = "如 15900000000")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("保存", {
                        when {
                            phone.isNotBlank() && !PhoneValidator.isValid(phone) ->
                                app.toast(PhoneValidator.MESSAGE)
                            contactPhone.isNotBlank() && !PhoneValidator.isValid(contactPhone) ->
                                app.toast("联系人手机号格式不正确")
                            else -> app.scope.launch {
                                app.saveProfile(nickname, phone, address, contactName, contactPhone)
                            }
                        }
                    })
                    GhostButton("退出登录", {
                        app.modal = ModalKind.Confirm("退出登录", "确定退出当前账号吗？") { app.logout() }
                    }, textColor = PatNestColors.Danger)
                }
            }

            Panel(title = "我的统计", modifier = Modifier.weight(1f)) {
                GridRows(
                    listOf(
                        StatItem("累计寄养", "${app.orders.size}", "单", AppIcon.Orders),
                        StatItem("我的宠物", "${app.pets.size}", "只", AppIcon.Paw),
                        StatItem("收藏机构", "${app.favorites.size}", "家", AppIcon.Heart),
                        StatItem("积分", "${app.profile?.points ?: 0}", "分", AppIcon.Star),
                    ),
                    columns = 2, gap = 12.dp,
                ) { stat ->
                    StatCard(stat.label, stat.value, stat.unit, icon = stat.icon)
                }
                Spacer(Modifier.height(6.dp))
                ThinDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 11.dp).clickable {
                        app.modal = ModalKind.ChangePassword
                    },
                ) {
                    Text("账号与安全", color = PatNestColors.Ink, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("›", color = PatNestColors.Ink3, fontSize = 15.sp)
                }
            }
        }

        if (app.accountRole != "sitter") {
            Spacer(Modifier.height(16.dp))
            val application = app.profile?.sitterApplication
            when (application?.status) {
                "pending" -> Panel(title = "机构入驻", subtitle = "申请审核中") {
                    Text(
                        "入驻申请已提交，平台正在审核营业执照等资质材料；审核通过后账号自动升级为机构。",
                        color = PatNestColors.Ink2, fontSize = 12.5.sp,
                    )
                }
                "rejected" -> Panel(title = "机构入驻", subtitle = "申请已驳回") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "资质审核未通过，可修改材料后重新提交。",
                                color = PatNestColors.Danger, fontSize = 12.5.sp,
                            )
                            application?.reviewNote?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    "驳回原因：$it",
                                    color = PatNestColors.Ink2, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        PrimaryButton("重新申请", {
                            app.modal = ModalKind.ApplySitter
                        }, height = 36.dp)
                    }
                }
                "active", "inactive" -> Panel(title = "机构入驻", subtitle = "审核已通过") {
                    Text(
                        "资质审核已通过，重新登录后即可进入机构端管理店铺与接单。",
                        color = PatNestColors.Ink2, fontSize = 12.5.sp,
                    )
                }
                "banned" -> Panel(title = "机构入驻", subtitle = "机构已被封禁") {
                    Text(
                        "该账号关联的机构已被平台封禁，如需恢复请联系管理员。",
                        color = PatNestColors.Danger, fontSize = 12.5.sp,
                    )
                }
                else -> Panel(title = "机构入驻", subtitle = "上传资质材料，审核通过后可发布寄养服务") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "提交机构名称、联系人及营业执照等证明材料，平台审核通过后即可接单。",
                            color = PatNestColors.Ink2, fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("申请成为机构", {
                            app.modal = ModalKind.ApplySitter
                        }, height = 36.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SupportPanel(app)

        Spacer(Modifier.height(16.dp))
        AccountSecurityPanel(app)
    }
}

/** 帮助与权益：联系客服（真提工单）、积分余额与最近流水、我的工单进度 */
@Composable
private fun SupportPanel(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn) {
            app.loadPoints()
            app.loadWallet()
            app.loadTickets()
        }
    }

    Panel(title = "帮助与权益", subtitle = "遇到问题直接提工单，平台会在这里回复") {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp).clickable {
                app.modal = ModalKind.Ticket
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("联系客服", color = PatNestColors.Ink, fontSize = 13.5.sp)
                Text("订单、支付、机构纠纷等问题提交工单", color = PatNestColors.Ink3, fontSize = 11.5.sp)
            }
            Text("›", color = PatNestColors.Ink3, fontSize = 15.sp)
        }
        ThinDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("可用积分", color = PatNestColors.Ink, fontSize = 13.5.sp)
            Spacer(Modifier.weight(1f))
            Text("${app.pointsBalance} 分", color = PatNestColors.BrandInk, fontSize = 13.sp)
        }
        if (app.pointsHistory.isNotEmpty()) {
            ThinDivider()
            app.pointsHistory.take(3).forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.reason.ifBlank { "积分变动" }, color = PatNestColors.Ink2, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        (if (entry.change >= 0) "+" else "") + entry.change + " 分",
                        color = if (entry.change >= 0) PatNestColors.Ok else PatNestColors.Danger,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        ThinDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp).clickable {
                app.go(AppPage.Wallet)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("我的钱包", color = PatNestColors.Ink, fontSize = 13.5.sp)
                Text("真实货币余额，可用于支付订单", color = PatNestColors.Ink3, fontSize = 11.5.sp)
            }
            Text("¥${app.walletBalance}", color = PatNestColors.BrandInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("›", color = PatNestColors.Ink3, fontSize = 15.sp)
        }
        if (app.walletTransactions.isNotEmpty()) {
            ThinDivider()
            app.walletTransactions.take(3).forEach { tx ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(walletTypeLabel(tx.type), color = PatNestColors.Ink2, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    val signed = walletSignedAmount(tx)
                    val positive = signed >= 0
                    Text(
                        (if (positive) "+" else "-") + "¥" + moneyText(if (positive) signed else -signed),
                        color = if (positive) PatNestColors.Danger else PatNestColors.Ok,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (app.tickets.isNotEmpty()) {
            ThinDivider()
            Text("我的工单", color = PatNestColors.Ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            app.tickets.take(3).forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.title, color = PatNestColors.Ink, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Tag(ticketStatusLabel(t.status), if (t.status == "resolved") TagKind.Ok else TagKind.Warn)
                }
            }
        }
    }
}

private fun ticketStatusLabel(status: String) = when (status) {
    "open" -> "待处理"
    "processing" -> "处理中"
    "resolved" -> "已解决"
    "closed" -> "已关闭"
    else -> status
}

/** 账户安全：注销账户 / 注销机构；申请提交成功后立即退出登录，3 天内重新登录自动撤销。 */
@Composable
private fun AccountSecurityPanel(app: AppState) {
    Panel(title = "账户安全", subtitle = "申请提交成功后将立即退出登录；3 天内重新登录可自动撤销") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("注销账户", color = PatNestColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (app.isSitterAccount) {
                        "可选择注销整个账户或仅注销机构；申请后立即退出登录，3 天内重新登录自动撤销。"
                    } else {
                        "申请后立即退出登录，进入 3 天冷静期；重新登录自动撤销，到期后自动删除账号数据。"
                    },
                    color = PatNestColors.Ink2, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (app.isSitterAccount) {
                GhostButton("注销机构", {
                    app.modal = ModalKind.Confirm(
                        "注销机构",
                        "提交后将立即退出登录并进入 3 天冷静期：重新登录自动撤销；到期后机构将被注销，账号退回主人角色。",
                    ) { app.scope.launch { app.requestAccountCancel("sitter") } }
                }, textColor = PatNestColors.Danger, height = 36.dp)
                Spacer(Modifier.width(10.dp))
            }
            GhostButton("注销账户", {
                app.modal = ModalKind.Confirm(
                    "注销整个账户",
                    "提交后将立即退出登录并进入 3 天冷静期：重新登录自动撤销；到期后账号及个人数据将被删除（存在订单等业务记录时转为已删除状态）。",
                ) { app.scope.launch { app.requestAccountCancel("account") } }
            }, textColor = PatNestColors.Danger, height = 36.dp)
        }
    }
}

