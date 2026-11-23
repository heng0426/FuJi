package com.xiaofu.daily

import android.content.Context
import android.content.res.Configuration
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
import com.xiaofu.daily.widget.WidgetRefreshScheduler
import com.xiaofu.daily.widget.WidgetUpdater

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val mode = ThemePreference.read(newBase)
        if (mode == ThemeMode.SYSTEM) {
            super.attachBaseContext(newBase)
            return
        }
        val config = Configuration(newBase.resources.configuration)
        val nightMode = if (mode == ThemeMode.DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onResume() {
        super.onResume()
        // App 内写入记录或改档案后，回到桌面时小组件与最新数据一致
        WidgetUpdater.refreshAllAsync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 跨零点后准时刷新小组件；WorkManager 会在重启后保留任务
        WidgetRefreshScheduler.scheduleMidnightRefresh(this)
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
