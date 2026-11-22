package com.pocketdev.dropway.domain.model

import org.json.JSONObject
import java.util.UUID

/**
 * Mutation record queued in the local outbox for cloud synchronization.
 */
data class SyncMutation(
    val id: String = UUID.randomUUID().toString(),
    val entityType: String, // "HOUSE", "FOLDER", "NAVIGATION"
    val entityId: String,
    val action: String, // "CREATE", "UPDATE", "DELETE"
    val payload: String, // JSON serialized data
    val clientTimestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val status: String = "PENDING" // "PENDING", "PROCESSING", "FAILED", "SYNCED"
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("entityType", entityType)
            put("entityId", entityId)
            put("action", action)
            put("payload", payload)
            put("clientTimestamp", clientTimestamp)
            put("retryCount", retryCount)
            if (lastError != null) put("lastError", lastError)
            put("status", status)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SyncMutation {
            return SyncMutation(
                id = json.optString("id", UUID.randomUUID().toString()),
                entityType = json.optString("entityType", "HOUSE"),
                entityId = json.optString("entityId", ""),
                action = json.optString("action", "CREATE"),
                payload = json.optString("payload", "{}"),
                clientTimestamp = json.optLong("clientTimestamp", System.currentTimeMillis()),
                retryCount = json.optInt("retryCount", 0),
                lastError = json.optString("lastError").takeIf { it.isNotBlank() && it != "null" },
                status = json.optString("status", "PENDING")
            )
        }
    }
}
