package com.watermelon.storage.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.watermelon.common.repository.PlaybackPositionRepository
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PlaybackPositionRepository] backed by the `PlaybackPositions` table.
 *
 * Table: PlaybackPositions(mediaId TEXT, fileSize INTEGER, positionMs INTEGER,
 *                          updatedAt INTEGER, PRIMARY KEY (mediaId, fileSize))
 * `mediaId` stores the content:// URI, mirroring the convention used by MediaItems.
 */
class PlaybackPositionRepositoryImpl(
    private val db: WatermelonDatabase
) : PlaybackPositionRepository {

    override suspend fun savePosition(uri: String, fileSize: Long, positionMs: Long) =
        withContext(Dispatchers.IO) {
            runCatching {
                db.writableDatabase.insertWithOnConflict(
                    "PlaybackPositions",
                    null,
                    ContentValues().apply {
                        put("mediaId", uri)
                        put("fileSize", fileSize)
                        put("positionMs", positionMs)
                        put("updatedAt", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            Unit
        }

    override suspend fun getPosition(uri: String, fileSize: Long): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                db.readableDatabase.rawQuery(
                    "SELECT positionMs FROM PlaybackPositions WHERE mediaId = ? AND fileSize = ?",
                    arrayOf(uri, fileSize.toString())
                ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            }.getOrNull()
        }

    override suspend fun clearPosition(uri: String, fileSize: Long) =
        withContext(Dispatchers.IO) {
            runCatching {
                db.writableDatabase.delete(
                    "PlaybackPositions", "mediaId = ? AND fileSize = ?", arrayOf(uri, fileSize.toString())
                )
            }
            Unit
        }
}
