package com.pocketdev.dropway.data.model

import org.json.JSONObject

data class SameParcelMeta(
    val navId: String,
    val trackingNumber: String?,
    val createdAt: String,
    val houseId: String?,
    val deliveryStatus: String?,
    val fullName: String?,
    val address: String?
) {
    companion object {
        fun fromJson(json: JSONObject): SameParcelMeta {
            return SameParcelMeta(
                navId = json.optString("nav_id", ""),
                trackingNumber = json.optString("tracking_number").takeIf { it.isNotBlank() },
                createdAt = json.optString("created_at", ""),
                houseId = json.optString("house_id").takeIf { it.isNotBlank() },
                deliveryStatus = json.optString("delivery_status").takeIf { it.isNotBlank() },
                fullName = json.optString("full_name").takeIf { it.isNotBlank() },
                address = json.optString("address").takeIf { it.isNotBlank() }
            )
        }
    }
}
