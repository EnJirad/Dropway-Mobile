package com.pocketdev.dropway.data.sharing

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.pocketdev.dropway.data.model.House
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultMetadata(
    val vaultId: String,
    val vaultTitle: String,
    val creatorId: String,
    val creatorName: String,
    val recordCount: Int,
    val createdAt: String,
    val checksum: String,
    val formatVersion: Int
)

data class LicenseResult(
    val isValid: Boolean,
    val planName: String = "",
    val expiryTimestampMs: Long = 0L,
    val message: String = ""
)

/**
 * VaultCryptoManager: Handles device-level hardware encryption,
 * proprietary .dwv container packing/unpacking, and offline subscription license validation.
 */
object VaultCryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "DropwayVaultDeviceKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // Magic Bytes for .dwv files: 'DWVAULT' + Version 0x02
    val DWV_MAGIC = byteArrayOf('D'.code.toByte(), 'W'.code.toByte(), 'V'.code.toByte(), 'A'.code.toByte(), 'U'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    const val DWV_VERSION: Byte = 0x02

    // Master Platform Licensing Salt for Dropway Subscription Gating
    private val PLATFORM_LICENSE_SALT = "DropwayPlatformLicenseSalt_v2_202611".toByteArray(StandardCharsets.UTF_8)
    private val LICENSE_SIGNATURE_KEY = "DropwaySecretKey_PlatformOwner_SubscriptionEngine_2026".toByteArray(StandardCharsets.UTF_8)

    // -----------------------------------------------------------------------------------------
    // 1. Device-Level Hardware AES-256-GCM Encryption
    // -----------------------------------------------------------------------------------------

    private fun getOrCreateDeviceKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) return entry.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        } catch (_: Exception) {
            // JVM Unit test fallback
            val fallback = MessageDigest.getInstance("SHA-256").digest("DropwayDeviceFallbackKey".toByteArray())
            return SecretKeySpec(fallback, "AES")
        }
    }

    fun encryptDeviceString(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateDeviceKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherText)
            Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        } catch (_: Exception) {
            plaintext
        }
    }

    fun decryptDeviceString(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        return try {
            val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (decoded.size <= GCM_IV_LENGTH) return ciphertext

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH)

            val cipherTextLength = decoded.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherTextLength)
            System.arraycopy(decoded, GCM_IV_LENGTH, cipherText, 0, cipherTextLength)

            val secretKey = getOrCreateDeviceKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ciphertext
        }
    }

    // -----------------------------------------------------------------------------------------
    // 2. Proprietary .dwv Binary Vault Packager (No Plain JSON)
    // -----------------------------------------------------------------------------------------

    /**
     * Packs houses into an encrypted, compressed .dwv binary file.
     */
    fun packVault(
        houses: List<House>,
        vaultTitle: String,
        creatorId: String,
        creatorName: String
    ): ByteArray {
        val vaultId = UUID.randomUUID().toString()
        val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

        // 1. Build Payload JSON
        val rootJson = JSONObject().apply {
            put("vault_id", vaultId)
            put("vault_title", vaultTitle)
            put("creator_id", creatorId)
            put("creator_name", creatorName)
            put("created_at", createdAt)
            val array = JSONArray()
            for (h in houses) {
                array.put(h.toJson())
            }
            put("records", array)
        }
        val payloadRawBytes = rootJson.toString().toByteArray(StandardCharsets.UTF_8)

        // 2. Compute Checksum
        val checksumBytes = MessageDigest.getInstance("SHA-256").digest(payloadRawBytes)
        val checksumHex = checksumBytes.joinToString("") { "%02x".format(it) }

        // 3. Compress Payload with ZLIB
        val compressedOut = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val deflaterOut = DeflaterOutputStream(compressedOut, deflater)
        deflaterOut.write(payloadRawBytes)
        deflaterOut.close()
        val compressedBytes = compressedOut.toByteArray()

        // 4. Derive Vault Encryption Key using Platform Licensing Key
        val vaultKeyBytes = deriveVaultKey(PLATFORM_LICENSE_SALT)
        val vaultSecretKey = SecretKeySpec(vaultKeyBytes, "AES")

        // 5. Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, vaultSecretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encryptedPayload = cipher.doFinal(compressedBytes)

        // 6. Build Metadata Header JSON
        val metaJson = JSONObject().apply {
            put("vault_id", vaultId)
            put("vault_title", vaultTitle)
            put("creator_id", creatorId)
            put("creator_name", creatorName)
            put("record_count", houses.size)
            put("created_at", createdAt)
            put("checksum", checksumHex)
            put("format_version", DWV_VERSION.toInt())
        }
        val metaBytes = metaJson.toString().toByteArray(StandardCharsets.UTF_8)

        // 7. Assemble Binary Container:
        // [MAGIC 7 bytes] [VERSION 1 byte] [META_LEN 4 bytes] [META_BYTES] [IV 12 bytes] [PAYLOAD_LEN 4 bytes] [PAYLOAD_BYTES]
        val totalCapacity = DWV_MAGIC.size + 1 + 4 + metaBytes.size + GCM_IV_LENGTH + 4 + encryptedPayload.size
        val buffer = ByteBuffer.allocate(totalCapacity)

        buffer.put(DWV_MAGIC)
        buffer.put(DWV_VERSION)
        buffer.putInt(metaBytes.size)
        buffer.put(metaBytes)
        buffer.put(iv)
        buffer.putInt(encryptedPayload.size)
        buffer.put(encryptedPayload)

        return buffer.array()
    }

    /**
     * Inspects a .dwv file header and returns metadata without needing subscription or decrypting payload.
     */
    fun inspectVaultHeader(bytes: ByteArray): VaultMetadata? {
        if (bytes.size < DWV_MAGIC.size + 1 + 4) return null

        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(DWV_MAGIC.size)
        buffer.get(magic)
        if (!magic.contentEquals(DWV_MAGIC)) return null

        val version = buffer.get()
        if (version != DWV_VERSION) return null

        val metaLen = buffer.int
        if (metaLen <= 0 || metaLen > bytes.size - buffer.position()) return null

        val metaBytes = ByteArray(metaLen)
        buffer.get(metaBytes)

        return try {
            val json = JSONObject(String(metaBytes, StandardCharsets.UTF_8))
            VaultMetadata(
                vaultId = json.optString("vault_id"),
                vaultTitle = json.optString("vault_title", "คลังบ้าน"),
                creatorId = json.optString("creator_id"),
                creatorName = json.optString("creator_name", "ผู้ส่งนิรนาม"),
                recordCount = json.optInt("record_count", 0),
                createdAt = json.optString("created_at"),
                checksum = json.optString("checksum"),
                formatVersion = json.optInt("format_version", 1)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unpacks a .dwv vault container.
     * Enforces active subscription check: non-subscribers are blocked from decrypting.
     */
    fun unpackVault(bytes: ByteArray, isSubscribed: Boolean): Result<List<House>> {
        if (!isSubscribed) {
            return Result.failure(IllegalStateException("SUBSCRIPTION_REQUIRED: ต้องมีแพ็กเกจ Dropway Active Subscription เพื่อเปิดคลังข้อมูลนี้"))
        }

        return runCatching {
            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(DWV_MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(DWV_MAGIC)) {
                throw IllegalArgumentException("ไฟล์ไม่ใช่รูปแบบ Dropway Vault (.dwv) ที่ถูกต้อง")
            }

            val version = buffer.get()
            if (version != DWV_VERSION) {
                throw IllegalArgumentException("เวอร์ชันไฟล์ไม่รองรับ (v$version)")
            }

            val metaLen = buffer.int
            val metaBytes = ByteArray(metaLen)
            buffer.get(metaBytes)
            val metaJson = JSONObject(String(metaBytes, StandardCharsets.UTF_8))
            val creatorId = metaJson.optString("creator_id")
            val creatorName = metaJson.optString("creator_name")

            val iv = ByteArray(GCM_IV_LENGTH)
            buffer.get(iv)

            val payloadLen = buffer.int
            val encryptedPayload = ByteArray(payloadLen)
            buffer.get(encryptedPayload)

            // Decrypt with Platform Key
            val vaultKeyBytes = deriveVaultKey(PLATFORM_LICENSE_SALT)
            val vaultSecretKey = SecretKeySpec(vaultKeyBytes, "AES")
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, vaultSecretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val compressedBytes = cipher.doFinal(encryptedPayload)

            // Decompress ZLIB
            val inflater = Inflater()
            val inflaterIn = InflaterInputStream(ByteArrayInputStream(compressedBytes), inflater)
            val decompressedOut = ByteArrayOutputStream()
            val tempBuffer = ByteArray(4096)
            var bytesRead: Int
            while (inflaterIn.read(tempBuffer).also { bytesRead = it } != -1) {
                decompressedOut.write(tempBuffer, 0, bytesRead)
            }
            inflaterIn.close()

            val rawJsonString = decompressedOut.toString(StandardCharsets.UTF_8.name())
            val root = JSONObject(rawJsonString)
            val recordsArray = root.optJSONArray("records") ?: JSONArray()

            val houses = mutableListOf<House>()
            for (i in 0 until recordsArray.length()) {
                val item = recordsArray.getJSONObject(i)
                val house = House.fromJson(item).copy(
                    creatorId = creatorId,
                    creatorName = creatorName,
                    sourceType = "shared",
                    isShared = true
                )
                houses.add(house)
            }
            houses
        }
    }

    private fun deriveVaultKey(salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(LICENSE_SIGNATURE_KEY)
        return digest.digest(salt)
    }

    // -----------------------------------------------------------------------------------------
    // 3. Cryptographic Subscription License Verification
    // -----------------------------------------------------------------------------------------

    /**
     * Generates an offline activation code for testing or licensing:
     * Format: DW-[PLAN]-[EXPIRY_HEX]-[SIGNATURE_HEX]
     */
    fun generateLicenseCode(userId: String, plan: String = "PRO", validDays: Int = 30): String {
        val expiryMs = System.currentTimeMillis() + (validDays.toLong() * 24 * 60 * 60 * 1000)
        val expiryHex = java.lang.Long.toHexString(expiryMs)
        val payload = "$userId|$plan|$expiryHex"

        val hmac = MessageDigest.getInstance("SHA-256").digest((payload + String(LICENSE_SIGNATURE_KEY, StandardCharsets.UTF_8)).toByteArray())
        val sigHex = hmac.take(4).joinToString("") { "%02X".format(it) }

        return "DW-$plan-$expiryHex-$sigHex".uppercase(Locale.US)
    }

    /**
     * Verifies an offline activation code entered by the user.
     */
    fun verifyLicenseCode(code: String, userId: String): LicenseResult {
        val clean = code.trim().uppercase(Locale.US)
        val parts = clean.split("-")
        if (parts.size != 4 || parts[0] != "DW") {
            return LicenseResult(isValid = false, message = "รูปแบบรหัสไม่ถูกต้อง (ตัวอย่าง: DW-PRO-...)")
        }

        val plan = parts[1]
        val expiryHex = parts[2]
        val sigHex = parts[3]

        return try {
            val expiryMs = java.lang.Long.parseUnsignedLong(expiryHex, 16)
            if (System.currentTimeMillis() > expiryMs) {
                return LicenseResult(isValid = false, message = "รหัสเปิดใช้งานนี้หมดอายุแล้ว")
            }

            // Verify signature
            val payload = "$userId|$plan|$expiryHex"
            val hmac = MessageDigest.getInstance("SHA-256").digest((payload + String(LICENSE_SIGNATURE_KEY, StandardCharsets.UTF_8)).toByteArray())
            val expectedSigHex = hmac.take(4).joinToString("") { "%02X".format(it) }

            if (expectedSigHex.equals(sigHex, ignoreCase = true)) {
                LicenseResult(
                    isValid = true,
                    planName = plan,
                    expiryTimestampMs = expiryMs,
                    message = "เปิดใช้งานแพ็กเกจ $plan สำเร็จ"
                )
            } else {
                // Also check with wildcard/universal test code
                val universalPayload = "ALL|$plan|$expiryHex"
                val universalHmac = MessageDigest.getInstance("SHA-256").digest((universalPayload + String(LICENSE_SIGNATURE_KEY, StandardCharsets.UTF_8)).toByteArray())
                val universalSig = universalHmac.take(4).joinToString("") { "%02X".format(it) }

                if (universalSig.equals(sigHex, ignoreCase = true)) {
                    LicenseResult(
                        isValid = true,
                        planName = plan,
                        expiryTimestampMs = expiryMs,
                        message = "เปิดใช้งานแพ็กเกจ $plan สำเร็จ"
                    )
                } else {
                    LicenseResult(isValid = false, message = "รหัสเปิดใช้งานไม่ถูกต้อง")
                }
            }
        } catch (_: Exception) {
            LicenseResult(isValid = false, message = "รหัสเปิดใช้งานไม่ถูกต้องหรือถูกดัดแปลง")
        }
    }
}
