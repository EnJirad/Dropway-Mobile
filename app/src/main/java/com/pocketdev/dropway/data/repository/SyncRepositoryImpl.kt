package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.remote.api.ApiEndpoints
import com.pocketdev.dropway.data.remote.api.DropwayApiClient
import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.SyncMutation
import com.pocketdev.dropway.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementation of SyncRepository managing outbox persistence and cloud synchronizations.
 */
class SyncRepositoryImpl(
    private val localDb: LocalHouseDatabase,
    private val apiClient: DropwayApiClient
) : SyncRepository {

    override suspend fun queueMutation(mutation: SyncMutation): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.queueOutboxMutation(mutation)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to queue mutation", cause = e)
        }
    }

    override suspend fun getPendingMutations(): AppResult<List<SyncMutation>> = withContext(Dispatchers.IO) {
        try {
            val list = localDb.getPendingOutboxMutations()
            AppResult.Success(list)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to get pending mutations", cause = e)
        }
    }

    override suspend fun markMutationSynced(id: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.deleteOutboxMutation(id)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to mark mutation synced", cause = e)
        }
    }

    override suspend fun markMutationFailed(id: String, error: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.updateOutboxMutationStatus(id, "FAILED", error)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to mark mutation failed", cause = e)
        }
    }

    override suspend fun pushPendingMutations(): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            val pending = localDb.getPendingOutboxMutations()
            if (pending.isEmpty()) return@withContext AppResult.Success(0)

            val array = JSONArray()
            for (m in pending) {
                array.put(m.toJson())
            }
            val payload = JSONObject().apply {
                put("mutations", array)
            }

            val response = apiClient.post(ApiEndpoints.SYNC_PUSH, payload)
            if (response.isSuccessful) {
                for (m in pending) {
                    localDb.deleteOutboxMutation(m.id)
                }
                AppResult.Success(pending.size)
            } else {
                val err = response as com.pocketdev.dropway.data.remote.api.ApiResponse.Error
                for (m in pending) {
                    localDb.updateOutboxMutationStatus(m.id, "FAILED", err.message)
                }
                AppResult.Error(message = err.message, code = err.code)
            }
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to push mutations", cause = e)
        }
    }

    override suspend fun pullRemoteUpdates(since: Long): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${ApiEndpoints.SYNC_PULL}?since=$since"
            val response = apiClient.get(endpoint)
            if (response.isSuccessful) {
                // Ingest pulled updates
                AppResult.Success(0)
            } else {
                val err = response as com.pocketdev.dropway.data.remote.api.ApiResponse.Error
                AppResult.Error(message = err.message, code = err.code)
            }
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to pull updates", cause = e)
        }
    }

    override suspend fun getPendingMutationCount(): Int = withContext(Dispatchers.IO) {
        localDb.getPendingOutboxCount()
    }
}
