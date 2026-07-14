package com.watermelon.storage.repository

import android.content.ContentValues
import com.watermelon.common.model.MediaItem
import com.watermelon.common.repository.MediaRepository
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.indexer.MediaStoreIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [MediaRepository] backed by the SQLite MediaItems table (Phase-2 cache).
 * [markAsPlayed] updates [MediaItem.lastPlayedAt] and immediately reflects the change
 * in the in-memory flow so the ⭐ badge clears without waiting for a full reloadCache.
 */
class MediaRepositoryImpl(
    private val database: WatermelonDatabase,
    private val indexer: MediaStoreIndexer
) : MediaRepository {

    private val mediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())

    override fun observeAllMedia(): Flow<List<MediaItem>> = mediaFlow.asStateFlow()

    override suspend fun getByUri(uri: String): MediaItem? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            "MediaItems", null, "mediaId = ?", arrayOf(uri), null, null, null
        ).use { c -> if (c.moveToFirst()) c.toMediaItem() else null }
    }

    override suspend fun refreshIndex() {
        com.watermelon.common.util.FileLogger.i("Media", "refreshIndex — starting indexer")
        indexer.refresh(force = true)
        com.watermelon.common.util.FileLogger.i("Media", "refreshIndex — indexer done, reloading cache")
        reloadCache()
    }

    override suspend fun markAsPlayed(uri: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply { put("lastPlayedAt", now) }
        database.writableDatabase.update("MediaItems", values, "mediaId = ?", arrayOf(uri))
        // Update in-memory flow immediately — clears ⭐ without waiting for a full reload.
        mediaFlow.value = mediaFlow.value.map {
            if (it.uri == uri) it.copy(lastPlayedAt = now) else it
        }
    }

    private suspend fun reloadCache() = withContext(Dispatchers.IO) {
        val items = ArrayList<MediaItem>()
        database.readableDatabase.query(
            "MediaItems", null, null, null, null, null, "displayName ASC"
        ).use { c -> while (c.moveToNext()) items += c.toMediaItem() }
        com.watermelon.common.util.FileLogger.i("Media", "reloadCache — loaded ${items.size} items from DB")
        mediaFlow.value = items
    }

    private fun android.database.Cursor.toMediaItem(): MediaItem {
        val lastPlayedIdx = getColumnIndex("lastPlayedAt")
        val dateAddedIdx  = getColumnIndex("dateAdded")
        val name          = getString(getColumnIndexOrThrow("displayName"))
        return MediaItem(
            uri          = getString(getColumnIndexOrThrow("mediaId")),
            fileSize     = getLong(getColumnIndexOrThrow("fileSize")),
            displayName  = name,
            parentFolder = getString(getColumnIndexOrThrow("parentFolder")),
            durationMs   = getLong(getColumnIndexOrThrow("durationMs")),
            width        = getInt(getColumnIndexOrThrow("width")),
            height       = getInt(getColumnIndexOrThrow("height")),
            mimeType     = getString(getColumnIndexOrThrow("mimeType")) ?: "",
            firstSeenAt  = getLong(getColumnIndexOrThrow("firstSeenAt")),
            lastPlayedAt = if (lastPlayedIdx >= 0 && !isNull(lastPlayedIdx))
                               getLong(lastPlayedIdx) else null,
            dateAdded    = if (dateAddedIdx >= 0) getLong(dateAddedIdx) else 0L,
            fileExtension = name.substringAfterLast('.', "").lowercase()
        )
    }
}
