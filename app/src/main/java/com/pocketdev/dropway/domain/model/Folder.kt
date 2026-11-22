package com.pocketdev.dropway.domain.model

import org.json.JSONObject
import java.util.UUID

/**
 * Domain entity representing an address folder / territory grouping.
 */
data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val color: String = "#3B82F6",
    val icon: String = "folder",
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val houseCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("color", color)
            put("icon", icon)
            put("isDefault", isDefault)
            put("isArchived", isArchived)
            put("sortOrder", sortOrder)
            put("houseCount", houseCount)
            put("syncStatus", syncStatus.name)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    companion object {
        const val DEFAULT_FOLDER_ID = "mae_sot_default"
        const val DEFAULT_FOLDER_NAME = "Mae Sot Deliveries"

        fun createDefault(): Folder {
            return Folder(
                id = DEFAULT_FOLDER_ID,
                name = DEFAULT_FOLDER_NAME,
                description = "Primary zone delivery database for Mae Sot",
                color = "#0D9488",
                icon = "home",
                isDefault = true,
                sortOrder = 0,
                syncStatus = SyncStatus.SYNCED
            )
        }

        fun fromJson(json: JSONObject): Folder {
            return Folder(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Untitled Folder"),
                description = json.optString("description", ""),
                color = json.optString("color", "#3B82F6"),
                icon = json.optString("icon", "folder"),
                isDefault = json.optBoolean("isDefault", false),
                isArchived = json.optBoolean("isArchived", false),
                sortOrder = json.optInt("sortOrder", 0),
                houseCount = json.optInt("houseCount", 0),
                syncStatus = SyncStatus.fromString(json.optString("syncStatus", "SYNCED")),
                createdAt = json.optString("createdAt", ""),
                updatedAt = json.optString("updatedAt", "")
            )
        }
    }
}
