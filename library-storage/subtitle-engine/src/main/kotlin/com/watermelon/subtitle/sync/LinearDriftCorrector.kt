package com.watermelon.subtitle.sync

/**
 * Linear Drift corrector (default sync mode — Manifest §6.3). Applies a single constant
 * offset to every subtitle timestamp. The underlying .srt/.ass file is never modified; the
 * offset is stored in the SubtitleOffsets table and applied at render time.
 */
class LinearDriftCorrector {

    /** A subtitle cue with start/end in milliseconds. */
    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    /** Shift every cue by [offsetMs] (may be negative). Times are clamped at 0. */
    fun apply(cues: List<Cue>, offsetMs: Long): List<Cue> =
        cues.map { cue ->
            cue.copy(
                startMs = (cue.startMs + offsetMs).coerceAtLeast(0L),
                endMs = (cue.endMs + offsetMs).coerceAtLeast(0L)
            )
        }

    /** Shift a single timestamp (used by the render path). */
    fun applyToTimestamp(timestampMs: Long, offsetMs: Long): Long =
        (timestampMs + offsetMs).coerceAtLeast(0L)
}
