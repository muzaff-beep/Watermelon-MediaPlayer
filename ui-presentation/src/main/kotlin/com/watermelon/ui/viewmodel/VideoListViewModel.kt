package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.MediaItem
import com.watermelon.common.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lists [MediaItem]s for a folder. Shuffle state is per-folder-view and survives within the
 * same navigation destination. When [isShuffled], the list order is randomised; the
 * randomised order is re-seeded each time shuffle is toggled on.
 *
 * Arron's rule: shuffle applies to the folder (or playlist). Playlists carry their own
 * independent shuffle state.
 */
class VideoListViewModel(
    private val mediaRepository: MediaRepository,
    private val folderPath: String
) : ViewModel() {

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    val videos: StateFlow<List<MediaItem>> = combine(
        mediaRepository.observeAllMedia(),
        _isShuffled
    ) { all, shuffled ->
        val filtered = all.filter { it.parentFolder == folderPath }
        if (shuffled) filtered.shuffled() else filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch { mediaRepository.refreshIndex() }
    }

    /** Record that the user opened this URI — clears its ⭐ new-file badge. */
    fun markPlayed(uri: String) {
        viewModelScope.launch { mediaRepository.markAsPlayed(uri) }
    }

    /** Toggle shuffle on/off. Re-seeds the random order when enabling. */
    fun toggleShuffle() {
        _isShuffled.value = !_isShuffled.value
    }
}
