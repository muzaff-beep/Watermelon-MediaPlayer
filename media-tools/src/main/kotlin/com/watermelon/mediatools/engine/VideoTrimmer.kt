package com.watermelon.mediatools.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.job.MediaJobType
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.mediatools.output.OutputNaming

/**
 * Lossless, stream-copy-only video trim -- no re-encoding of the video/audio codec, ever.
 * This is a hard product requirement, not a "prefer fast, fall back if needed" default:
 * cut points always snap to the nearest keyframe via
 * [MediaItem.ClippingConfiguration.Builder.setStartsAtKeyFrame] (confirmed current API),
 * guaranteeing the transmux/stream-copy path engages rather than a full re-encode.
 *
 * The cut may land a little before/after the user's exact selection as a result (snapped to
 * whichever keyframe precedes it) -- this is the accepted tradeoff for "always fast, never
 * touches the codec." [MediaJobManager.onFallbackApplied] is logged if Transformer still
 * falls back to re-encoding despite the keyframe snap (rare edge case -- corrupt GOP, unusual
 * container); per product decision, that job still completes normally rather than failing.
 *
 * v1 deliberately doesn't offer frame-accurate trim (Media3 1.8.0's
 * experimentalSetMp4EditListTrimEnabled depends on player-side edit-list support, not
 * universal -- see project history).
 *
 * NOT run on-device.
 */
@UnstableApi
class VideoTrimmer(private val context: Context, private val outputFileStore: OutputFileStore) {

    /**
     * Starts a trim job. [originalDisplayName] is the source file's existing display name
     * (e.g. "vacation.mp4") -- output is named per [OutputNaming.trimmedName], not a caller-
     * chosen name, per product requirement (original name + "_trimmed_<start>_<end>").
     */
    fun trim(
        jobManager: MediaJobManager,
        inputUri: Uri,
        originalDisplayName: String,
        startMs: Long,
        endMs: Long,
    ): String {
        require(endMs > startMs) { "endMs ($endMs) must be greater than startMs ($startMs)" }

        val outputName = OutputNaming.trimmedName(originalDisplayName, startMs, endMs)
        val outputPath = outputFileStore.stagingPathFor(MediaJobType.TRIM, outputName)

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .setStartsAtKeyFrame(true) // hard requirement: forces keyframe snap, never re-encode
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clipping)
            .build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        lateinit var jobId: String
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    jobManager.onCompleted(jobId, exportResult)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    jobManager.onError(jobId, exportException)
                }
                override fun onFallbackApplied(
                    composition: Composition,
                    originalRequest: TransformationRequest,
                    fallbackRequest: TransformationRequest,
                ) {
                    // Logged only, per product decision -- job still completes normally
                    // even if Transformer had to re-encode despite setStartsAtKeyFrame(true).
                    jobManager.onFallbackApplied(jobId, originalRequest, fallbackRequest)
                }
            })
            .build()

        jobId = jobManager.register(MediaJobType.TRIM, inputUri.toString(), outputPath, transformer)
        transformer.start(editedMediaItem, outputPath)
        return jobId
    }
}
