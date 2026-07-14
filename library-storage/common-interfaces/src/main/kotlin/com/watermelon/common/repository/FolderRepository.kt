package com.watermelon.common.repository

import com.watermelon.common.model.FolderNode
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolderTree(): Flow<List<FolderNode>>
    suspend fun getFolder(path: String): FolderNode?
}
