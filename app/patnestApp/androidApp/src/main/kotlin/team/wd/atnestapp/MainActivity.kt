package team.wd.atnestapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import team.wd.atnestapp.ui.launch.Launch
import team.wd.atnestapp.utils.initAppVersionHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAppVersionHelper(this)

        setContent {
            Launch()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    Launch()
}
