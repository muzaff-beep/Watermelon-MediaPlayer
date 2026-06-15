package com.watermelon.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which modal sheet is currently open over the player, if any. */
enum class PlayerSheet { NONE, SETTINGS, SUBTITLE, SYNC, TRACKS }

/**
 * Single source of truth for the player's UI chrome — visibility, active sheet, lock.
 *
 * One holder eliminates the competing-timer / overlapping-touch-handler bugs: the gesture
 * layer, the controls, and the auto-hide logic all read from and write to this one object.
 *
 * RULES enforced by readers:
 *   - When [sheet] != NONE  → auto-hide is suspended, gesture layer is disabled.
 *   - When [isLocked]       → controls hidden, gesture layer disabled (except the unlock tap).
 *   - Gesture layer is live ONLY when controls are hidden, no sheet is open, and not locked
 *     (or by the project's chosen rule — see [gesturesEnabled]).
 */
@Stable
class PlayerUiState {
    var controlsVisible by mutableStateOf(false)
    var sheet by mutableStateOf(PlayerSheet.NONE)
    var isLocked by mutableStateOf(false)

    val sheetOpen: Boolean get() = sheet != PlayerSheet.NONE

    /** Controls may show only when not locked. */
    fun toggleControls() {
        if (isLocked) return
        controlsVisible = !controlsVisible
        if (!controlsVisible) sheet = PlayerSheet.NONE
    }

    fun showControls() { if (!isLocked) controlsVisible = true }
    fun hideControls() { controlsVisible = false; sheet = PlayerSheet.NONE }

    fun openSheet(s: PlayerSheet) { if (!isLocked) { sheet = s; controlsVisible = true } }
    fun closeSheet() { sheet = PlayerSheet.NONE }

    fun lock()   { isLocked = true;  controlsVisible = false; sheet = PlayerSheet.NONE }
    fun unlock() { isLocked = false; controlsVisible = true }

    /** Auto-hide should run only when playing, controls shown, no sheet open, not locked. */
    fun autoHideEligible(isPlaying: Boolean): Boolean =
        isPlaying && controlsVisible && !sheetOpen && !isLocked

    /** The gesture surface is active only when nothing is capturing touch above it. */
    val gesturesEnabled: Boolean
        get() = !sheetOpen && !isLocked
}
