package com.watermelon.storage.indexer

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.watermelon.common.model.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 — fast MediaStore cursor scan. Builds a folder tree grouped by BUCKET_DISPLAY_NAME
 * (so the key matches what Phase 2 stores in MediaItem.parentFolder) and tags each folder with
 * its storage volume so internal and external (SD/USB) storage can be presented separately.
 * Runs on [Dispatchers.IO].
 */
class Phase1Sweep(private val contentResolver: ContentResolver) {

    /** Lightweight result row from the sweep — just enough to group into folders. */
    data class SweepRow(
        val uri: String,
        val displayName: String,
        val bucketId: String,
        val bucketName: String,
        val relativePath: String,
        val volumeName: String
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
        // RELATIVE_PATH and VOLUME_NAME were added in API 29; fall back on older devices.
        val hasApi29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (hasApi29) MediaStore.Video.Media.RELATIVE_PATH
                         else @Suppress("DEPRECATION") MediaStore.Video.Media.DATA

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(pathColumn)
            if (hasApi29) add(MediaStore.Video.Media.VOLUME_NAME)
        }.toTypedArray()

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
            val volumeCol = if (hasApi29)
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.VOLUME_NAME) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val rawPath = cursor.getString(pathCol) ?: ""
                val relativePath = if (hasApi29) rawPath
                    else rawPath.substringBeforeLast('/').substringAfterLast('/').let {
                        if (it.isEmpty()) rawPath else it
                    }
                val volumeName = if (volumeCol >= 0) cursor.getString(volumeCol) ?: "" else ""
                out += SweepRow(
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "",
                    bucketId = cursor.getString(bucketIdCol) ?: "",
                    bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                    relativePath = relativePath,
                    volumeName = volumeName
                )
            }
        }
        return out
    }

    /**
     * Group flat rows into a one-level folder tree, keyed on BUCKET_DISPLAY_NAME (matches
     * Phase 2's parentFolder) and tagged with the storage volume.
     */
    private fun buildFolderTree(rows: List<SweepRow>): List<FolderNode> =
        rows.groupBy { it.bucketName }
            .filterValues { it.isNotEmpty() }
            .map { (bucketName, items) ->
                FolderNode(
                    path = bucketName,            // matches Phase 2's parentFolder
                    displayName = bucketName,
                    itemCount = items.size,
                    children = emptyList(),
                    volume = volumeLabel(items.first().volumeName),
                    thumbnailUri = items.first().uri
                )
            }
            // Sort internal storage first, then by name — keeps volumes grouped together.
            .sortedWith(compareBy({ it.volume }, { it.displayName.lowercase() }))

    /** Maps a raw MediaStore volume name to a user-facing storage label. */
    private fun volumeLabel(volumeName: String): String = when {
        volumeName.isEmpty() -> "Internal storage"
        volumeName.equals("external_primary", ignoreCase = true) -> "Internal storage"
        else -> "SD card"
    }
}
