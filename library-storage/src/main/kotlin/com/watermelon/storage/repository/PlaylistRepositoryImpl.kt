package com.watermelon.storage.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.common.model.SystemPlaylist
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Playlist repository backed by SQLite.
 *
 * Tables used:
 *   Playlists        (id TEXT PK, name TEXT, type TEXT, createdAt INTEGER)
 *   PlaylistItems    (playlistId TEXT, uri TEXT, addedAt INTEGER)
 *   Favourites       (uri TEXT PK, addedAt INTEGER)
 *
 * Recently Added is computed dynamically from MediaItems.firstSeenAt (last 7 days).
 * These tables are created via MigrationV7ToV8 if not already present.
 */
class PlaylistRepositoryImpl(
    private val db: WatermelonDatabase,
    private val mediaRepository: MediaRepository
) : PlaylistRepository {

    private val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

    override fun observeAll(): Flow<List<Playlist>> = combine(
        mediaRepository.observeAllMedia(),
        observeUserPlaylists()
    ) { allMedia, userPlaylists ->
        val now = System.currentTimeMillis()
        val recentCount = allMedia.count { it.firstSeenAt >= now - sevenDaysMs }
        val favCount = getFavouriteCount()

        val recentlyAdded = Playlist(
            id        = SystemPlaylist.ID_RECENTLY_ADDED,
            name      = "Recently Added",
            type      = PlaylistType.RECENTLY_ADDED,
            itemCount = recentCount
        )
        val favourites = Playlist(
            id        = SystemPlaylist.ID_FAVOURITES,
            name      = "Favourites",
            type      = PlaylistType.FAVOURITES,
            itemCount = favCount
        )
        listOf(recentlyAdded, favourites) + userPlaylists
    }.flowOn(Dispatchers.IO)

    override fun observeVideos(playlistId: String): Flow<List<MediaItem>> =
        mediaRepository.observeAllMedia().map { all ->
            when (playlistId) {
                SystemPlaylist.ID_RECENTLY_ADDED -> {
                    val cutoff = System.currentTimeMillis() - sevenDaysMs
                    all.filter { it.firstSeenAt >= cutoff }
                        .sortedByDescending { it.firstSeenAt }
                }
                SystemPlaylist.ID_FAVOURITES -> {
                    val favUris = getFavouriteUris()
                    all.filter { it.uri in favUris }
                }
                else -> {
                    val uris = getPlaylistItemUris(playlistId)
                    val uriIndex = uris.withIndex().associate { (i, uri) -> uri to i }
                    all.filter { it.uri in uriIndex }.sortedBy { uriIndex[it.uri] }
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun addToFavourites(uri: String) = withContext(Dispatchers.IO) {
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

    private fun observeUserPlaylists(): Flow<List<Playlist>> = flow {
        val playlists = mutableListOf<Playlist>()
        db.readableDatabase.rawQuery(
            "SELECT id, name, createdAt FROM Playlists WHERE type = ? ORDER BY createdAt ASC",
            arrayOf(PlaylistType.USER.name)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val count = db.readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM PlaylistItems WHERE playlistId = ?", arrayOf(id)
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                playlists += Playlist(
                    id        = id,
                    name      = cursor.getString(1),
                    type      = PlaylistType.USER,
                    itemCount = count,
                    createdAt = cursor.getLong(2)
                )
            }
        }
        emit(playlists)
    }.flowOn(Dispatchers.IO)

    private fun getFavouriteCount(): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM Favourites", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun getFavouriteUris(): Set<String> {
        val uris = mutableSetOf<String>()
        db.readableDatabase.rawQuery("SELECT uri FROM Favourites", null).use { cursor ->
            while (cursor.moveToNext()) uris += cursor.getString(0)
        }
        return uris
    }

    private fun getPlaylistItemUris(playlistId: String): List<String> {
        val uris = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            "SELECT uri FROM PlaylistItems WHERE playlistId = ? ORDER BY addedAt ASC",
            arrayOf(playlistId)
        ).use { cursor ->
            while (cursor.moveToNext()) uris += cursor.getString(0)
        }
        return uris
    }
}
