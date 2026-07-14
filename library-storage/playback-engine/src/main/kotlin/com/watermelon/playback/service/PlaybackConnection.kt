package com.watermelon.playback.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.watermelon.common.util.FileLogger

/**
 * Creates and holds a MediaController connected to WatermelonPlaybackService's session.
 * The controller implements Player, so it drops straight into PlaybackControllerImpl.
 *
 * Connection is async (ListenableFuture). [onReady] fires on the main thread once the
 * controller is connected; until then [controller] is null and callers should show a
 * brief connecting state.
 */
@UnstableApi
class PlaybackConnection(private val context: Context) {

    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    fun connect(onReady: (MediaController) -> Unit) {
        FileLogger.i("Connection", "connecting to playback service")
        val token = SessionToken(
            context,
            ComponentName(context, WatermelonPlaybackService::class.java)
        )
        val fut = MediaController.Builder(context, token).buildAsync()
        future = fut
        fut.addListener({
            runCatching {
                val c = fut.get()
                controller = c
                FileLogger.i("Connection", "controller connected")
                onReady(c)
            }.onFailure {
                FileLogger.e("Connection", "controller connect failed", it)
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        FileLogger.i("Connection", "releasing controller")
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    /** True once connected. */
    val isConnected: Boolean get() = controller != null

    /** The connected controller as a Player, or null while connecting. */
    fun playerOrNull(): Player? = controller
}
