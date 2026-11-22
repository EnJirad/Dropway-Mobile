package com.pocketdev.dropway

import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.sharing.VaultCryptoManager
import com.pocketdev.dropway.util.MapProviderRegistry
import org.junit.Assert.*
import org.junit.Test

class VaultCryptoManagerTest {

    @Test
    fun testMapProviderRegistryCompleteness() {
        val providers = MapProviderRegistry.PROVIDERS
        assertEquals("Must contain 16 map providers", 16, providers.size)

        val defaultProvider = MapProviderRegistry.getById(MapProviderRegistry.DEFAULT_PROVIDER_ID)
        assertEquals("Default provider must be Google Satellite", "google_satellite", defaultProvider.id)
        assertTrue(defaultProvider.tileUrl.contains("lyrs=s"))

        // Check required providers exist
        val ids = providers.map { it.id }.toSet()
        assertTrue(ids.contains("google_satellite"))
        assertTrue(ids.contains("google_hybrid"))
        assertTrue(ids.contains("google_road"))
        assertTrue(ids.contains("google_terrain"))
        assertTrue(ids.contains("google_traffic"))
        assertTrue(ids.contains("osm_standard"))
        assertTrue(ids.contains("esri_satellite"))
        assertTrue(ids.contains("esri_street"))
        assertTrue(ids.contains("esri_topo"))
        assertTrue(ids.contains("esri_terrain"))
        assertTrue(ids.contains("esri_relief"))
        assertTrue(ids.contains("esri_physical"))
        assertTrue(ids.contains("esri_natgeo"))
        assertTrue(ids.contains("open_topo_map"))
        assertTrue(ids.contains("wikimedia"))
        assertTrue(ids.contains("ign_france"))
    }

    @Test
    fun testDwvPackAndInspectHeader() {
        val sampleHouses = listOf(
            House(
                id = "h_101",
                fullName = "นายสมชาย ใจดี",
                phone = "0812345678",
                address = "123/4 หมู่ 2 ต.แม่ตาว อ.แม่สอด",
                lat = 16.71234,
                lng = 98.56789,
                note = "บ้านไม้ 2 ชั้น ประตูสีเขียว"
            ),
            House(
                id = "h_102",
                fullName = "นางสาวสมหญิง นามสมมุติ",
                phone = "0898765432",
                address = "55 หมู่ 1 ต.แม่สอด",
                lat = 16.72000,
                lng = 98.57000
            )
        )

        val dwvBytes = VaultCryptoManager.packVault(
            houses = sampleHouses,
            vaultTitle = "สายส่งแม่ตาว โซน 1",
            creatorId = "drv_test_001",
            creatorName = "สมชาย สายส่ง"
        )

        assertNotNull(dwvBytes)
        assertTrue(dwvBytes.size > 50)

        // Magic bytes check
        val magic = dwvBytes.take(7).toByteArray()
        assertTrue(magic.contentEquals(VaultCryptoManager.DWV_MAGIC))

        // Inspect header without unpacking
        val meta = VaultCryptoManager.inspectVaultHeader(dwvBytes)
        assertNotNull(meta)
        assertEquals("สายส่งแม่ตาว โซน 1", meta?.vaultTitle)
        assertEquals("drv_test_001", meta?.creatorId)
        assertEquals("สมชาย สายส่ง", meta?.creatorName)
        assertEquals(2, meta?.recordCount)
    }

    @Test
    fun testDwvSubscriptionGating() {
        val sampleHouses = listOf(
            House(
                id = "h_secret_1",
                fullName = "ลูกค้าคนสำคัญ",
                phone = "0800000000",
                address = "99 หมู่ 3 อ.แม่สอด",
                lat = 16.6666,
                lng = 98.5555
            )
        )

        val dwvBytes = VaultCryptoManager.packVault(
            houses = sampleHouses,
            vaultTitle = "พิกัดลับ",
            creatorId = "drv_creator_99",
            creatorName = "วิชัย พนักงานส่ง"
        )

        // 1. Unsubscribed user must FAIL to unpack
        val failResult = VaultCryptoManager.unpackVault(dwvBytes, isSubscribed = false)
        assertTrue("Unsubscribed user must fail", failResult.isFailure)
        assertTrue(failResult.exceptionOrNull()?.message?.contains("SUBSCRIPTION_REQUIRED") == true)

        // 2. Subscribed user must SUCCEED to unpack
        val successResult = VaultCryptoManager.unpackVault(dwvBytes, isSubscribed = true)
        assertTrue("Subscribed user must succeed", successResult.isSuccess)
        val unpackedHouses = successResult.getOrThrow()
        assertEquals(1, unpackedHouses.size)

        val h = unpackedHouses[0]
        assertEquals("ลูกค้าคนสำคัญ", h.fullName)
        assertEquals(16.6666, h.lat ?: 0.0, 0.0001)
        assertEquals("drv_creator_99", h.creatorId)
        assertEquals("วิชัย พนักงานส่ง", h.creatorName)
        assertEquals("shared", h.sourceType)
        assertTrue(h.isShared)
    }

    @Test
    fun testLicenseCodeGenerationAndVerification() {
        val userId = "drv_test_user_77"
        val code = VaultCryptoManager.generateLicenseCode(userId, "PRO", validDays = 30)

        assertTrue(code.startsWith("DW-PRO-"))

        val verifySuccess = VaultCryptoManager.verifyLicenseCode(code, userId)
        assertTrue(verifySuccess.isValid)
        assertEquals("PRO", verifySuccess.planName)
        assertTrue(verifySuccess.expiryTimestampMs > System.currentTimeMillis())

        // Tampered code must fail
        val tamperedCode = code.replace("PRO", "VIP")
        val verifyFail = VaultCryptoManager.verifyLicenseCode(tamperedCode, userId)
        assertFalse(verifyFail.isValid)
    }
}
