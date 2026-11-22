package com.pocketdev.dropway.data.repository

import android.content.Context
import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.model.NavigationHouse
import com.pocketdev.dropway.data.model.SameParcelMeta
import com.pocketdev.dropway.data.sharing.VaultCryptoManager
import com.pocketdev.dropway.data.sharing.VaultMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository providing 100% Offline-First access to Dropway Mobile Vault & Navigation.
 * All operations operate against local SQLite storage with zero remote server dependency.
 */
class LocalHouseRepository(
    private val context: Context,
    private val localDb: LocalHouseDatabase,
    private val prefs: PreferencesManager
) {

    suspend fun initialize(): Int = withContext(Dispatchers.IO) {
        localDb.seedFromAssetsIfEmpty()
    }

    suspend fun getHouses(sourceType: String? = null): List<House> = withContext(Dispatchers.IO) {
        localDb.getAllHouses(sourceType)
    }

    suspend fun saveHouse(house: House): Result<House> = withContext(Dispatchers.IO) {
        runCatching {
            val creatorId = house.creatorId ?: prefs.userId
            val creatorName = house.creatorName ?: prefs.driverName
            val prepared = house.copy(
                creatorId = creatorId,
                creatorName = creatorName,
                sourceType = house.sourceType
            )
            localDb.upsertHouse(prepared, creatorId, creatorName)
            prepared
        }
    }

    suspend fun deleteHouse(houseId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.deleteHouse(houseId)
            Unit
        }
    }

    suspend fun getHouseCount(): Int = withContext(Dispatchers.IO) {
        localDb.getHouseCount()
    }

    // -----------------------------------------------------------------------------------------
    // Navigation Operations
    // -----------------------------------------------------------------------------------------

    suspend fun getNavigationStops(): Result<List<NavigationHouse>> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.getNavigationStops(prefs.userId)
        }
    }

    suspend fun addToNavigation(
        house: House,
        trackingNumber: String? = null,
        navNote: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.addToNavigation(house, trackingNumber, navNote, prefs.userId)
        }
    }

    suspend fun updateDeliveryStatus(
        navId: String,
        status: String,
        deliveryNote: String? = null,
        reportReason: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.updateDeliveryStatus(navId, status, deliveryNote, reportReason)
        }
    }

    suspend fun updateDriverNote(navId: String, note: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.updateDriverNote(navId, note)
        }
    }

    suspend fun deleteNavigationStop(navId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.deleteNavigationStop(navId)
        }
    }

    suspend fun clearDeliveredStops(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.clearDeliveredStops(prefs.userId)
        }
    }

    suspend fun clearAllStops(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.clearAllStops(prefs.userId)
        }
    }

    suspend fun checkExistingParcel(trackingNumber: String): Result<SameParcelMeta?> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.checkExistingParcel(trackingNumber)
        }
    }

    /**
     * Reorders navigation stops in the database based on ordered navId list.
     */
    suspend fun reorderNavigationStops(orderedNavIds: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            localDb.updateNavigationPriorities(orderedNavIds)
        }
    }

    /**
     * Optimizes navigation stops order using Nearest-Neighbor TSP algorithm from current coordinates.
     */
    suspend fun optimizeNavigationRoute(startLat: Double?, startLng: Double?): Result<com.pocketdev.dropway.util.RouteOptimizer.OptimizationResult> = withContext(Dispatchers.Default) {
        runCatching {
            val stops = localDb.getNavigationStops(prefs.userId)
            val result = com.pocketdev.dropway.util.RouteOptimizer.optimize(startLat, startLng, stops)
            // Persist the new sequence in the local database
            val orderedIds = result.optimizedStops.map { it.navId }
            localDb.updateNavigationPriorities(orderedIds)
            result
        }
    }

    /**
     * Imports houses from Map Marker KML, KMZ, or CSV.
     */
    suspend fun importFromMapMarker(
        inputStream: java.io.InputStream,
        filename: String
    ): Result<com.pocketdev.dropway.util.MapMarkerImporter.ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val summary = com.pocketdev.dropway.util.MapMarkerImporter.parse(
                inputStream = inputStream,
                filename = filename,
                creatorId = prefs.userId,
                creatorName = prefs.driverName
            )
            if (summary.validHouses.isNotEmpty()) {
                localDb.batchUpsertHouses(
                    houses = summary.validHouses,
                    creatorId = prefs.userId,
                    creatorName = prefs.driverName
                )
            }
            summary
        }
    }

    // -----------------------------------------------------------------------------------------
    // Proprietary .dwv Encrypted Sharing Container
    // -----------------------------------------------------------------------------------------

    suspend fun exportDwvVault(vaultTitle: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = localDb.exportToDwv(
                vaultTitle = vaultTitle,
                creatorId = prefs.userId,
                creatorName = prefs.driverName
            )
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "Dropway_${prefs.driverName}_$dateStr.dwv")
            file.writeBytes(bytes)
            file
        }
    }

    suspend fun inspectDwv(bytes: ByteArray): VaultMetadata? = withContext(Dispatchers.Default) {
        VaultCryptoManager.inspectVaultHeader(bytes)
    }

    suspend fun importDwvVault(bytes: ByteArray): Result<LocalHouseDatabase.DwvImportResult> = withContext(Dispatchers.IO) {
        localDb.importFromDwv(bytes, prefs.isSubscribed)
    }
}
