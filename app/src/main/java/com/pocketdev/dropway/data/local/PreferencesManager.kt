package com.pocketdev.dropway.data.local

import android.content.Context
import android.content.SharedPreferences
import com.pocketdev.dropway.util.MapProviderRegistry
import java.util.UUID

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dropway_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DATABASE_URL = "neon_database_url"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_HAND_MODE = "hand_mode" // "right" or "left"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DRIVER_NAME = "driver_name"
        private const val KEY_DEFAULT_MAP_LAYER = "default_map_layer"
        private const val KEY_IS_SUBSCRIBED = "is_subscribed"
        private const val KEY_SUBSCRIPTION_PLAN = "subscription_plan"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry_ms"
    }

    var defaultMapLayer: String
        get() = prefs.getString(KEY_DEFAULT_MAP_LAYER, MapProviderRegistry.DEFAULT_PROVIDER_ID) ?: MapProviderRegistry.DEFAULT_PROVIDER_ID
        set(value) = prefs.edit().putString(KEY_DEFAULT_MAP_LAYER, value).apply()

    var driverName: String
        get() = prefs.getString(KEY_DRIVER_NAME, "พนักงานสายส่ง") ?: "พนักงานสายส่ง"
        set(value) = prefs.edit().putString(KEY_DRIVER_NAME, value.trim()).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    var handMode: String
        get() = prefs.getString(KEY_HAND_MODE, "right") ?: "right"
        set(value) = prefs.edit().putString(KEY_HAND_MODE, value).apply()

    var userId: String
        get() {
            var id = prefs.getString(KEY_USER_ID, "") ?: ""
            if (id.isBlank()) {
                id = "drv_" + UUID.randomUUID().toString().take(8)
                prefs.edit().putString(KEY_USER_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var isSubscribed: Boolean
        get() {
            val explicit = prefs.getBoolean(KEY_IS_SUBSCRIBED, false)
            val expiry = subscriptionExpiryMs
            return explicit || (expiry > System.currentTimeMillis())
        }
        set(value) = prefs.edit().putBoolean(KEY_IS_SUBSCRIBED, value).apply()

    var subscriptionPlan: String
        get() = prefs.getString(KEY_SUBSCRIPTION_PLAN, "PRO_MONTHLY") ?: "PRO_MONTHLY"
        set(value) = prefs.edit().putString(KEY_SUBSCRIPTION_PLAN, value).apply()

    var subscriptionExpiryMs: Long
        get() = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_SUBSCRIPTION_EXPIRY, value).apply()

    var sharingSubscriptionExpiryMs: Long
        get() = subscriptionExpiryMs
        set(value) { subscriptionExpiryMs = value }

    // Legacy compatibility stub (kept as harmless property returning true for offline operation)
    var databaseUrl: String
        get() = prefs.getString(KEY_DATABASE_URL, "offline://dropway.local") ?: "offline://dropway.local"
        set(value) = prefs.edit().putString(KEY_DATABASE_URL, value).apply()

    var lastConnectionStatus: String = "Offline-First Local Vault Active"

    fun isConfigured(): Boolean = true
}
