package com.watermelon.mediatools.job

/**
 * One in-flight or finished media processing job (extract audio / trim / compress).
 *
 * [progressPercent] comes from polling `Transformer.getProgress()` on a timer while
 * [state] is [MediaJobState.Running] — Transformer has no push-based progress stream.
 */
data class MediaJob(
    val id: String,
    val type: MediaJobType,
    val inputUri: String,
    val outputPath: String,
    val state: MediaJobState,
    val progressPercent: Int = 0,
)

enum class MediaJobType { EXTRACT_AUDIO, TRIM, COMPRESS }

sealed class MediaJobState {
    data object Queued : MediaJobState()
    data object Running : MediaJobState()

    /**
     * [awaitingOriginalFileDecision]: true only for TRIM/COMPRESS jobs, right after
     * completion — UI should show a "keep or delete the original video?" prompt bound to
     * [MediaJob.inputUri]. False for EXTRACT_AUDIO (source video isn't replaced) and false
     * again once the user has answered (see MediaJobManager.resolveOriginalFileDecision).
     */
    data class Completed(val outputUri: String, val awaitingOriginalFileDecision: Boolean = false) : MediaJobState()
    data class Failed(val reason: String) : MediaJobState()
    data object Cancelled : MediaJobState()
}
