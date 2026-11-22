package com.pocketdev.dropway.data.sharing

/**
 * Centralized, configurable subscription pricing and configuration.
 * All pricing is defined here and never hardcoded in UI or business logic.
 */
object SubscriptionConfig {
    /**
     * Target monthly pro subscription price in Thai Baht (99 THB / month).
     */
    var monthlyPriceThb: Int = 99
    var currencyCode: String = "THB"

    const val PLAN_ID = "dropway_pro_monthly"
    const val PLAN_TITLE = "Dropway Pro (ส่งพัสดุอัจฉริยะ)"
    const val PLAN_DESCRIPTION = "ปลดล็อกคลังบ้านไม่จำกัด, โหมดขับรถ HUD นำทางสด, นำเข้า Map Marker KML และจัดเรียงเส้นทางอัตโนมัติ"

    fun getFormattedPrice(): String {
        return "$monthlyPriceThb $currencyCode / เดือน"
    }
}
