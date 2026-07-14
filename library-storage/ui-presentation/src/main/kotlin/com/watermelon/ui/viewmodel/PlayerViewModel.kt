package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.common.model.UserIntent
import kotlinx.coroutines.flow.StateFlow

/**
 * MVI ViewModel for the player. Delegates to [PlaybackController] only — no direct
 * dependency on the concrete playback-engine (module boundary rules).
 */
class PlayerViewModel(
    private val controller: PlaybackController
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = controller.playbackState
    val currentPositionMs: StateFlow<Long>      = controller.currentPositionMs
    val repeatMode: StateFlow<RepeatMode>       = controller.repeatMode
    val shuffleEnabled: StateFlow<Boolean>      = controller.shuffleEnabled
    val sleepTimerRemainingMs: StateFlow<Long>  = controller.sleepTimerRemainingMs
    val sleepTimerRunning: StateFlow<Boolean>   = controller.sleepTimerRunning

    fun onIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.Play           -> controller.play(intent.uri)
            is UserIntent.Seek           -> controller.seekTo(intent.positionMs)
            is UserIntent.SetSpeed       -> controller.setSpeed(intent.speed)
            UserIntent.Pause             -> controller.pause()
            UserIntent.Resume            -> controller.resume()
            UserIntent.RefreshLibrary    -> Unit
        }
    }

    /** Cycles NONE → ONE → ALL → NONE. */
    fun cycleRepeat() {
        val next = when (controller.repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.NONE
        }
        controller.setRepeat(next)
    }

    fun toggleShuffle() = controller.setShuffle(!controller.shuffleEnabled.value)

    fun setSleepTimer(mode: SleepTimerMode) = controller.setSleepTimer(mode)
    fun cancelSleepTimer()                   = controller.cancelSleepTimer()
    fun takeScreenshot(): String?            = controller.takeScreenshot()
}
