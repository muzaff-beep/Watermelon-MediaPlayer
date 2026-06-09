package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.common.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    /** All playlists in display order: Recently Added, Favourites, user playlists. */
    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentVideos = MutableStateFlow<List<MediaItem>>(emptyList())
    val currentVideos: StateFlow<List<MediaItem>> = _currentVideos.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    fun loadVideos(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.observeVideos(playlistId).collect { videos ->
                _currentVideos.value =
                    if (_isShuffled.value) videos.shuffled() else videos
            }
        }
    }

    fun toggleShuffle() { _isShuffled.value = !_isShuffled.value }

    fun addToFavourites(uri: String) {
        viewModelScope.launch { playlistRepository.addToFavourites(uri) }
    }

    fun removeFromFavourites(uri: String) {
        viewModelScope.launch { playlistRepository.removeFromFavourites(uri) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch { playlistRepository.renamePlaylist(playlistId, newName) }
    }

    fun addToPlaylist(playlistId: String, uri: String) {
        viewModelScope.launch { playlistRepository.addToPlaylist(playlistId, uri) }
    }

    fun removeFromPlaylist(playlistId: String, uri: String) {
        viewModelScope.launch { playlistRepository.removeFromPlaylist(playlistId, uri) }
    }
}

