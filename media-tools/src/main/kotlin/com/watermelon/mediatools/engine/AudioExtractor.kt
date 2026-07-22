package com.watermelon.mediatools.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.watermelon.common.util.FileLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AudioExtractor"
private const val TIMEOUT_US = 10_000L

/**
 * Extracts a video's audio track and encodes it to a real .mp3 file.
 *
 * Deliberately bypasses Media3 Transformer for this one path. Transformer's encode-side
 * only targets codecs MediaCodec itself provides (no MP3 encoder exists on Android), and
 * routing raw PCM through Transformer's Muxer SPI to reach our own libmp3lame encoder was
 * an unverified API bet (see project history) — so this uses plain MediaExtractor +
 * MediaCodec decode instead, which is stable, well-documented Android SDK, not Media3.
 *
 * This means AudioExtractor does NOT go through MediaJobManager/Transformer.Listener like
 * VideoTrimmer/VideoCompressor will — it needs its own progress/cancel plumbing. Callers
 * should run [extract] on a background thread/coroutine; it blocks.
 *
 * NOT tested on-device. MediaCodec's synchronous API shape here is the standard decode-loop
 * pattern, but this specific file has not been run against a real device/emulator.
 */
class AudioExtractor {

    class Result(val outputPath: String, val durationUs: Long)

    /**
     * Suspend entry point for use with MediaJobManager.registerCoroutineJob. Runs the
     * blocking [extract] work, checking real coroutine cancellation (so MediaJobManager.cancel()
     * actually interrupts the decode loop, not just marks state as Cancelled).
     */
    suspend fun extractSuspending(
        inputPath: String,
        outputPath: String,
        bitrateKbps: Int = 128,
        onProgress: (Int) -> Unit,
    ): Result {
        val ctx = currentCoroutineContext()
        return extract(
            inputPath, outputPath, bitrateKbps,
            isCancelled = { !ctx.isActive },
            onProgress = onProgress,
        )
    }

    /**
     * @param onProgress called with 0-100 as decoding advances, from the calling thread.
     * @throws IllegalStateException if the input has no audio track, or on codec/IO failure.
     */
    fun extract(
        inputPath: String,
        outputPath: String,
        bitrateKbps: Int = 128,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): Result {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        val (trackIndex, format) = findAudioTrack(extractor)
            ?: error("No audio track found in $inputPath")
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: error("Audio track has no MIME type")
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val encoder = Mp3Encoder(sampleRate, channelCount, bitrateKbps)
        check(encoder.isValid) { "Mp3Encoder failed to initialize (native lib missing/broken)" }

        File(outputPath).parentFile?.mkdirs()
        val out = BufferedOutputStream(FileOutputStream(outputPath))

        try {
            runDecodeLoop(extractor, codec, encoder, out, channelCount, durationUs, isCancelled, onProgress)
            encoder.flush(out)
        } finally {
            out.flush()
            out.close()
            encoder.close()
            codec.stop()
            codec.release()
            extractor.release()
        }

        FileLogger.i(TAG, "extract complete outputPath=$outputPath")
        return Result(outputPath, durationUs)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to format
        }
        return null
    }

    private fun runDecodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        encoder: Mp3Encoder,
        out: java.io.OutputStream,
        channelCount: Int,
        durationUs: Long,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (isCancelled()) {
                FileLogger.i(TAG, "extract cancelled mid-decode")
                return
            }

            // Feed input.
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            // Drain output.
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)!!
                    // Assumes the decoder emits standard 16-bit PCM (the common case for
                    // AAC/MP3/etc. decode on Android), not float PCM. Not verified across
                    // all device/codec combinations — if Mp3Encoder output sounds corrupted
                    // on a real device, check KEY_PCM_ENCODING on the decoder's output
                    // format first.
                    val pcmShorts = ShortArray(bufferInfo.size / 2)
                    outBuffer.asShortBuffer().get(pcmShorts)
                    val samplesPerChannel = pcmShorts.size / channelCount
                    encoder.encodeChunk(pcmShorts, samplesPerChannel, out)
                }
                codec.releaseOutputBuffer(outIndex, false)

                if (durationUs > 0) {
                    val pct = ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * 100)
                        .toInt().coerceIn(0, 100)
                    onProgress(pct)
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEos = true
                }
            }
        }
        onProgress(100)
    }
}
