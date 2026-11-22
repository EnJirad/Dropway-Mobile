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
     * Calculates distance in meters between two geographical points using the Haversine formula.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * Formats distance in meters to a human-readable Thai string (e.g. "45 ม." or "1.2 กม.").
     */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.toInt()} ม."
        } else {
            String.format(Locale.US, "%.1f กม.", meters / 1000.0)
        }
    }

    /**
     * Validates whether a coordinate string is valid.
     */
    fun isValid(input: String?): Boolean {
        return parse(input) != null
    }
}
