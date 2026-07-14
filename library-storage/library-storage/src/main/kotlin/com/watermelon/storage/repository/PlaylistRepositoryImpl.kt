package com.watermelon.storage.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.common.model.SystemPlaylist
import com.watermelon.common.repository.FolderVisibilityStore
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Playlist repository backed by SQLite.
 *
 * Tables used:
 *   Playlists          (id TEXT PK, name TEXT, type TEXT, createdAt INTEGER)
 *   PlaylistItems      (playlistId TEXT, uri TEXT, addedAt INTEGER)
 *   Favourites         (uri TEXT PK, addedAt INTEGER)
 *   PlaybackPositions  (mediaId TEXT, fileSize INTEGER, positionMs INTEGER, updatedAt INTEGER)
 *
 * Recently Added is computed dynamically from MediaItems.firstSeenAt (last 7 days).
 * Continue Watching is computed dynamically from PlaybackPositions — any video with a
 * saved resume position (written by PlaybackControllerImpl during playback) shows up here,
 * most-recently-watched first, until it either finishes (position cleared) or is removed.
 * These tables are created via MigrationV7ToV8 if not already present.
 *
 * Folder visibility: videos in a folder the user has hidden (via [FolderVisibilityStore],
 * the same mechanism the plain folder-tree browser uses) are excluded from every computed
 * system playlist (Recently Added, Continue Watching, Favourites) as well as from user
 * playlists, since none of those should surface content the user asked to hide.
 */
class PlaylistRepositoryImpl(
    private val db: WatermelonDatabase,
    private val mediaRepository: MediaRepository,
    private val folderVisibilityStore: FolderVisibilityStore
) : PlaylistRepository {

    private val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

    /** All media, minus anything living in a folder the user has hidden. */
    private fun visibleMedia(all: List<MediaItem>): List<MediaItem> =
        all.filter { folderVisibilityStore.isFolderVisible(it.parentFolder) }

    override fun observeAll(): Flow<List<Playlist>> = combine(
        mediaRepository.observeAllMedia(),
        observeUserPlaylistRows(),
        folderVisibilityStore.visibilityVersion
    ) { allMediaUnfiltered, userPlaylistRows, _ ->
        val allMedia = visibleMedia(allMediaUnfiltered)
        val now = System.currentTimeMillis()
        val recentItems = allMedia.filter {
            val ts = if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt
            ts >= now - sevenDaysMs
        }
        val favUris = getFavouriteUris()
        val favItems = allMedia.filter { it.uri in favUris }
        val positionsByKey = getContinueWatchingPositions()
        val continueWatchingItems =
            allMedia.filter { positionsByKey.containsKey(it.uri to it.fileSize) }

        val recentlyAdded = Playlist(
            id              = SystemPlaylist.ID_RECENTLY_ADDED,
            name            = "Recently Added · Last 7 Days",
            type            = PlaylistType.RECENTLY_ADDED,
            itemCount       = recentItems.size,
            totalDurationMs = recentItems.sumOf { it.durationMs }
        )
        val favourites = Playlist(
            id              = SystemPlaylist.ID_FAVOURITES,
            name            = "Favourites",
            type            = PlaylistType.FAVOURITES,
            itemCount       = favItems.size,
            totalDurationMs = favItems.sumOf { it.durationMs }
        )
        val continueWatching = Playlist(
            id              = SystemPlaylist.ID_CONTINUE_WATCHING,
            name            = "Continue Watching",
            type            = PlaylistType.CONTINUE_WATCHING,
            itemCount       = continueWatchingItems.size,
            totalDurationMs = continueWatchingItems.sumOf { it.durationMs }
        )
        // itemCount/totalDurationMs derived from the same visibility-filtered allMedia list as
        // the system playlists above, rather than a raw COUNT(*) against PlaylistItems — so a
        // user playlist containing a since-deleted file or a file in a now-hidden folder no
        // longer inflates its displayed count.
        val userPlaylists = userPlaylistRows.map { row ->
            val items = allMedia.filter { it.uri in row.uris }
            Playlist(
                id              = row.id,
                name            = row.name,
                type            = PlaylistType.USER,
                itemCount       = items.size,
                totalDurationMs = items.sumOf { it.durationMs },
                createdAt       = row.createdAt
            )
        }
        listOf(continueWatching, recentlyAdded, favourites) + userPlaylists
    }.flowOn(Dispatchers.IO)

    override fun observeVideos(playlistId: String): Flow<List<MediaItem>> = combine(
        mediaRepository.observeAllMedia(),
        folderVisibilityStore.visibilityVersion
    ) { allUnfiltered, _ ->
        val all = visibleMedia(allUnfiltered)
        com.watermelon.common.util.FileLogger.i("Playlist",
            "observeVideos($playlistId) — total media in library: ${allUnfiltered.size}, " +
            "visible (folder-enabled): ${all.size}")
        val result = when (playlistId) {
            SystemPlaylist.ID_RECENTLY_ADDED -> {
                val cutoff = System.currentTimeMillis() - sevenDaysMs
                val filtered = all.filter {
                    val ts = if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt
                    ts >= cutoff
                }.sortedByDescending {
                    if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt
                }
                com.watermelon.common.util.FileLogger.i("Playlist",
                    "RecentlyAdded — ${filtered.size} videos within 7 days (cutoff=$cutoff)")
                filtered
            }
            SystemPlaylist.ID_FAVOURITES -> {
                val favUris = getFavouriteUris()
                val inFav = all.filter { it.uri in favUris }
                com.watermelon.common.util.FileLogger.i("Playlist",
                    "Favourites — favUris=${favUris.size}, matched videos=${inFav.size}")
                applyCustomOrder(playlistId, inFav)
            }
            SystemPlaylist.ID_CONTINUE_WATCHING -> {
                // Keyed by (uri, fileSize) — same stable identity used for MediaItem and
                // SubtitleOffsets — so a replaced file at the same path doesn't wrongly
                // inherit a stale resume position.
                val positionsByKey = getContinueWatchingPositions()
                val inProgress = all
                    .filter { positionsByKey.containsKey(it.uri to it.fileSize) }
                    .sortedByDescending { positionsByKey.getValue(it.uri to it.fileSize) }
                com.watermelon.common.util.FileLogger.i("Playlist",
                    "ContinueWatching — ${positionsByKey.size} saved position(s), " +
                    "${inProgress.size} matched to current library")
                inProgress
            }
            else -> {
                val uris = getPlaylistItemUris(playlistId)
                val uriSet = uris.toSet()
                val inPlaylist = all.filter { it.uri in uriSet }
                com.watermelon.common.util.FileLogger.i("Playlist",
                    "UserPlaylist($playlistId) — items=${uris.size}, matched=${inPlaylist.size}")
                applyCustomOrder(playlistId, inPlaylist)
            }
        }
        result
    }.flowOn(Dispatchers.IO)

    /**
     * Orders [items] by the saved CustomOrder for [containerId]. Items without a saved
     * position fall to the end in their existing order. If no custom order exists, returns
     * [items] unchanged (caller's natural order = original indexed order).
     */
    private fun applyCustomOrder(containerId: String, items: List<MediaItem>): List<MediaItem> {
        val order = getCustomOrderMap(containerId)
        if (order.isEmpty()) return items
        return items.sortedBy { order[it.uri] ?: Int.MAX_VALUE }
    }

    private fun getCustomOrderMap(containerId: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        runCatching {
            db.readableDatabase.rawQuery(
                "SELECT uri, position FROM CustomOrder WHERE containerId = ? ORDER BY position ASC",
                arrayOf(containerId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    map[cursor.getString(0)] = cursor.getInt(1)
                }
            }
        }
        return map
    }

    override suspend fun addToFavourites(uri: String) = withContext(Dispatchers.IO) {
        com.watermelon.common.util.FileLogger.i("Playlist", "addToFavourites($uri)")
        db.writableDatabase.insertWithOnConflict(
            "Favourites",
            null,
            ContentValues().apply {
                put("uri", uri)
                put("addedAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        Unit
    }

    override suspend fun removeFromFavourites(uri: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("Favourites", "uri = ?", arrayOf(uri))
        Unit
    }

    override suspend fun isFavourite(uri: String): Boolean = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT 1 FROM Favourites WHERE uri = ?", arrayOf(uri)
        ).use { it.moveToFirst() }
    }

    override suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        db.writableDatabase.insert(
            "Playlists", null,
            ContentValues().apply {
                put("id", id)
                put("name", name)
                put("type", PlaylistType.USER.name)
                put("createdAt", System.currentTimeMillis())
            }
        )
        id
    }

    override suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("Playlists", "id = ? AND type = ?",
            arrayOf(playlistId, PlaylistType.USER.name))
        db.writableDatabase.delete("PlaylistItems", "playlistId = ?", arrayOf(playlistId))
        Unit
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "Playlists",
            ContentValues().apply { put("name", newName) },
            "id = ? AND type = ?",
            arrayOf(playlistId, PlaylistType.USER.name)
        )
        Unit
    }

    override suspend fun addToPlaylist(playlistId: String, uri: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict(
            "PlaylistItems", null,
            ContentValues().apply {
                put("playlistId", playlistId)
                put("uri", uri)
                put("addedAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        Unit
    }

    override suspend fun removeFromPlaylist(playlistId: String, uri: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(
            "PlaylistItems", "playlistId = ? AND uri = ?", arrayOf(playlistId, uri)
        )
        Unit
    }

    override suspend fun saveCustomOrder(containerId: String, orderedUris: List<String>) =
        withContext(Dispatchers.IO) {
            val write = db.writableDatabase
            write.beginTransaction()
            try {
                write.delete("CustomOrder", "containerId = ?", arrayOf(containerId))
                orderedUris.forEachIndexed { index, uri ->
                    write.insertWithOnConflict(
                        "CustomOrder", null,
                        ContentValues().apply {
                            put("containerId", containerId)
                            put("uri", uri)
                            put("position", index)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                write.setTransactionSuccessful()
            } finally {
                write.endTransaction()
            }
            Unit
        }

    /** Raw user playlist row before item-count/duration are resolved against visible media. */
    private data class UserPlaylistRow(
        val id: String,
        val name: String,
        val createdAt: Long,
        val uris: Set<String>
    )

    private fun observeUserPlaylistRows(): Flow<List<UserPlaylistRow>> = flow {
        val rows = mutableListOf<UserPlaylistRow>()
        runCatching {
            db.readableDatabase.rawQuery(
                "SELECT id, name, createdAt FROM Playlists WHERE type = ? ORDER BY createdAt ASC",
                arrayOf(PlaylistType.USER.name)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    rows += UserPlaylistRow(
                        id        = id,
                        name      = cursor.getString(1),
                        createdAt = cursor.getLong(2),
                        uris      = getPlaylistItemUris(id).toSet()
                    )
                }
            }
        }
        emit(rows)
    }.flowOn(Dispatchers.IO)

    /**
     * Maps (uri, fileSize) -> updatedAt for every saved in-progress position, most recent
     * first is applied by the caller via sortedByDescending. positionMs = 0 rows (cleared,
     * e.g. on natural end-of-video) are excluded — they shouldn't appear as "in progress".
     */
    private fun getContinueWatchingPositions(): Map<Pair<String, Long>, Long> {
        val map = mutableMapOf<Pair<String, Long>, Long>()
        runCatching {
            db.readableDatabase.rawQuery(
                "SELECT mediaId, fileSize, updatedAt FROM PlaybackPositions WHERE positionMs > 0",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val uri      = cursor.getString(0)
                    val fileSize = cursor.getLong(1)
                    val updated  = cursor.getLong(2)
                    map[uri to fileSize] = updated
                }
            }
        }
        return map
    }

    private fun getFavouriteUris(): Set<String> {
        val uris = mutableSetOf<String>()
        runCatching {
            db.readableDatabase.rawQuery("SELECT uri FROM Favourites", null).use { cursor ->
                while (cursor.moveToNext()) uris += cursor.getString(0)
            }
        }
        return uris
    }

    private fun getPlaylistItemUris(playlistId: String): List<String> {
        val uris = mutableListOf<String>()
        runCatching {
            db.readableDatabase.rawQuery(
                "SELECT uri FROM PlaylistItems WHERE playlistId = ? ORDER BY addedAt ASC",
                arrayOf(playlistId)
            ).use { cursor ->
                while (cursor.moveToNext()) uris += cursor.getString(0)
            }
        }
        return uris
    }
}