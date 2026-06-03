package com.watermelon.storage.indexer

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.watermelon.common.model.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 — fast, cheap MediaStore cursor scan. Builds the folder tree from the `BUCKET`
 * columns and emits it immediately so the UI can render before Phase 2 metadata extraction
 * completes (Manifest §5.2). Runs on [Dispatchers.IO].
 */
class Phase1Sweep(private val contentResolver: ContentResolver) {

    /** Lightweight result row from the sweep — just enough to group into folders. */
    data class SweepRow(
        val uri: String,
        val displayName: String,
        val bucketId: String,
        val bucketName: String,
        val relativePath: String
    )

    @Volatile
    private var lastUris: List<String> = emptyList()

    /** URIs from the most recent [sweep]; consumed by Phase 2 to know what to enrich. */
    fun lastSweepUris(): List<String> = lastUris

    suspend fun sweep(): List<FolderNode> = withContext(Dispatchers.IO) {
        val rows = queryVideos()
        lastUris = rows.map { it.uri }
        buildFolderTree(rows)
    }

    private fun queryVideos(): List<SweepRow> {
        // RELATIVE_PATH was added in API 29; fall back to DATA (full path) on API 23-28.
        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (useRelativePath) MediaStore.Video.Media.RELATIVE_PATH
                         else @Suppress("DEPRECATION") MediaStore.Video.Media.DATA

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            pathColumn
        )
        val out = ArrayList<SweepRow>()
        contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(pathColumn)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val rawPath = cursor.getString(pathCol) ?: ""
                // On API < 29 rawPath is the full file path; extract the parent directory name.
                val relativePath = if (useRelativePath) rawPath
                    else rawPath.substringBeforeLast('/').substringAfterLast('/').let {
                        if (it.isEmpty()) rawPath else it
                    }
                out += SweepRow(
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "",
                    bucketId = cursor.getString(bucketIdCol) ?: "",
                    bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                    relativePath = relativePath
                )
            }
        }
        return out
    }

    /** Group flat rows into a one-level folder tree (empty directories are hidden). */
    private fun buildFolderTree(rows: List<SweepRow>): List<FolderNode> =
        rows.groupBy { it.relativePath.ifEmpty { it.bucketName } }
            .filterValues { it.isNotEmpty() }
            .map { (path, items) ->
                FolderNode(
                    path = path,
                    displayName = items.first().bucketName,
                    itemCount = items.size,
                    children = emptyList()
                )
            }
            .sortedBy { it.displayName.lowercase() }
}
