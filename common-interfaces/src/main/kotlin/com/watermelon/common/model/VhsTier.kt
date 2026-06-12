package com.watermelon.common.model

/**
 * VHS visual effect tier, chosen by device RAM so the shader cost matches the hardware.
 * Higher tier = more effect layers = more GPU/CPU cost.
 *
 *   A (high RAM ≥ 6 GB)  → Scanlines + Jitter + Tracking distortion
 *   B (mid RAM 3–6 GB)   → Scanlines + Jitter
 *   C (low RAM < 3 GB)   → Scanlines only  (lightest; heavy effects skipped)
 *
 * The effect is only active during fast-forward / rewind, never during normal playback.
 */
enum class VhsTier {
    A, B, C;

    val hasScanlines: Boolean get() = true                 // all tiers
    val hasJitter: Boolean    get() = this == A || this == B
    val hasTracking: Boolean  get() = this == A

    companion object {
        /** Total-RAM thresholds in bytes. */
        private const val GB = 1024L * 1024L * 1024L

        /** Pick a tier from total device RAM. */
        fun fromTotalRamBytes(totalRam: Long): VhsTier = when {
            totalRam >= 6 * GB -> A
            totalRam >= 3 * GB -> B
            else               -> C
        }
    }
}
