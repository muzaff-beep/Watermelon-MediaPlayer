package com.watermelon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Font scale tuned for Farsi/Arabic-friendly typefaces (Manifest §1.1 RTL-Native).
 * Uses the platform default family (which carries Noto Naskh / Vazir on most devices);
 * a bundled Farsi typeface can replace [FontFamily.Default] without touching call sites.
 *
 * Expanded per the UI Design System spec's "professional typography" / "engineering
 * presentation board" requirement: the previous scale only covered 5 Material3 slots
 * and had no caption, overline/badge, or tabular-numeral style — so timecodes (seek bar,
 * duration labels) shifted width per-digit and badges had no dedicated small label style.
 */
object WatermelonTypography {

    private val farsiFriendly = FontFamily.Default

    val typography = Typography(
        displayLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(
            fontFamily = farsiFriendly,
            fontSize = 16.sp,
            // Content text follows the locale direction; alignment resolves per layout.
            textDirection = TextDirection.Content,
            textAlign = TextAlign.Start
        ),
        bodyMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 14.sp),
        labelLarge = TextStyle(fontFamily = farsiFriendly, fontSize = 14.sp, fontWeight = FontWeight.Medium),

        // ── New in the expanded scale ────────────────────────────────────────
        // Caption: secondary metadata under list items (file size, folder counts).
        labelMedium = TextStyle(fontFamily = farsiFriendly, fontSize = 12.sp),

        // Overline / badge label: small caps-style tag for status badges ("NEW",
        // "4K", duration chips). Deliberately compact and letter-spaced.
        labelSmall = TextStyle(
            fontFamily = farsiFriendly,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    )

    /**
     * Timecode / numeric readout style (seek bar position, duration, sleep-timer
     * countdown). Tabular figures keep digit width constant so numbers don't visually
     * shift as they update — an "engineering board" detail the base Typography scale
     * doesn't express, since Material3's [Typography] has no numeric-only slot.
     */
    val timecode = TextStyle(
        fontFamily = farsiFriendly,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum"
    )
}