package com.watermelon.common.model

/**
 * The active playback surface mode. PIP and BACKGROUND are mutually exclusive (issue 9):
 * enabling one must disable the other. NORMAL is the default foreground player.
 */
enum class PlaybackMode {
    NORMAL,
    PIP,
    BACKGROUND
}
