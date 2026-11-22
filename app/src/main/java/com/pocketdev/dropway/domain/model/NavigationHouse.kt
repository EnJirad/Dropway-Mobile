package com.pocketdev.dropway.domain.model

import org.json.JSONObject
import java.util.UUID

/**
 * Domain entity representing an active navigation stop in the driver's delivery route.
 */
data class NavigationHouse(
    val navId: String = "nav_" + UUID.randomUUID().toString(),
    val userId: String = "local-driver",
    val houseId: String? = null,
    val folderId: String = Folder.DEFAULT_FOLDER_ID,
    val navNote: String? = null,
    val navPriority: Int = 0,
    val deliveryStatus: String = "pending", // "pending", "en_route", "delivered", "reported"
    val deliveryNote: String? = null,
    val deliveredAt: String? = null,
    val reportReason: String? = null,
    val reportedAt: String? = null,
    val driverNote: String? = null,
    val trackingNumber: String? = null,
    val parcelCount: Int = 1,
    val fullName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String? = null,
    val quantity: Int = 1,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val createdAt: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("navId", navId)
            put("userId", userId)
            put("houseId", houseId ?: JSONObject.NULL)
            put("folderId", folderId)
            put("navNote", navNote ?: JSONObject.NULL)
            put("navPriority", navPriority)
            put("deliveryStatus", deliveryStatus)
            put("deliveryNote", deliveryNote ?: JSONObject.NULL)
            put("deliveredAt", deliveredAt ?: JSONObject.NULL)
            put("reportReason", reportReason ?: JSONObject.NULL)
            put("reportedAt", reportedAt ?: JSONObject.NULL)
            put("driverNote", driverNote ?: JSONObject.NULL)
            put("trackingNumber", trackingNumber ?: JSONObject.NULL)
            put("parcelCount", parcelCount)
            put("fullName", fullName ?: JSONObject.NULL)
            put("phone", phone ?: JSONObject.NULL)
            put("address", address ?: JSONObject.NULL)
            if (lat != null) put("lat", lat) else put("lat", JSONObject.NULL)
            if (lng != null) put("lng", lng) else put("lng", JSONObject.NULL)
            put("note", note ?: JSONObject.NULL)
            put("quantity", quantity)
            put("syncStatus", syncStatus.name)
            put("createdAt", createdAt ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromDataModel(data: com.pocketdev.dropway.data.model.NavigationHouse): NavigationHouse {
            return NavigationHouse(
                navId = data.navId,
                userId = data.userId,
                houseId = data.houseId,
                navNote = data.navNote,
                navPriority = data.navPriority,
                deliveryStatus = data.deliveryStatus,
                deliveryNote = data.deliveryNote,
                deliveredAt = data.deliveredAt,
                reportReason = data.reportReason,
                reportedAt = data.reportedAt,
                driverNote = data.driverNote,
                trackingNumber = data.trackingNumber,
                parcelCount = data.parcelCount,
                fullName = data.fullName,
                phone = data.phone,
                address = data.address,
                lat = data.lat,
                lng = data.lng,
                note = data.note,
                quantity = data.quantity,
                syncStatus = SyncStatus.SYNCED,
                createdAt = data.createdAt
            )
        }

        fun toDataModel(domain: NavigationHouse): com.pocketdev.dropway.data.model.NavigationHouse {
            return com.pocketdev.dropway.data.model.NavigationHouse(
                navId = domain.navId,
                userId = domain.userId,
                houseId = domain.houseId,
                navNote = domain.navNote,
                navPriority = domain.navPriority,
                deliveryStatus = domain.deliveryStatus,
                deliveryNote = domain.deliveryNote,
                deliveredAt = domain.deliveredAt,
                reportReason = domain.reportReason,
                reportedAt = domain.reportedAt,
                driverNote = domain.driverNote,
                trackingNumber = domain.trackingNumber,
                parcelCount = domain.parcelCount,
                fullName = domain.fullName,
                phone = domain.phone,
                address = domain.address,
                lat = domain.lat,
                lng = domain.lng,
                note = domain.note,
                quantity = domain.quantity,
                createdAt = domain.createdAt ?: ""
            )
        }
    }
}
