package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.House
import com.pocketdev.dropway.domain.model.NavigationHouse
import com.pocketdev.dropway.domain.repository.NavigationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of NavigationRepository managing the driver's active route queue.
 */
class NavigationRepositoryImpl(
    private val localDb: LocalHouseDatabase
) : NavigationRepository {

    override suspend fun getNavigationStops(userId: String): AppResult<List<NavigationHouse>> = withContext(Dispatchers.IO) {
        try {
            val raw = localDb.getNavigationStops(userId)
            val domain = raw.map { NavigationHouse.fromDataModel(it) }
            AppResult.Success(domain)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to fetch navigation stops", cause = e)
        }
    }

    override suspend fun addToNavigation(
        house: House,
        trackingNumber: String?,
        navNote: String?,
        userId: String
    ): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val dataModel = House.toDataModel(house)
            val navId = localDb.addToNavigation(dataModel, trackingNumber, navNote, userId)
            AppResult.Success(navId)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to add to navigation", cause = e)
        }
    }

    override suspend fun updateDeliveryStatus(
        navId: String,
        status: String,
        deliveryNote: String?,
        reportReason: String?
    ): AppResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ok = localDb.updateDeliveryStatus(navId, status, deliveryNote, reportReason)
            AppResult.Success(ok)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to update delivery status", cause = e)
        }
    }

    override suspend fun updateDriverNote(navId: String, note: String): AppResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ok = localDb.updateDriverNote(navId, note)
            AppResult.Success(ok)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to update driver note", cause = e)
        }
    }

    override suspend fun deleteNavigationStop(navId: String): AppResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ok = localDb.deleteNavigationStop(navId)
            AppResult.Success(ok)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to delete navigation stop", cause = e)
        }
    }

    override suspend fun clearDeliveredStops(userId: String): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            val count = localDb.clearDeliveredStops(userId)
            AppResult.Success(count)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to clear delivered stops", cause = e)
        }
    }

    override suspend fun clearAllStops(userId: String): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            val count = localDb.clearAllStops(userId)
            AppResult.Success(count)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to clear all stops", cause = e)
        }
    }
}
