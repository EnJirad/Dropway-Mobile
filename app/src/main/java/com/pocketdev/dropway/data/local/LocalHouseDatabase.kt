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
import com.pocketdev.dropway.domain.model.Folder
import com.pocketdev.dropway.domain.model.SyncMutation
import com.pocketdev.dropway.domain.model.SyncStatus
import com.pocketdev.dropway.domain.model.User
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
 * Manages Home Vault, Folders Hierarchy, Navigation Queue, Scanned Parcels, Outbox Sync Queue, and User Identity.
 */
class LocalHouseDatabase(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "dropway_vault.db"
        const val DATABASE_VERSION = 4

        // Tables
        const val TABLE_HOUSES = "houses"
        const val TABLE_NAVIGATION_STOPS = "navigation_stops"
        const val TABLE_SCANNED_PARCELS = "scanned_parcels"
        const val TABLE_FOLDERS = "folders"
        const val TABLE_FOLDER_HOUSES = "folder_houses"
        const val TABLE_OUTBOX_MUTATIONS = "outbox_mutations"
        const val TABLE_USER_PROFILE = "user_profile"

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
        const val COL_FOLDER_ID = "folder_id"
        const val COL_SYNC_STATUS = "sync_status"
        const val COL_IS_DELETED = "is_deleted"
        const val COL_HOUSE_NO = "house_no"
        const val COL_VILLAGE = "village"
        const val COL_SUBDISTRICT = "subdistrict"
        const val COL_DISTRICT = "district"
        const val COL_PROVINCE = "province"
        const val COL_POSTAL_CODE = "postal_code"
        const val COL_LANDMARK = "landmark"

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

        // Folder Columns
        const val COL_FOLDER_NAME = "name"
        const val COL_FOLDER_DESC = "description"
        const val COL_FOLDER_COLOR = "color"
        const val COL_FOLDER_ICON = "icon"
        const val COL_IS_DEFAULT = "is_default"
        const val COL_IS_ARCHIVED = "is_archived"
        const val COL_SORT_ORDER = "sort_order"

        // Outbox Mutations Columns
        const val COL_MUTATION_ID = "id"
        const val COL_ENTITY_TYPE = "entity_type"
        const val COL_ENTITY_ID = "entity_id"
        const val COL_ACTION = "action"
        const val COL_PAYLOAD = "payload"
        const val COL_CLIENT_TIMESTAMP = "client_timestamp"
        const val COL_RETRY_COUNT = "retry_count"
        const val COL_LAST_ERROR = "last_error"
        const val COL_MUTATION_STATUS = "status"

        // User Profile Columns
        const val COL_USER_EMAIL = "email"
        const val COL_USER_DISPLAY_NAME = "display_name"
        const val COL_USER_PHONE = "phone_number"
        const val COL_USER_AUTH_TOKEN = "auth_token"
        const val COL_USER_ROLE = "role"
        const val COL_USER_PLAN_TYPE = "plan_type"
        const val COL_USER_LAST_SYNCED = "last_synced_at"

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
                $COL_UPDATED_AT TEXT NOT NULL,
                $COL_FOLDER_ID TEXT DEFAULT '${Folder.DEFAULT_FOLDER_ID}',
                $COL_SYNC_STATUS TEXT DEFAULT 'SYNCED',
                $COL_IS_DELETED INTEGER DEFAULT 0,
                $COL_HOUSE_NO TEXT DEFAULT '',
                $COL_VILLAGE TEXT DEFAULT '',
                $COL_SUBDISTRICT TEXT DEFAULT '',
                $COL_DISTRICT TEXT DEFAULT '',
                $COL_PROVINCE TEXT DEFAULT '',
                $COL_POSTAL_CODE TEXT DEFAULT '',
                $COL_LANDMARK TEXT DEFAULT ''
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_phone ON $TABLE_HOUSES($COL_PHONE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_address ON $TABLE_HOUSES($COL_ADDRESS);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_name ON $TABLE_HOUSES($COL_FULL_NAME);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_created ON $TABLE_HOUSES($COL_CREATED_AT DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_source ON $TABLE_HOUSES($COL_SOURCE_TYPE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_folder ON $TABLE_HOUSES($COL_FOLDER_ID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_houses_sync ON $TABLE_HOUSES($COL_SYNC_STATUS);")

        // 2. Navigation Stops Table
        createNavigationTable(db)

        // 3. Scanned Parcels Table
        createParcelsTable(db)

        // 4. Folders Table
        createFoldersTable(db)

        // 5. Folder Houses Association Table
        createFolderHousesTable(db)

        // 6. Outbox Mutations Queue
        createOutboxTable(db)

        // 7. User Profile Table
        createUserProfileTable(db)

        // Insert Default Folder
        val now = nowIso()
        val cvFolder = ContentValues().apply {
            put(COL_ID, Folder.DEFAULT_FOLDER_ID)
            put(COL_FOLDER_NAME, Folder.DEFAULT_FOLDER_NAME)
            put(COL_FOLDER_DESC, "Primary zone delivery database for Mae Sot")
            put(COL_FOLDER_COLOR, "#0D9488")
            put(COL_FOLDER_ICON, "home")
            put(COL_IS_DEFAULT, 1)
            put(COL_IS_ARCHIVED, 0)
            put(COL_SORT_ORDER, 0)
            put(COL_SYNC_STATUS, SyncStatus.SYNCED.name)
            put(COL_CREATED_AT, now)
            put(COL_UPDATED_AT, now)
        }
        db.insertWithOnConflict(TABLE_FOLDERS, null, cvFolder, SQLiteDatabase.CONFLICT_IGNORE)
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

    private fun createFoldersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FOLDERS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_FOLDER_NAME TEXT NOT NULL,
                $COL_FOLDER_DESC TEXT DEFAULT '',
                $COL_FOLDER_COLOR TEXT DEFAULT '#3B82F6',
                $COL_FOLDER_ICON TEXT DEFAULT 'folder',
                $COL_IS_DEFAULT INTEGER DEFAULT 0,
                $COL_IS_ARCHIVED INTEGER DEFAULT 0,
                $COL_SORT_ORDER INTEGER DEFAULT 0,
                $COL_SYNC_STATUS TEXT DEFAULT 'SYNCED',
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_UPDATED_AT TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    private fun createFolderHousesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FOLDER_HOUSES (
                $COL_FOLDER_ID TEXT NOT NULL,
                $COL_HOUSE_ID TEXT NOT NULL,
                $COL_CREATED_AT TEXT NOT NULL,
                $COL_SYNC_STATUS TEXT DEFAULT 'SYNCED',
                PRIMARY KEY ($COL_FOLDER_ID, $COL_HOUSE_ID)
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_folder_houses_house ON $TABLE_FOLDER_HOUSES($COL_HOUSE_ID);")
    }

    private fun createOutboxTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OUTBOX_MUTATIONS (
                $COL_MUTATION_ID TEXT PRIMARY KEY,
                $COL_ENTITY_TYPE TEXT NOT NULL,
                $COL_ENTITY_ID TEXT NOT NULL,
                $COL_ACTION TEXT NOT NULL,
                $COL_PAYLOAD TEXT NOT NULL,
                $COL_CLIENT_TIMESTAMP INTEGER NOT NULL,
                $COL_RETRY_COUNT INTEGER DEFAULT 0,
                $COL_LAST_ERROR TEXT,
                $COL_MUTATION_STATUS TEXT DEFAULT 'PENDING'
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_status ON $TABLE_OUTBOX_MUTATIONS($COL_MUTATION_STATUS, $COL_CLIENT_TIMESTAMP);")
    }

    private fun createUserProfileTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_USER_PROFILE (
                $COL_ID TEXT PRIMARY KEY,
                $COL_USER_EMAIL TEXT,
                $COL_USER_DISPLAY_NAME TEXT,
                $COL_USER_PHONE TEXT,
                $COL_USER_ROLE TEXT DEFAULT 'driver',
                $COL_USER_PLAN_TYPE TEXT DEFAULT 'free',
                $COL_USER_AUTH_TOKEN TEXT DEFAULT '',
                $COL_USER_LAST_SYNCED INTEGER DEFAULT 0
            );
            """.trimIndent()
        )
    }

    private fun addColumnIfNotExists(db: SQLiteDatabase, table: String, column: String, typeDef: String) {
        try {
            db.rawQuery("SELECT $column FROM $table LIMIT 0;", null).use { }
        } catch (e: Exception) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $typeDef;")
        }
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
        if (oldVersion < 4) {
            createFoldersTable(db)
            createFolderHousesTable(db)
            createOutboxTable(db)
            createUserProfileTable(db)

            addColumnIfNotExists(db, TABLE_HOUSES, COL_FOLDER_ID, "TEXT DEFAULT '${Folder.DEFAULT_FOLDER_ID}'")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_SYNC_STATUS, "TEXT DEFAULT 'SYNCED'")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_IS_DELETED, "INTEGER DEFAULT 0")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_HOUSE_NO, "TEXT DEFAULT ''")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_VILLAGE, "TEXT DEFAULT ''")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_SUBDISTRICT, "TEXT DEFAULT ''")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_DISTRICT, "TEXT DEFAULT ''")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_PROVINCE, "TEXT DEFAULT ''")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_POSTAL_CODE, "TEXT DEFAULT ''")
            addColumnIfNotExists(db, TABLE_HOUSES, COL_LANDMARK, "TEXT DEFAULT ''")

            val now = nowIso()
            db.execSQL(
                """
                INSERT OR IGNORE INTO $TABLE_FOLDERS (
                    $COL_ID, $COL_FOLDER_NAME, $COL_FOLDER_DESC, $COL_FOLDER_COLOR, $COL_FOLDER_ICON,
                    $COL_IS_DEFAULT, $COL_IS_ARCHIVED, $COL_SORT_ORDER, $COL_SYNC_STATUS, $COL_CREATED_AT, $COL_UPDATED_AT
                ) VALUES (
                    '${Folder.DEFAULT_FOLDER_ID}', '${Folder.DEFAULT_FOLDER_NAME}',
                    'Primary zone delivery database for Mae Sot', '#0D9488', 'home', 1, 0, 0, 'SYNCED', '$now', '$now'
                );
                """.trimIndent()
            )

            // Link existing houses into default Mae Sot folder
            db.execSQL(
                """
                INSERT OR IGNORE INTO $TABLE_FOLDER_HOUSES ($COL_FOLDER_ID, $COL_HOUSE_ID, $COL_CREATED_AT, $COL_SYNC_STATUS)
                SELECT '${Folder.DEFAULT_FOLDER_ID}', $COL_ID, '$now', 'SYNCED' FROM $TABLE_HOUSES;
                """.trimIndent()
            )
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
        val selection = if (sourceType != null) "$COL_SOURCE_TYPE = ? AND $COL_IS_DELETED = 0" else "$COL_IS_DELETED = 0"
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

    fun getHousesInFolder(folderId: String): List<House> {
        val list = mutableListOf<House>()
        val db = readableDatabase
        val query = """
            SELECT h.* FROM $TABLE_HOUSES h
            INNER JOIN $TABLE_FOLDER_HOUSES fh ON h.$COL_ID = fh.$COL_HOUSE_ID
            WHERE fh.$COL_FOLDER_ID = ? AND h.$COL_IS_DELETED = 0
            ORDER BY h.$COL_CREATED_AT DESC;
        """.trimIndent()
        db.rawQuery(query, arrayOf(folderId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToHouse(cursor))
            }
        }
        return list
    }

    fun getHouseCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_HOUSES WHERE $COL_IS_DELETED = 0;", null).use { cursor ->
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
            put(COL_IS_DELETED, 0)
            put(COL_SYNC_STATUS, "SYNCED")
        }
        val result = db.insertWithOnConflict(TABLE_HOUSES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        // Automatically link to default folder if not linked
        assignHouseToFolder(Folder.DEFAULT_FOLDER_ID, house.id)
        return result
    }

    fun deleteHouse(id: String): Int {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_IS_DELETED, 1)
            put(COL_SYNC_STATUS, "PENDING_DELETE")
            put(COL_UPDATED_AT, nowIso())
        }
        return db.update(TABLE_HOUSES, cv, "$COL_ID = ?", arrayOf(id))
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
                                $COL_NOTE, $COL_QUANTITY, $COL_SOURCE_TYPE, $COL_CREATED_AT, $COL_UPDATED_AT,
                                $COL_FOLDER_ID, $COL_SYNC_STATUS, $COL_IS_DELETED
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'personal', ?, ?, '${Folder.DEFAULT_FOLDER_ID}', 'SYNCED', 0);
                            """.trimIndent()
                        )

                        val folderStmt = db.compileStatement(
                            """
                            INSERT OR IGNORE INTO $TABLE_FOLDER_HOUSES (
                                $COL_FOLDER_ID, $COL_HOUSE_ID, $COL_CREATED_AT, $COL_SYNC_STATUS
                            ) VALUES ('${Folder.DEFAULT_FOLDER_ID}', ?, ?, 'SYNCED');
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

                                    folderStmt.clearBindings()
                                    folderStmt.bindString(1, id)
                                    folderStmt.bindString(2, createdAt)
                                    folderStmt.executeInsert()

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
    // Folders Operations
    // -----------------------------------------------------------------------------------------

    fun getAllFolders(includeArchived: Boolean = false): List<Folder> {
        val list = mutableListOf<Folder>()
        val db = readableDatabase
        val selection = if (includeArchived) null else "$COL_IS_ARCHIVED = 0"
        db.query(
            TABLE_FOLDERS,
            null,
            selection,
            null,
            null,
            null,
            "$COL_SORT_ORDER ASC, $COL_CREATED_AT DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val folderId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID))
                val houseCount = getHouseCountInFolder(folderId)
                list.add(
                    Folder(
                        id = folderId,
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_NAME)),
                        description = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_DESC)) ?: "",
                        color = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_COLOR)) ?: "#3B82F6",
                        icon = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_ICON)) ?: "folder",
                        isDefault = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_DEFAULT)) == 1,
                        isArchived = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ARCHIVED)) == 1,
                        sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SORT_ORDER)),
                        houseCount = houseCount,
                        syncStatus = SyncStatus.fromString(cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS))),
                        createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)) ?: "",
                        updatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getFolderById(id: String): Folder? {
        val db = readableDatabase
        db.query(TABLE_FOLDERS, null, "$COL_ID = ?", arrayOf(id), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val houseCount = getHouseCountInFolder(id)
                return Folder(
                    id = id,
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_NAME)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_DESC)) ?: "",
                    color = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_COLOR)) ?: "#3B82F6",
                    icon = cursor.getString(cursor.getColumnIndexOrThrow(COL_FOLDER_ICON)) ?: "folder",
                    isDefault = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_DEFAULT)) == 1,
                    isArchived = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ARCHIVED)) == 1,
                    sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SORT_ORDER)),
                    houseCount = houseCount,
                    syncStatus = SyncStatus.fromString(cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS))),
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)) ?: "",
                    updatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)) ?: ""
                )
            }
        }
        return null
    }

    fun upsertFolder(folder: Folder): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ID, folder.id)
            put(COL_FOLDER_NAME, folder.name)
            put(COL_FOLDER_DESC, folder.description)
            put(COL_FOLDER_COLOR, folder.color)
            put(COL_FOLDER_ICON, folder.icon)
            put(COL_IS_DEFAULT, if (folder.isDefault) 1 else 0)
            put(COL_IS_ARCHIVED, if (folder.isArchived) 1 else 0)
            put(COL_SORT_ORDER, folder.sortOrder)
            put(COL_SYNC_STATUS, folder.syncStatus.name)
            put(COL_CREATED_AT, folder.createdAt.ifBlank { nowIso() })
            put(COL_UPDATED_AT, nowIso())
        }
        return db.insertWithOnConflict(TABLE_FOLDERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFolder(id: String): Int {
        val db = writableDatabase
        db.delete(TABLE_FOLDER_HOUSES, "$COL_FOLDER_ID = ?", arrayOf(id))
        return db.delete(TABLE_FOLDERS, "$COL_ID = ?", arrayOf(id))
    }

    fun assignHouseToFolder(folderId: String, houseId: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_FOLDER_ID, folderId)
            put(COL_HOUSE_ID, houseId)
            put(COL_CREATED_AT, nowIso())
            put(COL_SYNC_STATUS, SyncStatus.SYNCED.name)
        }
        return db.insertWithOnConflict(TABLE_FOLDER_HOUSES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeHouseFromFolder(folderId: String, houseId: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_FOLDER_HOUSES, "$COL_FOLDER_ID = ? AND $COL_HOUSE_ID = ?", arrayOf(folderId, houseId))
    }

    fun getHouseCountInFolder(folderId: String): Int {
        val db = readableDatabase
        db.rawQuery(
            """
            SELECT COUNT(*) FROM $TABLE_FOLDER_HOUSES fh
            INNER JOIN $TABLE_HOUSES h ON fh.$COL_HOUSE_ID = h.$COL_ID
            WHERE fh.$COL_FOLDER_ID = ? AND h.$COL_IS_DELETED = 0;
            """.trimIndent(),
            arrayOf(folderId)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return 0
    }

    fun getHouseIdsInFolder(folderId: String): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        db.query(TABLE_FOLDER_HOUSES, arrayOf(COL_HOUSE_ID), "$COL_FOLDER_ID = ?", arrayOf(folderId), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
        }
        return list
    }

    // -----------------------------------------------------------------------------------------
    // Outbox Queue Operations
    // -----------------------------------------------------------------------------------------

    fun queueOutboxMutation(mutation: SyncMutation): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_MUTATION_ID, mutation.id)
            put(COL_ENTITY_TYPE, mutation.entityType)
            put(COL_ENTITY_ID, mutation.entityId)
            put(COL_ACTION, mutation.action)
            put(COL_PAYLOAD, mutation.payload)
            put(COL_CLIENT_TIMESTAMP, mutation.clientTimestamp)
            put(COL_RETRY_COUNT, mutation.retryCount)
            put(COL_LAST_ERROR, mutation.lastError)
            put(COL_MUTATION_STATUS, mutation.status)
        }
        return db.insertWithOnConflict(TABLE_OUTBOX_MUTATIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPendingOutboxMutations(): List<SyncMutation> {
        val list = mutableListOf<SyncMutation>()
        val db = readableDatabase
        db.query(
            TABLE_OUTBOX_MUTATIONS,
            null,
            "$COL_MUTATION_STATUS = ? OR $COL_MUTATION_STATUS = ?",
            arrayOf("PENDING", "FAILED"),
            null,
            null,
            "$COL_CLIENT_TIMESTAMP ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    SyncMutation(
                        id = cursor.getString(cursor.getColumnIndexOrThrow(COL_MUTATION_ID)),
                        entityType = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENTITY_TYPE)),
                        entityId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ENTITY_ID)),
                        action = cursor.getString(cursor.getColumnIndexOrThrow(COL_ACTION)),
                        payload = cursor.getString(cursor.getColumnIndexOrThrow(COL_PAYLOAD)),
                        clientTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CLIENT_TIMESTAMP)),
                        retryCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RETRY_COUNT)),
                        lastError = cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_ERROR)),
                        status = cursor.getString(cursor.getColumnIndexOrThrow(COL_MUTATION_STATUS))
                    )
                )
            }
        }
        return list
    }

    fun updateOutboxMutationStatus(id: String, status: String, error: String? = null): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_MUTATION_STATUS, status)
            if (error != null) {
                put(COL_LAST_ERROR, error)
            }
        }
        return db.update(TABLE_OUTBOX_MUTATIONS, cv, "$COL_MUTATION_ID = ?", arrayOf(id)) > 0
    }

    fun deleteOutboxMutation(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_OUTBOX_MUTATIONS, "$COL_MUTATION_ID = ?", arrayOf(id))
    }

    fun getPendingOutboxCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_OUTBOX_MUTATIONS WHERE $COL_MUTATION_STATUS = 'PENDING';", null).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return 0
    }

    // -----------------------------------------------------------------------------------------
    // User Profile Operations
    // -----------------------------------------------------------------------------------------

    fun getUserProfile(): User? {
        val db = readableDatabase
        db.query(TABLE_USER_PROFILE, null, null, null, null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) {
                return User(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                    email = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_EMAIL)) ?: "",
                    displayName = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_DISPLAY_NAME)) ?: "",
                    phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_PHONE)),
                    role = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_ROLE)) ?: "driver",
                    planType = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_PLAN_TYPE)) ?: "free",
                    authToken = cursor.getString(cursor.getColumnIndexOrThrow(COL_USER_AUTH_TOKEN)) ?: "",
                    lastSyncedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_USER_LAST_SYNCED))
                )
            }
        }
        return null
    }

    fun saveUserProfile(user: User): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_ID, user.id)
            put(COL_USER_EMAIL, user.email)
            put(COL_USER_DISPLAY_NAME, user.displayName)
            put(COL_USER_PHONE, user.phoneNumber)
            put(COL_USER_ROLE, user.role)
            put(COL_USER_PLAN_TYPE, user.planType)
            put(COL_USER_AUTH_TOKEN, user.authToken)
            put(COL_USER_LAST_SYNCED, user.lastSyncedAt)
        }
        return db.insertWithOnConflict(TABLE_USER_PROFILE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearUserProfile(): Int {
        val db = writableDatabase
        return db.delete(TABLE_USER_PROFILE, null, null)
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
