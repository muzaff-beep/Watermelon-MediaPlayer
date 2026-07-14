package com.watermelon.ui.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures video frames and saves them to Pictures/Watermelon/Screenshots.
 *
 * Two modes:
 * - SINGLE: capture one frame at current position
 * - BURST: capture 9 frames (4 before + current + 4 after)
 *
 * Frame extraction uses MediaMetadataRetriever. Burst mode extracts at:
 * current ± (frameDuration * 4) where frameDuration = duration / frameCount estimate.
 *
 * Saving goes through [MediaStore] on API 29+ (Scoped Storage): writing directly to
 * Environment.getExternalStoragePublicDirectory() with a raw File/FileOutputStream is
 * blocked on modern Android without WRITE_EXTERNAL_STORAGE (which this app doesn't — and
 * shouldn't — request), so that path silently produced zero screenshots on Android 10+.
 * MediaStore inserts also make the screenshot show up in the Gallery immediately, which
 * a private app-specific directory would not.
 */
object ScreenshotManager {

    enum class Mode { SINGLE, BURST }

    suspend fun takeScreenshot(
        context: Context,
        videoUri: String,
        currentPositionMs: Long,
        durationMs: Long,
        mode: Mode = Mode.SINGLE
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(videoUri))

                val frames = when (mode) {
                    Mode.SINGLE -> listOf(currentPositionMs)
                    Mode.BURST -> {
                        val frameDuration = (durationMs / 9).coerceAtLeast(100L)
                        (4 downTo 0).map { (currentPositionMs - frameDuration * (4 - it)).coerceIn(0, durationMs) } +
                        (1..4).map { (currentPositionMs + frameDuration * it).coerceIn(0, durationMs) }
                    }
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val savedUris = mutableListOf<Uri>()

                frames.forEachIndexed { index, positionMs ->
                    val bitmap = retriever.getFrameAtTime(
                        positionMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    bitmap?.let { bmp ->
                        val filename = when (mode) {
                            Mode.SINGLE -> "watermelon_${timestamp}.png"
                            Mode.BURST -> "watermelon_${timestamp}_${index + 1}.png"
                        }
                        saveBitmap(context, bmp, filename)?.let { savedUris += it }
                        bmp.recycle()
                    }
                }

                if (savedUris.isEmpty()) {
                    ScreenshotResult.Error("No frames could be saved")
                } else {
                    ScreenshotResult.Success(savedUris)
                }
            } finally {
                retriever.release()
            }
        }.getOrElse { error ->
            ScreenshotResult.Error(error.message ?: "Unknown error")
        }
    }

    /**
     * Saves [bitmap] as "$filename" under Pictures/Watermelon/Screenshots and returns its
     * URI, or null on failure. Uses MediaStore on API 29+ (no permission required, visible
     * in Gallery right away); falls back to a direct public-directory write below API 29,
     * where the legacy storage model still allows it.
     */
    private fun saveBitmap(context: Context, bitmap: Bitmap, filename: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Watermelon/Screenshots")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val wrote = runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return@runCatching false
                true
            }.getOrDefault(false)

            if (!wrote) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            runCatching { resolver.update(uri, values, null, null) }
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Watermelon/Screenshots"
            ).apply { mkdirs() }
            val file = File(dir, filename)
            val wrote = runCatching {
                FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
            }.isSuccess
            if (!wrote) null else Uri.fromFile(file)
        }
    }
}

sealed class ScreenshotResult {
    data class Success(val uris: List<Uri>) : ScreenshotResult()
    data class Error(val message: String) : ScreenshotResult()
}
