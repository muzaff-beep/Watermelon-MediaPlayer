package com.watermelon.playback.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.watermelon.common.model.VhsTier
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.vhs.VhsTierDetector

/**
 * Foreground service wrapping ExoPlayer + MediaSession for background playback
 * (Manifest §4.1). The VHS tier probe runs **once** in [onCreate] and is immutable for the
 * session lifetime (Teams §3 Implementation Notes).
 */
@UnstableApi
class WatermelonPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var controller: PlaybackControllerImpl

    /** Probed once at startup; exposed so the UI can pick the matching renderer. */
    var vhsTier: VhsTier = VhsTier.C
        private set

    override fun onCreate() {
        super.onCreate()

        // One-shot VHS tier detection — immutable for the session.
        vhsTier = VhsTierDetector.resolveVhsTier(this)

        player = ExoPlayer.Builder(this).build()
        controller = PlaybackControllerImpl(this, player)

        val callback = MediaSessionCallback(onScreenshot = { controller.takeScreenshot() })
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Stop the service if the user swipes the app away while paused.
        val session = mediaSession ?: return
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        controller.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
