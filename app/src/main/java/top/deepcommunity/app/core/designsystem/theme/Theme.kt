package top.deepcommunity.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class ExtendedColors(
    val glassBackground: Color,
    val glassBorder: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val surfaceElevated: Color,
    val border: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        glassBackground = LightGlassBackground,
        glassBorder = LightGlassBorder,
        textSecondary = LightTextSecondary,
        textTertiary = LightTextTertiary,
        surfaceElevated = LightSurfaceElevated,
        border = LightBorder
    )
}

private val LightColorScheme = lightColorScheme(
    primary = LightTextPrimary,          // 纯黑
    onPrimary = LightBackground,         // 纯白
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTextPrimary,           // 纯白
    onPrimary = DarkBackground,          // 纯黑
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder
)

@Composable
fun DeepCommunityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        ExtendedColors(
            glassBackground = DarkGlassBackground,
            glassBorder = DarkGlassBorder,
            textSecondary = DarkTextSecondary,
            textTertiary = DarkTextTertiary,
            surfaceElevated = DarkSurfaceElevated,
            border = DarkBorder
        )
    } else {
        ExtendedColors(
            glassBackground = LightGlassBackground,
            glassBorder = LightGlassBorder,
            textSecondary = LightTextSecondary,
            textTertiary = LightTextTertiary,
            surfaceElevated = LightSurfaceElevated,
            border = LightBorder
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// 扩展快速访问
object DeepTheme {
    val extended: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
