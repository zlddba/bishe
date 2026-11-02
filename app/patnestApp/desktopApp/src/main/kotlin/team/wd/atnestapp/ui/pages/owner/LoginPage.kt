package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.ChipRow
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.theme.PatNestBlushBackground
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.brandGlow
import team.wd.atnestapp.ui.theme.patShadow
import team.wd.atnestapp.utils.PhoneValidator

/**
 * 登录 / 注册页：账号密码登录，未注册账号可直接注册。
 * 应用启动后默认以游客身份浏览，需要登录的操作才会进入这里。
 */
@Composable
fun LoginPage(
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    var mode by remember { mutableStateOf("登录") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    fun submitLogin() {
        if (busy) return
        when {
            account.isBlank() -> onError("请输入账号")
            account.trim().length < 3 -> onError("账号至少 3 位")
            PhoneValidator.looksLikePhone(account) && !PhoneValidator.isValid(account) ->
                onError(PhoneValidator.MESSAGE)
            password.isBlank() -> onError("请输入密码")
            else -> onLogin(account.trim(), password)
        }
    }

    fun submitRegister() {
        if (busy) return
        when {
            phone.isBlank() -> onError("请输入手机号")
            !PhoneValidator.isValid(phone) -> onError(PhoneValidator.MESSAGE)
            account.isBlank() -> onError("请输入账号")
            account.trim().length < 3 -> onError("账号至少 3 位")
            PhoneValidator.looksLikePhone(account) && !PhoneValidator.isValid(account) ->
                onError(PhoneValidator.MESSAGE)
            password.length < 8 -> onError("密码至少 8 位")
            password.length > 128 -> onError("密码不能超过 128 位")
            password != confirmPassword -> onError("两次输入的密码不一致")
            // 昵称不在此收集：注册时默认沿用账号名，登录后可在"个人中心"修改
            else -> onRegister(account.trim(), password, phone.trim())
        }
    }

    PatNestBlushBackground {
        Column(
            Modifier
                .align(Alignment.Center)
                .width(430.dp)
                .patShadow(30.dp, PatNestShape.Xl)
                .clip(PatNestShape.Xl)
                .background(Color.White),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(PatNestColors.Brand50, PatNestColors.BrandSoft),
                        ),
                    )
                    .padding(vertical = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .brandGlow(12.dp, PatNestShape.Pill)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk))),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Paw, Modifier.size(28.dp), Color.White)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "宠栖 PetNest",
                        color = PatNestColors.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "宠物寄养服务预约 · Desktop",
                        color = PatNestColors.Ink2,
                        fontSize = 12.5.sp,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 34.dp, vertical = 26.dp)) {
                ChipRow(
                    options = listOf("登录", "注册"),
                    selected = mode,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onSelect = { mode = it },
                )

                Spacer(Modifier.height(22.dp))

                if (mode == "登录") {
                    FieldLabel("账号")
                    SoftTextField(
                        value = account,
                        onValueChange = { account = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "手机号 / 用户名",
                    )
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("密码")
                    SoftTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "请输入密码",
                        password = true,
                        onSubmit = { submitLogin() },
                    )
                    PrimaryButton(
                        if (busy) "登录中…" else "登 录",
                        ::submitLogin,
                        Modifier.fillMaxWidth().padding(top = 22.dp),
                        height = 47.dp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkText("没有账号？立即注册") { mode = "注册" }
                        Spacer(Modifier.width(18.dp))
                        LinkText("先逛逛", color = PatNestColors.Ink2) { onCancel() }
                    }
                } else {
                    FieldLabel("手机号")
                    SoftTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "请输入 11 位手机号",
                    )
                    Spacer(Modifier.height(13.dp))
                    FieldLabel("账号")
                    SoftTextField(
                        value = account,
                        onValueChange = { account = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "手机号 / 用户名",
                    )
                    Spacer(Modifier.height(13.dp))
                    FieldLabel("密码")
                    SoftTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "8-128 位",
                        password = true,
                    )
                    Text(
                        "密码长度 8-128 位，建议使用字母与数字组合",
                        color = PatNestColors.Ink3,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Spacer(Modifier.height(13.dp))
                    FieldLabel("确认密码")
                    SoftTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "再次输入密码",
                        password = true,
                        onSubmit = { submitRegister() },
                    )
                    PrimaryButton(
                        if (busy) "注册中…" else "注册并登录",
                        ::submitRegister,
                        Modifier.fillMaxWidth().padding(top = 22.dp),
                        height = 47.dp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinkText("已有账号？去登录") { mode = "登录" }
                        Spacer(Modifier.width(18.dp))
                        LinkText("先逛逛", color = PatNestColors.Ink2) { onCancel() }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (mode == "注册") {
                            "昵称默认使用账号名，注册后可在「个人中心」修改"
                        } else {
                            "登录后可预约机构、管理宠物档案与订单"
                        },
                        color = PatNestColors.Ink3,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
