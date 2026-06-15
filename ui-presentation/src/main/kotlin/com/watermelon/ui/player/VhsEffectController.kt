package com.watermelon.ui.player

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Self-contained VHS effect module. The player screen no longer carries VHS logic — it only:
 *   1. tells this controller the current surface size (onSurfaceSize)
 *   2. tells it whether FF/FR is happening and at what speed (setRewind)
 *   3. asks it for a RenderEffect to apply to the video surface (effectOrNull)
 *
 * When disabled in settings ([enabled] == false or [intensity] == 0), EVERY output is a no-op:
 * effectOrNull() returns null, the animation loop never runs, and the reverse sound is never
 * started. The effect "clips on" only when enabled AND FF/FR is active. This keeps the player
 * agnostic to VHS entirely.
 *
 * The actual shader build + reverse sound live in the app module and are injected as callbacks
 * (the same provider hooks already wired in MainActivity), so this controller stays free of
 * Android-version and app-module dependencies.
 */
class VhsEffectController(
    /** Builds the shader effect (app module). (intensity, timeSec, w, h) -> effect or null. */
    private val shaderProvider: (Float, Float, Float, Float) -> RenderEffect?,
    /** Drives the synthetic reverse sound (app module). (active, speed) -> Unit. */
    private val reverseSound: (Boolean, Float) -> Unit
) {
    // Settings-driven configuration (updated each composition via [configure]).
    private var enabled = false
    private var intensity = 0f

    // Live state set by the player.
    var surfaceW by mutableFloatStateOf(0f); private set
    var surfaceH by mutableFloatStateOf(0f); private set
    private var rewindActive by mutableStateOf(false)
    private var rewindForward by mutableStateOf(false)

    /** True when the effect should currently be visible. */
    val active: Boolean
        get() = enabled && intensity > 0f && rewindActive &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Called by the player each composition with the latest settings. */
    fun configure(enabled: Boolean, intensity: Float) {
        this.enabled = enabled
        this.intensity = intensity
        if (!enabled || intensity == 0f) reverseSound(false, 0f)  // ensure sound off when disabled
    }

    fun onSurfaceSize(w: Float, h: Float) { surfaceW = w; surfaceH = h }

    /**
     * Notify the controller that FF/FR is happening. Forward = real playback speed (pitch-up
     * handled by the controller); reverse = seek-step + synthetic whirr handled here.
     */
    fun setRewind(active: Boolean, forward: Boolean, speed: Float) {
        rewindActive = active
        rewindForward = forward
        // Reverse sound only on reverse, and only when the effect is enabled.
        if (active && !forward && enabled && intensity > 0f) reverseSound(true, speed)
        else reverseSound(false, 0f)
    }

    // Animation clock — only runs while active.
    private var timeSec by mutableFloatStateOf(0f)

    @Composable
    fun DriveAnimation() {
        LaunchedEffect(active) {
            if (active) {
                val start = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now -> timeSec = (now - start) / 1_000_000_000f }
                }
            }
        }
    }

    /** The RenderEffect to apply to the video surface, or null when inactive/disabled. */
    fun effectOrNull(): RenderEffect? =
        if (active) shaderProvider(intensity, timeSec, surfaceW, surfaceH) else null
}

/** Remember a [VhsEffectController] across recompositions. */
@Composable
fun rememberVhsEffectController(
    shaderProvider: (Float, Float, Float, Float) -> RenderEffect?,
    reverseSound: (Boolean, Float) -> Unit
): VhsEffectController = remember { VhsEffectController(shaderProvider, reverseSound) }
