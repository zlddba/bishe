package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.WalletDto
import team.wd.atnestapp.data.api.WalletTransactionDto
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape

/** 钱包交易类型 → 中文 */
fun walletTypeLabel(type: String): String = when (type) {
    "recharge" -> "充值"
    "consume" -> "消费"
    "refund" -> "退款"
    "withdraw" -> "提现"
    "adjust_add" -> "调账入账"
    "adjust_sub" -> "调账扣减"
    else -> type.ifBlank { "其他" }
}

/** 钱包渠道 → 中文 */
fun walletChannelLabel(channel: String): String = when (channel) {
    "mock" -> "模拟支付"
    "wechat" -> "微信"
    "alipay" -> "支付宝"
    "balance" -> "钱包余额"
    else -> channel.ifBlank { "—" }
}

/** 金额展示：整钱不带小数，小数最多两位；不用 String.format 以免跟随系统 locale */
fun moneyText(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** 流水方向：服务端 amount 恒为正，靠 type 区分收支（支出为负） */
fun walletSignedAmount(tx: WalletTransactionDto): Double = when (tx.type) {
    "consume", "withdraw", "adjust_sub" -> -tx.amount
    else -> tx.amount
}

@Composable
fun WalletPage(app: AppState) {
    var showRecharge by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.loadWallet() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeader("我的钱包", "真实货币余额，可用于支付寄养订单")
            Spacer(Modifier.weight(1f))
            // 钱包没有提现通道，退款走「申请 → 客服线下打款 → 后台确认」
            GhostButton("申请退款", { showWithdraw = true }, height = 38.dp)
            PrimaryButton("充值", { showRecharge = true }, height = 38.dp, icon = AppIcon.Plus)
        }

        WalletBalanceCard(app)

        Spacer(Modifier.height(16.dp))

        Panel(title = "交易流水", subtitle = "最近的资金变动") {
            if (app.walletLoading && app.walletTransactions.isEmpty()) {
                Text("加载中…", color = PatNestColors.Ink3, fontSize = 13.sp)
            } else if (app.walletTransactions.isEmpty()) {
                if (app.walletError != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(app.walletError ?: "加载失败", color = PatNestColors.Danger, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        GhostButton("重试", { app.scope.launch { app.loadWallet(force = true) } }, height = 32.dp)
                    }
                } else {
                    Text("暂无交易记录", color = PatNestColors.Ink3, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                ) {
                    items(app.walletTransactions, key = { it.id }) { tx ->
                        TransactionRow(tx)
                        ThinDivider()
                    }
                }
            }
        }
    }

    if (showRecharge) {
        RechargeDialog(
            onDismiss = { showRecharge = false },
            onConfirm = { amount ->
                // 这里是 suspend lambda，由弹窗内部的 scope 执行，busy 才能覆盖整个请求周期
                val ok = app.recharge(amount)
                if (ok) showRecharge = false
            },
        )
    }

    if (showWithdraw) {
        WithdrawDialog(
            balance = app.wallet?.balance ?: app.walletBalance,
            onDismiss = { showWithdraw = false },
            onConfirm = { amount, name, no, contact ->
                app.scope.launch {
                    val ok = app.applyWalletWithdrawal(amount, name, no, contact)
                    if (ok) showWithdraw = false
                }
            },
        )
    }
}

/**
 * 余额退款申请。
 *
 * 收款信息是必填的：钱包没有提现通道，客服要靠这些信息线下打款；
 * 不填就只能靠电话追问，退款单会一直挂着。
 */
@Composable
private fun WithdrawDialog(
    balance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var accountNo by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66241A1E))
            .clickable { onDismiss() },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .width(420.dp)
                .clip(PatNestShape.Lg)
                .background(Color.White)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(22.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("申请余额退款", color = PatNestColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(26.dp).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        AppIcon(AppIcon.Close, Modifier.size(16.dp), PatNestColors.Ink3)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "当前可用余额 ¥${moneyText(balance)}",
                    color = PatNestColors.Ink3, fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))

                FieldLabel("退款金额（元）")
                SoftTextField(
                    amountText,
                    { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    Modifier.fillMaxWidth(),
                    placeholder = "不超过可用余额",
                )
                Spacer(Modifier.height(10.dp))
                FieldLabel("收款人姓名")
                SoftTextField(name, { name = it.take(32) }, Modifier.fillMaxWidth(), placeholder = "与银行卡/支付宝一致")
                Spacer(Modifier.height(10.dp))
                FieldLabel("收款账号")
                SoftTextField(
                    accountNo,
                    { accountNo = it.take(64) },
                    Modifier.fillMaxWidth(),
                    placeholder = "银行卡号 / 支付宝账号",
                )
                Spacer(Modifier.height(10.dp))
                FieldLabel("联系电话")
                SoftTextField(contact, { contact = it.take(20) }, Modifier.fillMaxWidth(), placeholder = "方便客服确认")

                Spacer(Modifier.height(10.dp))
                if (errorText.isNotEmpty()) {
                    Text(errorText, color = PatNestColors.Danger, fontSize = 11.5.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "提交后金额会立即从余额扣除，由客服线下打款；若被驳回，金额自动退回钱包。",
                    color = PatNestColors.Ink3, fontSize = 11.5.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("取消", onDismiss, Modifier.weight(1f), height = 40.dp)
                    PrimaryButton(
                        "提交申请",
                        {
                            val amount = amountText.toDoubleOrNull()
                            // 原先四种失败原因合并成一个静默 return，用户点完毫无反馈
                            errorText = when {
                                amount == null -> "请输入退款金额"
                                amount <= 0.0 -> "退款金额需大于 0"
                                amount > balance -> "退款金额不能超过可用余额 ¥$balance"
                                name.isBlank() -> "请填写收款人"
                                accountNo.isBlank() -> "请填写收款账号"
                                else -> ""
                            }
                            if (errorText.isNotEmpty()) return@PrimaryButton
                            onConfirm(amount ?: 0.0, name.trim(), accountNo.trim(), contact.trim())
                        },
                        Modifier.weight(1f),
                        height = 40.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletBalanceCard(app: AppState) {
    val wallet = app.wallet
    val balance = wallet?.balance ?: app.walletBalance
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PatNestShape.Lg)
            .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk)))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text("可用余额（元）", color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("¥", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                moneyText(balance),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("冻结金额", "¥${moneyText(wallet?.frozenBalance ?: 0.0)}", Modifier.weight(1f))
            MiniStat("累计充值", "¥${moneyText(wallet?.totalRecharge ?: 0.0)}", Modifier.weight(1f))
            MiniStat("累计消费", "¥${moneyText(wallet?.totalConsume ?: 0.0)}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TransactionRow(tx: WalletTransactionDto) {
    val signed = walletSignedAmount(tx)
    val positive = signed >= 0
    // 中国习惯：收入（正）用红色强调、支出（负）用绿色
    val amountColor = if (positive) PatNestColors.Danger else PatNestColors.Ok
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(walletTypeLabel(tx.type), color = PatNestColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tx.createdAt.replace('T', ' ').take(16),
                    color = PatNestColors.Ink3, fontSize = 11.5.sp,
                )
                Text("·", color = PatNestColors.Ink3, fontSize = 11.5.sp)
                Text(walletChannelLabel(tx.channel), color = PatNestColors.Ink3, fontSize = 11.5.sp)
                if (tx.status.isNotBlank()) {
                    Text("·", color = PatNestColors.Ink3, fontSize = 11.5.sp)
                    Text(tx.status, color = PatNestColors.Ink3, fontSize = 11.5.sp)
                }
            }
            if (tx.remark.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    tx.remark,
                    color = PatNestColors.Ink2, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            (if (positive) "+" else "-") + "¥" + moneyText(if (positive) signed else -signed),
            color = amountColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RechargeDialog(onDismiss: () -> Unit, onConfirm: suspend (Double) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66241A1E))
            .clickable { if (!busy) onDismiss() },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .width(380.dp)
                .clip(PatNestShape.Lg)
                .background(Color.White)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(22.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("钱包充值", color = PatNestColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(26.dp).clickable { if (!busy) onDismiss() }, contentAlignment = Alignment.Center) {
                        AppIcon(AppIcon.Close, Modifier.size(16.dp), PatNestColors.Ink3)
                    }
                }
                Spacer(Modifier.height(16.dp))
                FieldLabel("充值金额（元）")
                SoftTextField(
                    amountText,
                    { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    Modifier.fillMaxWidth(),
                    placeholder = "如 100",
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "演示环境为模拟支付：提交后直接确认到账，不会产生真实扣款。",
                    color = PatNestColors.Ink3, fontSize = 11.5.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("取消", onDismiss, Modifier.weight(1f), height = 40.dp)
                    PrimaryButton(
                        if (busy) "处理中…" else "确认充值",
                        {
                            val amount = amountText.toDoubleOrNull()
                            if (amount == null || amount <= 0.0 || busy) {
                                return@PrimaryButton
                            }
                            // busy 必须在协程内复位：原先 onConfirm 是异步启动，
                            // 调用一返回就置回 false，网络往返期间还能连点、重复下单充值
                            busy = true
                            scope.launch {
                                try {
                                    onConfirm(amount)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        Modifier.weight(1f),
                        height = 40.dp,
                    )
                }
            }
        }
    }
}
