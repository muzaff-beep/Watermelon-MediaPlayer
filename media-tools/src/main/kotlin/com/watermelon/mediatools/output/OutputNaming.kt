package com.watermelon.mediatools.output

import java.util.concurrent.TimeUnit

/** Builds output display names as "originalName_suffix.ext", per product requirement. */
object OutputNaming {

    /** "video.mp4", 15000L, 16450L -> "video_trimmed_00-00-15_00-00-16.mp4" */
    fun trimmedName(originalDisplayName: String, startMs: Long, endMs: Long): String {
        val (base, ext) = splitExtension(originalDisplayName, fallbackExt = "mp4")
        return "${base}_trimmed_${formatTimestamp(startMs)}_${formatTimestamp(endMs)}.$ext"
    }

    /** "video.mp4" -> "video_compressed.mp4" */
    fun compressedName(originalDisplayName: String): String {
        val (base, ext) = splitExtension(originalDisplayName, fallbackExt = "mp4")
        return "${base}_compressed.$ext"
    }

    /** "video.mp4" -> "video_audio.mp3" (extension always forced to mp3 regardless of input) */
    fun extractedAudioName(originalDisplayName: String): String {
        val (base, _) = splitExtension(originalDisplayName, fallbackExt = "mp4")
        return "${base}_audio.mp3"
    }

    private fun splitExtension(name: String, fallbackExt: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot in 1 until name.length - 1) {
            name.substring(0, dot) to name.substring(dot + 1)
        } else {
            name to fallbackExt
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return "%02d-%02d-%02d".format(h, m, s)
    }
}
