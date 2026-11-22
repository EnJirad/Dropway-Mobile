package com.pocketdev.dropway.data.remote

import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.model.NavigationHouse
import com.pocketdev.dropway.data.model.SameParcelMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class DuplicateParcelException(val sameStop: Boolean, message: String) : Exception(message)

class NeonClient(private val getDatabaseUrl: () -> String) {

    private fun parseHost(connectionString: String): String {
        val trimmed = connectionString.trim()
        val regex = Regex("^(?:postgres|postgresql)://(?:([^:]+)(?::([^@]*))?@)?([^/:?]+)")
        val match = regex.find(trimmed)
        return match?.groupValues?.get(3)
            ?: throw IllegalArgumentException("ไม่สามารถดึง host จาก Connection String ได้ กรุณาตรวจสอบรูปแบบ")
    }

    suspend fun execute(query: String, params: List<Any?> = emptyList(), customUrl: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connString = customUrl ?: getDatabaseUrl()
            if (connString.isBlank()) {
                throw IllegalStateException("ยังไม่ได้ระบุ Connection String ของ Neon ในหน้าตั้งค่า")
            }

            val host = parseHost(connString)
            val endpoint = "https://$host/sql"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection

            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Neon-Connection-String", connString)
                conn.connectTimeout = 12000
                conn.readTimeout = 20000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("query", query)
                    val jsonParams = JSONArray()
                    params.forEach { p ->
                        when (p) {
                            null -> jsonParams.put(JSONObject.NULL)
                            is Number -> jsonParams.put(p)
                            is Boolean -> jsonParams.put(p)
                            else -> jsonParams.put(p.toString())
                        }
                    }
                    put("params", jsonParams)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

                if (responseCode !in 200..299) {
                    val errorObj = try { JSONObject(responseText) } catch (_: Exception) { null }
                    val message = errorObj?.optString("message") ?: "HTTP $responseCode: $responseText"
                    throw RuntimeException(message)
                }

                JSONObject(responseText)
            } finally {
                conn.disconnect()
            }
        }

    suspend fun testConnection(customUrl: String): Result<Long> = runCatching {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val res = execute("SELECT 1 AS ok;", emptyList(), customUrl)
            val duration = System.currentTimeMillis() - start
            val rows = res.optJSONArray("rows")
            if (rows != null && rows.length() > 0) {
                duration
            } else {
                throw RuntimeException("ไม่ได้รับข้อมูลตอบกลับจาก Neon")
            }
        }
    }

    suspend fun ensureSchema(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            // 1. Table houses
            execute(
                """
                CREATE TABLE IF NOT EXISTS public.houses (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    full_name TEXT,
                    phone TEXT,
                    address TEXT,
                    lat DOUBLE PRECISION,
                    lng DOUBLE PRECISION,
                    note TEXT,
                    quantity INTEGER NOT NULL DEFAULT 1,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
                );
                """.trimIndent()
            )

            // 2. Table user_navigation_houses
            execute(
                """
                CREATE TABLE IF NOT EXISTS public.user_navigation_houses (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id TEXT NOT NULL DEFAULT 'default-user',
                    house_id UUID REFERENCES public.houses(id) ON DELETE CASCADE,
                    nav_note TEXT,
                    nav_priority INTEGER NOT NULL DEFAULT 0,
                    delivery_status TEXT NOT NULL DEFAULT 'pending',
                    delivery_note TEXT,
                    delivered_at TIMESTAMPTZ,
                    report_reason TEXT,
                    reported_at TIMESTAMPTZ,
                    driver_note TEXT,
                    call_count INTEGER NOT NULL DEFAULT 0,
                    call_note TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
                );
                """.trimIndent()
            )

            // 2.1 Additional columns
            execute("ALTER TABLE public.user_navigation_houses ADD COLUMN IF NOT EXISTS tracking_number TEXT;")
            execute("ALTER TABLE public.user_navigation_houses ALTER COLUMN house_id DROP NOT NULL;")
            execute("ALTER TABLE public.user_navigation_houses ADD COLUMN IF NOT EXISTS dest_name TEXT;")
            execute("ALTER TABLE public.user_navigation_houses ADD COLUMN IF NOT EXISTS dest_phone TEXT;")
            execute("ALTER TABLE public.user_navigation_houses ADD COLUMN IF NOT EXISTS dest_address TEXT;")

            // 2.2 Table parcels
            execute(
                """
                CREATE TABLE IF NOT EXISTS public.parcels (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    nav_id UUID NOT NULL REFERENCES public.user_navigation_houses(id) ON DELETE CASCADE,
                    tracking_number TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                );
                """.trimIndent()
            )

            // 3. View navigation_view
            execute(
                """
                CREATE OR REPLACE VIEW public.navigation_view AS
                SELECT
                    unh.id AS nav_id,
                    unh.user_id,
                    unh.house_id,
                    unh.nav_note,
                    unh.nav_priority,
                    unh.delivery_status,
                    unh.delivery_note,
                    unh.delivered_at,
                    unh.report_reason,
                    unh.reported_at,
                    unh.driver_note,
                    unh.call_count,
                    unh.call_note,
                    COALESCE(h.full_name, unh.dest_name) AS full_name,
                    COALESCE(h.phone, unh.dest_phone) AS phone,
                    COALESCE(h.address, unh.dest_address) AS address,
                    h.lat,
                    h.lng,
                    h.note,
                    COALESCE(h.quantity, 1) AS quantity,
                    COALESCE(h.created_at, unh.created_at) AS created_at,
                    COALESCE(h.updated_at, unh.updated_at) AS updated_at,
                    COALESCE(
                      (SELECT string_agg(p.tracking_number, ', ' ORDER BY p.created_at)
                         FROM public.parcels p
                         WHERE p.nav_id = unh.id AND p.tracking_number IS NOT NULL),
                      unh.tracking_number
                    ) AS tracking_number,
                    GREATEST((SELECT count(*)::int FROM public.parcels p WHERE p.nav_id = unh.id), 1) AS parcel_count
                FROM public.user_navigation_houses unh
                LEFT JOIN public.houses h ON unh.house_id = h.id;
                """.trimIndent()
            )

            // 4. Indexes
            execute("CREATE INDEX IF NOT EXISTS idx_houses_phone ON public.houses(phone);")
            execute("CREATE INDEX IF NOT EXISTS idx_houses_address ON public.houses(address);")
            execute("CREATE INDEX IF NOT EXISTS idx_houses_created_at ON public.houses(created_at DESC);")
        }
    }

    suspend fun getHouses(search: String? = null): Result<List<House>> = runCatching {
        withContext(Dispatchers.IO) {
            val query: String
            val params: List<Any?>
            if (search.isNullOrBlank()) {
                query = "SELECT * FROM public.houses ORDER BY created_at DESC LIMIT 50000;"
                params = emptyList()
            } else {
                query = """
                    SELECT * FROM public.houses
                    WHERE full_name ILIKE $1
                       OR phone ILIKE $1
                       OR address ILIKE $1
                       OR note ILIKE $1
                    ORDER BY created_at DESC
                    LIMIT 50000;
                """.trimIndent()
                params = listOf("%${search.trim()}%")
            }

            val res = execute(query, params)
            val rows = res.optJSONArray("rows") ?: JSONArray()
            val list = mutableListOf<House>()
            for (i in 0 until rows.length()) {
                list.add(House.fromJson(rows.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun createHouse(house: House): Result<House> = runCatching {
        withContext(Dispatchers.IO) {
            val query = """
                INSERT INTO public.houses (
                    id, full_name, phone, address, lat, lng, note, quantity, created_at, updated_at
                ) VALUES (
                    $1, $2, $3, $4, $5, $6, $7, $8, now(), now()
                )
                RETURNING *;
            """.trimIndent()
            val params = listOf(
                house.id,
                house.fullName,
                house.phone,
                house.address,
                house.lat,
                house.lng,
                house.note,
                house.quantity
            )
            val res = execute(query, params)
            val rows = res.getJSONArray("rows")
            House.fromJson(rows.getJSONObject(0))
        }
    }

    suspend fun updateHouse(house: House): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val query = """
                UPDATE public.houses SET
                    full_name = $1,
                    phone = $2,
                    address = $3,
                    lat = $4,
                    lng = $5,
                    note = $6,
                    quantity = $7,
                    updated_at = now()
                WHERE id = $8;
            """.trimIndent()
            val params = listOf(
                house.fullName,
                house.phone,
                house.address,
                house.lat,
                house.lng,
                house.note,
                house.quantity,
                house.id
            )
            execute(query, params)
        }
    }

    suspend fun deleteHouse(houseId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            execute("DELETE FROM public.houses WHERE id = $1;", listOf(houseId))
        }
    }

    suspend fun getNavigationStops(userId: String = "default-user"): Result<List<NavigationHouse>> = runCatching {
        withContext(Dispatchers.IO) {
            val query = """
                SELECT * FROM public.navigation_view
                WHERE user_id = $1 OR user_id = 'default-user'
                ORDER BY nav_priority ASC, created_at DESC;
            """.trimIndent()
            val res = execute(query, listOf(userId))
            val rows = res.optJSONArray("rows") ?: JSONArray()
            val list = mutableListOf<NavigationHouse>()
            for (i in 0 until rows.length()) {
                list.add(NavigationHouse.fromJson(rows.getJSONObject(i)))
            }
            list
        }
    }

    suspend fun checkExistingParcel(trackingNumber: String): Result<SameParcelMeta?> = runCatching {
        withContext(Dispatchers.IO) {
            val clean = trackingNumber.trim().replace(" ", "").uppercase()
            if (clean.isBlank()) return@withContext null

            val query = """
                SELECT
                  p.nav_id,
                  p.tracking_number,
                  p.created_at,
                  unh.house_id,
                  unh.delivery_status,
                  COALESCE(h.full_name, unh.dest_name) AS full_name,
                  COALESCE(h.address, unh.dest_address) AS address
                FROM public.parcels p
                JOIN public.user_navigation_houses unh ON unh.id = p.nav_id
                LEFT JOIN public.houses h ON h.id = unh.house_id
                WHERE upper(replace(p.tracking_number, ' ', '')) = $1
                ORDER BY p.created_at DESC
                LIMIT 1;
            """.trimIndent()

            val res = execute(query, listOf(clean))
            val rows = res.optJSONArray("rows")
            if (rows != null && rows.length() > 0) {
                SameParcelMeta.fromJson(rows.getJSONObject(0))
            } else {
                null
            }
        }
    }

    suspend fun addToNavigation(
        houseId: String?,
        trackingNumber: String? = null,
        destName: String? = null,
        destPhone: String? = null,
        destAddress: String? = null,
        userId: String = "default-user",
        force: Boolean = false
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            // Check existing navigation for house
            var existingNavId: String? = null
            if (!houseId.isNullOrBlank()) {
                val checkRes = execute(
                    """
                    SELECT id FROM public.user_navigation_houses
                    WHERE house_id = $1 AND (user_id = $2 OR user_id = 'default-user') AND delivery_status <> 'delivered'
                    ORDER BY created_at DESC LIMIT 1;
                    """.trimIndent(),
                    listOf(houseId, userId)
                )
                val checkRows = checkRes.optJSONArray("rows")
                if (checkRows != null && checkRows.length() > 0) {
                    existingNavId = checkRows.getJSONObject(0).optString("id")
                }
            }

            val cleanTracking = trackingNumber?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

            // Check duplicate tracking number in today's queue
            if (cleanTracking != null) {
                val dupRes = execute(
                    """
                    SELECT nav_id FROM public.parcels
                    WHERE upper(tracking_number) = $1
                      AND (
                        nav_id = $2
                        OR (created_at AT TIME ZONE 'Asia/Bangkok')::date = (now() AT TIME ZONE 'Asia/Bangkok')::date
                      );
                    """.trimIndent(),
                    listOf(cleanTracking, existingNavId)
                )
                val dupRows = dupRes.optJSONArray("rows")
                if (dupRows != null && dupRows.length() > 0) {
                    var inSameStop = false
                    for (i in 0 until dupRows.length()) {
                        val rowNavId = dupRows.getJSONObject(i).optString("nav_id")
                        if (existingNavId != null && rowNavId == existingNavId) {
                            inSameStop = true
                            break
                        }
                    }
                    if (inSameStop) {
                        throw DuplicateParcelException(true, "บ้านนี้มีพัสดุเลขนี้ในคิวแล้ว")
                    } else if (!force) {
                        throw DuplicateParcelException(false, "เลขพัสดุนี้ถูกบันทึกวันนี้แล้ว ต้องการเพิ่มซ้ำหรือไม่?")
                    }
                }
            }

            val navId: String
            if (existingNavId != null) {
                navId = existingNavId
            } else {
                val newId = UUID.randomUUID().toString()
                execute(
                    """
                    INSERT INTO public.user_navigation_houses (
                        id, user_id, house_id, nav_note, nav_priority, delivery_status,
                        dest_name, dest_phone, dest_address, created_at, updated_at
                    ) VALUES (
                        $1, $2, $3, null, 0, 'pending',
                        $4, $5, $6, now(), now()
                    );
                    """.trimIndent(),
                    listOf(
                        newId,
                        userId,
                        houseId,
                        if (houseId == null) destName else null,
                        if (houseId == null) destPhone else null,
                        if (houseId == null) destAddress else null
                    )
                )
                navId = newId
            }

            // Insert parcel
            execute(
                """
                INSERT INTO public.parcels (nav_id, tracking_number)
                VALUES ($1, $2);
                """.trimIndent(),
                listOf(navId, cleanTracking)
            )

            navId
        }
    }

    suspend fun updateDeliveryStatus(
        navId: String,
        status: String,
        deliveryNote: String? = null,
        reportReason: String? = null
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val query: String
            val params: List<Any?>
            when (status) {
                "delivered" -> {
                    query = """
                        UPDATE public.user_navigation_houses SET
                            delivery_status = 'delivered',
                            delivered_at = now(),
                            delivery_note = COALESCE($1, delivery_note),
                            updated_at = now()
                        WHERE id = $2;
                    """.trimIndent()
                    params = listOf(deliveryNote, navId)
                }
                "reported" -> {
                    query = """
                        UPDATE public.user_navigation_houses SET
                            delivery_status = 'reported',
                            reported_at = now(),
                            report_reason = $1,
                            delivery_note = COALESCE($2, delivery_note),
                            updated_at = now()
                        WHERE id = $3;
                    """.trimIndent()
                    params = listOf(reportReason, deliveryNote, navId)
                }
                else -> {
                    query = """
                        UPDATE public.user_navigation_houses SET
                            delivery_status = 'pending',
                            delivered_at = null,
                            reported_at = null,
                            updated_at = now()
                        WHERE id = $1;
                    """.trimIndent()
                    params = listOf(navId)
                }
            }
            execute(query, params)
        }
    }

    suspend fun updateDriverNote(navId: String, driverNote: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            execute(
                "UPDATE public.user_navigation_houses SET driver_note = $1, updated_at = now() WHERE id = $2;",
                listOf(driverNote, navId)
            )
        }
    }

    suspend fun deleteNavigationStop(navId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            execute("DELETE FROM public.user_navigation_houses WHERE id = $1;", listOf(navId))
        }
    }

    suspend fun clearDeliveredStops(userId: String = "default-user"): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            execute(
                "DELETE FROM public.user_navigation_houses WHERE (user_id = $1 OR user_id = 'default-user') AND delivery_status = 'delivered';",
                listOf(userId)
            )
        }
    }

    suspend fun clearAllStops(userId: String = "default-user"): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            execute(
                "DELETE FROM public.user_navigation_houses WHERE user_id = $1 OR user_id = 'default-user';",
                listOf(userId)
            )
        }
    }

    suspend fun seedMaeSotData(): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            val houses = listOf(
                House("f191d818-eeee-4c5b-a13f-7cf8def9920f", null, null, "1442 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67492, 98.5723, "ฉลองนคร 3 ซ.15/4 หลังที่เกือบจะถึงหลังที่จะโผล่สามแยกอ่ะเป็นหลังกลางๆ"),
                House("a0343f80-3278-48a0-9e75-912a9437e7cd", "นางสาว ศรุตา บรรพชาติ", null, "1262 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67519, 98.57225, "ฉลองนคร 3 ซ.15/5 หลังที่ 4 ซ้ายมือ ตรงทางโค้ง"),
                House("99ec6253-4af5-4948-9472-12fb151d6463", "nyein ShinPyi", null, "749 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66377, 98.58105, "ผาทอง ซ.5 วนกลับ อยู่ทางขวามือ บ้านตรงหน้าโค้ง"),
                House("ccac7578-bedc-40da-bce6-75220ad3cc87", "ชนัญญา", null, "1416 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66588, 98.58056, "ผาทอง ซ.4 ห้องแถว ตรงโค้ง หลัง TK"),
                House("a432bd16-b1dc-47da-8262-05e224afc6e8", "Rich man", null, "1416 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66588, 98.58056, "ผาทอง ซ.4 ห้องแถว ตรงโค้ง หลัง TK"),
                House("6fdf2be6-43cf-4d4c-ac9a-bed89376b8e2", "Naw Bah Blut Moo", null, "905 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67059, 98.59243, "ไพร์วิลเลจ ซ.2 ขวา บ้านหลังที่ 2 ทางขวามือ"),
                House("dcdc37fe-a056-40db-8c90-1cb3ae183057", "ญาณิศา", null, "974 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66967, 98.59345, "ทุ่งเศรษฐี ซ.2 บ้านหลังที่ 3 ทางซ้ายมือ"),
                House("3d4b97cd-68de-4a5e-b048-155445ff6035", "คำมา", null, "407 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66887, 98.58071, "ผาทอง ซ.4 หลังที่ อยู่ ทางขวามือ ตรงข้ามบ้าน 25"),
                House("8dc692e0-8db8-437c-b610-6ddbdc6e9020", "Win Win", null, "759 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66459, 98.57862, "ผาทอง ซ.5 โค้งแล้ว อยู่ทางขวามือ"),
                House("b2cb6596-64dd-41ef-bc16-d3d7a37e31d0", "LIN XZ8888", null, "917 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67111, 98.59139, "ไพร์วิลเลจ ซ.2 ซ้าย บ้านหลังที่ 2 ทางซ้ายมือ"),
                House("f156c55f-9058-4971-a098-b273a949cacb", "ตุ๊บป่อง ploy", null, "977 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66988, 98.59351, "ทุ่งเศรษฐี ซ.2 บ้านหลังที่ 5 ทางซ้ายมือ"),
                House("3ea02c1b-779a-4ff7-a2bd-d4a2be2a4590", "เบญจมาศ กมลกิตติกานนฑ์", null, "628 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.6621, 98.58008, "ผาทอง ซ.1 โค้งขวา อยู่ทางขวามือ"),
                House("3dc1fbc9-008d-4014-81a9-258940efc64a", "เม็ดทราย", null, "843 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67582, 98.57186, "ฉลองนคร 2 ซ.ตรง บ้านที่ 5 ซ้ายมือ เลยซอย 7"),
                House("36695655-a3ad-4b48-bd05-efc7f0429d46", "คุณเดียร์", null, "910 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67142, 98.59282, "ไพร์วิลเลจ ซ.2 ขวา เข้าโค้งมา บ้านหลังที่ 5 ทางขวามือ"),
                House("2b651550-6349-4eac-a633-99199642b505", "Zarchimin", null, "715 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.65499, 98.58299, "ผาทอง ซ.หลังสุสาน คอกวัวเลยคอกวัวไปอยู่ซอยที่เข้าจากทางมัสยิดเข้าไปข้างล่างสุดเลย"),
                House("6f42e940-3f64-4403-99c0-753fa324251b", "user3877947784625", null, "561 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66353, 98.5865, "หน้าเมาน์เท่นวิวล์ ซ.3 บ้านหลังที่ 4 ซ้าย"),
                House("eb251dda-0c67-4226-9713-46157bfa520b", "Hein Ko Ko Kyaw", null, "942 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66405, 98.58153, "ผาทอง ซ.1 อยู่ทางซ้ายมือ"),
                House("e18ee451-a86d-4273-bf2c-a08da30683a8", "เตย", null, "13/6 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66493, 98.58173, "ผาทอง ซ.1 เป็นร้านขายส้มตำก่อนจะถึงซอย 1 อยู่ทางซ้ายมือเลย"),
                House("004546cc-73c4-45ca-98b2-53a03c78853f", null, null, "915 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67268, 98.58328, "ซ.หลังโรงเรียน"),
                House("468c7b13-83de-493c-906f-6176b32e3554", "Anna", null, "1212 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67077, 98.5926, "ไพร์วิลเลจ ซ.2 ขวา เข้าโค้งมา บ้านหลังที่ 1 ทางขวามือ"),
                House("98ec44bf-8cec-4fc4-8a7e-251e1b0ddb92", "chamaiphon", null, "1066 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.6708, 98.59192, "ไพร์วิลเลจ ซ.กลาง บ้านหลังที่ 3 ทางซ้ายมือ"),
                House("029dd1b6-78a7-4f00-9e6c-1302e724195b", "Mar Yee", null, "1352 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66495, 98.58112, "ผาทอง ซ.5 วนกลับ โค้ง อยู่ทางซ้ายมือ เป็นห้องที่ 1"),
                House("ec7c54b0-24ec-4d4c-9a38-96c8f0a53f5f", "นาย สมชาย วันดี", null, "842 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.67085, 98.59147, "ไพร์วิลเลจ ซ.1 ซ้าย เข้าโค้งมา บ้านหลังที่ 2 ทางซ้ายมือ"),
                House("56e88b68-aaad-454d-84f8-c68268934fa1", "นิตยา จาเร", null, "590 | 347 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66854, 98.58078, "ผาทอง ซ.4 อยู่ซ้ายมือ 347 คนไทย อยู่แม่ตาวใหม่"),
                House("ee4c67d0-c0b5-4473-990f-310019630a61", "ศักดิ์สิทธิ์ จักร์เพ็ญ", null, "960 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66177, 98.57757, "ผาทอง ซ.9 อยู่ทางขวามือ บ้านตรงหัวมุมโค้ง"),
                House("4063ce23-a48f-4ccd-825f-d1f6b7c4e375", "ภวัต คงประเสริฐ", null, "888/94 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66455, 98.58846, "เมาน์เท่นวิวล์ ซ.4 บ้านหลังที่ 4 ขวา คนนี้มันจะลงบ้านเลขที่เป็นหมู่ 1 อ่ะ"),
                House("e0651891-0ecb-4be8-ab20-d28d31bc0fea", "พัชรินทร์ ดีแก้ว เนเน่", null, "550 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", 16.66219, 98.57866, "ผาทอง ซ.8 อยู่ทางซ้ายมือ")
            )

            var count = 0
            for (h in houses) {
                val q = """
                    INSERT INTO public.houses (id, full_name, phone, address, lat, lng, note, quantity, created_at, updated_at)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, 1, now(), now())
                    ON CONFLICT (id) DO NOTHING;
                """.trimIndent()
                execute(q, listOf(h.id, h.fullName, h.phone, h.address, h.lat, h.lng, h.note))
                count++
            }
            count
        }
    }
}
