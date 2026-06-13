package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AzurePrimaryDark,
    secondary = AzureSecondaryDark,
    tertiary = AzureTertiaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = BackgroundDark,
    onSecondary = BackgroundDark,
    onTertiary = SurfaceDark,
    onBackground = BackgroundLight,
    onSurface = BackgroundLight,
    onSurfaceVariant = BackgroundLight
)

private val LightColorScheme = lightColorScheme(
    primary = AzurePrimaryLight,
    secondary = AzureSecondaryLight,
    tertiary = AzureTertiaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onPrimary = SurfaceLight,
    onSecondary = SurfaceLight,
    onTertiary = SurfaceLight,
    onBackground = BackgroundDark,
    onSurface = BackgroundDark,
    onSurfaceVariant = BackgroundDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
