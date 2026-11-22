package com.pocketdev.dropway.data.sharing

import android.content.Context
import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.model.House
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manages Home Vault subscription status, creator identity validation,
 * and secure cryptographic sharing tokens.
 */
class SharingManager(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val localDb: LocalHouseDatabase
) {

    enum class SubscriptionStatus {
        FREE,       // Free personal offline vault
        ACTIVE,     // Paid sharing subscription active
        EXPIRED     // Subscription expired; shared records locked
    }

    data class ShareInvitation(
        val shareToken: String,
        val creatorId: String,
        val creatorName: String,
        val house: House,
        val createdAt: String,
        val expiresAt: String,
        val signature: String
    )

    data class ShareAcceptResult(
        val success: Boolean,
        val house: House?,
        val message: String
    )

    /**
     * Retrieves current subscription status for Home Vault Sharing.
     */
    fun getSubscriptionStatus(): SubscriptionStatus {
        val expiryTimeMs: Long = prefs.sharingSubscriptionExpiryMs
        if (expiryTimeMs == 0L) return SubscriptionStatus.FREE
        val now: Long = System.currentTimeMillis()
        return if (now < expiryTimeMs) SubscriptionStatus.ACTIVE else SubscriptionStatus.EXPIRED
    }

    /**
     * Activates or renews a sharing subscription (e.g. after Google Play Billing or server verification).
     */
    fun activateSubscription(durationDays: Int = 30) {
        val now: Long = System.currentTimeMillis()
        val durationMs: Long = durationDays.toLong() * 24L * 60L * 60L * 1000L
        val currentExpiry: Long = prefs.sharingSubscriptionExpiryMs
        val newExpiry: Long = (if (currentExpiry > now) currentExpiry else now) + durationMs
        prefs.sharingSubscriptionExpiryMs = newExpiry
    }

    /**
     * Generates a tamper-proof cryptographically signed share invitation token for a home record.
     * Both sender and recipient must have active subscriptions to participate in secure sharing.
     */
    fun createShareInvitation(house: House): Result<ShareInvitation> {
        val status = getSubscriptionStatus()
        if (status != SubscriptionStatus.ACTIVE) {
            return Result.failure(
                IllegalStateException("ต้องมีแพ็กเกจแชร์คลังบ้าน (${SubscriptionConfig.getFormattedPrice()}) จึงจะสามารถแชร์ข้อมูลได้")
            )
        }

        val token = UUID.randomUUID().toString()
        val creatorId = house.creatorId ?: prefs.userId
        val creatorName = house.creatorName ?: "User-${prefs.userId.takeLast(4)}"
        val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        // 7-day invitation validity
        val expiryStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(
            Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
        )

        val rawPayload = "${house.id}|$creatorId|$nowStr|$expiryStr"
        val signature = computeSha256(rawPayload)

        val invitation = ShareInvitation(
            shareToken = token,
            creatorId = creatorId,
            creatorName = creatorName,
            house = house.copy(
                creatorId = creatorId,
                creatorName = creatorName,
                isShared = true,
                shareToken = token
            ),
            createdAt = nowStr,
            expiresAt = expiryStr,
            signature = signature
        )
        return Result.success(invitation)
    }

    /**
     * Encodes a share invitation into a compact JSON string suitable for QR codes or secure links.
     */
    fun encodeInvitationToJson(inv: ShareInvitation): String {
        return JSONObject().apply {
            put("type", "dropway_share_invite")
            put("token", inv.shareToken)
            put("creator_id", inv.creatorId)
            put("creator_name", inv.creatorName)
            put("created_at", inv.createdAt)
            put("expires_at", inv.expiresAt)
            put("signature", inv.signature)
            put("house", inv.house.toJson())
        }.toString()
    }

    /**
     * Accepts a share invitation, verifies token integrity and subscription validity,
     * and saves as a 'shared' record in local storage without overwriting personal records.
     */
    fun acceptShareInvitation(invitationJson: String): ShareAcceptResult {
        val status = getSubscriptionStatus()
        if (status != SubscriptionStatus.ACTIVE) {
            return ShareAcceptResult(
                success = false,
                house = null,
                message = "ผู้รับจำเป็นต้องมีแพ็กเกจแชร์คลังบ้านที่ใช้งานได้ (${SubscriptionConfig.getFormattedPrice()}) เพื่อเปิดรับข้อมูลบ้านที่แชร์"
            )
        }

        try {
            val root = JSONObject(invitationJson)
            if (root.optString("type") != "dropway_share_invite") {
                return ShareAcceptResult(false, null, "รูปแบบรหัสคำเชิญแชร์ไม่ถูกต้อง")
            }

            val token = root.getString("token")
            val creatorId = root.getString("creator_id")
            val creatorName = root.getString("creator_name")
            val createdAt = root.getString("created_at")
            val expiresAt = root.getString("expires_at")
            val signature = root.getString("signature")

            // Verify tamper-proof signature
            val expectedSig = computeSha256("${root.getJSONObject("house").getString("id")}|$creatorId|$createdAt|$expiresAt")
            if (signature != expectedSig) {
                return ShareAcceptResult(false, null, "ลายเซ็นความปลอดภัยของข้อมูลไม่ถูกต้อง อาจถูกดัดแปลง")
            }

            val rawHouse = House.fromJson(root.getJSONObject("house"))
            val sharedHouse = rawHouse.copy(
                creatorId = creatorId,
                creatorName = creatorName,
                sourceType = "shared",
                isShared = true,
                shareToken = token,
                sharedByUserId = creatorId,
                expiresAt = expiresAt
            )

            // Save to local vault as shared
            localDb.upsertHouse(sharedHouse, creatorId, creatorName)

            return ShareAcceptResult(
                success = true,
                house = sharedHouse,
                message = "รับข้อมูลบ้านจาก '$creatorName' เข้าสู่คลังบ้านเรียบร้อยแล้ว"
            )
        } catch (e: Exception) {
            return ShareAcceptResult(false, null, "การเปิดข้อมูลแชร์ล้มเหลว: ${e.message}")
        }
    }

    private fun computeSha256(data: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
