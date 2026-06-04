package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watermelon.common.model.FolderNode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the folder browser. Screens emit [UserIntent]; this VM translates them
 * into repository calls and exposes the folder tree as a [StateFlow] (Teams §6 MVI Pattern).
 */
class FolderViewModel(
    private val folderRepository: FolderRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val folderTree: StateFlow<List<FolderNode>> =
        folderRepository.observeFolderTree()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
