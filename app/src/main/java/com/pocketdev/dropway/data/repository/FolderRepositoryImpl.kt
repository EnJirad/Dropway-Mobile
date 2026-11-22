package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.Folder
import com.pocketdev.dropway.domain.model.SyncMutation
import com.pocketdev.dropway.domain.model.SyncStatus
import com.pocketdev.dropway.domain.repository.FolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of FolderRepository managing local folder hierarchies and association tables.
 */
class FolderRepositoryImpl(
    private val localDb: LocalHouseDatabase
) : FolderRepository {

    override suspend fun getFolders(includeArchived: Boolean): AppResult<List<Folder>> = withContext(Dispatchers.IO) {
        try {
            val list = localDb.getAllFolders(includeArchived)
            AppResult.Success(list)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to fetch folders", cause = e)
        }
    }

    override suspend fun getFolderById(id: String): AppResult<Folder?> = withContext(Dispatchers.IO) {
        try {
            val folder = localDb.getFolderById(id)
            AppResult.Success(folder)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to fetch folder", cause = e)
        }
    }

    override suspend fun createFolder(folder: Folder): AppResult<Folder> = withContext(Dispatchers.IO) {
        try {
            val prepared = folder.copy(syncStatus = SyncStatus.PENDING_INSERT)
            localDb.upsertFolder(prepared)

            val mutation = SyncMutation(
                entityType = "FOLDER",
                entityId = prepared.id,
                action = "CREATE",
                payload = prepared.toJson().toString()
            )
            localDb.queueOutboxMutation(mutation)

            AppResult.Success(prepared)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to create folder", cause = e)
        }
    }

    override suspend fun updateFolder(folder: Folder): AppResult<Folder> = withContext(Dispatchers.IO) {
        try {
            val prepared = folder.copy(syncStatus = SyncStatus.PENDING_UPDATE)
            localDb.upsertFolder(prepared)

            val mutation = SyncMutation(
                entityType = "FOLDER",
                entityId = prepared.id,
                action = "UPDATE",
                payload = prepared.toJson().toString()
            )
            localDb.queueOutboxMutation(mutation)

            AppResult.Success(prepared)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to update folder", cause = e)
        }
    }

    override suspend fun deleteFolder(id: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.deleteFolder(id)
            val mutation = SyncMutation(
                entityType = "FOLDER",
                entityId = id,
                action = "DELETE",
                payload = """{"id":"$id"}"""
            )
            localDb.queueOutboxMutation(mutation)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to delete folder", cause = e)
        }
    }

    override suspend fun getDefaultFolder(): AppResult<Folder> = withContext(Dispatchers.IO) {
        try {
            val folder = localDb.getFolderById(Folder.DEFAULT_FOLDER_ID) ?: Folder.createDefault()
            AppResult.Success(folder)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to fetch default folder", cause = e)
        }
    }

    override suspend fun assignHouseToFolder(folderId: String, houseId: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.assignHouseToFolder(folderId, houseId)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to assign house to folder", cause = e)
        }
    }

    override suspend fun removeHouseFromFolder(folderId: String, houseId: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.removeHouseFromFolder(folderId, houseId)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to remove house from folder", cause = e)
        }
    }

    override suspend fun getHouseIdsInFolder(folderId: String): AppResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val ids = localDb.getHouseIdsInFolder(folderId)
            AppResult.Success(ids)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to get houses in folder", cause = e)
        }
    }
}
