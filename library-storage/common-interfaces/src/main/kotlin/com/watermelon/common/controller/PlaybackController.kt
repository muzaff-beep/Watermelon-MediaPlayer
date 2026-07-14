package com.watermelon.common.controller

import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>
    val currentPositionMs: StateFlow<Long>
    val repeatMode: StateFlow<RepeatMode>
    val shuffleEnabled: StateFlow<Boolean>
    val sleepTimerRemainingMs: StateFlow<Long>
    val sleepTimerRunning: StateFlow<Boolean>

    fun play(uri: String, startPositionMs: Long = 0)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)                   // 0.5f .. 8.0f
    fun setRepeat(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun setSleepTimer(mode: SleepTimerMode)
    fun cancelSleepTimer()
    fun takeScreenshot(): String?                // returns local file path or null
}
