package com.xiaofu.daily.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.model.RecordType

// ===== 设计令牌（docs/UI设计规范-小福日常.md §2）=====

private val LightColors = lightColorScheme(
    primary = Color(0xFFF09C63),
    onPrimary = Color(0xFF3A1E0A),
    primaryContainer = Color(0xFFFFEBDD),
    onPrimaryContainer = Color(0xFF4A2408),
    secondary = Color(0xFF6F5B4E),
    onSecondary = Color.White,
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFFBEDE3),
    onSurfaceVariant = Color(0xFF6E5A4B),
    outline = Color(0xFFF0E2D6),
    outlineVariant = Color(0xFFF0E2D6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB77E),
    onPrimary = Color(0xFF4A2306),
    primaryContainer = Color(0xFF4A2C12),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFDCC2B2),
    onSecondary = Color(0xFF3E2D22),
    background = Color(0xFF151110),
    onBackground = Color(0xFFF1E4DA),
    surface = Color(0xFF211A16),
    onSurface = Color(0xFFF1E4DA),
    surfaceVariant = Color(0xFF2C2320),
    onSurfaceVariant = Color(0xFFC9B4A5),
    outline = Color(0xFF3A2E27),
    outlineVariant = Color(0xFF3A2E27),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF3A211F),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = TextStyle(
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp,
            fontFeatureSettings = "tnum",
        ),
        headlineSmall = TextStyle(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = TextStyle(
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
    )
}

/** 供 UI 判断当前生效的是否为深色主题（不依赖系统设置） */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

@Composable
fun XiaoFuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

// ===== 六类记录的固定图标与语义色（禁止页面内散落定义）=====

data class RecordStyle(
    val iconRes: Int,
    val fg: Color,
    val bg: Color,
)

private val LightRecordStyles = mapOf(
    RecordType.WATER to RecordStyle(R.drawable.ic_ms_water_drop, Color(0xFF3E7CB1), Color(0xFFE8F1F8)),
    RecordType.FOOD to RecordStyle(R.drawable.ic_ms_restaurant, Color(0xFFB5501A), Color(0xFFFDEBDD)),
    RecordType.WEIGHT to RecordStyle(R.drawable.ic_ms_monitor_weight, Color(0xFF5E6E4E), Color(0xFFEEF2E6)),
    RecordType.DEWORM to RecordStyle(R.drawable.ic_ms_pest_control, Color(0xFF6F55A0), Color(0xFFF0EBFA)),
    RecordType.VACCINE to RecordStyle(R.drawable.ic_ms_vaccines, Color(0xFF2C7366), Color(0xFFE4F2EF)),
    RecordType.ANOMALY to RecordStyle(R.drawable.ic_ms_warning, Color(0xFFB3261E), Color(0xFFFBE9E7)),
    RecordType.VET to RecordStyle(R.drawable.ic_ms_local_hospital, Color(0xFF4A6FA5), Color(0xFFE8EEF8)),
)

private val DarkRecordStyles = mapOf(
    RecordType.WATER to RecordStyle(R.drawable.ic_ms_water_drop, Color(0xFF6FB6F0), Color(0xFF13283C)),
    RecordType.FOOD to RecordStyle(R.drawable.ic_ms_restaurant, Color(0xFFFF9D57), Color(0xFF3B2112)),
    RecordType.WEIGHT to RecordStyle(R.drawable.ic_ms_monitor_weight, Color(0xFFA9CE85), Color(0xFF202D18)),
    RecordType.DEWORM to RecordStyle(R.drawable.ic_ms_pest_control, Color(0xFFC3A4FA), Color(0xFF2B2141)),
    RecordType.VACCINE to RecordStyle(R.drawable.ic_ms_vaccines, Color(0xFF5FD6BE), Color(0xFF132D28)),
    RecordType.ANOMALY to RecordStyle(R.drawable.ic_ms_warning, Color(0xFFFF9A93), Color(0xFF3D1B19)),
    RecordType.VET to RecordStyle(R.drawable.ic_ms_local_hospital, Color(0xFF9DBDEC), Color(0xFF1A2940)),
)

/** 加餐使用独立的棕金语义色（与主粮区分） */
val SnackStyle: RecordStyle
    @Composable @ReadOnlyComposable get() =
        if (LocalAppDarkTheme.current) {
            RecordStyle(R.drawable.ic_ms_cookie, Color(0xFFF2C46B), Color(0xFF35280F))
        } else {
            RecordStyle(R.drawable.ic_ms_cookie, Color(0xFF9A6B2F), Color(0xFFFBF0DE))
        }

@Composable
@ReadOnlyComposable
fun recordStyle(type: RecordType): RecordStyle =
    if (LocalAppDarkTheme.current) DarkRecordStyles.getValue(type) else LightRecordStyles.getValue(type)

/** 页面统一留白与按钮尺寸（设计规范 §2.5） */
object Dimens {
    val pagePadding = 20.dp
    val sectionGap = 12.dp
    val cardPadding = 18.dp
    val cardRadius = 20.dp
    val buttonHeight = 50.dp
    val buttonRadius = 16.dp
    val bottomButtonInset = 20.dp
    val touchTarget = 44.dp
}
