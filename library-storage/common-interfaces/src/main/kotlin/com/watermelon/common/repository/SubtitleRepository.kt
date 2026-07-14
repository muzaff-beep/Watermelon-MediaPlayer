package com.watermelon.common.repository

import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack

interface SubtitleRepository {
    suspend fun findSubtitles(mediaItem: MediaItem, preferredLanguages: List<String>): List<SubtitleTrack>
    suspend fun downloadSubtitle(track: SubtitleTrack): String  // returns local file path
}
