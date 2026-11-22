package com.pocketdev.dropway.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dropway_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DATABASE_URL = "neon_database_url"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_HAND_MODE = "hand_mode" // "right" or "left"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_STATUS = "last_status"
    }

    var databaseUrl: String
        get() = prefs.getString(KEY_DATABASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DATABASE_URL, value.trim()).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    var handMode: String
        get() = prefs.getString(KEY_HAND_MODE, "right") ?: "right"
        set(value) = prefs.edit().putString(KEY_HAND_MODE, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "default-user") ?: "default-user"
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var lastConnectionStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_STATUS, value).apply()

    var sharingSubscriptionExpiryMs: Long
        get() = prefs.getLong("sharing_subscription_expiry_ms", 0L)
        set(value) = prefs.edit().putLong("sharing_subscription_expiry_ms", value).apply()

    fun isConfigured(): Boolean {
        val url = databaseUrl
        return url.isNotBlank() && (url.startsWith("postgres://") || url.startsWith("postgresql://"))
    }
}
