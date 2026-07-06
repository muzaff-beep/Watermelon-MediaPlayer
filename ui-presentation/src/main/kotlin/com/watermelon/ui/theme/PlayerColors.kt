package com.watermelon.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the player's visual palette — the same Watermelon
 * MediaPlayer brand palette used by [WatermelonColors] / [WatermelonTheme], in both a
 * dark and a light [Scheme]. The player screens draw with plain Canvas calls and don't
 * go through Material3's colorScheme, so they read [PlayerColors.current] instead, which
 * resolves against [LocalWatermelonDarkTheme] — the same flag the Settings dark/light
 * toggle sets.
 *
 * MODULARITY: components must reference the *semantic roles* on [current] (e.g.
 * [Scheme.seekBarFill], [Scheme.iconActive]) — never [Palette] hex values directly.
 */
object PlayerColors {

    /** Raw Watermelon brand palette. Do not reference these directly from components. */
    object Palette {
        val WatermelonRed  = WatermelonColors.Palette.WatermelonRed
        val SoftTeal        = WatermelonColors.Palette.SoftTeal
        val WarningYellow   = WatermelonColors.Palette.WarningYellow
        val DeepCarbon      = WatermelonColors.Palette.DeepCarbon
        val SlateGray       = WatermelonColors.Palette.SlateGray
        val PaperWhite      = WatermelonColors.Palette.PaperWhite
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
        val accentSecondary: Color,
        /** Buffering / warning / transient-caution states — never used for hard errors. */
        val warning: Color
    )

    val Dark = Scheme(
        background       = Palette.DeepCarbon,
        sheetBackground  = Palette.SlateGray,
        controlBarScrim  = Palette.DeepCarbon,
        seekBarFill      = Palette.WatermelonRed,
        seekBarBuffered  = Palette.PaperWhite.copy(alpha = 0.30f),
        seekBarTrack     = Palette.PaperWhite.copy(alpha = 0.18f),
        seekBarThumb     = Palette.WatermelonRed,
        seekBarThumbRing = Palette.PaperWhite,
        iconActive       = Palette.WatermelonRed,
        iconDefault      = Palette.PaperWhite,
        iconInactive     = Palette.PaperWhite.copy(alpha = 0.55f),
        iconFocus        = Palette.SoftTeal,
        levelFill        = Palette.WatermelonRed,
        levelTrack       = Palette.PaperWhite.copy(alpha = 0.20f),
        levelIcon        = Palette.PaperWhite,
        textPrimary      = Palette.PaperWhite,
        textSecondary    = Palette.PaperWhite.copy(alpha = 0.70f),
        accent           = Palette.WatermelonRed,
        accentSecondary  = Palette.SoftTeal,
        warning          = Palette.WarningYellow
    )

    val Light = Scheme(
        background       = Palette.PaperWhite,
        sheetBackground  = Color(0xFFFFFFFF),
        controlBarScrim  = Palette.PaperWhite,
        seekBarFill      = Palette.WatermelonRed,
        seekBarBuffered  = Palette.DeepCarbon.copy(alpha = 0.25f),
        seekBarTrack     = Palette.SlateGray.copy(alpha = 0.25f),
        seekBarThumb     = Palette.WatermelonRed,
        seekBarThumbRing = Color(0xFFFFFFFF),
        iconActive       = Palette.WatermelonRed,
        iconDefault      = Palette.DeepCarbon,
        iconInactive     = Palette.SlateGray,
        iconFocus        = Palette.SoftTeal,
        levelFill        = Palette.WatermelonRed,
        levelTrack       = Palette.SlateGray.copy(alpha = 0.25f),
        levelIcon        = Palette.DeepCarbon,
        textPrimary      = Palette.DeepCarbon,
        textSecondary    = Palette.SlateGray,
        accent           = Palette.WatermelonRed,
        accentSecondary  = Palette.SoftTeal,
        warning          = Palette.WarningYellow
    )

    /** Resolves to [Dark] or [Light] based on the active theme (Settings → Pure dark theme). */
    val current: Scheme
        @Composable get() = if (LocalWatermelonDarkTheme.current) Dark else Light
}
