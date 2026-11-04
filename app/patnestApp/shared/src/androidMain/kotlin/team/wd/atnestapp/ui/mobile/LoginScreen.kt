package team.wd.atnestapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.components.AppIcon

/**
 * 登录 / 注册页。桌面端从 .env 读接口地址，手机端没有这个条件，
 * 所以把它做成默认收起的高级项，填过一次就写进 SharedPreferences。
 */
@Composable
fun LoginScreen(state: MobileState) {
    var mode by remember { mutableStateOf("登录") }
    var account by remember { mutableStateOf(state.account) }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf(state.serverAddress) }
    val localScope = rememberCoroutineScope()

    fun submit() {
        if (account.isBlank() || password.isBlank()) {
            state.toast("请填写账号和密码")
            return
        }
        localScope.launch {
            if (mode == "登录") state.login(account, password) else state.register(account, password, phone)
        }
    }

    MobilePageBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.padding(top = 48.dp)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PrimaryGradient)
                        .padding(15.dp)
                ) {
                    AppIcon(AppIcon.Paw, Modifier.fillMaxSize(), MobileColors.Panel)
                }
                Text("宠栖 PetNest", color = MobileColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Text(
                    if (mode == "登录") "登录后即可预约寄养、查看动态与订单" else "创建账号，开始给宠物找寄养",
                    color = MobileColors.Ink2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Column(Modifier.padding(top = 30.dp, bottom = 24.dp)) {
                ChipRow(listOf("登录", "注册"), mode, Modifier.padding(bottom = 18.dp)) { picked -> mode = picked }

            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                MsField(account, { account = it }, placeholder = "手机号 / 用户名", label = "账号")
                if (mode == "注册") {
                    MsField(phone, { phone = it }, placeholder = "11 位手机号", label = "手机号", numeric = true)
                }
                MsField(
                    password,
                    { password = it },
                    placeholder = if (mode == "登录") "登录密码" else "至少 6 位",
                    label = "密码",
                    secret = true,
                    onSubmit = { submit() },
                )
            }

            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                text = if (state.busy) "请稍候" else if (mode == "登录") "登录" else "注册并登录",
                onClick = { submit() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).clickable { showServer = !showServer },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("服务端地址", color = MobileColors.Ink3, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(if (showServer) "收起" else "修改", color = MobileColors.BrandDeep, fontSize = 12.sp)
            }
            if (showServer) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(MobileShape.Lg)
                        .background(MobileColors.Panel)
                        .border(1.dp, MobileColors.Line, MobileShape.Lg)
                        .padding(14.dp),
                ) {
                    Text("填写后端接口的根地址，例如 http://192.168.3.100:9200", color = MobileColors.Ink3, fontSize = 11.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            MsField(serverDraft, { serverDraft = it }, placeholder = MobileSession.DEFAULT_BASE_URL)
                        }
                        GhostButton("保存", onClick = { state.updateServerAddress(serverDraft) })
                    }
                }
            }

            Text(
                "演示账号 zlddba / Zl123456@",
                color = MobileColors.Ink3,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
            }
        }
    }
}
