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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListViewModel(
    private val mediaRepository: MediaRepository,
    private val folderPath: String,
    private val playlistRepository: PlaylistRepository? = null,
    val isPlaylist: Boolean = false,
    /** True for the "Videos" bottom-nav tab: aggregates every video across every folder
     *  already tracked by the app (see [folderRepository]/[folderVisibilityStore] below),
     *  rather than one specific folder or playlist. [folderPath] is ignored in this mode. */
    val isAllVideos: Boolean = false,
    private val folderRepository: com.watermelon.common.repository.FolderRepository? = null,
    private val folderVisibilityStore: com.watermelon.common.repository.FolderVisibilityStore? = null
) : ViewModel() {

    private val _isShuffled   = MutableStateFlow(false)
    private val _selection    = MutableStateFlow(SelectionState())

    val isShuffled: StateFlow<Boolean>       = _isShuffled.asStateFlow()
    val selection: StateFlow<SelectionState> = _selection.asStateFlow()

    private val sourceVideos: kotlinx.coroutines.flow.Flow<List<MediaItem>> = when {
        isAllVideos && folderRepository != null -> {
            com.watermelon.common.util.FileLogger.i("VideoList", "source = all tracked videos")
            // Aggregates strictly from folders the app has already scanned/indexed
            // (FolderRepository.observeFolderTree, the exact same source the Folders
            // screen itself uses) — deliberately NOT a fresh recursive filesystem walk.
            // A folder is included as long as it's tracked, regardless of whether it's on
            // internal or external (SD card / USB) storage — FolderNode carries a
            // [FolderNode.volume] label but nothing filters on it, so both are included
            // automatically. Hidden folders (Settings > Folder visibility) are excluded,
            // matching how every other screen already treats "hidden" as "not part of
            // the library" rather than a videos-tab-specific rule.
            kotlinx.coroutines.flow.combine(
                mediaRepository.observeAllMedia(),
                folderRepository.observeFolderTree(),
                folderVisibilityStore?.visibilityVersion ?: kotlinx.coroutines.flow.MutableStateFlow(0)
            ) { allMedia, trackedFolders, _ ->
                val hidden = folderVisibilityStore?.getHiddenFolders() ?: emptySet()
                val trackedPaths = trackedFolders.map { it.path }.toSet() - hidden
                allMedia.filter { it.parentFolder in trackedPaths }
            }
        }
        isPlaylist && playlistRepository != null -> {
            com.watermelon.common.util.FileLogger.i("VideoList", "source = playlist '$folderPath'")
            playlistRepository.observeVideos(folderPath)
        }
        else -> {
            com.watermelon.common.util.FileLogger.i("VideoList", "source = folder '$folderPath'")
            mediaRepository.observeAllMedia().map { all ->
                all.filter { it.parentFolder == folderPath }
            }
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

    /**
     * Resolves the ordered URI list that PlaybackQueue should be seeded with when [tappedUri]
     * is played from the list currently shown by this screen ([currentlyDisplayed], in
     * display order).
     *
     * Continue Watching is a special case: it's a cross-folder list of in-progress videos
     * (see Playlist's PlaylistType.CONTINUE_WATCHING doc — "auto-populated from saved resume
     * positions"), existing purely to let someone resume something unfinished. It was never
     * meant to be a real playback context — next/previous or auto-advance from a video
     * opened there should behave exactly as if it had been opened from wherever it actually
     * lives, not hop to the next unrelated in-progress video. So when isPlaylist is true and
     * folderPath is the Continue Watching id, this queries all media in [tappedUri]'s real
     * parentFolder instead of using [currentlyDisplayed] directly. Every other screen
     * (folders, user playlists, Favourites, Recently Added) uses [currentlyDisplayed] as-is —
     * their next/prev genuinely is meant to walk that list.
     */
    suspend fun resolvePlaybackQueueUris(
        tappedUri: String,
        currentlyDisplayed: List<MediaItem>
    ): List<String> {
        val isContinueWatching = isPlaylist &&
            folderPath == com.watermelon.common.model.SystemPlaylist.ID_CONTINUE_WATCHING
        if (!isContinueWatching) return currentlyDisplayed.map { it.uri }

        val tapped = currentlyDisplayed.firstOrNull { it.uri == tappedUri }
            ?: mediaRepository.getByUri(tappedUri)
            ?: return listOf(tappedUri) // unresolvable — at least keep the tapped video playable

        val allMedia = mediaRepository.observeAllMedia().first()
        return allMedia
            .filter { it.parentFolder == tapped.parentFolder }
            .map { it.uri }
            .ifEmpty { listOf(tappedUri) }
    }

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

    /**
     * Removes the selected videos from the playlist currently being viewed. Only meaningful
     * when this VideoListViewModel was constructed with [isPlaylist] true.
     *
     * Routes to the right underlying mechanism depending on which playlist this is:
     *  - a user playlist → removeFromPlaylist (deletes rows from PlaylistItems)
     *  - Favourites → removeFromFavourites (a separate Favourites table, not PlaylistItems)
     *  - Recently Added / Continue Watching → no-op. Both are computed views (from
     *    dateAdded/firstSeenAt and PlaybackPositions respectively), not stored membership
     *    lists, so there's nothing to "remove" — the caller shouldn't offer this action for
     *    them in the first place (see isRemovable), but the guard keeps this safe either way.
     */
    fun removeSelectedFromPlaylist() {
        val repo = playlistRepository ?: return
        if (!isPlaylist || !isRemovable) return
        val uris = _selection.value.selectedUris.toList()
        viewModelScope.launch {
            if (folderPath == com.watermelon.common.model.SystemPlaylist.ID_FAVOURITES) {
                uris.forEach { repo.removeFromFavourites(it) }
            } else {
                uris.forEach { repo.removeFromPlaylist(folderPath, it) }
            }
            clearSelection()
        }
    }

    /** Whether the playlist currently being viewed supports removing items at all —
     *  false for the two purely-computed system playlists (Recently Added, Continue
     *  Watching), true for Favourites and any user playlist. Exposed so the screen can
     *  decide whether to show the "remove from playlist" action at all. */
    val isRemovable: Boolean =
        folderPath != com.watermelon.common.model.SystemPlaylist.ID_RECENTLY_ADDED &&
        folderPath != com.watermelon.common.model.SystemPlaylist.ID_CONTINUE_WATCHING
}
