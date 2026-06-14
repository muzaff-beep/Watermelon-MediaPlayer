package com.watermelon.subtitle.source

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.subtitle.parser.SrtParser
import java.io.File

class LocalSidecarSourceImpl {
    /**
     * Loads and parses a local subtitle file.
     * Currently supports only .srt files.
     */
    fun loadSubtitles(filePath: String): List<ParsedSubtitle> {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return emptyList()

        val content = file.readText()
        return if (filePath.endsWith(".srt", ignoreCase = true)) {
            SrtParser.parse(content)
        } else {
            emptyList()
        }
    }
}
