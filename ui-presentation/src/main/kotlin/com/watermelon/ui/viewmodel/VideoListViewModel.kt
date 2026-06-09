package com.watermelon.ui.viewmodel

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SelectionState
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListViewModel(
    private val mediaRepository: MediaRepository,
    private val folderPath: String,
    private val playlistRepository: PlaylistRepository? = null
) : ViewModel() {

    private val _isShuffled   = MutableStateFlow(false)
    private val _selection    = MutableStateFlow(SelectionState())

    val isShuffled: StateFlow<Boolean>       = _isShuffled.asStateFlow()
    val selection: StateFlow<SelectionState> = _selection.asStateFlow()

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

    fun markPlayed(uri: String) {
        viewModelScope.launch { mediaRepository.markAsPlayed(uri) }
    }

    fun toggleShuffle() { _isShuffled.value = !_isShuffled.value }

    // ── Selection ─────────────────────────────────────────────────────────────

    fun onLongPress(uri: String) {
        _selection.value = SelectionState(selectedUris = setOf(uri))
    }

    fun onToggleSelect(uri: String) {
        if (!_selection.value.isActive) return
        _selection.value = _selection.value.toggle(uri)
    }

    fun selectAll() {
        _selection.value = _selection.value.selectAll(videos.value.map { it.uri })
    }

    fun clearSelection() {
        _selection.value = _selection.value.clear()
    }

    // ── Batch actions ─────────────────────────────────────────────────────────

    /** Returns an ACTION_SEND_MULTIPLE Intent ready to pass to startActivity. */
    fun buildShareIntent(): Intent {
        val uris = ArrayList(_selection.value.selectedUris.map { Uri.parse(it) })
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type  = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun deleteSelected(contentResolver: ContentResolver) {
        val uris = _selection.value.selectedUris.toList()
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uriStr ->
                runCatching {
                    val uri = Uri.parse(uriStr)
                    contentResolver.delete(uri, null, null)
                }
            }
            clearSelection()
            mediaRepository.refreshIndex()
        }
    }

    fun addSelectedToFavourites() {
        val repo = playlistRepository ?: return
        val uris = _selection.value.selectedUris.toList()
        viewModelScope.launch {
            uris.forEach { repo.addToFavourites(it) }
            clearSelection()
        }
    }

    fun addSelectedToPlaylist(playlistId: String) {
        val repo = playlistRepository ?: return
        val uris = _selection.value.selectedUris.toList()
        viewModelScope.launch {
            uris.forEach { repo.addToPlaylist(playlistId, it) }
            clearSelection()
        }
    }
}
