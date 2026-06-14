package com.watermelon.subtitle.parser

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.model.SubtitleFormat
import com.watermelon.subtitle.bidi.BidiFormatter

/**
 * Parses SubRip (.srt) content into a [ParsedSubtitle]. Each cue's base direction is resolved
 * and its text is bidi-formatted at parse time so the renderer can draw it directly.
 *
 * Robustness: tolerates CRLF/LF, BOM, blank-line variations, missing indices.
 * HTML-ish tags (<i>, <b>, <font>) are stripped to plain text (styling arrives with ASS/S2).
 * Legacy encoding (Windows-1256 for FA/AR) is handled by the caller before passing content.
 */
object SrtParser {

    private val TIME = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val TAG  = Regex("""</?[a-zA-Z][^>]*>""")

    fun parse(content: String, language: String? = null): ParsedSubtitle {
        val text = content
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val blocks = text.split(Regex("\n[ \t]*\n"))
        val cues = ArrayList<SubtitleCue>(blocks.size)
        var idx = 0

        for (block in blocks) {
            val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timingLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (timingLineIdx < 0) continue
            val timing = lines[timingLineIdx]
            val times = TIME.findAll(timing).toList()
            if (times.size < 2) continue
            val start = times[0].toMs()
            val end   = times[1].toMs()
            val body  = lines.drop(timingLineIdx + 1).joinToString("\n")
            if (body.isBlank()) continue
            val clean = TAG.replace(body, "").trim()
            val rtl = BidiFormatter.isRtl(clean)
            val display = clean.split("\n").joinToString("\n") { BidiFormatter.format(it, rtl) }
            cues += SubtitleCue(
                index = idx++, startMs = start, endMs = end,
                rawText = clean, baseRtl = rtl, displayText = display
            )
        }
        return ParsedSubtitle(
            cues = cues.sortedBy { it.startMs },
            language = language
        )
    }

    private fun MatchResult.toMs(): Long {
        val (h, m, s, ms) = destructured
        val msPadded = ms.padEnd(3, '0').take(3)
        return h.toLong() * 3_600_000 + m.toLong() * 60_000 +
               s.toLong() * 1_000 + msPadded.toLong()
    }
}
