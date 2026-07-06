package com.watermelon.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single spacing grid for Watermelon MediaPlayer, per the UI Design System spec's
 * "grid-based composition, perfect alignment, large amounts of whitespace."
 *
 * All layout padding, gaps, and margins should reference these tokens instead of
 * hand-picked dp values — the previous implementation used inconsistent values (2, 6,
 * 10, 12dp...) with no shared base unit across screens.
 *
 * Base unit is 4dp; every step is a multiple of it so nested layouts stay on-grid.
 */
object WatermelonSpacing {
    /** 4dp — tightest gap: icon-to-label, chip internal padding. */
    val xs: Dp = 4.dp

    /** 8dp — default gap between related controls in a row/column. */
    val sm: Dp = 8.dp

    /** 16dp — standard screen/card padding, the most common spacing value. */
    val md: Dp = 16.dp

    /** 24dp — section separation, spacing between unrelated control groups. */
    val lg: Dp = 24.dp

    /** 32dp — major layout divisions, top-level screen margins on larger surfaces. */
    val xl: Dp = 32.dp

    /** Standard hairline separator thickness (spec: "thin separators"). */
    val hairline: Dp = 1.dp
}
