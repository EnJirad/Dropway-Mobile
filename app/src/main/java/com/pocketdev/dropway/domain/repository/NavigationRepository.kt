package com.pocketdev.dropway.domain.repository

import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.House
import com.pocketdev.dropway.domain.model.NavigationHouse

/**
 * Repository interface defining navigation queue and delivery stop operations.
 */
interface NavigationRepository {
    suspend fun getNavigationStops(userId: String = "local-driver"): AppResult<List<NavigationHouse>>
    suspend fun addToNavigation(
        house: House,
        trackingNumber: String? = null,
        navNote: String? = null,
        userId: String = "local-driver"
    ): AppResult<String>
    suspend fun updateDeliveryStatus(
        navId: String,
        status: String,
        deliveryNote: String? = null,
        reportReason: String? = null
    ): AppResult<Boolean>
    suspend fun updateDriverNote(navId: String, note: String): AppResult<Boolean>
    suspend fun deleteNavigationStop(navId: String): AppResult<Boolean>
    suspend fun clearDeliveredStops(userId: String = "local-driver"): AppResult<Int>
    suspend fun clearAllStops(userId: String = "local-driver"): AppResult<Int>
}
