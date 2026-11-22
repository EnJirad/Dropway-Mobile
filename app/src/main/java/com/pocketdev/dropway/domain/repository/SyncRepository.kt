package com.pocketdev.dropway.domain.repository

import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.SyncMutation

/**
 * Repository interface defining synchronization and outbox operations.
 */
interface SyncRepository {
    suspend fun queueMutation(mutation: SyncMutation): AppResult<Unit>
    suspend fun getPendingMutations(): AppResult<List<SyncMutation>>
    suspend fun markMutationSynced(id: String): AppResult<Unit>
    suspend fun markMutationFailed(id: String, error: String): AppResult<Unit>
    suspend fun pullRemoteUpdates(since: Long = 0L): AppResult<Int>
    suspend fun pushPendingMutations(): AppResult<Int>
    suspend fun getPendingMutationCount(): Int
}
