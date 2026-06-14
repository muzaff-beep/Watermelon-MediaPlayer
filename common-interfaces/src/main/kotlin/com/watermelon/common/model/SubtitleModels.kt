package com.watermelon.common.model

/** Subtitle container formats the engine understands. */
enum class SubtitleFormat { SRT, ASS, VTT, UNKNOWN }

/**
 * A single timed subtitle line. [rawText] is the original parsed text; [displayText] is the
 * bidi-formatted version safe to hand to the text layout engine. [baseRtl] records the
 * resolved base direction so the renderer aligns the cue correctly.
 */
data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val rawText: String,
    val baseRtl: Boolean = false,
    val displayText: String = rawText
)

/**
 * Describes a video for subtitle lookup. [displayName] and [parentFolder] drive local
 * sidecar discovery (S1); the remaining fields are used by online sources (S3).
 */
data class VideoQuery(
    val displayName: String,
    val parentFolder: String,
    val sizeBytes: Long = 0L,
    val osHash: String? = null,
    val durationMs: Long = 0L,
    val languages: List<String> = listOf("fa", "ar", "ur", "ku", "en")
)
