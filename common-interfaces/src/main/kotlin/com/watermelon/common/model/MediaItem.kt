package com.watermelon.common.model

/**
 * A single indexed media file. Identity for resume/subtitles is [uri] + [fileSize]
 * (stable under scoped storage).
 *
 * [firstSeenAt] — epoch-ms when the URI was first written to the MediaItems table.
 *                 Set on INSERT, never overwritten. 0 = pre-feature rows.
 * [lastPlayedAt] — epoch-ms when playback last started. null = never played (⭐ new).
 */
data class MediaItem(
    val uri: String,
    val fileSize: Long,
    val displayName: String,
    val parentFolder: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val firstSeenAt: Long = 0L,
    val lastPlayedAt: Long? = null
)
