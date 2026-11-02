package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppState
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
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.utils.PhoneValidator

private data class StatItem(val label: String, val value: String, val unit: String, val icon: AppIcon)

@Composable
fun ProfilePage(app: AppState) {
    var nickname by remember { mutableStateOf(app.nickname.ifBlank { "陈雨" }) }
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
            Panel(title = "机构入驻", subtitle = "申请成为机构账号，审核通过后可发布寄养服务") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "提交机构名称、类型、地址、容量和价格，平台审核通过后即可接单。",
                        color = PatNestColors.Ink2, fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton("申请成为机构", {
                        app.modal = ModalKind.ApplySitter
                    }, height = 36.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        AccountSecurityPanel(app)
    }
}

/** 账户安全：注销账户 / 注销机构，含冷静期提示与撤销入口。 */
@Composable
private fun AccountSecurityPanel(app: AppState) {
    val cancelMode = app.profile?.cancelMode
    val cancelDeadline = app.profile?.cancelDeadline

    Panel(title = "账户安全", subtitle = "注销采用冷静期机制：申请后 3 天内登录即自动撤销") {
        if (cancelMode != null && cancelDeadline != null) {
            val modeText = if (cancelMode == "sitter") "仅注销机构" else "注销整个账户"
            val deadlineText = cancelDeadline.replace('T', ' ').take(16)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(PatNestColors.DangerSoft, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "注销申请已提交：$modeText",
                    color = PatNestColors.Danger, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "冷静期至 $deadlineText 自动注销；期间登录将自动撤销该申请。",
                    color = PatNestColors.Danger, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                PrimaryButton("撤销注销", {
                    app.scope.launch { app.withdrawAccountCancel() }
                }, Modifier.padding(top = 12.dp), height = 34.dp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("注销账户", color = PatNestColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (app.isSitterAccount) {
                            "可选择注销整个账户或仅注销机构；申请后进入 3 天冷静期，登录即撤销。"
                        } else {
                            "申请后进入 3 天冷静期，登录即撤销；到期后自动删除账号数据。"
                        },
                        color = PatNestColors.Ink2, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (app.isSitterAccount) {
                    GhostButton("注销机构", {
                        app.modal = ModalKind.Confirm(
                            "注销机构",
                            "申请后进入 3 天冷静期：期间登录自动撤销；到期后机构将被注销，账号退回主人角色。",
                        ) { app.scope.launch { app.requestAccountCancel("sitter") } }
                    }, textColor = PatNestColors.Danger, height = 36.dp)
                    Spacer(Modifier.width(10.dp))
                }
                GhostButton("注销账户", {
                    app.modal = ModalKind.Confirm(
                        "注销整个账户",
                        "申请后进入 3 天冷静期：期间登录自动撤销；到期后账号及个人数据将被删除（存在订单等业务记录时转为已删除状态）。",
                    ) { app.scope.launch { app.requestAccountCancel("account") } }
                }, textColor = PatNestColors.Danger, height = 36.dp)
            }
        }
    }
}

