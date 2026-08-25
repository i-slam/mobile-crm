package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ProfessionalPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = ProfessionalPrimaryContainerDark,
    onPrimaryContainer = ProfessionalOnPrimaryContainerDark,
    secondary = ProfessionalSecondaryDark,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = ProfessionalSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = ProfessionalTertiaryDark,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = ProfessionalTertiaryContainerDark,
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = PolishBackgroundDark,
    onBackground = PolishOnBackgroundDark,
    surface = PolishSurfaceDark,
    onSurface = PolishOnSurfaceDark,
    surfaceVariant = PolishSurfaceVariantDark,
    onSurfaceVariant = PolishOnSurfaceVariantDark,
    outline = PolishOutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = ProfessionalPrimary,
    onPrimary = Color.White,
    primaryContainer = ProfessionalPrimaryContainer,
    onPrimaryContainer = ProfessionalOnPrimaryContainer,
    secondary = ProfessionalSecondary,
    onSecondary = Color.White,
    secondaryContainer = ProfessionalSecondaryContainer,
    onSecondaryContainer = ProfessionalOnSecondaryContainer,
    tertiary = ProfessionalTertiary,
    onTertiary = Color.White,
    tertiaryContainer = ProfessionalTertiaryContainer,
    onTertiaryContainer = ProfessionalOnTertiaryContainer,
    background = PolishBackgroundLight,
    onBackground = PolishOnBackgroundLight,
    surface = PolishSurfaceLight,
    onSurface = PolishOnSurfaceLight,
    surfaceVariant = PolishSurfaceVariantLight,
    onSurfaceVariant = PolishOnSurfaceVariantLight,
    outline = PolishOutlineLight,
    outlineVariant = PolishOutlineVariantLight,
    error = StatusError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
