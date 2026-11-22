package com.pocketdev.dropway.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.remote.NeonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository providing Offline-First access to Home Vault.
 * All core operations (Read, Create, Update, Delete, Search) operate against local SQLite.
 */
class LocalHouseRepository(
    private val context: Context,
    private val localDb: LocalHouseDatabase,
    private val neonClient: NeonClient,
    private val prefs: PreferencesManager
) {

    /**
     * Initializes local repository: seeds 4,530 houses from assets if local database is empty.
     */
    suspend fun initialize(): Int = withContext(Dispatchers.IO) {
        localDb.seedFromAssetsIfEmpty()
    }

    /**
     * Retrieves all houses from local storage.
     */
    suspend fun getHouses(sourceType: String? = null): List<House> = withContext(Dispatchers.IO) {
        localDb.getAllHouses(sourceType)
    }

    /**
     * Saves or updates a house in local storage, preserving creator identity.
     */
    suspend fun saveHouse(house: House): Result<House> = withContext(Dispatchers.IO) {
        runCatching {
            val creatorId = house.creatorId ?: prefs.userId
            val creatorName = house.creatorName ?: "User-${prefs.userId.takeLast(4)}"
            val prepared = house.copy(
                creatorId = creatorId,
                creatorName = creatorName,
                sourceType = house.sourceType
            )
            localDb.upsertHouse(prepared, creatorId, creatorName)

            // Optional background remote sync if Neon DB is configured
            if (prefs.isConfigured()) {
                try {
                    neonClient.createHouse(prepared)
                } catch (_: Exception) {}
            }
            prepared
        }
    }

    /**
     * Deletes a house from local storage.
     */
    suspend fun deleteHouse(houseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.deleteHouse(houseId)

            // Optional background remote deletion if Neon DB is configured
            if (prefs.isConfigured()) {
                try {
                    neonClient.deleteHouse(houseId)
                } catch (_: Exception) {}
            }
            Unit
        }
    }

    /**
     * Exports local vault to a shareable JSON backup file.
     */
    suspend fun exportBackupFile(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = localDb.exportToJson(prefs.userId)
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val exportDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupFile = File(exportDir, "dropway_backup_$dateStr.json")
            backupFile.writeText(jsonText, Charsets.UTF_8)
            backupFile
        }
    }

    /**
     * Imports houses from a JSON backup string with validation.
     */
    suspend fun importBackup(jsonText: String): LocalHouseDatabase.ImportResult = withContext(Dispatchers.IO) {
        localDb.importFromJson(jsonText)
    }

    /**
     * Returns total count of houses in local vault.
     */
    suspend fun getHouseCount(): Int = withContext(Dispatchers.IO) {
        localDb.getHouseCount()
    }
}
