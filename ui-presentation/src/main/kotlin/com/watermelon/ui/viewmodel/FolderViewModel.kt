package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.FolderNode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the folder browser. Combines the Phase-1 folder tree with the Phase-2
 * media flow to enrich each [FolderNode] with [FolderNode.totalDurationMs] once metadata
 * is available. Screens emit [UserIntent]; this VM translates them into repository calls.
 */
class FolderViewModel(
    private val folderRepository: FolderRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    /**
     * Folder tree enriched with total playtime per folder. Updates whenever Phase 1 emits
     * a new tree OR Phase 2 completes and mediaRepository emits a new media batch.
     * folder.path == item.parentFolder because both use BUCKET_DISPLAY_NAME.
     */
    val folderTree: StateFlow<List<FolderNode>> = combine(
        folderRepository.observeFolderTree(),
        mediaRepository.observeAllMedia()
    ) { folders, allMedia ->
        val durationByFolder = allMedia
            .groupBy { it.parentFolder }
            .mapValues { (_, items) -> items.sumOf { it.durationMs } }
        folders.map { folder ->
            folder.copy(totalDurationMs = durationByFolder[folder.path] ?: 0L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    /** Single MVI entry point. */
    fun onIntent(intent: UserIntent) {
        when (intent) {
            is UserIntent.RefreshLibrary -> refresh()
            else -> Unit // folder screen only handles library-level intents
        }
    }

    private fun refresh() {
        viewModelScope.launch { mediaRepository.refreshIndex() }
    }

    suspend fun folderAt(path: String): FolderNode? = folderRepository.getFolder(path)
}
