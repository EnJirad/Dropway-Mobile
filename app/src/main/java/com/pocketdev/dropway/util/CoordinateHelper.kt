package com.pocketdev.dropway.util

import java.util.Locale

object CoordinateHelper {

    data class ParsedCoord(val lat: Double, val lng: Double)

    /**
     * Parses a coordinate string in format "lat,lng" with whitespace normalization and range validation.
     * Latitude: [-90.0, 90.0], Longitude: [-180.0, 180.0].
     */
    fun parse(input: String?): ParsedCoord? {
        if (input.isNullOrBlank()) return null
        val parts = input.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat < -90.0 || lat > 90.0) return null
        if (lng < -180.0 || lng > 180.0) return null
        return ParsedCoord(lat, lng)
    }

    /**
     * Formats latitude and longitude into human-readable standard "lat,lng" format with 6 decimal places.
     */
    fun format(lat: Double?, lng: Double?): String {
        if (lat == null || lng == null) return ""
        return String.format(Locale.US, "%.6f,%.6f", lat, lng)
    }

    /**
     * Validates whether a coordinate string is valid.
     */
    fun isValid(input: String?): Boolean {
        return parse(input) != null
    }
}
