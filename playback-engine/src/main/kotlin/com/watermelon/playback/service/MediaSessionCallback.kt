package com.watermelon.playback.service

import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Handles MediaSession commands (play, pause, seek, skip) plus a custom screenshot command.
 * Standard player transport commands are delegated to the underlying [Player]; only custom
 * session commands need explicit handling here.
 */
class MediaSessionCallback(
    private val onScreenshot: () -> String?
) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand(CMD_SCREENSHOT, android.os.Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.accept(
            sessionCommands,
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: android.os.Bundle
    ): ListenableFuture<SessionResult> {
        return when (customCommand.customAction) {
            CMD_SCREENSHOT -> {
                val path = onScreenshot()
                val result = if (path != null) {
                    val extras = android.os.Bundle().apply { putString(KEY_PATH, path) }
                    SessionResult(SessionResult.RESULT_SUCCESS, extras)
                } else {
                    SessionResult(SessionError.ERROR_UNKNOWN)
                }
                Futures.immediateFuture(result)
            }
            else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    companion object {
        const val CMD_SCREENSHOT = "com.watermelon.playback.SCREENSHOT"
        const val KEY_PATH = "path"
    }
}
