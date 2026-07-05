package com.watermelon.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the player's visual palette — now the same WVGC brand
 * palette used by [WatermelonColors] / [WatermelonTheme], in both a dark and a light
 * [Scheme]. The player screens draw with plain Canvas calls and don't go through
 * Material3's colorScheme, so they read [PlayerColors.current] instead, which resolves
 * against [LocalWatermelonDarkTheme] — the same flag the Settings dark/light toggle sets.
 *
 * MODULARITY: components must reference the *semantic roles* on [current] (e.g.
 * [Scheme.seekBarFill], [Scheme.iconActive]) — never [Palette] hex values directly.
 */
object PlayerColors {

    /** Raw WVGC brand palette. Do not reference these directly from components. */
    object Palette {
        val WatermelonRed = WatermelonColors.Palette.WatermelonRed
        val FreshTeal      = WatermelonColors.Palette.FreshTeal
        val DeepNavy       = WatermelonColors.Palette.DeepNavy
        val Charcoal       = WatermelonColors.Palette.Charcoal
        val SlateGray      = WatermelonColors.Palette.SlateGray
        val OffWhite       = WatermelonColors.Palette.OffWhite
        val DeepCharcoal   = WatermelonColors.Palette.DeepCharcoal
    }

    /** One full set of semantic player tokens. */
    data class Scheme(
        val background: Color,
        val sheetBackground: Color,
        val controlBarScrim: Color,
        val seekBarFill: Color,
        val seekBarBuffered: Color,
        val seekBarTrack: Color,
        val seekBarThumb: Color,
        val seekBarThumbRing: Color,
        val iconActive: Color,
        val iconDefault: Color,
        val iconInactive: Color,
        val iconFocus: Color,
        val levelFill: Color,
        val levelTrack: Color,
        val levelIcon: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val accent: Color,
        val accentSecondary: Color
    )

    val Dark = Scheme(
        background       = Palette.Charcoal,
        sheetBackground  = Palette.DeepCharcoal,
        controlBarScrim  = Palette.Charcoal,
        seekBarFill      = Palette.WatermelonRed,
        seekBarBuffered  = Palette.OffWhite.copy(alpha = 0.30f),
        seekBarTrack     = Palette.OffWhite.copy(alpha = 0.18f),
        seekBarThumb     = Palette.WatermelonRed,
        seekBarThumbRing = Palette.OffWhite,
        iconActive       = Palette.WatermelonRed,
        iconDefault      = Palette.OffWhite,
        iconInactive     = Palette.OffWhite.copy(alpha = 0.55f),
        iconFocus        = Palette.FreshTeal,
        levelFill        = Palette.WatermelonRed,
        levelTrack       = Palette.OffWhite.copy(alpha = 0.20f),
        levelIcon        = Palette.OffWhite,
        textPrimary      = Palette.OffWhite,
        textSecondary    = Palette.OffWhite.copy(alpha = 0.70f),
        accent           = Palette.WatermelonRed,
        accentSecondary  = Palette.FreshTeal
    )

    val Light = Scheme(
        background       = Palette.OffWhite,
        sheetBackground  = Color(0xFFFFFFFF),
        controlBarScrim  = Palette.OffWhite,
        seekBarFill      = Palette.WatermelonRed,
        seekBarBuffered  = Palette.DeepNavy.copy(alpha = 0.25f),
        seekBarTrack     = Palette.SlateGray.copy(alpha = 0.25f),
        seekBarThumb     = Palette.WatermelonRed,
        seekBarThumbRing = Color(0xFFFFFFFF),
        iconActive       = Palette.WatermelonRed,
        iconDefault      = Palette.DeepNavy,
        iconInactive     = Palette.SlateGray,
        iconFocus        = Palette.FreshTeal,
        levelFill        = Palette.WatermelonRed,
        levelTrack       = Palette.SlateGray.copy(alpha = 0.25f),
        levelIcon        = Palette.DeepNavy,
        textPrimary      = Palette.Charcoal,
        textSecondary    = Palette.SlateGray,
        accent           = Palette.WatermelonRed,
        accentSecondary  = Palette.FreshTeal
    )

    /** Resolves to [Dark] or [Light] based on the active theme (Settings → Pure dark theme). */
    val current: Scheme
        @Composable get() = if (LocalWatermelonDarkTheme.current) Dark else Light
}
