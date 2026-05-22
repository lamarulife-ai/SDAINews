package com.sdai.news.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { AMOLED, CYBER, MINIMAL }

@Composable
fun SdaiTheme(
    mode: ThemeMode = ThemeMode.AMOLED,
    content: @Composable () -> Unit,
) {
    val palette = when (mode) {
        ThemeMode.AMOLED -> AmoledPalette
        ThemeMode.CYBER -> CyberPalette
        ThemeMode.MINIMAL -> MinimalPalette
    }
    // We ride on top of Material3's ColorScheme for component defaults
    // (Switches, Sliders, Cards, etc.) but the live theme tokens that
    // our screens read come from [Sdai] / [LocalSdaiPalette].
    val colorScheme = if (mode == ThemeMode.MINIMAL) {
        lightColorScheme(
            background = palette.background,
            surface = palette.surface,
            primary = palette.primary,
            onPrimary = palette.onPrimary,
        )
    } else {
        darkColorScheme(
            background = palette.background,
            surface = palette.surface,
            primary = palette.primary,
            onPrimary = palette.onPrimary,
        )
    }
    CompositionLocalProvider(LocalSdaiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SdaiTypography,
            content = content,
        )
    }
}
