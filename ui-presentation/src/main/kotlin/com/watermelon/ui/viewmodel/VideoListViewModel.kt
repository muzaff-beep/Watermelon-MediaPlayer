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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListViewModel(
    private val mediaRepository: MediaRepository,
    private val folderPath: String,
    private val playlistRepository: PlaylistRepository? = null,
    private val isPlaylist: Boolean = false
) : ViewModel() {

    private val _isShuffled   = MutableStateFlow(false)
    private val _selection    = MutableStateFlow(SelectionState())

    val isShuffled: StateFlow<Boolean>       = _isShuffled.asStateFlow()
    val selection: StateFlow<SelectionState> = _selection.asStateFlow()

    private val sourceVideos: kotlinx.coroutines.flow.Flow<List<MediaItem>> =
        if (isPlaylist && playlistRepository != null) {
            com.watermelon.common.util.FileLogger.i("VideoList", "source = playlist '$folderPath'")
            playlistRepository.observeVideos(folderPath)
        } else {
            com.watermelon.common.util.FileLogger.i("VideoList", "source = folder '$folderPath'")
            mediaRepository.observeAllMedia().map { all ->
                all.filter { it.parentFolder == folderPath }
            }
        }

    val videos: StateFlow<List<MediaItem>> = combine(
        sourceVideos,
        _isShuffled
    ) { list, shuffled ->
        com.watermelon.common.util.FileLogger.i("VideoList",
            "videos emitted: ${list.size} (shuffled=$shuffled)")
        if (shuffled) list.shuffled() else list
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

    /**
     * Deletes the selected videos. On API 30+ scoped storage you cannot delete media you
     * don't own with a plain ContentResolver.delete — it throws RecoverableSecurityException
     * or silently no-ops. Instead we build a MediaStore delete request (IntentSender) that
     * the Activity launches to get the one-tap system consent dialog.
     *
     * Returns an IntentSender to launch (API 30+), or null if it deleted directly (older
     * APIs / owned media). After a successful launched delete, the Activity should call
     * [onDeleteConfirmed].
     */
    fun buildDeleteRequest(contentResolver: ContentResolver): android.content.IntentSender? {
        val rawUris = _selection.value.selectedUris
        if (rawUris.isEmpty()) return null

        // Rebuild each URI from its numeric ID against the canonical Video collection.
        // Passing stored URI strings directly to createDeleteRequest can throw
        // "Invalid Uri" if the string form isn't the exact volume-qualified content URI
        // MediaStore expects. ContentUris.withAppendedId guarantees a valid one.
        val uris = rawUris.mapNotNull { raw ->
            runCatching {
                val parsed = Uri.parse(raw)
                val id = android.content.ContentUris.parseId(parsed)
                android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
            }.getOrNull()
        }
        if (uris.isEmpty()) {
            com.watermelon.common.util.FileLogger.e("Delete", "no valid URIs to delete from ${rawUris.size} selected")
            return null
        }

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            com.watermelon.common.util.FileLogger.i("Delete", "createDeleteRequest for ${uris.size} uris")
            android.provider.MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        } else {
            com.watermelon.common.util.FileLogger.i("Delete", "direct delete for ${uris.size} uris (pre-30)")
            viewModelScope.launch(Dispatchers.IO) {
                uris.forEach { runCatching { contentResolver.delete(it, null, null) } }
                clearSelection()
                mediaRepository.refreshIndex()
            }
            null
        }
    }

    /** Called by the Activity after a launched delete request succeeds. */
    fun onDeleteConfirmed() {
        com.watermelon.common.util.FileLogger.i("Delete", "delete confirmed — clearing selection + refreshing")
        clearSelection()
        viewModelScope.launch { mediaRepository.refreshIndex() }
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

    fun createPlaylistAndAddSelected(name: String) {
        val repo = playlistRepository ?: return
        val uris = _selection.value.selectedUris.toList()
        viewModelScope.launch {
            val playlistId = repo.createPlaylist(name)
            uris.forEach { repo.addToPlaylist(playlistId, it) }
            clearSelection()
        }
    }
}
