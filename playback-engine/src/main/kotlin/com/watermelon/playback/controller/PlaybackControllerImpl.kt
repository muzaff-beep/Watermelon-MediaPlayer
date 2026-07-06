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
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.common.repository.PlaybackPositionRepository
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
 * Concrete [PlaybackController] backed by Media3 ExoPlayer.
 *
 * Repeat and shuffle delegate directly to ExoPlayer's built-in support.
 * Sleep timer supports three modes: end-of-video, end-of-folder (stub), custom minutes.
 *
 * Resume position: if [positionRepository] is supplied, [play] auto-resolves a saved
 * position for (uri, fileSize) when the caller doesn't specify one explicitly, and the
 * current position is periodically persisted (piggybacking on the existing position
 * ticker) plus flushed on [pause] and [release]. The saved position is cleared once a
 * video finishes naturally (STATE_ENDED) so finished videos don't restart mid-way.
 */
@UnstableApi
class PlaybackControllerImpl(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val screenshotProvider: (() -> Bitmap?)? = null,
    private val positionRepository: PlaybackPositionRepository? = null
) : PlaybackController {

    private val _playbackState   = MutableStateFlow(PlaybackState.IDLE)
    private val _currentPosition = MutableStateFlow(0L)
    private val _isSeekingFast   = MutableStateFlow(false)
    private val _repeatMode      = MutableStateFlow(RepeatMode.NONE)
    private val _shuffleEnabled  = MutableStateFlow(false)

    override val playbackState: StateFlow<PlaybackState>  = _playbackState.asStateFlow()
    override val currentPositionMs: StateFlow<Long>       = _currentPosition.asStateFlow()
    override val isSeekingFast: StateFlow<Boolean>        = _isSeekingFast.asStateFlow()
    override val repeatMode: StateFlow<RepeatMode>        = _repeatMode.asStateFlow()
    override val shuffleEnabled: StateFlow<Boolean>       = _shuffleEnabled.asStateFlow()

    private val sleepTimer = SleepTimerManager(scope) { pause() }
    override val sleepTimerRemainingMs: StateFlow<Long>   = sleepTimer.remainingMs
    override val sleepTimerRunning: StateFlow<Boolean>    = sleepTimer.isRunning
    private var pendingSleepMode: SleepTimerMode? = null
    private var positionJob: Job? = null

    // Identity of the currently-loaded item, for saving/clearing its resume position.
    private var currentUriForPosition: String? = null
    private var currentFileSizeForPosition: Long = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            com.watermelon.common.util.FileLogger.i("Playback",
                "onPlaybackStateChanged: ${stateName(state)} playWhenReady=${player.playWhenReady}")
            val mapped = when (state) {
                Player.STATE_IDLE      -> PlaybackState.IDLE
                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                Player.STATE_READY     ->
                    if (player.playWhenReady) PlaybackState.PLAYING else PlaybackState.PAUSED
                Player.STATE_ENDED     -> PlaybackState.IDLE
                else                   -> PlaybackState.IDLE
            }
            _playbackState.value = mapped

            if (state == Player.STATE_ENDED) {
                // Finished naturally — don't resume mid-way next time.
                clearSavedPositionAsync()
            }

            // Sleep timer: end-of-video mode.
            if (state == Player.STATE_ENDED &&
                pendingSleepMode is SleepTimerMode.EndOfVideo) {
                pendingSleepMode = null
                pause()
            }
            // End-of-folder: stub — treated as end-of-video until queue management is built.
            if (state == Player.STATE_ENDED &&
                pendingSleepMode is SleepTimerMode.EndOfFolder) {
                pendingSleepMode = null
                pause()
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

    init { player.addListener(listener) }

    /**
     * Resolves a human-readable filename for the notification title. For content:// URIs
     * we query MediaStore DISPLAY_NAME; falls back to "Video" if unavailable.
     */
    private fun resolveDisplayName(uri: String): String {
        return runCatching {
            val parsed = android.net.Uri.parse(uri)
            context.contentResolver.query(
                parsed,
                arrayOf(android.provider.MediaStore.Video.Media.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    name?.substringBeforeLast('.')?.ifEmpty { name }
                } else null
            }
        }.getOrNull() ?: "Video"
    }

    /**
     * Resolves the file size for [uri] via MediaStore, used as part of the stable
     * (uri, fileSize) identity key for resume positions — same convention as MediaItem
     * and SubtitleOffsets. Returns 0 if unresolvable (e.g. non-content:// uri).
     */
    private fun resolveFileSize(uri: String): Long {
        return runCatching {
            val parsed = android.net.Uri.parse(uri)
            context.contentResolver.query(
                parsed,
                arrayOf(android.provider.MediaStore.Video.Media.SIZE),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.getOrNull() ?: 0L
    }

    private fun stateName(s: Int) = when (s) {
        Player.STATE_IDLE      -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY     -> "READY"
        Player.STATE_ENDED     -> "ENDED"
        else                   -> "UNKNOWN($s)"
    }

    override fun play(uri: String, startPositionMs: Long) {
        // Guard: if this uri is already loaded, don't reload it (prevents duplicate
        // play() calls from recomposition restarting the video).
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == uri && player.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            com.watermelon.common.util.FileLogger.i("Playback", "play() skipped — uri already loaded")
            player.playWhenReady = true
            return
        }

        val fileSize = resolveFileSize(uri)
        currentUriForPosition = uri
        currentFileSizeForPosition = fileSize

        com.watermelon.common.util.FileLogger.i("Playback",
            "play() called uri=$uri requestedStart=$startPositionMs")
        _playbackState.value = PlaybackState.LOADING

        val title = resolveDisplayName(uri)
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .build()
        val item = Media3Item.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(item, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        com.watermelon.common.util.FileLogger.i("Playback",
            "play() — setMediaItem+prepare+playWhenReady done; playerState=${player.playbackState}")
        startPositionTicker()

        // If the caller didn't specify an explicit resume position, look one up
        // asynchronously (SQLite read off the main thread) and seek once resolved —
        // avoids blocking playback start on a DB query.
        if (startPositionMs <= 0L && positionRepository != null) {
            scope.launch {
                val saved = runCatching { positionRepository.getPosition(uri, fileSize) }.getOrNull()
                // Only apply if this is still the item the user is watching (guards against
                // a fast subsequent play() call to a different uri completing first).
                if (saved != null && saved > 0L && currentUriForPosition == uri) {
                    com.watermelon.common.util.FileLogger.i("Playback",
                        "play() — resuming saved position=$saved for uri=$uri")
                    seekTo(saved)
                }
            }
        }
    }

    override fun pause()  {
        com.watermelon.common.util.FileLogger.i("Playback", "pause()")
        player.playWhenReady = false
        saveSavedPositionAsync()
    }
    override fun resume() {
        com.watermelon.common.util.FileLogger.i("Playback", "resume()")
        player.playWhenReady = true
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    override fun setSpeed(speed: Float) {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        // For FF, let pitch rise with speed → the accelerating-cassette whine. At 1× keep
        // pitch normal. Above 1×, scale pitch up but gently (sqrt) so it whines, not chipmunks.
        val pitch = if (s > 1f) kotlin.math.sqrt(s) else 1f
        player.playbackParameters = PlaybackParameters(s, pitch)
        com.watermelon.common.util.FileLogger.i("Playback", "setSpeed=$s pitch=$pitch")
    }

    override fun setRepeat(mode: RepeatMode) {
        _repeatMode.value = mode
        player.repeatMode = when (mode) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE  -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL  -> Player.REPEAT_MODE_ALL
        }
    }

    override fun setShuffle(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        player.shuffleModeEnabled = enabled
    }

    override fun setSleepTimer(mode: SleepTimerMode) {
        sleepTimer.cancel()
        pendingSleepMode = null
        when (mode) {
            is SleepTimerMode.EndOfVideo  -> pendingSleepMode = mode
            is SleepTimerMode.EndOfFolder -> pendingSleepMode = mode  // stub
            is SleepTimerMode.Custom      -> sleepTimer.start(mode.minutes)
        }
    }

    override fun cancelSleepTimer() {
        sleepTimer.cancel()
        pendingSleepMode = null
    }

    override fun setVhsIntensity(level: Float) { _isSeekingFast.value = level > 0f }

    override fun takeScreenshot(): String? {
        val bitmap = screenshotProvider?.invoke() ?: return null
        return runCatching {
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val out = File(dir, "shot_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out.absolutePath
        }.getOrNull()
    }

    /**
     * Releases resources. The final position save is done synchronously (via runBlocking)
     * *before* cancelling [scope] — otherwise scope.cancel() would abort the fire-and-forget
     * coroutine from [saveSavedPositionAsync] before its SQLite write completes, silently
     * dropping the last few seconds of resume progress on every teardown.
     *
     * This runs on whatever thread calls release() — typically the main thread, from
     * Activity#onStop() — so the wait is bounded by [RELEASE_SAVE_TIMEOUT_MS] rather than
     * left open-ended: a slow disk (or a wedged write) blocks the UI thread for at most that
     * long instead of risking an ANR. If it times out, the last few seconds of resume
     * progress may be lost, which is the same failure mode as before this fix — just with a
     * guaranteed upper bound instead of none.
     */
    fun release() {
        val repo = positionRepository
        val uri = currentUriForPosition
        if (repo != null && uri != null) {
            val positionMs = player.currentPosition
            val fileSize = currentFileSizeForPosition
            runCatching {
                kotlinx.coroutines.runBlocking {
                    val saved = kotlinx.coroutines.withTimeoutOrNull(RELEASE_SAVE_TIMEOUT_MS) {
                        // Dispatchers.IO so the actual disk write isn't pinned to the caller's
                        // thread while we wait on it.
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            repo.savePosition(uri, fileSize, positionMs)
                        }
                        true
                    }
                    if (saved == null) {
                        com.watermelon.common.util.FileLogger.w("Playback",
                            "release() — position save timed out after ${RELEASE_SAVE_TIMEOUT_MS}ms, may lose last few seconds")
                    }
                }
            }
        }
        positionJob?.cancel()
        sleepTimer.cancel()
        player.removeListener(listener)
        scope.cancel()
    }

    private fun startPositionTicker() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            var ticksSinceSave = 0
            while (isActive) {
                _currentPosition.value = player.currentPosition
                ticksSinceSave++
                // Persist roughly every ~5s (POSITION_TICK_MS * SAVE_EVERY_N_TICKS) rather
                // than on every 250ms tick, to avoid hammering SQLite during playback.
                if (ticksSinceSave >= SAVE_EVERY_N_TICKS) {
                    ticksSinceSave = 0
                    saveSavedPositionAsync()
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    /** Fire-and-forget save of the current position for the currently-loaded item. */
    private fun saveSavedPositionAsync() {
        val repo = positionRepository ?: return
        val uri = currentUriForPosition ?: return
        val fileSize = currentFileSizeForPosition
        val positionMs = player.currentPosition
        scope.launch {
            runCatching { repo.savePosition(uri, fileSize, positionMs) }
        }
    }

    /** Fire-and-forget clear of the saved position for the currently-loaded item. */
    private fun clearSavedPositionAsync() {
        val repo = positionRepository ?: return
        val uri = currentUriForPosition ?: return
        val fileSize = currentFileSizeForPosition
        scope.launch {
            runCatching { repo.clearPosition(uri, fileSize) }
        }
    }

    companion object {
        const val MIN_SPEED        = 0.5f
        const val MAX_SPEED        = 8.0f
        private const val POSITION_TICK_MS = 250L
        private const val SAVE_EVERY_N_TICKS = 20  // ~5s at 250ms/tick
        private const val RELEASE_SAVE_TIMEOUT_MS = 300L
    }
}