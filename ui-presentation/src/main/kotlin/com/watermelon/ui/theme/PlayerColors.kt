package com.watermelon.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the player's visual palette (watermelon brand).
 *
 * MODULARITY: components must reference the *semantic roles* below (e.g. [seekBarFill],
 * [iconActive]) — never the raw [Palette] hex values directly. To retune the player's look,
 * edit the role mappings here in one place; every control, custom seekbar, level indicator,
 * sheet, and icon updates automatically.
 */
object PlayerColors {

    /** Raw brand palette. Do not reference these directly from components. */
    object Palette {
        val PrimaryRed   = Color(0xFFCE1126)  // watermelon flesh
        val PrimaryGreen = Color(0xFF007A3D)  // watermelon rind
        val Background   = Color(0xFF000000)  // main dark background
        val White        = Color(0xFFFFFFFF)  // text, icons, light elements
        val DarkSurface  = Color(0xFF1A1A1A)  // cards, sheets, elevated surfaces
    }

    // ── Semantic roles (reference THESE from components) ────────────────────────

    // Backgrounds / surfaces
    val background       = Palette.Background
    val sheetBackground  = Palette.DarkSurface
    val controlBarScrim  = Palette.Background       // used with alpha as a gradient

    // Seekbar
    val seekBarFill      = Palette.PrimaryRed
    val seekBarBuffered  = Palette.White.copy(alpha = 0.30f)
    val seekBarTrack     = Palette.White.copy(alpha = 0.18f)
    val seekBarThumb     = Palette.PrimaryRed
    val seekBarThumbRing = Palette.White

    // Icons / buttons
    val iconActive       = Palette.PrimaryRed
    val iconDefault      = Palette.White
    val iconInactive     = Palette.White.copy(alpha = 0.55f)
    val iconFocus        = Palette.PrimaryGreen     // D-pad focus (TV) / pressed accent

    // Level indicators (brightness / volume)
    val levelFill        = Palette.PrimaryRed
    val levelTrack       = Palette.White.copy(alpha = 0.20f)
    val levelIcon        = Palette.White

    // Text
    val textPrimary      = Palette.White
    val textSecondary    = Palette.White.copy(alpha = 0.70f)

    // Accents
    val accent           = Palette.PrimaryRed
    val accentSecondary  = Palette.PrimaryGreen
}
