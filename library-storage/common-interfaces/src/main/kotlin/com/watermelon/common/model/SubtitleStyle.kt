package com.watermelon.common.model

/** Per-cue direction handling. AUTO = detect from dominant script (BidiFormatter). */
enum class SubtitleDirection { AUTO, FORCE_RTL, FORCE_LTR }

/** Vertical anchor for the subtitle block. */
enum class SubtitlePosition { BOTTOM, TOP }

/**
 * Subtitle rendering style. ONE shared style for both tracks (per user decision);
 * only [direction] (and later font) may differ per track for mixed-language dual subs.
 *
 * [sizeSp] is free numerical (12..48). [textColorArgb] full ARGB. Effects (stroke/shadow/
 * shade/frame) arrive in ST2 as additional stackable fields on this model.
 */
data class SubtitleStyle(
    val enabled: Boolean = true,
    val sizeSp: Int = 18,
    val textColorArgb: Long = 0xFFFFFFFF,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val direction: SubtitleDirection = SubtitleDirection.AUTO,
    /** Direction override for the secondary track (dual subs), independent of primary. */
    val secondaryDirection: SubtitleDirection = SubtitleDirection.AUTO
)