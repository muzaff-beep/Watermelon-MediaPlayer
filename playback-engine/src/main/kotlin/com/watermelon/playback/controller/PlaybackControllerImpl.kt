package com.watermelon.playback.controller

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Concrete [PlaybackController] backed by Media3 ExoPlayer. Exposes [playbackState],
 * [currentPositionMs], and [isSeekingFast] as StateFlows (Implementation Notes, Teams §3).
 *
 * @param player        the ExoPlayer instance (injectable for tests with a fake Player).
 * @param scope         coroutine scope for the position ticker + sleep timer.
 * @param screenshotProvider supplies the current surface frame as a Bitmap (PlayerView /
 *                      PlayerSurface getBitmap()); null when no surface is attached.
 */
@UnstableApi
class PlaybackControllerImpl(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val screenshotProvider: (() -> Bitmap?)? = null
) : PlaybackController {

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isSeekingFast = MutableStateFlow(false)
    override val isSeekingFast: StateFlow<Boolean> = _isSeekingFast.asStateFlow()

    private val sleepTimer = SleepTimerManager(scope) { pause() }
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = when (state) {
                Player.STATE_IDLE -> PlaybackState.IDLE
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY ->
                    if (player.playWhenReady) PlaybackState.PLAYING else PlaybackState.PAUSED
                Player.STATE_ENDED -> PlaybackState.IDLE
                else -> PlaybackState.IDLE
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (player.playbackState == Player.STATE_READY) {
                _playbackState.value =
                    if (playWhenReady) PlaybackState.PLAYING else PlaybackState.PAUSED
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPositionTicker()
        }
    }

    init {
        player.addListener(listener)
    }

    override fun play(uri: String, startPositionMs: Long) {
        _playbackState.value = PlaybackState.LOADING
        player.setMediaItem(Media3Item.fromUri(uri), startPositionMs)
        player.prepare()
        player.playWhenReady = true
        startPositionTicker()
    }

    override fun pause() { player.playWhenReady = false }

    override fun resume() { player.playWhenReady = true }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    /** 0.5f .. 2.0f — boundaries clamped per Manifest §4.2. */
    override fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        player.playbackParameters = PlaybackParameters(clamped)
    }

    override fun setSleepTimer(minutes: Int) = sleepTimer.start(minutes)

    override fun cancelSleepTimer() = sleepTimer.cancel()

    /** Drives the VHS overlay: >0 marks fast seeking active. */
    override fun setVhsIntensity(level: Float) {
        _isSeekingFast.value = level > 0f
    }

    override fun takeScreenshot(): String? {
        val bitmap = screenshotProvider?.invoke() ?: return null
        return runCatching {
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val out = File(dir, "shot_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out.absolutePath
        }.getOrNull()
    }

    fun release() {
        positionJob?.cancel()
        sleepTimer.cancel()
        player.removeListener(listener)
        scope.cancel()
    }

    private fun startPositionTicker() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = player.currentPosition
                delay(POSITION_TICK_MS)
            }
        }
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
        private const val POSITION_TICK_MS = 250L
    }
}
