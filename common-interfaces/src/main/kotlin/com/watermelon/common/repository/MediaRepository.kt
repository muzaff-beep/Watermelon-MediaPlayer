package com.watermelon.common.repository

import com.watermelon.common.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** Full list of all indexed media, emitted on every change. */
    fun observeAllMedia(): Flow<List<MediaItem>>

    /** Single media item by its content URI. Returns null if not indexed. */
    suspend fun getByUri(uri: String): MediaItem?

    /** Trigger a re-index. Phase-1 immediate; Phase-2 background. */
    suspend fun refreshIndex()

    /**
     * Record that playback started for [uri]. Sets [MediaItem.lastPlayedAt] to now,
     * clearing the ⭐ new-file badge. Also updates the in-memory flow immediately so the
     * UI reflects the change without waiting for the next full reloadCache.
     */
    suspend fun markAsPlayed(uri: String)
}
