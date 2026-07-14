package com.watermelon.subtitle.source

import android.content.Context
import android.os.Environment
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleFormat
import com.watermelon.common.model.VideoQuery
import com.watermelon.common.util.FileLogger
import com.watermelon.subtitle.parser.SrtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * Scans the folder beside a video for matching sidecar subtitle files. Used internally by
 * [SubtitleRepositoryImpl] as the offline-first step — always tried before any network call.
 *
 * Matches: movie.mp4 → movie.srt, movie.fa.srt, movie.farsi.srt, movie.ar.srt ...
 * Language is inferred from the filename's secondary extension (fa/ar/ur/ku + aliases).
 * Encoding: auto-detects UTF-8; falls back to Windows-1256 for legacy FA/AR subtitles.
 */
class LocalSidecarSourceImpl(private val context: Context) {

    /**
     * Memoizes [resolveFolder] results by the raw `parentFolder` string (including a cached
     * `null` for "not found"). Without this, every video's subtitle lookup that couldn't be
     * resolved via a direct path join fell through to [findDirNamed] — a recursive scan of
     * the entire external storage tree (depth 4) — and browsing a folder with many videos
     * repeated that full scan once per video. `Collections.synchronizedMap` (rather than
     * `ConcurrentHashMap`) is used because it allows null values, needed to cache negative
     * results. A benign race (two lookups miss the cache at once and both scan) is possible
     * but harmless — it only costs one duplicate scan, never an incorrect result.
     */
    private val resolvedFolderCache: MutableMap<String, File?> =
        java.util.Collections.synchronizedMap(mutableMapOf())

    /**
     * Find and return the best-matching parsed subtitle for [query], or null if none found.
     * Tries candidates in language-preference order.
     */
    suspend fun findAndParse(query: VideoQuery): ParsedSubtitle? =
        withContext(Dispatchers.IO) {
            val folder = resolveFolder(query.parentFolder) ?: run {
                FileLogger.i("Subtitle", "sidecar: folder not found: ${query.parentFolder}")
                return@withContext null
            }
            val baseName = query.displayName.substringBeforeLast('.').lowercase()
            val candidates = mutableListOf<Triple<File, String?, Float>>() // file, lang, score

            folder.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                val ext = f.extension.lowercase()
                if (ext !in SUBTITLE_EXTS) return@forEach
                val fileBase = f.name.substringBeforeLast('.').lowercase()
                if (!fileBase.startsWith(baseName)) return@forEach
                val lang = inferLanguage(f.name)
                val score = if (fileBase == baseName) 1.0f else 0.8f
                candidates += Triple(f, lang, score)
            }

            if (candidates.isEmpty()) {
                FileLogger.i("Subtitle", "sidecar: no matches for $baseName")
                return@withContext null
            }

            // Sort: prefer requested languages, then exact-name matches
            val sorted = candidates.sortedWith(
                compareByDescending<Triple<File, String?, Float>> { (_, lang, _) ->
                    val li = query.languages.indexOf(lang ?: "")
                    if (li >= 0) query.languages.size - li else 0
                }.thenByDescending { it.third }
            )

            FileLogger.i("Subtitle", "sidecar: ${candidates.size} candidate(s); trying ${sorted.first().first.name}")

            for ((file, lang, _) in sorted) {
                val result = parseFile(file, lang)
                if (result != null && result.cues.isNotEmpty()) return@withContext result
            }
            null
        }

    private fun parseFile(file: File, language: String?): ParsedSubtitle? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val content = decode(bytes)
        return when (file.extension.lowercase()) {
            "srt" -> runCatching { SrtParser.parse(content, language) }.getOrNull()
            else  -> { FileLogger.i("Subtitle", "sidecar: ${file.extension} not yet supported"); null }
        }
    }

    private fun resolveFolder(parentFolder: String): File? {
        if (resolvedFolderCache.containsKey(parentFolder)) return resolvedFolderCache[parentFolder]
        val resolved = resolveFolderUncached(parentFolder)
        resolvedFolderCache[parentFolder] = resolved
        return resolved
    }

    private fun resolveFolderUncached(parentFolder: String): File? {
        File(parentFolder).let { if (it.isAbsolute && it.isDirectory) return it }
        val roots = listOfNotNull(
            Environment.getExternalStorageDirectory(),
            context.getExternalFilesDir(null)?.parentFile
        )
        for (root in roots) {
            File(root, parentFolder).let { if (it.isDirectory) return it }
        }
        return findDirNamed(Environment.getExternalStorageDirectory(), parentFolder, 4)
    }

    private fun findDirNamed(root: File, name: String, maxDepth: Int): File? {
        if (maxDepth < 0 || !root.isDirectory) return null
        if (root.name.equals(name, ignoreCase = true)) return root
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) findDirNamed(child, name, maxDepth - 1)?.let { return it }
        }
        return null
    }

    private fun inferLanguage(fileName: String): String? {
        val parts = fileName.lowercase().substringBeforeLast('.').split('.', '_', '-')
        for (p in parts.asReversed()) { LANG_ALIASES[p]?.let { return it } }
        return null
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { String(bytes, Charset.forName("windows-1256")) }.getOrDefault(utf8)
    }

    companion object {
        private val SUBTITLE_EXTS = setOf("srt", "ass", "ssa", "vtt")
        private val LANG_ALIASES = mapOf(
            "fa" to "fa", "far" to "fa", "farsi" to "fa", "persian" to "fa", "per" to "fa",
            "ar" to "ar", "ara" to "ar", "arabic" to "ar",
            "ur" to "ur", "urd" to "ur", "urdu" to "ur",
            "ku" to "ku", "kur" to "ku", "kurdish" to "ku",
            "en" to "en", "eng" to "en", "english" to "en"
        )
    }
}
