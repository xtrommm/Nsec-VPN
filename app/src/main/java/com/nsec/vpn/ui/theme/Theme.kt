package com.nsec.vpn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nsec.vpn.data.model.ThemeMode

private val ProLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = PrimaryBlue,
    secondary = AccentIndigo,
    onSecondary = Color.White,
    secondaryContainer = LightCardHighlight,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightCardHighlight,
    onSurfaceVariant = TextSecondary,
    outline = LightCardBorder,
    outlineVariant = LightCardBorderHighlight,
    error = CrimsonError,
    onError = Color.White,
)

private val ProDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueContainerDark,
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = AccentIndigo,
    onSecondary = Color.White,
    secondaryContainer = DarkCardHighlight,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = EmeraldGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCardHighlight,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorder,
    outlineVariant = DarkCardBorderHighlight,
    error = CrimsonError,
    onError = Color.White,
)

// Semantic Extensions on ColorScheme for Clean Theme-Aware Styling
val ColorScheme.textPrimary: Color @Composable get() = onSurface
val ColorScheme.textSecondary: Color @Composable get() = onSurfaceVariant
val ColorScheme.textTertiary: Color @Composable get() = if (outline == DarkCardBorder) DarkTextTertiary else TextTertiary
val ColorScheme.cardBorder: Color @Composable get() = outline
val ColorScheme.cardHighlight: Color @Composable get() = surfaceVariant
val ColorScheme.cardBorderHighlight: Color @Composable get() = outlineVariant
val ColorScheme.primaryBlueContainer: Color @Composable get() = primaryContainer
val ColorScheme.emeraldBadgeContainer: Color @Composable get() = if (outline == DarkCardBorder) EmeraldContainerDark else EmeraldContainer
val ColorScheme.amberBadgeContainer: Color @Composable get() = if (outline == DarkCardBorder) AmberContainerDark else AmberContainer

@Composable
fun NsecVpnTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) ProDarkColorScheme else ProLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
