package com.watermelon.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Watermelon MediaPlayer brand palette — single source of truth for both dark and light
 * schemes. Raw hex values live ONLY here; [WatermelonColors] exposes them as dark/light
 * semantic roles consumed by [WatermelonTheme] (Material3 colorScheme) and by
 * [PlayerColors] (player-specific tokens), so retuning the brand means editing this one
 * object.
 *
 * Palette per the Watermelon MediaPlayer UI Design System spec: OLED-first dark interface,
 * Swiss-inspired minimalism, Sony Walkman / Nakamichi deck industrial influence.
 */
object WatermelonColors {

    /** Raw brand swatches. Do not reference these directly from components. */
    object Palette {
        val WatermelonRed  = Color(0xFFE63946)  // Primary — brand accent
        val DeepCarbon      = Color(0xFF0D0D0D)  // Surface — near-black OLED background
        val SlateGray       = Color(0xFF1A1A1A)  // Elevated — subtle elevation step
        val PaperWhite      = Color(0xFFF1FAEE)  // Text — primary on-dark text
        val SoftTeal        = Color(0xFF457B9D)  // Secondary — cool accent
        val WarningYellow   = Color(0xFFF4A261)  // Accent — warnings, buffering, badges
    }

    // ── Dark scheme (default / OLED-first) ──────────────────────────────────
    val DarkBackground       = Palette.DeepCarbon
    val DarkSurface          = Palette.SlateGray
    val DarkSurfaceVariant   = Palette.SlateGray
    val DarkOnBackground     = Palette.PaperWhite
    val DarkOnSurface        = Palette.PaperWhite
    val DarkOnSurfaceVariant = Palette.PaperWhite.copy(alpha = 0.70f)
    val DarkOutline          = Palette.PaperWhite.copy(alpha = 0.18f)

    // ── Light scheme ─────────────────────────────────────────────────────────
    // Watermelon is dark-mode-first; the light scheme is a secondary, less-emphasized
    // mode for users who explicitly opt out of "Pure dark theme" in Settings.
    val LightBackground       = Palette.PaperWhite
    val LightSurface          = Color(0xFFFFFFFF)
    val LightSurfaceVariant   = Palette.SlateGray.copy(alpha = 0.08f)
    val LightOnBackground     = Palette.DeepCarbon
    val LightOnSurface        = Palette.DeepCarbon
    val LightOnSurfaceVariant = Palette.DeepCarbon.copy(alpha = 0.65f)
    val LightOutline          = Palette.DeepCarbon.copy(alpha = 0.20f)

    // ── Shared accents (same in both modes) ─────────────────────────────────
    val Accent        = Palette.WatermelonRed   // primary
    val AccentVariant = Palette.SoftTeal         // secondary
    val Warning       = Palette.WarningYellow    // warnings, buffering, "new" badges
    val OnAccent      = Palette.PaperWhite
    val Error         = Palette.WatermelonRed
}
