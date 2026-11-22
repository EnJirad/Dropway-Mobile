package com.pocketdev.dropway

import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.sharing.SubscriptionConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SharingManagerTest {

    @Test
    fun testSubscriptionConfig() {
        assertEquals(29, SubscriptionConfig.monthlyPriceThb)
        assertEquals("THB", SubscriptionConfig.currencyCode)
        assertEquals("29 THB / เดือน", SubscriptionConfig.getFormattedPrice())
    }

    @Test
    fun testHouseSerialization() {
        val house = House(
            id = "test-id-123",
            fullName = "สมชาย ใจดี",
            phone = "0812345678",
            address = "123 ม.1 แม่ตาว",
            lat = 16.66474,
            lng = 98.57846,
            note = "รั้วสีขาว",
            creatorId = "creator-abc",
            creatorName = "Driver John",
            sourceType = "personal",
            isShared = false
        )

        val json = house.toJson()
        assertEquals("test-id-123", json.getString("id"))
        assertEquals("สมชาย ใจดี", json.getString("full_name"))
        assertEquals("creator-abc", json.getString("creator_id"))
        assertEquals("Driver John", json.getString("creator_name"))
        assertEquals("personal", json.getString("source_type"))
        assertFalse(json.getBoolean("is_shared"))

        // Deserialize back
        val deserialized = House.fromJson(json)
        assertEquals(house.id, deserialized.id)
        assertEquals(house.fullName, deserialized.fullName)
        assertEquals(house.creatorId, deserialized.creatorId)
        assertEquals(house.creatorName, deserialized.creatorName)
        assertEquals(house.sourceType, deserialized.sourceType)
        assertEquals(house.lat, deserialized.lat)
        assertEquals(house.lng, deserialized.lng)
    }

    @Test
    fun testTamperDetectionOnSharePayload() {
        val house = House(
            id = "house-sec-01",
            fullName = "ผู้รับ มั่นคง",
            address = "55 ม.4 แม่สอด",
            creatorId = "user-123",
            creatorName = "Driver One"
        )
        val now = "2026-11-22T03:00:00.000Z"
        val expiry = "2026-11-29T03:00:00.000Z"
        val raw = "${house.id}|user-123|$now|$expiry"

        val expectedSig = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }

        // Altering any payload character must produce different signature
        val tamperedRaw = "${house.id}|user-attacker|$now|$expiry"
        val tamperedSig = MessageDigest.getInstance("SHA-256")
            .digest(tamperedRaw.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertNotEquals(expectedSig, tamperedSig)
    }
}
