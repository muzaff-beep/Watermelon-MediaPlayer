package com.watermelon.mediatools.output

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.job.MediaJobType
import java.io.File

private const val TAG = "OutputFileStore"

/**
 * Publishes a finished job's output file into MediaStore so it shows up in the user's
 * gallery/file manager, same intent as this app's existing MediaStoreIndexer -- but that
 * indexer only *queries* MediaStore for files that already exist; there is no existing
 * insert path anywhere in this repo to follow, so this is new, not a followed convention.
 *
 * Important constraint: `Transformer.start(editedMediaItem, path)` takes a plain file path,
 * not a content:// Uri -- Transformer cannot write directly into a MediaStore pending entry.
 * So the flow here is: engines write to an app-private staging file first (see
 * [stagingPathFor]); once Transformer's onCompleted fires, [publish] copies that finished
 * file into the appropriate MediaStore collection and deletes the staging file.
 *
 * `onError` leaves the partial output file on disk (Media3 doesn't delete it) -- callers
 * should call [deleteStaging] on failure/cancellation to avoid orphaned files.
 *
 * Output locations, per product requirement: Movies/Watermelon/compressed and
 * Movies/Watermelon/trimmed (MediaStore.Video, so results show up in gallery/video apps).
 * Both are user-configurable via [compressedRelativePath]/[trimmedRelativePath] -- callers
 * read the current value from settings (same SharedPreferences-backed pattern as
 * FolderVisibilityStoreImpl's getString/putString in library-storage) and pass it in here;
 * this class doesn't own settings persistence itself, to avoid media-tools depending on
 * library-storage for a single pair of strings.
 */
class OutputFileStore(
    private val context: Context,
    private val compressedRelativePath: () -> String = { "Movies/Watermelon/compressed" },
    private val trimmedRelativePath: () -> String = { "Movies/Watermelon/trimmed" },
) {

    /** App-private staging path for [type]'s output before it's published to MediaStore. */
    fun stagingPathFor(type: MediaJobType, displayName: String): String {
        val dir = File(context.filesDir, "media-tools-staging").apply { mkdirs() }
        return File(dir, displayName).absolutePath
    }

    /**
     * Copies the finished staging file into MediaStore and returns its content Uri.
     * Collision naming ("(n)" suffix) is handled by MediaStore itself on API 29+ when
     * IS_PENDING isn't used; on API < 29 we resolve collisions manually since the legacy
     * DATA-path insert doesn't dedupe display names for us.
     */
    fun publish(type: MediaJobType, stagingPath: String, displayName: String): Uri? {
        val stagingFile = File(stagingPath)
        if (!stagingFile.exists()) {
            FileLogger.e(TAG, "publish called but staging file missing: $stagingPath")
            return null
        }

        val (collection, mimeType, relativePath) = when (type) {
            MediaJobType.EXTRACT_AUDIO -> Triple(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "audio/mpeg", // Real MP3 (Phase 0 Option B -- libmp3lame), not AAC/.m4a
                "Music/Watermelon"
            )
            MediaJobType.COMPRESS -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4",
                compressedRelativePath()
            )
            MediaJobType.TRIM -> Triple(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4",
                trimmedRelativePath()
            )
        }

        val hasApi29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val resolvedName = if (hasApi29) displayName else resolveNameCollision(displayName)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, resolvedName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (hasApi29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(collection, values) ?: run {
            FileLogger.e(TAG, "MediaStore insert returned null for $resolvedName")
            return null
        }

        return runCatching {
            resolver.openOutputStream(itemUri)?.use { out ->
                stagingFile.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("openOutputStream returned null")

            if (hasApi29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            stagingFile.delete()
            FileLogger.i(TAG, "published $resolvedName -> $itemUri (relativePath=$relativePath)")
            itemUri
        }.getOrElse { e ->
            FileLogger.e(TAG, "publish failed for $resolvedName, rolling back insert", e)
            resolver.delete(itemUri, null, null)
            null
        }
    }

    /** Deletes an orphaned staging file after a failed or cancelled job. */
    fun deleteStaging(stagingPath: String) {
        val deleted = File(stagingPath).delete()
        FileLogger.i(TAG, "deleteStaging path=$stagingPath deleted=$deleted")
    }

    /**
     * Legacy (API < 29) collision handling: MediaStore's DATA-path insert doesn't dedupe
     * display names automatically the way scoped-storage inserts do, so we check ourselves
     * and append " (n)" -- same convention noted in the blueprint.
     *
     * NOTE: on API < 29 the RELATIVE_PATH/subfolder split (compressed vs trimmed) above
     * has no effect -- legacy inserts via this ContentValues shape land in the default
     * DCIM/Movies location, not a custom subfolder. Placing files into an arbitrary custom
     * folder pre-API 29 requires writing directly to the legacy external storage path
     * (Environment.getExternalStoragePublicDirectory, deprecated but still functional pre-Q)
     * instead of a plain MediaStore insert. Not implemented here -- flagging rather than
     * silently ignoring on legacy devices.
     */
    private fun resolveNameCollision(displayName: String): String {
        val dotIndex = displayName.lastIndexOf('.')
        val base = if (dotIndex > 0) displayName.substring(0, dotIndex) else displayName
        val ext = if (dotIndex > 0) displayName.substring(dotIndex) else ""

        var candidate = displayName
        var n = 1
        val resolver = context.contentResolver
        while (nameExists(resolver, candidate)) {
            candidate = "$base ($n)$ext"
            n++
        }
        return candidate
    }

    private fun nameExists(resolver: android.content.ContentResolver, displayName: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        // Checking both collections since we don't know the type at this call site;
        // cheap enough given this only runs on legacy (API < 29) devices.
        for (uri in listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            resolver.query(uri, projection, selection, arrayOf(displayName), null)?.use {
                if (it.count > 0) return true
            }
        }
        return false
    }
}
