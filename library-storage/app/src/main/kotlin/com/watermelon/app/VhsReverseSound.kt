package com.watermelon.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesizes a faint, looping VHS-style reverse "whirr" while the user rewinds. Real
 * reversed audio is impossible on Android, so this fakes the illusion: a low sawtooth-ish
 * tone whose base frequency rises with rewind speed, plus a slow wobble for the tape feel.
 *
 * Call [start] with the current speed (2..8) when reverse begins or its speed changes,
 * and [stop] when the hold ends. Cheap: a single short looped PCM buffer on an AudioTrack.
 */
class VhsReverseSound {

    private val sampleRate = 22_050
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var genThread: Thread? = null
    @Volatile private var speed = 2f

    fun start(initialSpeed: Float) {
        speed = initialSpeed.coerceIn(2f, 8f)
        if (running) return
        running = true

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }

        genThread = Thread {
            val chunk = ShortArray(1024)
            var phase = 0.0
            while (running) {
                // base freq scales with rewind speed; faint, dry tone (no harmonics/wobble).
                val baseFreq = 70.0 + speed * 18.0
                for (i in chunk.indices) {
                    phase += 2.0 * PI * baseFreq / sampleRate
                    if (phase > 2 * PI) phase -= 2 * PI
                    val s = sin(phase)
                    chunk[i] = (s * 0.14 * Short.MAX_VALUE).toInt().toShort()  // faint, dry
                }
                track?.write(chunk, 0, chunk.size)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /** Update the pitch as rewind speed changes, without restarting. */
    fun setSpeed(newSpeed: Float) { speed = newSpeed.coerceIn(2f, 8f) }

    fun stop() {
        running = false
        runCatching { genThread?.join(200) }
        genThread = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }
}
