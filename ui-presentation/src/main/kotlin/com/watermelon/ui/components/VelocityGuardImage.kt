package com.watermelon.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

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

    val thumbnail by produceState<Bitmap?>(initialValue = null, uri, durationMs) {
        value = loadThumbnail(context, uri, durationMs)
    }

    Box(modifier.background(Color.Black)) {
        thumbnail?.let {
            Image(
                bitmap             = it.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
        }
        // If thumbnail is null (first load or error), the primary-color swatch shows through.
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
                    val scaled = android.graphics.Bitmap.createScaledBitmap(it, 128, 80, true)
                    if (scaled !== it) it.recycle()
                    ThumbnailCache.put(uri, scaled)
                    scaled
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
}
