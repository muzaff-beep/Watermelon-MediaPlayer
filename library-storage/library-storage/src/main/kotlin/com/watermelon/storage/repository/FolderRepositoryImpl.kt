package com.watermelon.storage.repository

import com.watermelon.common.model.FolderNode
import com.watermelon.common.repository.FolderRepository
import com.watermelon.storage.indexer.MediaStoreIndexer
import kotlinx.coroutines.flow.Flow

/**
 * [FolderRepository] surfacing the folder tree produced by Phase 1 of the
 * [MediaStoreIndexer]. The tree is emitted immediately on sweep so the UI renders fast.
 */
class FolderRepositoryImpl(
    private val indexer: MediaStoreIndexer
) : FolderRepository {

    override fun observeFolderTree(): Flow<List<FolderNode>> = indexer.folderTree

    override suspend fun getFolder(path: String): FolderNode? =
        indexer.folderTree.value.firstOrNull { it.path == path }
}
