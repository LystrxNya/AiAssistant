package com.aiassistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceLight,
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = TextPrimary,
    secondary = SuccessGreen,
    onSecondary = SurfaceLight,
    secondaryContainer = SuccessGreenLight,
    onSecondaryContainer = TextPrimary,
    tertiary = WarningOrange,
    onTertiary = SurfaceLight,
    error = ErrorRed,
    onError = SurfaceLight,
    errorContainer = ErrorRedLight,
    onErrorContainer = TextPrimary,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = AiBubbleLight,
    onSurfaceVariant = TextSecondary,
    outline = DividerLight,
    outlineVariant = DividerLight,
    inverseSurface = TextPrimary,
    inverseOnSurface = BackgroundLight,
    inversePrimary = PrimaryBlueLight,
    surfaceTint = PrimaryBlue
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryBlueDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = SuccessGreenLight,
    onSecondary = TextPrimary,
    secondaryContainer = SuccessGreen,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = WarningOrange,
    onTertiary = TextPrimary,
    error = ErrorRedLight,
    onError = TextPrimary,
    errorContainer = ErrorRed,
    onErrorContainer = TextPrimaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = AiBubbleDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    outlineVariant = DividerDark,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    inversePrimary = PrimaryBlue,
    surfaceTint = PrimaryBlueLight
)

@Composable
fun AiAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
