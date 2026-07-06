package com.watermelon.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Distinguishes "still extracting" from "extraction failed" — both used to collapse to null. */
private sealed interface ThumbnailResult {
    object Loading : ThumbnailResult
    data class Loaded(val bitmap: Bitmap) : ThumbnailResult
    object Failed : ThumbnailResult
}

/**
 * Thumbnail loader that always shows the same frame (10% into the video) regardless of
 * scroll speed. Uses an in-memory [LruCache] so each frame is extracted only once —
 * subsequent loads are instant, and no thumbnail ever changes during scroll.
 *
 * Replaces the previous fast/slow dual-source approach which caused jarring thumbnail
 * switches because MediaStore and Coil extracted different frames.
 *
 * [isScrollingFast] is kept for API compatibility but no longer changes behavior.
 * [durationMs] is used to calculate the 10% frame time. Defaults to 3 seconds if 0.
 */
@Composable
fun VelocityGuardImage(
    uri: String?,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
    isScrollingFast: Boolean = false
) {
    val context = LocalContext.current

    val result by produceState<ThumbnailResult>(initialValue = ThumbnailResult.Loading, uri, durationMs) {
        value = ThumbnailResult.Loading
        value = loadThumbnail(context, uri, durationMs)
            ?.let { ThumbnailResult.Loaded(it) }
            ?: ThumbnailResult.Failed
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when (val r = result) {
            is ThumbnailResult.Loaded -> Image(
                bitmap             = r.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
            ThumbnailResult.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ThumbnailResult.Failed -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = "Thumbnail unavailable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Process-wide LRU cache for extracted video thumbnails. 100 entries × ~40 KB each ≈ 4 MB.
 * Cleared automatically when the process is under memory pressure.
 */
private object ThumbnailCache {
    private val cache = LruCache<String, Bitmap>(100)
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
}

private suspend fun loadThumbnail(context: android.content.Context, uri: String?, durationMs: Long): android.graphics.Bitmap? {
    if (uri.isNullOrEmpty()) return null
    ThumbnailCache.get(uri)?.let { return it }
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, android.net.Uri.parse(uri))
                val frameTimeMicros = if (durationMs > 0L) durationMs * 100L else 3_000_000L
                val raw = retriever.getFrameAtTime(
                    frameTimeMicros,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                raw?.let {
                    // getFrameAtTime() returns the frame as decoded, ignoring the video's
                    // rotation metadata. A portrait phone video is typically stored as a
                    // landscape frame (e.g. 1920x1080) plus a 90°/270° rotation flag — without
                    // correcting for it here, `it.width`/`it.height` describe the un-rotated
                    // landscape frame, so the "aspect ratio preserving" scale below preserves
                    // the *wrong* ratio and the result comes out sideways/squashed once shown
                    // upright. Rotate first so width/height (and everything downstream) reflect
                    // the video's actual on-screen orientation.
                    val rotationDegrees = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                    )?.toIntOrNull() ?: 0
                    val oriented = if (rotationDegrees != 0) {
                        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        val rotated = android.graphics.Bitmap.createBitmap(
                            it, 0, 0, it.width, it.height, matrix, true
                        )
                        if (rotated !== it) it.recycle()
                        rotated
                    } else {
                        it
                    }
                    // Preserve aspect ratio — scale to fit within a 128x128 box, never stretch.
                    val maxDim = 128
                    val ratio = minOf(maxDim.toFloat() / oriented.width, maxDim.toFloat() / oriented.height, 1f)
                    val w = (oriented.width * ratio).toInt().coerceAtLeast(1)
                    val h = (oriented.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(oriented, w, h, true)
                    if (scaled !== oriented) oriented.recycle()
                    ThumbnailCache.put(uri, scaled)
                    scaled
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
}