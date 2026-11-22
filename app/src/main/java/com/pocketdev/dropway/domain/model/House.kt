package com.pocketdev.dropway.domain.model

import org.json.JSONObject
import java.util.UUID

/**
 * Domain entity representing an address / delivery destination.
 */
data class House(
    val id: String = UUID.randomUUID().toString(),
    val houseNo: String = "",
    val village: String = "",
    val subdistrict: String = "",
    val district: String = "",
    val province: String = "",
    val postalCode: String = "",
    val fullName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String? = null,
    val landmark: String? = null,
    val quantity: Int = 1,
    val folderId: String = Folder.DEFAULT_FOLDER_ID,
    val creatorId: String? = null,
    val creatorName: String? = null,
    val sourceType: String = "personal",
    val isShared: Boolean = false,
    val shareToken: String? = null,
    val sharedByUserId: String? = null,
    val expiresAt: String? = null,
    val recordVersion: Int = 1,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val isDeleted: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val displayAddress: String
        get() {
            if (!address.isNullOrBlank()) return address
            val parts = listOfNotNull(
                houseNo.takeIf { it.isNotBlank() },
                village.takeIf { it.isNotBlank() },
                subdistrict.takeIf { it.isNotBlank() },
                district.takeIf { it.isNotBlank() },
                province.takeIf { it.isNotBlank() },
                postalCode.takeIf { it.isNotBlank() }
            )
            return parts.joinToString(" ")
        }

    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() } ?: "ไม่ระบุชื่อ"

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("houseNo", houseNo)
            put("village", village)
            put("subdistrict", subdistrict)
            put("district", district)
            put("province", province)
            put("postalCode", postalCode)
            put("fullName", fullName ?: JSONObject.NULL)
            put("phone", phone ?: JSONObject.NULL)
            put("address", address ?: JSONObject.NULL)
            if (lat != null) put("lat", lat) else put("lat", JSONObject.NULL)
            if (lng != null) put("lng", lng) else put("lng", JSONObject.NULL)
            put("note", note ?: JSONObject.NULL)
            put("landmark", landmark ?: JSONObject.NULL)
            put("quantity", quantity)
            put("folderId", folderId)
            put("creatorId", creatorId ?: JSONObject.NULL)
            put("creatorName", creatorName ?: JSONObject.NULL)
            put("sourceType", sourceType)
            put("isShared", isShared)
            put("shareToken", shareToken ?: JSONObject.NULL)
            put("sharedByUserId", sharedByUserId ?: JSONObject.NULL)
            put("expiresAt", expiresAt ?: JSONObject.NULL)
            put("recordVersion", recordVersion)
            put("syncStatus", syncStatus.name)
            put("isDeleted", isDeleted)
            put("createdAt", createdAt ?: JSONObject.NULL)
            put("updatedAt", updatedAt ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromDataModel(data: com.pocketdev.dropway.data.model.House): House {
            return House(
                id = data.id,
                fullName = data.fullName,
                phone = data.phone,
                address = data.address,
                lat = data.lat,
                lng = data.lng,
                note = data.note,
                quantity = data.quantity,
                creatorId = data.creatorId,
                creatorName = data.creatorName,
                sourceType = data.sourceType,
                isShared = data.isShared,
                shareToken = data.shareToken,
                sharedByUserId = data.sharedByUserId,
                expiresAt = data.expiresAt,
                recordVersion = data.recordVersion,
                syncStatus = SyncStatus.SYNCED,
                isDeleted = false,
                createdAt = data.createdAt,
                updatedAt = data.updatedAt
            )
        }

        fun toDataModel(domain: House): com.pocketdev.dropway.data.model.House {
            return com.pocketdev.dropway.data.model.House(
                id = domain.id,
                fullName = domain.fullName,
                phone = domain.phone,
                address = domain.address ?: domain.displayAddress,
                lat = domain.lat,
                lng = domain.lng,
                note = domain.note,
                quantity = domain.quantity,
                creatorId = domain.creatorId,
                creatorName = domain.creatorName,
                sourceType = domain.sourceType,
                isShared = domain.isShared,
                shareToken = domain.shareToken,
                sharedByUserId = domain.sharedByUserId,
                expiresAt = domain.expiresAt,
                recordVersion = domain.recordVersion,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )
        }

        fun fromJson(json: JSONObject): House {
            return House(
                id = json.optString("id", UUID.randomUUID().toString()),
                houseNo = json.optString("houseNo", ""),
                village = json.optString("village", ""),
                subdistrict = json.optString("subdistrict", ""),
                district = json.optString("district", ""),
                province = json.optString("province", ""),
                postalCode = json.optString("postalCode", ""),
                fullName = json.optString("fullName").takeIf { it.isNotBlank() && it != "null" },
                phone = json.optString("phone").takeIf { it.isNotBlank() && it != "null" },
                address = json.optString("address").takeIf { it.isNotBlank() && it != "null" },
                lat = if (json.has("lat") && !json.isNull("lat")) json.optDouble("lat") else null,
                lng = if (json.has("lng") && !json.isNull("lng")) json.optDouble("lng") else null,
                note = json.optString("note").takeIf { it.isNotBlank() && it != "null" },
                landmark = json.optString("landmark").takeIf { it.isNotBlank() && it != "null" },
                quantity = json.optInt("quantity", 1),
                folderId = json.optString("folderId", Folder.DEFAULT_FOLDER_ID),
                creatorId = json.optString("creatorId").takeIf { it.isNotBlank() && it != "null" },
                creatorName = json.optString("creatorName").takeIf { it.isNotBlank() && it != "null" },
                sourceType = json.optString("sourceType", "personal"),
                isShared = json.optBoolean("isShared", false),
                shareToken = json.optString("shareToken").takeIf { it.isNotBlank() && it != "null" },
                sharedByUserId = json.optString("sharedByUserId").takeIf { it.isNotBlank() && it != "null" },
                expiresAt = json.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" },
                recordVersion = json.optInt("recordVersion", 1),
                syncStatus = SyncStatus.fromString(json.optString("syncStatus", "SYNCED")),
                isDeleted = json.optBoolean("isDeleted", false),
                createdAt = json.optString("createdAt").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = json.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
