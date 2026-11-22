package com.pocketdev.dropway.data.model

import org.json.JSONObject
import java.util.UUID

data class House(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String? = null,
    val quantity: Int = 1,
    val creatorId: String? = null,
    val creatorName: String? = null,
    val sourceType: String = "personal", // "personal" or "shared"
    val isShared: Boolean = false,
    val shareToken: String? = null,
    val sharedByUserId: String? = null,
    val expiresAt: String? = null,
    val recordVersion: Int = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("full_name", fullName ?: JSONObject.NULL)
            put("phone", phone ?: JSONObject.NULL)
            put("address", address ?: JSONObject.NULL)
            if (lat != null) put("lat", lat) else put("lat", JSONObject.NULL)
            if (lng != null) put("lng", lng) else put("lng", JSONObject.NULL)
            put("note", note ?: JSONObject.NULL)
            put("quantity", quantity)
            put("creator_id", creatorId ?: JSONObject.NULL)
            put("creator_name", creatorName ?: JSONObject.NULL)
            put("source_type", sourceType)
            put("is_shared", isShared)
            put("share_token", shareToken ?: JSONObject.NULL)
            put("shared_by_user_id", sharedByUserId ?: JSONObject.NULL)
            put("expires_at", expiresAt ?: JSONObject.NULL)
            put("record_version", recordVersion)
            put("created_at", createdAt ?: JSONObject.NULL)
            put("updated_at", updatedAt ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): House {
            return House(
                id = json.optString("id", UUID.randomUUID().toString()),
                fullName = json.optString("full_name").takeIf { it.isNotBlank() && it != "null" },
                phone = json.optString("phone").takeIf { it.isNotBlank() && it != "null" },
                address = json.optString("address").takeIf { it.isNotBlank() && it != "null" },
                lat = if (json.has("lat") && !json.isNull("lat")) json.optDouble("lat") else null,
                lng = if (json.has("lng") && !json.isNull("lng")) json.optDouble("lng") else null,
                note = json.optString("note").takeIf { it.isNotBlank() && it != "null" },
                quantity = json.optInt("quantity", 1),
                creatorId = json.optString("creator_id").takeIf { it.isNotBlank() && it != "null" },
                creatorName = json.optString("creator_name").takeIf { it.isNotBlank() && it != "null" },
                sourceType = json.optString("source_type", "personal"),
                isShared = json.optBoolean("is_shared", false),
                shareToken = json.optString("share_token").takeIf { it.isNotBlank() && it != "null" },
                sharedByUserId = json.optString("shared_by_user_id").takeIf { it.isNotBlank() && it != "null" },
                expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() && it != "null" },
                recordVersion = json.optInt("record_version", 1),
                createdAt = json.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
