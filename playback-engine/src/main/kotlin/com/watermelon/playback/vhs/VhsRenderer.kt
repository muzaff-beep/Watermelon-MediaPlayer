package com.watermelon.playback.vhs

import android.content.Context
import android.os.Build
import com.watermelon.common.model.VhsTier

/**
 * Abstract base for the VHS visual seeker. The visual layer is purely decorative — the
 * underlying ExoPlayer seek position updates identically across all tiers.
 *
 * Concrete renderers:
 *  - [AGSLFullPassRenderer]      Tier A (API 33+, ample RAM)
 *  - [AGSLLiteRenderer]          Tier B (API 33+, low RAM)
 *  - [PNGLegacyOverlayRenderer]  Tier C (API 23–32)
 */
abstract class VhsRenderer(protected val tier: VhsTier) {

    /** Intensity 0.0 (off) .. 1.0 (high), mapped from the Settings VHS submenu. */
    var intensity: Float = 0f

    /** Advance the animated time used by noise/jitter; called per frame while seeking. */
    abstract fun onFrame(timeSeconds: Float)

    /** Release any GPU/native resources held by the renderer. */
    abstract fun release()

    companion object {
        /** Factory: build the renderer matching the probed [tier]. */
        fun create(context: Context, tier: VhsTier): VhsRenderer = when {
            tier == VhsTier.A && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                AGSLFullPassRenderer(context)
            tier == VhsTier.B && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                AGSLLiteRenderer(context)
            else -> PNGLegacyOverlayRenderer(context)
        }
    }
}
