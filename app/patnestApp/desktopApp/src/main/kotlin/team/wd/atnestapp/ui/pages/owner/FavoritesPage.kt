package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.PageHeader
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SitterCard
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape

@Composable
fun FavoritesPage(app: AppState) {
    LaunchedEffect(app.loggedIn) {
        if (app.loggedIn && app.favorites.isEmpty() && !app.favoritesLoading) app.loadFavorites()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        PageHeader("我的收藏", "常用机构快捷入口") {
            Tag("共 ${app.favorites.size} 家", TagKind.Soft)
        }

        when {
            app.favoritesLoading && app.favorites.isEmpty() -> {
                Text("正在加载收藏…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            app.favorites.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.favoritesError ?: "还没有收藏的机构，去逛逛吧",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("去逛机构", { app.go(AppPage.Sitters) }, height = 32.dp)
                    }
                }
            }
            else -> {
                val items = app.favorites.withIndex().toList() + listOf(null)
                GridRows(items, columns = 3, gap = 16.dp) { iv ->
                    if (iv != null) {
                        val realIndex = app.sitters.indexOfFirst { it.id == iv.value.id }
                        Column {
                            SitterCard(
                                iv.value,
                                onOpen = {
                                    if (realIndex >= 0) app.openSitterDetail(realIndex) else app.toast("该机构暂不可用")
                                },
                                onBook = {
                                    if (realIndex >= 0) app.openBooking(realIndex) else app.toast("该机构暂不可用")
                                },
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                if (iv.value.status != "active") {
                                    Tag(
                                        if (iv.value.status == "banned") "已被平台封禁" else "已下架",
                                        if (iv.value.status == "banned") TagKind.Danger else TagKind.Warn,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                LinkText("取消收藏", color = PatNestColors.Ink3) {
                                    app.scope.launch { app.removeFavorite(iv.value.id) }
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .border(1.5.dp, PatNestColors.Brand200, PatNestShape.Lg)
                                .clip(PatNestShape.Lg)
                                .background(PatNestColors.Brand50.copy(alpha = 0.45f))
                                .clickable { app.go(AppPage.Sitters) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.weight(1f))
                            AppIcon(AppIcon.Plus, Modifier.size(26.dp), PatNestColors.BrandInk)
                            Text(
                                "继续添加收藏",
                                color = PatNestColors.BrandInk, fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
