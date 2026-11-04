package team.wd.atnestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import team.wd.atnestapp.ui.mobile.PatNestMobileApp
import team.wd.atnestapp.utils.initAppVersionHelper

/**
 * 手机端只有一个 Activity，页面切换、弹层、返回栈都交给 shared 里的 PatNestMobileApp，
 * 这样机构和主人两套工作台能共用同一份会话与接口客户端。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAppVersionHelper(this)

        setContent {
            PatNestMobileApp(applicationContext)
        }
    }
}
