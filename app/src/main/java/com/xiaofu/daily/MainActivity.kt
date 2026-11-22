package com.xiaofu.daily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xiaofu.daily.ui.XiaoFuApp
import com.xiaofu.daily.ui.theme.XiaoFuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiaoFuTheme {
                XiaoFuApp()
            }
        }
    }
}
