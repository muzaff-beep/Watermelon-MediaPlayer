package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.FolderNode
import com.watermelon.common.model.PlaylistType
import com.watermelon.common.model.SystemPlaylist
import com.watermelon.common.model.UserIntent
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the folder browser.
 *
 * Folder list order:
 *   1. Recently Added  (system playlist, always first)
 *   2. Favourites      (system playlist, always second)
 *   3. User playlists  (if any)
 *   4. Storage folders (from MediaStore)
 */
class FolderViewModel(
    private val folderRepository: FolderRepository,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val folderTree: StateFlow<List<FolderNode>> = combine(
        folderRepository.observeFolderTree(),
        mediaRepository.observeAllMedia(),
        playlistRepository.observeAll()
    ) { folders, allMedia, playlists ->
        val durationByFolder = allMedia
            .groupBy { it.parentFolder }
            .mapValues { (_, items) -> items.sumOf { it.durationMs } }
        val newFolderSet = allMedia
            .filter { it.lastPlayedAt == null }
            .map { it.parentFolder }
            .toSet()

        // Storage folders enriched with duration + new-file badge
        val storageFolders = folders.map { folder ->
            folder.copy(
                totalDurationMs = durationByFolder[folder.path] ?: 0L,
                hasNewFiles     = folder.path in newFolderSet
            )
        }

        // Playlist nodes mapped to FolderNode
        val playlistNodes = playlists.map { playlist ->
            FolderNode(
                path         = playlist.id,
                displayName  = playlist.name,
                itemCount    = playlist.itemCount,
                children     = emptyList(),
                totalDurationMs = playlist.totalDurationMs,
                playlistId   = playlist.id,
                playlistType = playlist.type
            )
        }

        // System playlists always first two, user playlists next, storage folders last
        val systemNodes = playlistNodes.filter { it.isSystemPlaylist }
            .sortedBy { if (it.playlistId == SystemPlaylist.ID_RECENTLY_ADDED) 0 else 1 }
        val userNodes = playlistNodes.filter { it.playlistType == PlaylistType.USER }

        systemNodes + userNodes + storageFolders
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun onIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.RefreshLibrary -> refresh()
            else -> Unit
        }
    }

    private fun refresh() {
        viewModelScope.launch { mediaRepository.refreshIndex() }
    }

    suspend fun folderAt(path: String): FolderNode? = folderRepository.getFolder(path)
}
