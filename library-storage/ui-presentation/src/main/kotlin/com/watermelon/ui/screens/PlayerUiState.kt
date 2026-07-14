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
 *   - When [sheet] != NONE     → auto-hide is suspended, gesture layer is disabled.
 *   - When [isLocked]          → controls hidden, gesture layer disabled (except the unlock tap).
 *   - When [controlsVisible]   → gesture layer disabled; controls own touch in their own
 *     regions, and a dedicated scrim tap-catcher (drawn behind the controls, not the
 *     full-screen gesture layer) handles "tap empty space to hide controls".
 *   - Gesture layer (swipe-to-seek/volume/brightness, FF/FR hold) is live ONLY when controls
 *     are hidden, no sheet is open, and not locked — see [gesturesEnabled].
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

    /**
     * The gesture surface is active only when nothing is capturing touch above it.
     *
     * Critically this includes [controlsVisible]: once controls (buttons, seek bar, control
     * panel) are on screen they own touch in their own regions. Leaving the full-screen
     * gesture layer live underneath them caused two symptoms that were really one bug:
     *   - Taps meant for a button (play/pause, menu, etc.) were also seen by the full-screen
     *     tap detector, which toggled controls off right as the button was pressed.
     *   - Drags on the tuner seek bar were also seen by the full-screen horizontal-drag→seek
     *     gesture underneath it, so two independent seek systems fought over the same touch.
     */
    val gesturesEnabled: Boolean
        get() = !sheetOpen && !isLocked && !controlsVisible
}
