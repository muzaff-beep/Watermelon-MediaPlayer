package com.watermelon.storage.indexer

import com.watermelon.common.model.FolderNode
import com.watermelon.common.model.IndexingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates the two-phase index (Manifest §5.2): Phase 1 fast sweep emits the folder tree
 * immediately, then Phase 2 extracts metadata in the background. Exposes [indexingState] and
 * the latest [folderTree]. A 60-minute cache window gates re-scans.
 *
 * [refresh] always waits for any in-flight scan to finish before returning — it previously used
 * `Mutex.tryLock()` and silently no-opped for every caller except the one that won the race.
 * Because [com.watermelon.storage.repository.MediaRepositoryImpl.refreshIndex] unconditionally
 * reloads the in-memory media cache from the DB immediately after `refresh()` returns, a caller
 * whose `tryLock()` failed would reload the cache *before* the concurrently-running scan had
 * finished writing new/updated rows — leaving the app's media list (and everything derived from
 * it, like the Recently Added count) stuck on a stale pre-scan snapshot until some later,
 * unrelated refresh happened to win the race. This was independent of folder-visibility
 * filtering, since it corrupted the upstream media list itself before any visibility filter
 * ever saw it.
 */
class MediaStoreIndexer(
    private val phase1Sweep: Phase1Sweep,
    private val phase2Extractor: Phase2Extractor,
    private val mediaUriProvider: suspend () -> List<String>
) {
    private val _indexingState = MutableStateFlow(IndexingState.IDLE)
    val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()

    private val _folderTree = MutableStateFlow<List<FolderNode>>(emptyList())
    val folderTree: StateFlow<List<FolderNode>> = _folderTree.asStateFlow()

    @Volatile
    private var lastScanAt: Long = 0L

    private val refreshMutex = Mutex()

    suspend fun refresh(force: Boolean = false) = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        // Re-check the cache window *inside* the lock: if this call was waiting behind another
        // caller's scan, that scan may have just satisfied this request already, so a
        // non-forced call can skip re-scanning. A forced call always re-scans — at worst this
        // means two back-to-back scans if two force=true calls race, which is wasteful but
        // still correct (unlike the previous tryLock() behavior, which could skip scanning
        // *and* still reload a stale cache).
        if (!force && now - lastScanAt < CACHE_WINDOW_MS) return@withLock
        _indexingState.value = IndexingState.SWEEPING
        val tree = phase1Sweep.sweep()
        _folderTree.value = tree
        _indexingState.value = IndexingState.EXTRACTING
        phase2Extractor.extract(mediaUriProvider())
        _indexingState.value = IndexingState.COMPLETE
        lastScanAt = System.currentTimeMillis()
    }

    companion object {
        const val CACHE_WINDOW_MS = 60 * 60 * 1000L  // 60-minute cache window
    }
}