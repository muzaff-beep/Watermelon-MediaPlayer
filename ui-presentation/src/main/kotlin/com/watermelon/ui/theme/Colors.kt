package com.watermelon.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * WVGC brand palette — single source of truth for both dark and light schemes.
 * Raw hex values live ONLY here; [WatermelonColors] exposes them as dark/light
 * semantic roles consumed by [WatermelonTheme] (Material3 colorScheme) and by
 * [PlayerColors] (player-specific tokens), so retuning the brand means editing
 * this one object.
 */
object WatermelonColors {

    /** Raw WVGC swatches. Do not reference these directly from components. */
    object Palette {
        val WatermelonRed  = Color(0xFFE63946)  // brand accent — vibrant red
        val FreshTeal       = Color(0xFF2A9D8F)  // secondary accent — backgrounds
        val DeepNavy        = Color(0xFF1D3557)  // dark blue
        val Charcoal        = Color(0xFF1A1A2E)  // deep black
        val SlateGray       = Color(0xFF4A5568)  // midtones
        val OffWhite        = Color(0xFFF8F9FA)  // highlights
        val DeepCharcoal    = Color(0xFF0F172A)  // deep shadows
    }

    // ── Dark scheme ──────────────────────────────────────────────────────────
    val DarkBackground       = Palette.Charcoal
    val DarkSurface          = Palette.DeepCharcoal
    val DarkSurfaceVariant   = Palette.SlateGray
    val DarkOnBackground     = Palette.OffWhite
    val DarkOnSurface        = Palette.OffWhite
    val DarkOnSurfaceVariant = Palette.OffWhite.copy(alpha = 0.70f)
    val DarkOutline          = Palette.SlateGray

    // ── Light scheme ─────────────────────────────────────────────────────────
    val LightBackground       = Palette.OffWhite
    val LightSurface          = Color(0xFFFFFFFF)
    val LightSurfaceVariant   = Palette.SlateGray.copy(alpha = 0.12f)
    val LightOnBackground     = Palette.Charcoal
    val LightOnSurface        = Palette.DeepNavy
    val LightOnSurfaceVariant = Palette.SlateGray
    val LightOutline          = Palette.SlateGray.copy(alpha = 0.40f)

    // ── Shared accents (same in both modes) ─────────────────────────────────
    val Accent        = Palette.WatermelonRed   // primary
    val AccentVariant = Palette.FreshTeal        // secondary
    val OnAccent      = Palette.OffWhite
    val Error         = Palette.WatermelonRed
}
