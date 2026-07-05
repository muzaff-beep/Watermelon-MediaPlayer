package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.repository.SubtitleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for subtitle search, download, and sync-offset management. Network lookups are
 * only triggered by an explicit user action (Privacy §14).
 */
class SubtitleViewModel(
    private val subtitleRepository: SubtitleRepository
) : ViewModel() {

    private val _tracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val tracks: StateFlow<List<SubtitleTrack>> = _tracks.asStateFlow()

    private val _downloadedPath = MutableStateFlow<String?>(null)
    val downloadedPath: StateFlow<String?> = _downloadedPath.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    /** Render-time sync offset in ms (persisted in SubtitleOffsets by the storage layer). */
    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    fun search(mediaItem: MediaItem, preferredLanguages: List<String>) {
        viewModelScope.launch {
            _tracks.value = subtitleRepository.findSubtitles(mediaItem, preferredLanguages)
        }
    }

    fun download(track: SubtitleTrack) {
        viewModelScope.launch {
            _downloadError.value = null
            runCatching { subtitleRepository.downloadSubtitle(track) }
                .onSuccess { _downloadedPath.value = it }
                .onFailure { e ->
                    com.watermelon.common.util.FileLogger.e("Subtitle", "download failed", e)
                    _downloadError.value = "Couldn't download this subtitle. Please try another."
                }
        }
    }

    /** Up/Down keys on TV nudge the offset; mobile uses a slider. */
    fun nudgeOffset(deltaMs: Long) {
        _offsetMs.value += deltaMs
    }

    fun setOffset(offsetMs: Long) { _offsetMs.value = offsetMs }
}
