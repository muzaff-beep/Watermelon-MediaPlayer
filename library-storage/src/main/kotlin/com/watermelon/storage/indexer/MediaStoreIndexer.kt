package com.watermelon.storage.indexer

import com.watermelon.common.model.FolderNode
import com.watermelon.common.model.IndexingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Orchestrates the two-phase index (Manifest §5.2): Phase 1 fast sweep emits the folder tree
 * immediately, then Phase 2 extracts metadata in the background. Exposes [indexingState] and
 * the latest [folderTree]. A 60-minute cache window gates re-scans.
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

    suspend fun refresh(force: Boolean = false) {
        if (!refreshMutex.tryLock()) return
        try {
            val now = System.currentTimeMillis()
            if (!force && now - lastScanAt < CACHE_WINDOW_MS) return
            _indexingState.value = IndexingState.SWEEPING
            val tree = phase1Sweep.sweep()
            _folderTree.value = tree
            _indexingState.value = IndexingState.EXTRACTING
            phase2Extractor.extract(mediaUriProvider())
            _indexingState.value = IndexingState.COMPLETE
            lastScanAt = now
        } finally {
            refreshMutex.unlock()
        }
    }

    companion object {
        const val CACHE_WINDOW_MS = 60 * 60 * 1000L  // 60-minute cache window
    }
}
