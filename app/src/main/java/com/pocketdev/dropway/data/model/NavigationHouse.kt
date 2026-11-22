package com.pocketdev.dropway.data.model

import org.json.JSONObject

data class NavigationHouse(
    val navId: String,
    val userId: String = "default-user",
    val houseId: String? = null,
    val navNote: String? = null,
    val navPriority: Int = 0,
    val deliveryStatus: String = "pending", // "pending", "delivered", "reported"
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
    val createdAt: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): NavigationHouse {
            val navId = json.optString("nav_id", json.optString("id", ""))
            return NavigationHouse(
                navId = navId,
                userId = json.optString("user_id", "default-user"),
                houseId = json.optString("house_id").takeIf { it.isNotBlank() && it != "null" },
                navNote = json.optString("nav_note").takeIf { it.isNotBlank() && it != "null" },
                navPriority = json.optInt("nav_priority", 0),
                deliveryStatus = json.optString("delivery_status", "pending").ifBlank { "pending" },
                deliveryNote = json.optString("delivery_note").takeIf { it.isNotBlank() && it != "null" },
                deliveredAt = json.optString("delivered_at").takeIf { it.isNotBlank() && it != "null" },
                reportReason = json.optString("report_reason").takeIf { it.isNotBlank() && it != "null" },
                reportedAt = json.optString("reported_at").takeIf { it.isNotBlank() && it != "null" },
                driverNote = json.optString("driver_note").takeIf { it.isNotBlank() && it != "null" },
                trackingNumber = json.optString("tracking_number").takeIf { it.isNotBlank() && it != "null" },
                parcelCount = json.optInt("parcel_count", 1),
                fullName = json.optString("full_name").takeIf { it.isNotBlank() && it != "null" },
                phone = json.optString("phone").takeIf { it.isNotBlank() && it != "null" },
                address = json.optString("address").takeIf { it.isNotBlank() && it != "null" },
                lat = if (json.has("lat") && !json.isNull("lat")) json.optDouble("lat") else null,
                lng = if (json.has("lng") && !json.isNull("lng")) json.optDouble("lng") else null,
                note = json.optString("note").takeIf { it.isNotBlank() && it != "null" },
                quantity = json.optInt("quantity", 1),
                createdAt = json.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
