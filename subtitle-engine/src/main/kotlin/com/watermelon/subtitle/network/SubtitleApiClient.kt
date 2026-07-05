package com.watermelon.subtitle.network

import com.watermelon.common.model.SubtitleTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Ktor client for the OpenSubtitles API with language filtering and mirror failover
 * (Manifest §6.2). Network is only ever touched after an explicit user action (Privacy §14).
 */
class SubtitleApiClient(
    private val mirrors: MirrorRotator = MirrorRotator.DEFAULT,
    private val httpClient: HttpClient = HttpClient(Android),
    private val responseParser: (String) -> List<SubtitleTrack> = ::parseTracks
) {
    /**
     * Query subtitles for a given OpenSubtitles file [hash], filtered to [preferredLanguages].
     * Rotates to the next mirror on 5xx/timeout; fail-closed when mirrors are exhausted.
     */
    suspend fun query(
        hash: String,
        fileSize: Long,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> {
        mirrors.reset()
        while (true) {
            val outcome = runCatching {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    val response: HttpResponse = httpClient.get("${mirrors.current}/api/v1/subtitles") {
                        header("User-Agent", USER_AGENT)
                        parameter("moviehash", hash)
                        parameter("moviebytesize", fileSize)
                        if (preferredLanguages.isNotEmpty()) {
                            parameter("languages", preferredLanguages.joinToString(","))
                        }
                    }
                    response
                }
            }

            val response = outcome.getOrNull()
            when {
                response != null && response.status.isSuccess() ->
                    return responseParser(response.bodyAsText())
                        .filterByLanguages(preferredLanguages)

                // Server error or timeout → fail over to next mirror.
                response == null /* timeout/exception */ ||
                    response.status.value in 500..599 -> {
                    if (mirrors.advance() == null) return emptyList() // fail-closed
                }

                // 4xx / other → no point retrying other mirrors.
                else -> return emptyList()
            }
        }
    }

    private fun List<SubtitleTrack>.filterByLanguages(prefs: List<String>): List<SubtitleTrack> {
        if (prefs.isEmpty()) return this
        return filter { it.language in prefs }
            .sortedWith(compareBy({ prefs.indexOf(it.language) }, { -it.rating }))
    }

    fun close() = httpClient.close()

    companion object {
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val USER_AGENT = "Watermelon/1.0"

        /**
         * Parses a mirror's subtitle-search response into [SubtitleTrack]s.
         *
         * Two response shapes are supported, matching the two mirror families in
         * [MirrorRotator.DEFAULT]:
         *  - api.opensubtitles.com (v1): `{ "data": [ { "attributes": { ... } } ] }`
         *  - rest.opensubtitles.org (legacy): a flat JSON array of subtitle objects.
         *
         * Field names are matched defensively across both dialects (e.g. "language" or
         * "ISO639", "url" or "SubDownloadLink") so a single parser can serve either mirror.
         * Entries missing a language or a download URL are dropped rather than failing the
         * whole batch. Malformed/non-JSON bodies yield an empty list rather than throwing,
         * matching this client's fail-closed behaviour on the calling side.
         *
         * NOTE: field names are based on the mirrors' documented response shapes and have
         * not been verified against a live response in this environment (no network access
         * during development) — worth a quick sanity check against a real payload from
         * whichever mirror ends up primary before shipping.
         */
        fun parseTracks(body: String): List<SubtitleTrack> {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val entries: List<JsonObject> = when {
                root is JsonObject && root["data"] is JsonArray ->
                    (root.jsonObject["data"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                root is JsonArray -> root.mapNotNull { it as? JsonObject }
                else -> emptyList()
            }
            return entries.mapNotNull { it.toSubtitleTrackOrNull() }
        }

        private fun JsonObject.toSubtitleTrackOrNull(): SubtitleTrack? {
            // v1 entries nest fields under "attributes"; legacy entries are flat.
            val attrs = (this["attributes"] as? JsonObject) ?: this
            val language = attrs.stringOrNull("language") ?: attrs.stringOrNull("ISO639") ?: return null
            val downloadUrl = attrs.stringOrNull("url")
                ?: attrs.stringOrNull("download_url")
                ?: attrs.stringOrNull("SubDownloadLink")
                ?: return null
            val label = attrs.stringOrNull("release")
                ?: attrs.stringOrNull("SubFileName")
                ?: attrs.stringOrNull("file_name")
                ?: "$language subtitle"
            val rating = attrs.floatOrNull("ratings") ?: attrs.floatOrNull("SubRating") ?: 0f
            return SubtitleTrack(language = language, label = label, downloadUrl = downloadUrl, rating = rating)
        }

        private fun JsonObject.stringOrNull(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.floatOrNull(key: String): Float? =
            (this[key] as? JsonPrimitive)?.floatOrNull
    }
}
