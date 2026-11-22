package com.pocketdev.dropway.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.model.NavigationHouse
import com.pocketdev.dropway.data.model.SameParcelMeta
import com.pocketdev.dropway.data.sharing.VaultCryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local-First SQLite Database for Dropway Mobile 2.0 (Offline-First Secure Vault).
 * Manages Home Vault, Navigation Queue, Scanned Parcels, and .dwv Encrypted Container Import/Export.
 */
class LocalHouseDatabase(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "dropway_vault.db"
        const val DATABASE_VERSION = 3

        // Tables
        const val TABLE_HOUSES = "houses"
        const val TABLE_NAVIGATION_STOPS = "navigation_stops"
        const val TABLE_SCANNED_PARCELS = "scanned_parcels"

        // Houses Columns
        const val COL_ID = "id"
        const val COL_FULL_NAME = "full_name"
        const val COL_PHONE = "phone"
        const val COL_ADDRESS = "address"
        const val COL_LAT = "lat"
        const val COL_LNG = "lng"
        const val COL_NOTE = "note"
        const val COL_QUANTITY = "quantity"
        const val COL_CREATOR_ID = "creator_id"
        const val COL_CREATOR_NAME = "creator_name"
        const val COL_SOURCE_TYPE = "source_type"
        const val COL_IS_SHARED = "is_shared"
        const val COL_SHARE_TOKEN = "share_token"
        const val COL_SHARED_BY_USER_ID = "shared_by_user_id"
        const val COL_EXPIRES_AT = "expires_at"
        const val COL_RECORD_VERSION = "record_version"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"

        // Navigation Columns
        const val COL_NAV_ID = "nav_id"
        const val COL_USER_ID = "user_id"
        const val COL_HOUSE_ID = "house_id"
        const val COL_NAV_NOTE = "nav_note"
        const val COL_NAV_PRIORITY = "nav_priority"
        const val COL_DELIVERY_STATUS = "delivery_status"
        const val COL_DELIVERY_NOTE = "delivery_note"
        const val COL_DELIVERED_AT = "delivered_at"
        const val COL_REPORT_REASON = "report_reason"
        const val COL_REPORTED_AT = "reported_at"
        const val COL_DRIVER_NOTE = "driver_note"
        const val COL_TRACKING_NUMBER = "tracking_number"
        const val COL_PARCEL_COUNT = "parcel_count"

        // Parcels Columns
        const val COL_SCANNED_AT = "scanned_at"

        @Volatile
        private var INSTANCE: LocalHouseDatabase? = null

        fun getInstance(context: Context): LocalHouseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalHouseDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Houses Table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HOUSES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_FULL_NAME TEXT,
                $COL_PHONE TEXT,
                $COL_ADDRESS TEXT,
                $COL_LAT REAL,
                $COL_LNG REAL,
                $COL_NOTE TEXT,
                $COL_QUANTITY INTEGER NOT NULL DEFAULT 1,
                $COL_CREATOR_ID TEXT,
                $COL_CREATOR_NAME TEXT,
                $COL_SOURCE_TYPE TEXT NOT NULL DEFAULT 'personal',
                $COL_IS_SHARED INTEGER NOT NULL DEFAULT 0,
                $COL_SHARE_TOKEN TEXT,
                $COL_SHARED_BY_USER_ID TEXT,
                $COL_EXPIRES_AT TEXT,
                $COL_RECORD_VERSION INTEGER NOT NULL DEFAULT 1,
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_UPDATED_AT TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_phone ON $TABLE_HOUSES($COL_PHONE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_address ON $TABLE_HOUSES($COL_ADDRESS);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_name ON $TABLE_HOUSES($COL_FULL_NAME);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_created ON $TABLE_HOUSES($COL_CREATED_AT DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_source ON $TABLE_HOUSES($COL_SOURCE_TYPE);")

        // 2. Navigation Stops Table
        createNavigationTable(db)

        // 3. Scanned Parcels Table
        createParcelsTable(db)
    }

    private fun createNavigationTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAVIGATION_STOPS (
                $COL_NAV_ID TEXT PRIMARY KEY,
                $COL_USER_ID TEXT NOT NULL DEFAULT 'local-driver',
                $COL_HOUSE_ID TEXT,
                $COL_NAV_NOTE TEXT,
                $COL_NAV_PRIORITY INTEGER NOT NULL DEFAULT 0,
                $COL_DELIVERY_STATUS TEXT NOT NULL DEFAULT 'pending',
                $COL_DELIVERY_NOTE TEXT,
                $COL_DELIVERED_AT TEXT,
                $COL_REPORT_REASON TEXT,
                $COL_REPORTED_AT TEXT,
                $COL_DRIVER_NOTE TEXT,
                $COL_TRACKING_NUMBER TEXT,
                $COL_PARCEL_COUNT INTEGER NOT NULL DEFAULT 1,
                $COL_FULL_NAME TEXT,
                $COL_PHONE TEXT,
                $COL_ADDRESS TEXT,
                $COL_LAT REAL,
                $COL_LNG REAL,
                $COL_NOTE TEXT,
                $COL_QUANTITY INTEGER NOT NULL DEFAULT 1,
                $COL_CREATED_AT TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nav_user ON $TABLE_NAVIGATION_STOPS($COL_USER_ID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nav_status ON $TABLE_NAVIGATION_STOPS($COL_DELIVERY_STATUS);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nav_tracking ON $TABLE_NAVIGATION_STOPS($COL_TRACKING_NUMBER);")
    }

    private fun createParcelsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCANNED_PARCELS (
                $COL_TRACKING_NUMBER TEXT PRIMARY KEY,
                $COL_NAV_ID TEXT,
                $COL_HOUSE_ID TEXT,
                $COL_SCANNED_AT TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_CREATOR_ID TEXT;")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_CREATOR_NAME TEXT;")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_SOURCE_TYPE TEXT NOT NULL DEFAULT 'personal';")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_IS_SHARED INTEGER NOT NULL DEFAULT 0;")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_SHARE_TOKEN TEXT;")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_SHARED_BY_USER_ID TEXT;")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_EXPIRES_AT TEXT;")
            db.execSQL("ALTER TABLE $TABLE_HOUSES ADD COLUMN $COL_RECORD_VERSION INTEGER NOT NULL DEFAULT 1;")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_source ON $TABLE_HOUSES($COL_SOURCE_TYPE);")
        }
        if (oldVersion < 3) {
            createNavigationTable(db)
            createParcelsTable(db)
        }
    }

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
    }

    // -----------------------------------------------------------------------------------------
    // Houses Operations
    // -----------------------------------------------------------------------------------------

    private fun cursorToHouse(cursor: Cursor): House {
        val latIdx = cursor.getColumnIndexOrThrow(COL_LAT)
        val lngIdx = cursor.getColumnIndexOrThrow(COL_LNG)
        val lat = if (cursor.isNull(latIdx)) null else cursor.getDouble(latIdx)
        val lng = if (cursor.isNull(lngIdx)) null else cursor.getDouble(lngIdx)

        return House(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
            fullName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FULL_NAME)),
            phone = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHONE)),
            address = cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDRESS)),
            lat = lat,
            lng = lng,
            note = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE)),
            quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)),
            creatorId = cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATOR_ID)),
            creatorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATOR_NAME)),
            sourceType = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE_TYPE)) ?: "personal",
            isShared = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SHARED)) == 1,
            shareToken = cursor.getString(cursor.getColumnIndexOrThrow(COL_SHARE_TOKEN)),
            sharedByUserId = cursor.getString(cursor.getColumnIndexOrThrow(COL_SHARED_BY_USER_ID)),
            expiresAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_EXPIRES_AT)),
            recordVersion = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECORD_VERSION)),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            updatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT))
        )
    }

    fun getAllHouses(sourceType: String? = null): List<House> {
        val list = mutableListOf<House>()
        val db = readableDatabase
        val selection = if (sourceType != null) "$COL_SOURCE_TYPE = ?" else null
        val selectionArgs = if (sourceType != null) arrayOf(sourceType) else null

        db.query(
            TABLE_HOUSES,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COL_CREATED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToHouse(cursor))
            }
        }
        return list
    }

    fun getHouseCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_HOUSES;", null).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    fun upsertHouse(house: House, creatorId: String? = null, creatorName: String? = null): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ID, house.id)
            put(COL_FULL_NAME, house.fullName)
            put(COL_PHONE, house.phone)
            put(COL_ADDRESS, house.address)
            put(COL_LAT, house.lat)
            put(COL_LNG, house.lng)
            put(COL_NOTE, house.note)
            put(COL_QUANTITY, house.quantity)
            put(COL_CREATOR_ID, house.creatorId ?: creatorId)
            put(COL_CREATOR_NAME, house.creatorName ?: creatorName)
            put(COL_SOURCE_TYPE, house.sourceType)
            put(COL_IS_SHARED, if (house.isShared) 1 else 0)
            put(COL_SHARE_TOKEN, house.shareToken)
            put(COL_SHARED_BY_USER_ID, house.sharedByUserId)
            put(COL_EXPIRES_AT, house.expiresAt)
            put(COL_RECORD_VERSION, house.recordVersion)
            put(COL_CREATED_AT, house.createdAt ?: nowIso())
            put(COL_UPDATED_AT, nowIso())
        }
        return db.insertWithOnConflict(TABLE_HOUSES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteHouse(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_HOUSES, "$COL_ID = ?", arrayOf(id))
    }

    fun seedFromAssetsIfEmpty(): Int {
        if (getHouseCount() > 0) return 0

        val db = writableDatabase
        var count = 0
        try {
            context.assets.open("houses_seed.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    val header = reader.readLine()
                    if (header == null) return 0

                    db.beginTransaction()
                    try {
                        val stmt = db.compileStatement(
                            """
                            INSERT OR REPLACE INTO $TABLE_HOUSES (
                                $COL_ID, $COL_FULL_NAME, $COL_PHONE, $COL_ADDRESS, $COL_LAT, $COL_LNG,
                                $COL_NOTE, $COL_QUANTITY, $COL_SOURCE_TYPE, $COL_CREATED_AT, $COL_UPDATED_AT
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'personal', ?, ?);
                            """.trimIndent()
                        )

                        var line: String? = reader.readLine()
                        while (line != null) {
                            if (line.isNotBlank()) {
                                val parts = parseCsvLine(line)
                                if (parts.size >= 8) {
                                    val id = parts[0].ifBlank { UUID.randomUUID().toString() }
                                    val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                                    val phone = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                                    val address = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                                    val lat = parts.getOrNull(4)?.toDoubleOrNull()
                                    val lng = parts.getOrNull(5)?.toDoubleOrNull()
                                    val note = parts.getOrNull(6)?.takeIf { it.isNotBlank() }
                                    val qty = parts.getOrNull(7)?.toIntOrNull() ?: 1
                                    val createdAt = parts.getOrNull(8)?.takeIf { it.isNotBlank() } ?: nowIso()
                                    val updatedAt = parts.getOrNull(9)?.takeIf { it.isNotBlank() } ?: createdAt

                                    stmt.clearBindings()
                                    stmt.bindString(1, id)
                                    if (name != null) stmt.bindString(2, name) else stmt.bindNull(2)
                                    if (phone != null) stmt.bindString(3, phone) else stmt.bindNull(3)
                                    if (address != null) stmt.bindString(4, address) else stmt.bindNull(4)
                                    if (lat != null) stmt.bindDouble(5, lat) else stmt.bindNull(5)
                                    if (lng != null) stmt.bindDouble(6, lng) else stmt.bindNull(6)
                                    if (note != null) stmt.bindString(7, note) else stmt.bindNull(7)
                                    stmt.bindLong(8, qty.toLong())
                                    stmt.bindString(9, createdAt)
                                    stmt.bindString(10, updatedAt)
                                    stmt.executeInsert()
                                    count++
                                }
                            }
                            line = reader.readLine()
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    cur.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString().trim())
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
            i++
        }
        result.add(cur.toString().trim())
        return result
    }

    // -----------------------------------------------------------------------------------------
    // Navigation Operations (Offline Local SQLite)
    // -----------------------------------------------------------------------------------------

    private fun cursorToNavigationHouse(cursor: Cursor): NavigationHouse {
        val latIdx = cursor.getColumnIndexOrThrow(COL_LAT)
        val lngIdx = cursor.getColumnIndexOrThrow(COL_LNG)
        val lat = if (cursor.isNull(latIdx)) null else cursor.getDouble(latIdx)
        val lng = if (cursor.isNull(lngIdx)) null else cursor.getDouble(lngIdx)

        return NavigationHouse(
            navId = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAV_ID)),
            userId = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_ID)),
            houseId = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOUSE_ID)),
            navNote = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAV_NOTE)),
            navPriority = cursor.getInt(cursor.getColumnIndexOrThrow(COL_NAV_PRIORITY)),
            deliveryStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_DELIVERY_STATUS)) ?: "pending",
            deliveryNote = cursor.getString(cursor.getColumnIndexOrThrow(COL_DELIVERY_NOTE)),
            deliveredAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_DELIVERED_AT)),
            reportReason = cursor.getString(cursor.getColumnIndexOrThrow(COL_REPORT_REASON)),
            reportedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_REPORTED_AT)),
            driverNote = cursor.getString(cursor.getColumnIndexOrThrow(COL_DRIVER_NOTE)),
            trackingNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_TRACKING_NUMBER)),
            parcelCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PARCEL_COUNT)),
            fullName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FULL_NAME)),
            phone = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHONE)),
            address = cursor.getString(cursor.getColumnIndexOrThrow(COL_ADDRESS)),
            lat = lat,
            lng = lng,
            note = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTE)),
            quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        )
    }

    fun getNavigationStops(userId: String = "local-driver"): List<NavigationHouse> {
        val list = mutableListOf<NavigationHouse>()
        val db = readableDatabase
        db.query(
            TABLE_NAVIGATION_STOPS,
            null,
            null,
            null,
            null,
            null,
            "$COL_NAV_PRIORITY ASC, $COL_CREATED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToNavigationHouse(cursor))
            }
        }
        return list
    }

    fun addToNavigation(
        house: House,
        trackingNumber: String? = null,
        navNote: String? = null,
        userId: String = "local-driver"
    ): String {
        val navId = "nav_" + UUID.randomUUID().toString()
        val createdAt = nowIso()
        val db = writableDatabase

        val cv = ContentValues().apply {
            put(COL_NAV_ID, navId)
            put(COL_USER_ID, userId)
            put(COL_HOUSE_ID, house.id)
            put(COL_NAV_NOTE, navNote)
            put(COL_NAV_PRIORITY, 0)
            put(COL_DELIVERY_STATUS, "pending")
            put(COL_TRACKING_NUMBER, trackingNumber)
            put(COL_PARCEL_COUNT, 1)
            put(COL_FULL_NAME, house.fullName)
            put(COL_PHONE, house.phone)
            put(COL_ADDRESS, house.address)
            put(COL_LAT, house.lat)
            put(COL_LNG, house.lng)
            put(COL_NOTE, house.note)
            put(COL_QUANTITY, house.quantity)
            put(COL_CREATED_AT, createdAt)
        }
        db.insertWithOnConflict(TABLE_NAVIGATION_STOPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        if (!trackingNumber.isNullOrBlank()) {
            val pcv = ContentValues().apply {
                put(COL_TRACKING_NUMBER, trackingNumber)
                put(COL_NAV_ID, navId)
                put(COL_HOUSE_ID, house.id)
                put(COL_SCANNED_AT, createdAt)
            }
            db.insertWithOnConflict(TABLE_SCANNED_PARCELS, null, pcv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        return navId
    }

    fun updateDeliveryStatus(
        navId: String,
        status: String,
        deliveryNote: String? = null,
        reportReason: String? = null
    ): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_DELIVERY_STATUS, status)
            if (status == "delivered") {
                put(COL_DELIVERED_AT, nowIso())
            } else if (status == "reported") {
                put(COL_REPORT_REASON, reportReason)
                put(COL_DELIVERY_NOTE, deliveryNote)
                put(COL_REPORTED_AT, nowIso())
            } else {
                putNull(COL_DELIVERED_AT)
                putNull(COL_REPORTED_AT)
            }
        }
        return db.update(TABLE_NAVIGATION_STOPS, cv, "$COL_NAV_ID = ?", arrayOf(navId)) > 0
    }

    fun updateDriverNote(navId: String, note: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_DRIVER_NOTE, note)
        }
        return db.update(TABLE_NAVIGATION_STOPS, cv, "$COL_NAV_ID = ?", arrayOf(navId)) > 0
    }

    fun deleteNavigationStop(navId: String): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_NAVIGATION_STOPS, "$COL_NAV_ID = ?", arrayOf(navId)) > 0
    }

    fun clearDeliveredStops(userId: String = "local-driver"): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAVIGATION_STOPS, "$COL_DELIVERY_STATUS = ?", arrayOf("delivered"))
    }

    fun clearAllStops(userId: String = "local-driver"): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAVIGATION_STOPS, null, null)
    }

    fun checkExistingParcel(trackingNumber: String): SameParcelMeta? {
        val db = readableDatabase
        db.query(
            TABLE_NAVIGATION_STOPS,
            null,
            "$COL_TRACKING_NUMBER = ?",
            arrayOf(trackingNumber),
            null,
            null,
            "$COL_CREATED_AT DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val nav = cursorToNavigationHouse(cursor)
                return SameParcelMeta(
                    navId = nav.navId,
                    trackingNumber = nav.trackingNumber,
                    createdAt = nav.createdAt ?: "",
                    houseId = nav.houseId,
                    deliveryStatus = nav.deliveryStatus,
                    fullName = nav.fullName,
                    address = nav.address
                )
            }
        }
        return null
    }

    // -----------------------------------------------------------------------------------------
    // Proprietary .dwv Encrypted Container Export & Import
    // -----------------------------------------------------------------------------------------

    fun exportToDwv(vaultTitle: String, creatorId: String, creatorName: String): ByteArray {
        val houses = getAllHouses()
        return VaultCryptoManager.packVault(
            houses = houses,
            vaultTitle = vaultTitle,
            creatorId = creatorId,
            creatorName = creatorName
        )
    }

    data class DwvImportResult(
        val totalProcessed: Int,
        val inserted: Int,
        val skipped: Int,
        val creatorName: String,
        val vaultTitle: String,
        val message: String
    )

    fun importFromDwv(dwvBytes: ByteArray, isSubscribed: Boolean): Result<DwvImportResult> {
        val unpackResult = VaultCryptoManager.unpackVault(dwvBytes, isSubscribed)
        return unpackResult.mapCatching { houses ->
            val meta = VaultCryptoManager.inspectVaultHeader(dwvBytes)
            val creatorName = meta?.creatorName ?: "เพื่อนร่วมงาน"
            val vaultTitle = meta?.vaultTitle ?: "คลังบ้าน"

            var inserted = 0
            var skipped = 0
            val db = writableDatabase

            db.beginTransaction()
            try {
                for (h in houses) {
                    val exists = db.rawQuery(
                        "SELECT 1 FROM $TABLE_HOUSES WHERE $COL_ID = ?;",
                        arrayOf(h.id)
                    ).use { it.moveToFirst() }

                    if (exists) {
                        skipped++
                    } else {
                        upsertHouse(h, h.creatorId, h.creatorName)
                        inserted++
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            DwvImportResult(
                totalProcessed = houses.size,
                inserted = inserted,
                skipped = skipped,
                creatorName = creatorName,
                vaultTitle = vaultTitle,
                message = "นำเข้าสำเร็จ $inserted หลัง จากคุณ $creatorName (ข้ามซ้ำ $skipped รายการ)"
            )
        }
    }
}
