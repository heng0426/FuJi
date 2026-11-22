package com.xiaofu.daily.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 橘白主题（小福是橘猫）：暖橘主色 + 奶白底色
private val Orange = Color(0xFFE8853D)
private val OrangeContainer = Color(0xFFFFDCC2)
private val WarmBrown = Color(0xFF6F5B4E)
private val Cream = Color(0xFFFFF8F3)
private val Ink = Color(0xFF211A16)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = WarmBrown,
    onSecondary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3DFD3),
    onSurfaceVariant = Color(0xFF53433A),
    outline = Color(0xFF85736A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB77E),
    onPrimary = Color(0xFF4A2600),
    primaryContainer = Color(0xFF6B3D12),
    onPrimaryContainer = OrangeContainer,
    secondary = Color(0xFFDCC2B2),
    onSecondary = Color(0xFF3E2D22),
    background = Color(0xFF16120F),
    onBackground = Color(0xFFEDE0D8),
    surface = Color(0xFF16120F),
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF53433A),
    onSurfaceVariant = Color(0xFFD8C2B6),
    outline = Color(0xFFA08C80),
)

@Composable
fun XiaoFuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
