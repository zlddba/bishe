package team.wd.atnestapp.ui.pages.owner

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.FieldLabel
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.StarRating
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.theme.PatNestColors
import kotlinx.coroutines.launch

@Composable
fun ReviewPage(app: AppState) {
    val order = app.orders.getOrNull(app.orderIndex)
    if (order == null) {
        Column(Modifier.fillMaxSize().padding(26.dp)) {
            PageHeader("四维评价", "订单数据加载中")
            Text(app.ordersError ?: "订单信息加载中…", color = PatNestColors.Ink2, fontSize = 13.sp)
        }
        return
    }
    var overall by remember { mutableStateOf(3) }
    var env by remember { mutableStateOf(5) }
    var service by remember { mutableStateOf(4) }
    var safety by remember { mutableStateOf(3) }
    var value by remember { mutableStateOf(5) }
    var text by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("返回订单", { app.go(AppPage.Orders) }, height = 34.dp, icon = AppIcon.Back)
            Spacer(Modifier.weight(1f))
            Tag("寄养已完成", TagKind.Brand)
        }

        Spacer(Modifier.height(14.dp))

        Column(Modifier.width(620.dp)) {
            Panel(
                title = "评价「${order.org}」",
                subtitle = "${order.pet} · ${order.range}",
            ) {
                FieldLabel("综合评分", Modifier.padding(bottom = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarRating(overall, size = 30.dp, interactive = true) { overall = it }
                    Text("已选 $overall 星", color = PatNestColors.Ink3, fontSize = 12.sp)
                }

                Spacer(Modifier.height(18.dp))

                DimStarRow("环境", env) { env = it }
                DimStarRow("服务", service) { service = it }
                DimStarRow("安全", safety) { safety = it }
                DimStarRow("性价比", value) { value = it }
            }

            Spacer(Modifier.height(14.dp))

            Panel(title = "文字评价", subtitle = "选填") {
                SoftTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "分享寄养体验，帮助更多主人选择……",
                    singleLine = false,
                    minHeight = 96.dp,
                )
                Text(
                    "可补充照片，最多 3 张",
                    color = PatNestColors.Ink3, fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            PrimaryButton(
                "提交评价",
                {
                    app.scope.launch {
                        app.submitReview(order.id, overall, service, env, safety, text)
                    }
                },
                Modifier.fillMaxWidth().padding(top = 16.dp),
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun DimStarRow(label: String, score: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, color = PatNestColors.Ink2, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        StarRating(score, size = 20.dp, interactive = true, onChange = onChange)
    }
}
