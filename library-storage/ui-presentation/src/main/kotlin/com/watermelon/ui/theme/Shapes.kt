package com.watermelon.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single shape scale for Watermelon MediaPlayer.
 *
 * Per the UI Design System spec: "Sharp corners with only slightly rounded cards
 * (10–14px)." Components should reference these tokens instead of hardcoding
 * `RoundedCornerShape(n.dp)` with ad hoc values — the previous implementation had six
 * different radii (6/8/12/20/28dp) scattered across files with no shared scale.
 *
 * Radii are intentionally small and few: this is an engineered, industrial aesthetic
 * (Nakamichi / Walkman deck influence), not a soft consumer-app one. When in doubt,
 * prefer [sharp] or [control] over [card].
 */
object WatermelonShapes {

    /** Raw dp radii, for call sites that need an asymmetric or single-edge corner
     *  (e.g. a panel rounding only its leading corners) and so must build their own
     *  [RoundedCornerShape] — reference these instead of a new hardcoded value. */
    object Radius {
        val sharp: Dp = 0.dp
        val small: Dp = 4.dp
        val control: Dp = 10.dp
        val card: Dp = 14.dp
    }

    /** True sharp corner. Use for scrims, dividers, seek-bar track fills, tick marks. */
    val sharp = RoundedCornerShape(Radius.sharp)

    /** Smallest rounding — chips, small badges, inline pills, thumbnails. */
    val small = RoundedCornerShape(Radius.small)

    /** Standard control radius — buttons, icon buttons, control-panel groups. Spec floor. */
    val control = RoundedCornerShape(Radius.control)

    /** Standard card radius — playlist cards, folder rows, list items. Spec ceiling. */
    val card = RoundedCornerShape(Radius.card)

    /**
     * Sheets and dialogs that need a slightly larger single-edge radius (e.g. a
     * bottom sheet's top corners, or a side panel's leading edge). Kept as one named
     * exception rather than an unbounded scale — do not introduce further one-offs.
     */
    val sheet = RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card)
}
