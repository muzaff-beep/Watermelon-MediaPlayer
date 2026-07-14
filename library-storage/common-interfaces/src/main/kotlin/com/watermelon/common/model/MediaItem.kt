package com.watermelon.common.model

/**
 * A single indexed media file. Identity for resume/subtitles is [uri] + [fileSize]
 * (stable under scoped storage).
 *
 * [firstSeenAt] — epoch-ms when the URI was first written to the MediaItems table.
 *                 Set on INSERT, never overwritten. 0 = pre-feature rows.
 * [lastPlayedAt] — epoch-ms when playback last started. null = never played (⭐ new).
 * [dateAdded]    — epoch-ms from MediaStore DATE_ADDED. Used for Date sort and
 *                  Recently Added (last 7 days). 0 = unknown.
 * [fileExtension]— lower-case extension without dot (mp4, mkv, avi). Used for File Type sort.
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
    val lastPlayedAt: Long? = null,
    val dateAdded: Long = 0L,
    val fileExtension: String = ""
) {
    /** Total pixel count, used for Quality sort (higher = better quality). */
    val pixelCount: Long get() = width.toLong() * height.toLong()
}
