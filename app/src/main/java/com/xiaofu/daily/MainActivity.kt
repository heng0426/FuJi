package com.xiaofu.daily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.xiaofu.daily.ui.XiaoFuApp
import com.xiaofu.daily.ui.theme.ThemeMode
import com.xiaofu.daily.ui.theme.ThemePreference
import androidx.core.view.WindowCompat
import com.xiaofu.daily.ui.theme.XiaoFuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode = remember { ThemePreference.read(this) }
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            XiaoFuTheme(darkTheme = dark) {
                XiaoFuApp()
            }
        }
    }
}
