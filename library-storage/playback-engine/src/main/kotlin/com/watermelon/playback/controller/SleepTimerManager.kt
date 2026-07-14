package com.watermelon.playback.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Countdown sleep timer. On expiry it invokes [onExpire] (which the controller wires to
 * auto-pause) and updates [remainingMs] every second for notification rendering.
 */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val onExpire: () -> Unit
) {
    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var job: Job? = null

    fun start(minutes: Int) {
        require(minutes > 0) { "Sleep timer minutes must be positive" }
        cancel()
        val total = minutes * 60_000L
        _remainingMs.value = total
        _isRunning.value = true
        job = scope.launch {
            var remaining = total
            while (isActive && remaining > 0) {
                delay(TICK_MS)
                remaining -= TICK_MS
                _remainingMs.value = remaining.coerceAtLeast(0L)
            }
            if (isActive) {
                _isRunning.value = false
                _remainingMs.value = 0L
                onExpire()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _isRunning.value = false
        _remainingMs.value = 0L
    }

    companion object {
        private const val TICK_MS = 1_000L
    }
}
