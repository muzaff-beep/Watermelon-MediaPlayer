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
import com.watermelon.common.repository.FolderVisibilityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Browser rows, tagged so the UI can render section headers:
 *   "My Video Playlists"  → Recently Added, Favourites, user playlists
 *   "Main Storage"        → storage folders
 */
sealed interface BrowserRow {
    data class Header(val title: String) : BrowserRow
    data class Folder(val node: FolderNode) : BrowserRow
}

/**
 * MVI ViewModel for the folder browser.
 *
 * Order:
 *   [My Video Playlists]
 *     Recently Added, Favourites, user playlists…
 *   [Main Storage]
 *     storage folders (hidden ones filtered out)
 */
class FolderViewModel(
    private val folderRepository: FolderRepository,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsStore: FolderVisibilityStore
) : ViewModel() {

    // Bumped when folder visibility changes, to re-run the combine.
    private val visibilityVersion = MutableStateFlow(0)

    val rows: StateFlow<List<BrowserRow>> = combine(
        folderRepository.observeFolderTree(),
        mediaRepository.observeAllMedia(),
        playlistRepository.observeAll(),
        visibilityVersion
    ) { folders, allMedia, playlists, _ ->
        val durationByFolder = allMedia
            .groupBy { it.parentFolder }
            .mapValues { (_, items) -> items.sumOf { it.durationMs } }
        val newFolderSet = allMedia
            .filter { it.lastPlayedAt == null }
            .map { it.parentFolder }
            .toSet()
        val hidden = settingsStore.getHiddenFolders()

        val storageFolders = folders
            .filter { it.path !in hidden }
            .map { folder ->
                folder.copy(
                    totalDurationMs = durationByFolder[folder.path] ?: 0L,
                    hasNewFiles     = folder.path in newFolderSet
                )
            }

        val playlistNodes = playlists.map { playlist ->
            FolderNode(
                path            = playlist.id,
                displayName     = playlist.name,
                itemCount       = playlist.itemCount,
                children        = emptyList(),
                totalDurationMs = playlist.totalDurationMs,
                playlistId      = playlist.id,
                playlistType    = playlist.type
            )
        }
        val systemNodes = playlistNodes.filter { it.isSystemPlaylist }
            .sortedBy { if (it.playlistId == SystemPlaylist.ID_RECENTLY_ADDED) 0 else 1 }
        val userNodes = playlistNodes.filter { it.playlistType == PlaylistType.USER }

        buildList {
            add(BrowserRow.Header("My Video Playlists"))
            (systemNodes + userNodes).forEach { add(BrowserRow.Folder(it)) }
            if (storageFolders.isNotEmpty()) {
                add(BrowserRow.Header("Main Storage"))
                storageFolders.forEach { add(BrowserRow.Folder(it)) }
            }
        }.also {
            com.watermelon.common.util.FileLogger.i("Folder",
                "rows built: ${it.size} rows | media=${allMedia.size} playlists=${playlists.size} " +
                "storage=${storageFolders.size} hidden=${hidden.size}")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * All folders (unfiltered) for the visibility settings screen, paired with each folder's
     * current visibility so the UI reacts immediately to toggles (was previously stale until
     * leaving/re-entering the screen, since this flow never observed visibilityVersion).
     */
    val allFoldersForSettings: StateFlow<List<Pair<FolderNode, Boolean>>> =
        kotlinx.coroutines.flow.combine(
            folderRepository.observeFolderTree(),
            visibilityVersion
        ) { tree, _ ->
            tree.map { it to settingsStore.isFolderVisible(it.path) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Backward-compatible flat folder list (playlists + visible storage folders, no headers).
     * Retained so existing FolderBrowserScreen keeps compiling until Phase E migrates it to [rows].
     */
    val folderTree: StateFlow<List<FolderNode>> = rows
        .let { rowsFlow ->
            kotlinx.coroutines.flow.MutableStateFlow<List<FolderNode>>(emptyList()).also { out ->
                viewModelScope.launch {
                    rowsFlow.collect { list ->
                        out.value = list.filterIsInstance<BrowserRow.Folder>().map { it.node }
                    }
                }
            }
        }

    init { refresh() }

    fun onIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.RefreshLibrary -> refresh()
            else -> Unit
        }
    }

    fun isFolderVisible(path: String): Boolean = settingsStore.isFolderVisible(path)

    fun setFolderHidden(path: String, hidden: Boolean) {
        com.watermelon.common.util.FileLogger.i("Visibility", "setFolderHidden($path, hidden=$hidden)")
        settingsStore.setFolderHidden(path, hidden)
        visibilityVersion.value += 1
        com.watermelon.common.util.FileLogger.i("Visibility",
            "hidden set now: ${settingsStore.getHiddenFolders()}")
    }

    private fun refresh() {
        viewModelScope.launch { mediaRepository.refreshIndex() }
    }
}