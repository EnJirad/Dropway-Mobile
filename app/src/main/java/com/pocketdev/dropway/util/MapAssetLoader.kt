package com.pocketdev.dropway.util

import android.content.Context

/**
 * Loads and caches bundled Leaflet JS and CSS assets in memory.
 * Ensures map components function 100% offline with zero CDN network dependency.
 */
object MapAssetLoader {

    private var cachedJs: String? = null
    private var cachedCss: String? = null

    fun getLeafletJs(context: Context): String {
        if (cachedJs == null) {
            cachedJs = try {
                context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
        return cachedJs ?: ""
    }

    fun getLeafletCss(context: Context): String {
        if (cachedCss == null) {
            cachedCss = try {
                context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
        return cachedCss ?: ""
    }
}
