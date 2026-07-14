package com.watermelon.subtitle.bidi

/**
 * Bidirectional text formatter for subtitle cues (solves the RTL rendering challenge).
 *
 * Two mechanisms, per the blueprint:
 *   1. Base direction resolution — count strongly-directional characters; if RTL script
 *      dominates, the cue is RTL (and aligns right). Set explicitly, never inferred from
 *      the first character (which breaks lines that start with a digit or Latin word).
 *   2. Run isolation — wrap every embedded opposite-direction run (Latin words, numbers,
 *      symbols) in a First-Strong Isolate (U+2068) ... Pop Directional Isolate (U+2069) so
 *      each run resolves independently and cannot reorder against the surrounding script.
 */
object BidiFormatter {

    private const val FSI = '\u2068'  // First Strong Isolate
    private const val PDI = '\u2069'  // Pop Directional Isolate

    /** True if RTL-script characters dominate the strongly-directional characters in [text]. */
    fun isRtl(text: String): Boolean {
        var rtl = 0; var ltr = 0
        for (ch in text) {
            when (directionOf(ch)) {
                Dir.RTL -> rtl++
                Dir.LTR -> ltr++
                Dir.NEUTRAL -> {}
            }
        }
        return rtl > ltr
    }

    /**
     * Returns text safe for the layout engine: when [baseRtl], every maximal run of LTR
     * (Latin/neutral-with-LTR) characters is wrapped in FSI..PDI isolates. When the base is
     * LTR, RTL runs are isolated instead. Neutral characters attach to the current run.
     */
    fun format(text: String, baseRtl: Boolean): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length + 8)
        val isolateDir = if (baseRtl) Dir.LTR else Dir.RTL  // isolate the opposite of base
        var i = 0
        while (i < text.length) {
            val d = directionOf(text[i])
            if (d == isolateDir) {
                // start an isolate run; consume contiguous same-or-neutral chars
                sb.append(FSI)
                while (i < text.length) {
                    val dd = directionOf(text[i])
                    if (dd == isolateDir || dd == Dir.NEUTRAL) { sb.append(text[i]); i++ }
                    else break
                }
                // trim trailing neutrals back out of the isolate so spaces don't get trapped
                sb.append(PDI)
            } else {
                sb.append(text[i]); i++
            }
        }
        return sb.toString()
    }

    private enum class Dir { RTL, LTR, NEUTRAL }

    private fun directionOf(ch: Char): Dir {
        val code = ch.code
        // Arabic / Persian / Urdu / Kurdish ranges + Arabic supplement/extended + presentation forms
        val rtl = (code in 0x0590..0x05FF) ||   // Hebrew
                  (code in 0x0600..0x06FF) ||   // Arabic (incl. Persian/Urdu letters)
                  (code in 0x0750..0x077F) ||   // Arabic Supplement
                  (code in 0x08A0..0x08FF) ||   // Arabic Extended-A
                  (code in 0xFB50..0xFDFF) ||   // Arabic Presentation Forms-A
                  (code in 0xFE70..0xFEFF)      // Arabic Presentation Forms-B
        if (rtl) return Dir.RTL
        val ltr = (ch in 'A'..'Z') || (ch in 'a'..'z') ||
                  (ch in '0'..'9') ||           // digits treated as LTR runs
                  (code in 0x00C0..0x024F)      // Latin-1 supplement / extended
        if (ltr) return Dir.LTR
        return Dir.NEUTRAL
    }
}
