package com.watermelon.storage.indexer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 2 — bulk MediaStore metadata extraction. Replaces per-file MediaMetadataRetriever
 * with a single MediaStore cursor query, which is orders of magnitude faster and makes
 * folder contents appear near-instantly after Phase 1 completes.
 *
 * MediaStore on API 29+ reliably provides: DURATION, WIDTH, HEIGHT, MIME_TYPE, SIZE.
 * For exotic codecs, a future Phase 3 can enrich specific rows with MediaMetadataRetriever.
 *
 * Two-query upsert preserves [firstSeenAt] and [lastPlayedAt] across re-extractions.
 *
 * [extract] is scoped to the given [uris] (the URIs Phase 1 just swept) via a `_ID IN (...)`
 * filter, batched to stay under SQLite's per-statement bound-parameter limit — it previously
 * ignored [uris] entirely and queried the whole MediaStore video collection unconditionally,
 * which meant every refresh scanned every video on the device rather than just what Phase 1
 * had actually found.
 */
class Phase2Extractor(
    private val context: Context,
    private val database: WatermelonDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun extract(uris: List<String>) = withContext(dispatcher) {
        if (uris.isEmpty()) return@withContext
        val db  = database.writableDatabase
        val now = System.currentTimeMillis()

        val ids = uris.mapNotNull { runCatching { ContentUris.parseId(Uri.parse(it)) }.getOrNull() }
        if (ids.isEmpty()) return@withContext

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        // Batch the `_ID IN (...)` filter so a large library doesn't exceed SQLite's
        // per-statement bound-parameter limit (999 on older SQLite builds).
        ids.chunked(BATCH_SIZE).forEach { batch ->
            val selection = "${MediaStore.Video.Media._ID} IN (${batch.joinToString(",") { "?" }})"
            val selectionArgs = batch.map { it.toString() }.toTypedArray()

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                val idxId          = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val idxName        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val idxSize        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val idxBucket      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val idxDuration    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val idxWidth       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val idxHeight      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val idxMime        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val idxDateAdded   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val idxDateModified = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id          = cursor.getLong(idxId)
                    val uriString   = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    val displayName = cursor.getString(idxName) ?: ""
                    val size        = cursor.getLong(idxSize)
                    val bucket      = cursor.getString(idxBucket) ?: ""
                    val duration    = cursor.getLong(idxDuration)
                    val width       = cursor.getInt(idxWidth)
                    val height      = cursor.getInt(idxHeight)
                    val mime        = cursor.getString(idxMime) ?: ""
                    val dateAdded   = cursor.getLong(idxDateAdded) * 1000L  // seconds -> ms
                    val dateModified = cursor.getLong(idxDateModified) * 1000L  // seconds -> ms

                    // 1. INSERT OR IGNORE — sets firstSeenAt only for new rows.
                    db.execSQL(
                        """INSERT OR IGNORE INTO MediaItems
                           (mediaId,fileSize,displayName,parentFolder,
                            durationMs,width,height,mimeType,firstSeenAt,dateAdded,dateModified)
                           VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                        arrayOf(uriString, size, displayName, bucket,
                                duration, width, height, mime, now, dateAdded, dateModified)
                    )

                    // 2. UPDATE — refreshes metadata; never touches firstSeenAt or lastPlayedAt.
                    db.execSQL(
                        """UPDATE MediaItems SET
                           fileSize=?,displayName=?,parentFolder=?,
                           durationMs=?,width=?,height=?,mimeType=?,dateAdded=?,dateModified=?
                           WHERE mediaId=?""",
                        arrayOf(size, displayName, bucket,
                                duration, width, height, mime, dateAdded, dateModified, uriString)
                    )
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 900
    }
}