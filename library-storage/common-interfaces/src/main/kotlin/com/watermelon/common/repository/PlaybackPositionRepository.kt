package com.watermelon.common.repository

/**
 * Persists per-video resume positions to the `PlaybackPositions` table (schema frozen since
 * v1 — see Manifest §10.1). Identity is [uri] + [fileSize], matching the same stable key used
 * for [com.watermelon.common.model.MediaItem] and subtitle offsets under scoped storage.
 */
interface PlaybackPositionRepository {
    /** Saves (or overwrites) the resume position for a video. Cheap — safe to call often. */
    suspend fun savePosition(uri: String, fileSize: Long, positionMs: Long)

    /** Returns the last saved resume position for a video, or null if none is stored. */
    suspend fun getPosition(uri: String, fileSize: Long): Long?

    /** Clears the saved position — e.g. once a video has finished playing to the end. */
    suspend fun clearPosition(uri: String, fileSize: Long)
}
