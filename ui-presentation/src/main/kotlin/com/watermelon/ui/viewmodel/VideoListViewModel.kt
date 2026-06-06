package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.MediaItem
import com.watermelon.common.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lists the [MediaItem]s whose [MediaItem.parentFolder] matches [folderPath]. Backed by
 * [MediaRepository.observeAllMedia] so it updates when Phase 2 completes and reloadCache runs.
 *
 * [refresh] triggers a fresh MediaStore scan — wired to VideoListScreen's pull-to-refresh.
 */
class VideoListViewModel(
    private val mediaRepository: MediaRepository,   // private val so refresh() can call it
    private val folderPath: String
) : ViewModel() {

    val videos: StateFlow<List<MediaItem>> =
        mediaRepository.observeAllMedia()
            .map { all -> all.filter { it.parentFolder == folderPath } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch { mediaRepository.refreshIndex() }
    }

    /** Record that the user opened this URI — clears its ⭐ new-file badge. */
    fun markPlayed(uri: String) {
        viewModelScope.launch { mediaRepository.markAsPlayed(uri) }
    }
}
