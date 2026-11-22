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
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
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
                createdAt = json.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = json.optString("updated_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}
