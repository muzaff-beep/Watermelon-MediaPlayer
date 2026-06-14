package com.watermelon.common.model

/**
 * A fully-parsed, render-ready subtitle. Distinct from [SubtitleTrack] which is a
 * search/download candidate. ParsedSubtitle holds timed cues that have been bidi-formatted
 * and are ready to display in the player.
 *
 * [offsetMs] is a global timing shift (positive = later, negative = earlier).
 */
data class ParsedSubtitle(
    val cues: List<SubtitleCue>,
    val language: String? = null,
    val sourceId: String = "local",
    val offsetMs: Long = 0L
) {
    /** The cue active at [positionMs] after applying [offsetMs], or null between cues. */
    fun cueAt(positionMs: Long): SubtitleCue? {
        val t = positionMs - offsetMs
        return cues.firstOrNull { t in it.startMs..it.endMs }
    }
}
