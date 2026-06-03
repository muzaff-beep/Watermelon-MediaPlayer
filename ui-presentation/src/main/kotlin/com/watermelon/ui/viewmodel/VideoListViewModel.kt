package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.MediaItem
import com.watermelon.common.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Lists the [MediaItem]s whose [MediaItem.parentFolder] matches [folderPath]. Backed by
 * [MediaRepository.observeAllMedia] so it updates live as Phase 2 enrichment lands rows.
 */
class VideoListViewModel(
    mediaRepository: MediaRepository,
    private val folderPath: String
) : ViewModel() {

    val videos: StateFlow<List<MediaItem>> =
        mediaRepository.observeAllMedia()
            .map { all -> all.filter { it.parentFolder == folderPath } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
