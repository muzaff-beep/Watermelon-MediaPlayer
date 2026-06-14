package com.watermelon.subtitle.repository

import android.content.Context
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.model.VideoQuery
import com.watermelon.common.repository.SubtitleRepository
import com.watermelon.common.util.FileLogger
import com.watermelon.subtitle.network.SubtitleApiClient
import com.watermelon.subtitle.source.LocalSidecarSourceImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [SubtitleRepository] implementation.
 *
 * S1 priority order:
 *   0. Local sidecar — scan folder beside the video for matching .srt / .ass files (offline)
 *   1. Local cache   — previously downloaded subtitles
 *   2. Remote API    — online lookup (only after user consent, S3)
 *
 * [parsedFor] provides the render-ready [ParsedSubtitle] for the player. The two-step model
 * keeps [SubtitleTrack] (search candidates with download URLs) separate from [ParsedSubtitle]
 * (timed, bidi-formatted cues ready for display).
 */
class SubtitleRepositoryImpl(
    private val context: Context,
    private val apiClient: SubtitleApiClient = SubtitleApiClient()
) : SubtitleRepository {

    private val sidecarSource = LocalSidecarSourceImpl(context)
    private val downloadClient: HttpClient by lazy { HttpClient(Android) }
    private val cacheDir: File by lazy {
        File(context.cacheDir, "subtitles").apply { mkdirs() }
    }

    // ── SubtitleRepository interface ────────────────────────────────────────────

    override suspend fun findSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        // 1. Local cache match first.
        val cached = cachedTracks(mediaItem, preferredLanguages)
        if (cached.isNotEmpty()) {
            FileLogger.i("Subtitle", "cache hit: ${cached.size} track(s) for ${mediaItem.displayName}")
            return@withContext cached
        }
        // 2. Remote lookup (placeholder until S3 wires the reachability probe).
        val hash = runCatching { hashFor(mediaItem) }.getOrNull() ?: return@withContext emptyList()
        FileLogger.i("Subtitle", "remote lookup: hash=$hash")
        apiClient.query(hash, mediaItem.fileSize, preferredLanguages)
    }

    override suspend fun downloadSubtitle(track: SubtitleTrack): String =
        withContext(Dispatchers.IO) {
            val target = File(cacheDir, fileNameFor(track))
            downloadClient.get(track.downloadUrl).bodyAsChannel().copyTo(target.outputStream())
            FileLogger.i("Subtitle", "downloaded: ${target.name}")
            target.absolutePath
        }

    // ── S1 extension: parsed render-ready subtitle ──────────────────────────────

    /**
     * Returns a [ParsedSubtitle] ready for the player, using the S1 offline-first strategy:
     *   1. Sidecar file in the video's folder
     *   2. Previously downloaded + cached file
     *   (Online fetch is triggered separately via [findSubtitles] + [downloadSubtitle])
     */
    suspend fun parsedFor(mediaItem: MediaItem, preferredLanguages: List<String>): ParsedSubtitle? =
        withContext(Dispatchers.IO) {
            // Step 0: local sidecar (no network, no user action required)
            val query = VideoQuery(
                displayName  = mediaItem.displayName,
                parentFolder = mediaItem.parentFolder,
                sizeBytes    = mediaItem.fileSize,
                durationMs   = mediaItem.durationMs,
                languages    = preferredLanguages
            )
            val sidecar = sidecarSource.findAndParse(query)
            if (sidecar != null) {
                FileLogger.i("Subtitle", "sidecar loaded: ${sidecar.cues.size} cues (${sidecar.language})")
                return@withContext sidecar
            }
            // Step 1: cached download
            val cachedFile = firstCachedFile(mediaItem, preferredLanguages)
            if (cachedFile != null) {
                FileLogger.i("Subtitle", "cache loaded: ${cachedFile.name}")
                return@withContext parseCachedFile(cachedFile)
            }
            FileLogger.i("Subtitle", "no local subtitle for ${mediaItem.displayName}")
            null
        }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private fun cachedTracks(mediaItem: MediaItem, langs: List<String>): List<SubtitleTrack> {
        val prefix = safeKey(mediaItem.uri)
        return cacheDir.listFiles { f -> f.name.startsWith(prefix) }
            ?.mapNotNull { file ->
                val lang = file.nameWithoutExtension.substringAfterLast('.', "")
                if (langs.isEmpty() || lang in langs)
                    SubtitleTrack(lang, file.name, file.toURI().toString(), rating = 0f)
                else null
            }.orEmpty()
    }

    private fun firstCachedFile(mediaItem: MediaItem, langs: List<String>): File? {
        val prefix = safeKey(mediaItem.uri)
        val files = cacheDir.listFiles { f -> f.name.startsWith(prefix) } ?: return null
        if (langs.isEmpty()) return files.firstOrNull()
        return langs.firstNotNullOfOrNull { lang ->
            files.firstOrNull { it.nameWithoutExtension.endsWith(".$lang") }
        } ?: files.firstOrNull()
    }

    private fun parseCachedFile(file: File): ParsedSubtitle? {
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val lang = file.nameWithoutExtension.substringAfterLast('.', "").ifEmpty { null }
        return when (file.extension.lowercase()) {
            "srt" -> runCatching {
                com.watermelon.subtitle.parser.SrtParser.parse(content, lang)
            }.getOrNull()
            else  -> null
        }
    }

    private fun hashFor(mediaItem: MediaItem): String =
        safeKey(mediaItem.uri) + mediaItem.fileSize.toString(16)

    private fun fileNameFor(track: SubtitleTrack): String =
        "${safeKey(track.label)}.${track.language}.srt"

    private fun safeKey(raw: String): String = raw.hashCode().toUInt().toString(16)
}
