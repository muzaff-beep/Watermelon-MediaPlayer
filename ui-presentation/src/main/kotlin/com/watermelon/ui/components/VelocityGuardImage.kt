package com.watermelon.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Velocity-aware thumbnail loader (Manifest §7).
 *
 * Fast fling ([isScrollingFast] = true):
 *   Renders a cheap MediaStore thumbnail loaded via ContentResolver.loadThumbnail (API 29+).
 *   This runs once per URI and keeps the list at 60fps during a fast fling.
 *
 * Settled ([isScrollingFast] = false):
 *   Hands off to Coil's AsyncImage, which provides disk/memory caching and automatic
 *   request cancellation. With VideoFrameDecoder registered in the app's ImageLoader
 *   (see NOTE below), Coil delivers frame-accurate video thumbnails.
 *
 * Falls back to a solid swatch on null URI, load failure, or pre-API-29 devices.
 *
 * NOTE — VideoFrameDecoder setup (one-time, in your Application class):
 *   val imageLoader = ImageLoader.Builder(this)
 *       .components { add(VideoFrameDecoder.Factory()) }
 *       .build()
 *   setSingletonImageLoader { imageLoader }
 * Requires: io.coil-kt.coil3:coil-compose and io.coil-kt.coil3:coil-video in build.gradle.kts.
 * If the project uses Coil 2, change the coil3 imports below to coil.
 */
@Composable
fun VelocityGuardImage(
    uri: String?,
    modifier: Modifier = Modifier,
    isScrollingFast: Boolean = false
) {
    val context = LocalContext.current

    // MediaStore thumbnail — cheap, loaded once, available while scrolling.
    val fastThumb by produceState<Bitmap?>(initialValue = null, uri) {
        val result = if (uri.isNullOrEmpty()) null else withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(
                        Uri.parse(uri),
                        Size(128, 80),
                        null
                    )
                } else null
            }.getOrNull()
        }
        value = result
    }

    // Solid swatch is always behind — shows while loading or on failure.
    Box(modifier.background(MaterialTheme.colorScheme.primary)) {
        when {
            // Settled: Coil with caching and cancellation. Upgraded by VideoFrameDecoder.
            !isScrollingFast && !uri.isNullOrEmpty() -> AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Fast fling: show MediaStore thumb if ready, else swatch shows through.
            fastThumb != null -> Image(
                bitmap = fastThumb!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Swatch fallback — no action needed, background handles it.
        }
    }
}
