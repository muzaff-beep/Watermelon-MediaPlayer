package com.watermelon.common.model

/** How the sleep timer should stop playback. */
sealed class SleepTimerMode {
    /** Stop when the current video finishes. */
    object EndOfVideo : SleepTimerMode()
    /** Stop when all videos in the current folder have played. */
    object EndOfFolder : SleepTimerMode()
    /** Stop after a fixed number of minutes. */
    data class Custom(val minutes: Int) : SleepTimerMode()
}
