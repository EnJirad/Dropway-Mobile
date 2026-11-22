package com.pocketdev.dropway.domain.repository

import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.Folder

/**
 * Repository interface defining folder and territory organization operations.
 */
interface FolderRepository {
    suspend fun getFolders(includeArchived: Boolean = false): AppResult<List<Folder>>
    suspend fun getFolderById(id: String): AppResult<Folder?>
    suspend fun createFolder(folder: Folder): AppResult<Folder>
    suspend fun updateFolder(folder: Folder): AppResult<Folder>
    suspend fun deleteFolder(id: String): AppResult<Unit>
    suspend fun getDefaultFolder(): AppResult<Folder>
    suspend fun assignHouseToFolder(folderId: String, houseId: String): AppResult<Unit>
    suspend fun removeHouseFromFolder(folderId: String, houseId: String): AppResult<Unit>
    suspend fun getHouseIdsInFolder(folderId: String): AppResult<List<String>>
}
