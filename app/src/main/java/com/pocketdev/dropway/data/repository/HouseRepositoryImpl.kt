package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.remote.api.DropwayApiClient
import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.Folder
import com.pocketdev.dropway.domain.model.House
import com.pocketdev.dropway.domain.model.SyncMutation
import com.pocketdev.dropway.domain.model.SyncStatus
import com.pocketdev.dropway.domain.repository.HouseRepository
import com.pocketdev.dropway.util.SearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of HouseRepository bridging local SQLite storage, outbox mutation tracking, and remote sync.
 */
class HouseRepositoryImpl(
    private val localDb: LocalHouseDatabase,
    private val prefs: PreferencesManager,
    private val apiClient: DropwayApiClient? = null
) : HouseRepository {

    override suspend fun getHouses(folderId: String?, query: String?): AppResult<List<House>> = withContext(Dispatchers.IO) {
        try {
            val rawList = if (!folderId.isNullOrBlank() && folderId != Folder.DEFAULT_FOLDER_ID) {
                localDb.getHousesInFolder(folderId)
            } else {
                localDb.getAllHouses()
            }

            val domainList = rawList.map { House.fromDataModel(it) }

            val filtered = if (!query.isNullOrBlank()) {
                val q = query.trim()
                domainList.filter { h ->
                    val dataModel = House.toDataModel(h)
                    SearchMatcher.matchHouseWithSearch(dataModel, q) > 0
                }
            } else {
                domainList
            }

            AppResult.Success(filtered)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to query houses", cause = e)
        }
    }

    override suspend fun getHouseById(id: String): AppResult<House?> = withContext(Dispatchers.IO) {
        try {
            val all = localDb.getAllHouses()
            val match = all.firstOrNull { it.id == id }?.let { House.fromDataModel(it) }
            AppResult.Success(match)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to find house", cause = e)
        }
    }

    override suspend fun saveHouse(house: House): AppResult<House> = withContext(Dispatchers.IO) {
        try {
            val creatorId = house.creatorId ?: prefs.userId
            val creatorName = house.creatorName ?: prefs.driverName
            val prepared = house.copy(
                creatorId = creatorId,
                creatorName = creatorName,
                syncStatus = SyncStatus.PENDING_UPDATE
            )

            val dataModel = House.toDataModel(prepared)
            localDb.upsertHouse(dataModel, creatorId, creatorName)

            // Queue outbox mutation for offline sync engine
            val mutation = SyncMutation(
                entityType = "HOUSE",
                entityId = prepared.id,
                action = if (house.recordVersion > 1) "UPDATE" else "CREATE",
                payload = prepared.toJson().toString()
            )
            localDb.queueOutboxMutation(mutation)

            AppResult.Success(prepared)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to save house", cause = e)
        }
    }

    override suspend fun deleteHouse(id: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.deleteHouse(id)
            val mutation = SyncMutation(
                entityType = "HOUSE",
                entityId = id,
                action = "DELETE",
                payload = """{"id":"$id"}"""
            )
            localDb.queueOutboxMutation(mutation)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to delete house", cause = e)
        }
    }

    override suspend fun getHouseCount(): Int = withContext(Dispatchers.IO) {
        localDb.getHouseCount()
    }

    override suspend fun searchHouses(query: String): AppResult<List<House>> = getHouses(null, query)

    override suspend fun seedFromAssetsIfEmpty(): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            val seeded = localDb.seedFromAssetsIfEmpty()
            AppResult.Success(seeded)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to seed houses", cause = e)
        }
    }
}
