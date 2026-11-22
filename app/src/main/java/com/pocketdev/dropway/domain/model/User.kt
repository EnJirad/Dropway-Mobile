package com.pocketdev.dropway.domain.model

import org.json.JSONObject

/**
 * Authenticated user profile and session identity.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val role: String = "driver", // "user", "driver", "admin"
    val planType: String = "free", // "free", "pro", "fleet"
    val authToken: String = "",
    val lastSyncedAt: Long = 0L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("email", email)
            put("displayName", displayName)
            if (phoneNumber != null) put("phoneNumber", phoneNumber)
            put("role", role)
            put("planType", planType)
            put("authToken", authToken)
            put("lastSyncedAt", lastSyncedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): User {
            return User(
                id = json.optString("id"),
                email = json.optString("email"),
                displayName = json.optString("displayName", "Courier Driver"),
                phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() && it != "null" },
                role = json.optString("role", "driver"),
                planType = json.optString("planType", "free"),
                authToken = json.optString("authToken", ""),
                lastSyncedAt = json.optLong("lastSyncedAt", 0L)
            )
        }
    }
}
