package com.watermelon.mediatools.engine

import java.io.OutputStream

/**
 * Thin JNI wrapper around libmp3lame. Not usable until the native library actually
 * builds — see media-tools/src/main/cpp/lame/README.md; this class assumes the
 * native lib is named `mp3encoder` per CMakeLists.txt.
 *
 * Usage: feed interleaved 16-bit PCM chunks via [encodeChunk], call [flush] once at
 * end-of-stream, then [close]. One instance = one encode session (not reusable).
 */
class Mp3Encoder(sampleRateHz: Int, numChannels: Int, bitrateKbps: Int = 128) {

    companion object {
        init {
            System.loadLibrary("mp3encoder")
        }
        // Worst-case MP3 frame size guidance from lame docs: 1.25 * numSamples + 7200 bytes.
        // Sized generously per chunk to avoid buffer-too-small failures; not tuned/measured.
        private const val OUT_BUFFER_BYTES = 32 * 1024
    }

    private var handle: Long = nativeInit(sampleRateHz, numChannels, bitrateKbps)
    private val outBuffer = ByteArray(OUT_BUFFER_BYTES)

    val isValid: Boolean get() = handle != 0L

    /** Encodes one chunk of interleaved 16-bit PCM and writes resulting MP3 bytes to [sink]. */
    fun encodeChunk(pcm: ShortArray, samplesPerChannel: Int, sink: OutputStream) {
        check(isValid) { "Mp3Encoder not initialized" }
        val written = nativeEncodeChunk(handle, pcm, samplesPerChannel, outBuffer)
        if (written > 0) sink.write(outBuffer, 0, written)
        else if (written < 0) error("lame encode error code=$written")
    }

    /** Call once after the last [encodeChunk], before [close]. */
    fun flush(sink: OutputStream) {
        check(isValid) { "Mp3Encoder not initialized" }
        val written = nativeFlush(handle, outBuffer)
        if (written > 0) sink.write(outBuffer, 0, written)
    }

    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeInit(sampleRateHz: Int, numChannels: Int, bitrateKbps: Int): Long
    private external fun nativeEncodeChunk(
        handle: Long, pcm: ShortArray, samplesPerChannel: Int, outBuffer: ByteArray
    ): Int
    private external fun nativeFlush(handle: Long, outBuffer: ByteArray): Int
    private external fun nativeClose(handle: Long)
}
