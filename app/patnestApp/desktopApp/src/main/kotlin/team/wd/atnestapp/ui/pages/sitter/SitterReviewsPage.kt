package team.wd.atnestapp.ui.pages.sitter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import team.wd.atnestapp.data.api.SitterReviewDto
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.SoftTextField
import team.wd.atnestapp.ui.components.StarRating
import team.wd.atnestapp.ui.components.StatCard
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.theme.PatNestColors

@Composable
fun SitterReviewsPage(app: AppState) {
    var selectedReviewId by remember { mutableStateOf(0L) }
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.sitterReviews.isEmpty() && !app.sitterReviewsLoading) app.loadSitterReviews()
    }

    val reviews = app.sitterReviews
    val avg = { selector: (SitterReviewDto) -> Int ->
        if (reviews.isEmpty()) 0.0 else reviews.map(selector).average()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("评价管理", "查看并回复主人评价") {
            PrimaryButton("刷新", {
                app.scope.launch { app.loadSitterReviews(force = true) }
            }, height = 38.dp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("综合评分", formatScore(avg { it.overallScore }), modifier = Modifier.weight(1f))
            StatCard("服务", formatScore(avg { it.serviceScore }), modifier = Modifier.weight(1f))
            StatCard("环境", formatScore(avg { it.environmentScore }), modifier = Modifier.weight(1f))
            StatCard("卫生", formatScore(avg { it.hygieneScore }), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        Panel(title = "主人评价") {
            when {
                app.sitterReviewsLoading && reviews.isEmpty() -> {
                    Text("正在加载评价…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
                }
                reviews.isEmpty() -> {
                    Text(
                        app.sitterReviewsError ?: "还没有收到评价",
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                    )
                }
                else -> {
                    reviews.forEachIndexed { index, review ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StarRating(review.overallScore, size = 12.dp)
                                Text("主人", color = PatNestColors.Ink, fontSize = 13.sp)
                                Text(formatTime(review.createdAt), color = PatNestColors.Ink3, fontSize = 11.5.sp)
                                Spacer(Modifier.weight(1f))
                                if (review.replyText.isNullOrBlank()) {
                                    PrimaryButton("回复", {
                                        selectedReviewId = review.id
                                        replyText = ""
                                    }, height = 30.dp)
                                }
                            }
                            Text(
                                review.ownerComment?.takeIf { it.isNotBlank() } ?: "（无文字评价）",
                                color = PatNestColors.Ink2, fontSize = 12.5.sp, lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            if (!review.replyText.isNullOrBlank()) {
                                Text(
                                    "机构回复：${review.replyText}",
                                    color = PatNestColors.BrandInk, fontSize = 12.5.sp, lineHeight = 20.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        if (index < reviews.lastIndex) ThinDivider()
                    }
                }
            }
        }

        if (selectedReviewId != 0L) {
            Spacer(Modifier.height(14.dp))
            Panel(title = "回复评价") {
                SoftTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "写下回复内容（最多 500 字）",
                    singleLine = false,
                    minHeight = 80.dp,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton("发送回复", {
                        if (replyText.isBlank()) {
                            app.toast("请输入回复内容")
                        } else {
                            app.scope.launch {
                                app.replyReview(selectedReviewId, replyText.trim())
                                selectedReviewId = 0
                                replyText = ""
                            }
                        }
                    }, height = 36.dp)
                    GhostButton("取消", {
                        selectedReviewId = 0
                        replyText = ""
                    }, height = 36.dp)
                }
            }
        }
    }
}

private fun formatScore(value: Double): String =
    if (value <= 0.0) "—" else ((value * 10).toInt() / 10.0).toString()

private fun formatTime(raw: String): String {
    if (raw.length < 10) return raw
    return raw.substring(5, 10).replace('-', '/')
}
