package com.pocketdev.dropway.domain.model

/**
 * Synchronization state for local records against the cloud authority.
 */
enum class SyncStatus {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT,
    ERROR;

    companion object {
        fun fromString(value: String?): SyncStatus {
            return when (value?.uppercase()) {
                "SYNCED" -> SYNCED
                "PENDING_INSERT" -> PENDING_INSERT
                "PENDING_UPDATE" -> PENDING_UPDATE
                "PENDING_DELETE" -> PENDING_DELETE
                "CONFLICT" -> CONFLICT
                "ERROR" -> ERROR
                else -> SYNCED
            }
        }
    }
}
