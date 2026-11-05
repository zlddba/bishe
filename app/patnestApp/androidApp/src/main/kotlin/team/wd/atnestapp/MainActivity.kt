package team.wd.atnestapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import team.wd.atnestapp.ui.design.DesignApp
import team.wd.atnestapp.utils.initAppVersionHelper

/**
 * 手机端只有一个 Activity，只承载设计稿还原版界面。
 *
 * 页面版式照 docs/KMP-Android端UI设计稿.html，数据全部走 MobileState / ApiClient 的真实接口；
 * 未登录可浏览首页、机构列表与详情，其余页面由 DNav 统一拦到登录页。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 沉浸式：状态栏 / 导航栏都透明，页面背景一直铺到屏幕边缘。
        // 深浅色跟随系统，图标颜色先由 auto 猜一份，进入 Compose 后再由 DTheme 按实际生效的主题校正。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        initAppVersionHelper(this)

        setContent {
            // 接口根地址由构建期从项目根的 .env（PATNEST_API_BASE）注入，App 内不再提供修改入口
            DesignApp(BuildConfig.API_BASE)
        }
    }
}
