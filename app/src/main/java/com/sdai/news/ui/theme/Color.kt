package com.sdai.news.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SD AI News palette system. Three themes — AMOLED Black (default
 * battery-friendly), Cyber Blue (brand), Minimal White (daytime read).
 *
 * Screens access colours via [Sdai.<token>] which resolves to whichever
 * palette is installed via [SdaiTheme]. Switching themes triggers one
 * recomposition with the new palette — no global mutable state.
 */
data class SdaiPalette(
    val background: Color,
    val surface: Color,
    val cardInner: Color,
    val primary: Color,
    val accent: Color,
    val highlight: Color,
    val border: Color,
    val ink: Color,         // primary text colour
    val inkSubtle: Color,
    val muted: Color,
    val mutedDeep: Color,
    val success: Color,
    val danger: Color,
    val onPrimary: Color,
)

/** AMOLED Black — pure-black background, pixel-off on OLED screens. */
val AmoledPalette = SdaiPalette(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0F),
    cardInner = Color(0xFF14141C),
    primary = Color(0xFF6366F1),
    accent = Color(0xFF22D3EE),
    highlight = Color(0xFFA78BFA),
    border = Color(0xFF22222E),
    ink = Color(0xFFF5F5FA),
    inkSubtle = Color(0xFFC2C2D0),
    muted = Color(0xFF8A8AA0),
    mutedDeep = Color(0xFF5A5A6E),
    success = Color(0xFF22C55E),
    danger = Color(0xFFEF4444),
    onPrimary = Color(0xFFFFFFFF),
)

/** Cyber Blue — brand palette, dark navy with electric-blue accents. */
val CyberPalette = SdaiPalette(
    background = Color(0xFF050B1F),
    surface = Color(0xFF0A1530),
    cardInner = Color(0xFF0F1D3F),
    primary = Color(0xFF3B82F6),
    accent = Color(0xFF06B6D4),
    highlight = Color(0xFF60A5FA),
    border = Color(0xFF1E3358),
    ink = Color(0xFFE5F0FF),
    inkSubtle = Color(0xFFB8CDED),
    muted = Color(0xFF7A93B8),
    mutedDeep = Color(0xFF4A6080),
    success = Color(0xFF10B981),
    danger = Color(0xFFF87171),
    onPrimary = Color(0xFFFFFFFF),
)

/** Minimal White — daytime read, light background with soft greys. */
val MinimalPalette = SdaiPalette(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    cardInner = Color(0xFFF4F4F6),
    primary = Color(0xFF1F2937),
    accent = Color(0xFF6366F1),
    highlight = Color(0xFF4F46E5),
    border = Color(0xFFE5E7EB),
    ink = Color(0xFF111827),
    inkSubtle = Color(0xFF374151),
    muted = Color(0xFF6B7280),
    mutedDeep = Color(0xFF9CA3AF),
    success = Color(0xFF16A34A),
    danger = Color(0xFFDC2626),
    onPrimary = Color(0xFFFFFFFF),
)

val LocalSdaiPalette = staticCompositionLocalOf { AmoledPalette }

/**
 * Composable accessor. `Sdai.background` etc. resolve to the active
 * palette installed by [SdaiTheme].
 */
val Sdai: SdaiPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalSdaiPalette.current
