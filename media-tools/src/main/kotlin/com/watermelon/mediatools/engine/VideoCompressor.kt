package com.watermelon.mediatools.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.job.MediaJobType
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.mediatools.output.OutputNaming

/**
 * Quick compressor: downscales resolution via [Presentation.createForShortSide] (confirmed
 * current API via docs this session) and re-encodes at a target audio/video bitrate.
 *
 * CAVEAT — not fully verified: [Presentation.createForShortSide] and
 * [AudioEncoderSettings.Builder.setBitrate] (bits per second — confirmed via docs) are
 * checked. The wiring below — [VideoEncoderSettings.Builder] and attaching both encoder
 * settings via [DefaultEncoderFactory.Builder] into [Transformer.Builder.setEncoderFactory] —
 * matches the long-standing Media3 Transformer pattern but Context7 couldn't confirm the
 * exact method signatures this session (searches came back with related-but-not-matching
 * snippets). If this doesn't compile as-is, check `DefaultEncoderFactory`/`VideoEncoderSettings`
 * against the real 1.6.0 sources first — don't assume this file is correct un-verified.
 *
 * NOT run on-device.
 */
@UnstableApi
class VideoCompressor(private val context: Context, private val outputFileStore: OutputFileStore) {

    enum class Preset(val targetShortSidePx: Int, val videoBitrateBps: Int, val audioBitrateBps: Int) {
        SMALL(480, 1_500_000, 96_000),
        MEDIUM(720, 4_000_000, 128_000),
        ORIGINAL_QUALITY(1080, 8_000_000, 192_000),
        // Exact tiers/bitrates are placeholders per the blueprint — open decision, not final.
    }

    fun compress(
        jobManager: MediaJobManager,
        inputUri: Uri,
        preset: Preset,
        originalDisplayName: String,
    ): String {
        val outputName = OutputNaming.compressedName(originalDisplayName)
        val outputPath = outputFileStore.stagingPathFor(MediaJobType.COMPRESS, outputName)

        val mediaItem = MediaItem.fromUri(inputUri)
        val videoEffects: List<Effect> = listOf(Presentation.createForShortSide(preset.targetShortSidePx))
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(/* audioProcessors= */ emptyList(), videoEffects))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(preset.videoBitrateBps)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(preset.audioBitrateBps)
                    .build()
            )
            .build()

        lateinit var jobId: String
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
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
                    jobManager.onFallbackApplied(jobId, originalRequest, fallbackRequest)
                }
            })
            .build()

        jobId = jobManager.register(MediaJobType.COMPRESS, inputUri.toString(), outputPath, transformer)
        transformer.start(editedMediaItem, outputPath)
        return jobId
    }
}
