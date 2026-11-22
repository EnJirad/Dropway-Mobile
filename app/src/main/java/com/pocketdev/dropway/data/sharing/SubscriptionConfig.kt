package com.pocketdev.dropway.data.sharing

/**
 * Centralized, configurable subscription pricing and configuration.
 * All pricing is defined here and never hardcoded in UI or business logic.
 */
object SubscriptionConfig {
    /**
     * Target monthly sharing subscription price in Thai Baht (THB 20–30 range).
     */
    var monthlyPriceThb: Int = 29
    var currencyCode: String = "THB"

    const val PLAN_ID = "dropway_vault_sharing_monthly"
    const val PLAN_TITLE = "Dropway Vault Sharing (แชร์คลังบ้าน)"
    const val PLAN_DESCRIPTION = "แชร์คลังบ้านและพิกัดส่งพัสดุระหว่างคนขับแบบเข้ารหัสและปลอดภัย"

    fun getFormattedPrice(): String {
        return "$monthlyPriceThb $currencyCode / เดือน"
    }
}
