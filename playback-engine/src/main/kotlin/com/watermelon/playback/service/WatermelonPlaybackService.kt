package com.watermelon.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.watermelon.common.util.FileLogger

/**
 * The single source of truth for playback. Owns the ExoPlayer and a MediaSession.
 * Survives Activity death so background audio continues. Media3's MediaSessionService
 * auto-generates the MediaStyle notification (thumbnail, title, play/pause, skip, seekbar)
 * from the session — no manual notification code required for the basics.
 *
 * The Activity connects to this service through a MediaController (see PlaybackConnection),
 * controlling the same player whether on-screen, in PiP, or backgrounded.
 *
 * Issue 15 (service), Issue 14 (notification), Issues 19-21 (tap -> player).
 */
@UnstableApi
class WatermelonPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        FileLogger.i("Service", "onCreate — building ExoPlayer + MediaSession")

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Tapping the notification opens the app and routes to the player screen.
        // The launch intent carries a flag the Activity reads to navigate to player.
        val sessionActivityPendingIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply {
                putExtra(EXTRA_OPEN_PLAYER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        mediaSession = MediaSession.Builder(this, player)
            .apply { sessionActivityPendingIntent?.let { setSessionActivity(it) } }
            .build()

        FileLogger.i("Service", "MediaSession ready")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * When the app task is removed (swiped away), stop playback + service if not playing,
     * so we don't leave a dangling notification. If still playing in background, keep alive.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            FileLogger.i("Service", "onTaskRemoved — not playing, stopping service")
            stopSelf()
        } else {
            FileLogger.i("Service", "onTaskRemoved — still playing, keeping service alive")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        FileLogger.i("Service", "onDestroy — releasing session + player")
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.watermelon.OPEN_PLAYER"
    }
}
