package com.pocketdev.dropway.util

import android.util.Xml
import com.pocketdev.dropway.data.model.House
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * High-performance, offline-first importer for Map Marker (com.exlyo.mapmarker) data.
 * Supports KML, KMZ (Zipped KML), and CSV formats.
 */
object MapMarkerImporter {

    data class ImportSummary(
        val totalParsed: Int,
        val validHouses: List<House>,
        val skippedCount: Int,
        val filename: String
    )

    private val THAI_PHONE_REGEX = Pattern.compile("(?:\\+?66|0)[689]\\d{8}")
    private val HOUSE_NO_REGEX = Pattern.compile("\\b(\\d{1,5}(?:/\\d{1,4})?)\\b")

    /**
     * Parses an input stream (KML, KMZ, or CSV) based on filename or format sniffing.
     */
    fun parse(inputStream: InputStream, filename: String, creatorId: String, creatorName: String): ImportSummary {
        val lowerName = filename.lowercase()
        return when {
            lowerName.endsWith(".kmz") -> parseKmz(inputStream, filename, creatorId, creatorName)
            lowerName.endsWith(".csv") -> parseCsv(inputStream, filename, creatorId, creatorName)
            else -> parseKml(inputStream, filename, creatorId, creatorName)
        }
    }

    /**
     * Parses KMZ (ZIP archive containing doc.kml or any .kml).
     */
    private fun parseKmz(inputStream: InputStream, filename: String, creatorId: String, creatorName: String): ImportSummary {
        val zis = ZipInputStream(inputStream)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.lowercase().endsWith(".kml")) {
                return parseKml(zis, filename, creatorId, creatorName)
            }
            entry = zis.nextEntry
        }
        return ImportSummary(0, emptyList(), 0, filename)
    }

    /**
     * Parses standard KML using Android's native XmlPullParser.
     */
    fun parseKml(inputStream: InputStream, filename: String, creatorId: String, creatorName: String): ImportSummary {
        val houses = mutableListOf<House>()
        var skipped = 0

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var inPlacemark = false
        var currentTag = ""

        var currentName: String? = null
        var currentDesc: String? = null
        var currentCoordStr: String? = null
        val currentExtendedData = mutableMapOf<String, String>()
        var currentDataKey: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase() ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = tagName
                    if (tagName == "placemark") {
                        inPlacemark = true
                        currentName = null
                        currentDesc = null
                        currentCoordStr = null
                        currentExtendedData.clear()
                        currentDataKey = null
                    } else if (inPlacemark && tagName == "data") {
                        currentDataKey = parser.getAttributeValue(null, "name")?.lowercase()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inPlacemark) {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when (currentTag) {
                                "name" -> if (currentName == null) currentName = text
                                "description" -> if (currentDesc == null) currentDesc = text
                                "coordinates" -> if (currentCoordStr == null) currentCoordStr = text
                                "value" -> {
                                    if (!currentDataKey.isNullOrBlank()) {
                                        currentExtendedData[currentDataKey] = text
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "placemark") {
                        inPlacemark = false
                        val house = createHouseFromPlacemark(
                            name = currentName,
                            desc = currentDesc,
                            coordStr = currentCoordStr,
                            extendedData = currentExtendedData,
                            creatorId = creatorId,
                            creatorName = creatorName
                        )
                        if (house != null) {
                            houses.add(house)
                        } else {
                            skipped++
                        }
                    } else if (tagName == "data") {
                        currentDataKey = null
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return ImportSummary(
            totalParsed = houses.size + skipped,
            validHouses = houses,
            skippedCount = skipped,
            filename = filename
        )
    }

    /**
     * Parses CSV exports from Map Marker or spreadsheets.
     */
    fun parseCsv(inputStream: InputStream, filename: String, creatorId: String, creatorName: String): ImportSummary {
        val houses = mutableListOf<House>()
        var skipped = 0

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val headerLine = reader.readLine() ?: return ImportSummary(0, emptyList(), 0, filename)
        val headers = parseCsvLine(headerLine).map { it.trim().lowercase() }

        val nameIdx = headers.indexOfFirst { it in listOf("name", "title", "placemark", "ชื่อ", "บ้านเลขที่") }
        val latIdx = headers.indexOfFirst { it in listOf("latitude", "lat", "พิกัด lat", "y") }
        val lngIdx = headers.indexOfFirst { it in listOf("longitude", "lng", "lon", "พิกัด lng", "x") }
        val descIdx = headers.indexOfFirst { it in listOf("description", "desc", "note", "notes", "รายละเอียด", "หมายเหตุ") }
        val phoneIdx = headers.indexOfFirst { it in listOf("phone", "tel", "เบอร์โทร", "เบอร์โทรศัพท์") }
        val addrIdx = headers.indexOfFirst { it in listOf("address", "ที่อยู่") }

        var line: String? = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                val cols = parseCsvLine(line)
                val lat = if (latIdx >= 0 && latIdx < cols.size) cols[latIdx].toDoubleOrNull() else null
                val lng = if (lngIdx >= 0 && lngIdx < cols.size) cols[lngIdx].toDoubleOrNull() else null
                val name = if (nameIdx >= 0 && nameIdx < cols.size) cols[nameIdx].trim() else null
                val desc = if (descIdx >= 0 && descIdx < cols.size) cols[descIdx].trim() else null
                val phone = if (phoneIdx >= 0 && phoneIdx < cols.size) cols[phoneIdx].trim() else null
                val addr = if (addrIdx >= 0 && addrIdx < cols.size) cols[addrIdx].trim() else null

                if (name != null || (lat != null && lng != null)) {
                    val finalPhone = phone ?: extractPhone(desc)
                    houses.add(
                        House(
                            id = "imp_" + UUID.randomUUID().toString().take(12),
                            fullName = name ?: "จุดส่งพัสดุ",
                            phone = finalPhone,
                            address = addr ?: desc,
                            lat = lat,
                            lng = lng,
                            note = desc,
                            creatorId = creatorId,
                            creatorName = creatorName,
                            sourceType = "map_marker"
                        )
                    )
                } else {
                    skipped++
                }
            }
            line = reader.readLine()
        }

        return ImportSummary(
            totalParsed = houses.size + skipped,
            validHouses = houses,
            skippedCount = skipped,
            filename = filename
        )
    }

    private fun createHouseFromPlacemark(
        name: String?,
        desc: String?,
        coordStr: String?,
        extendedData: Map<String, String>,
        creatorId: String,
        creatorName: String
    ): House? {
        val cleanName = name?.trim() ?: "จุดส่งพัสดุ"
        var lat: Double? = null
        var lng: Double? = null

        if (!coordStr.isNullOrBlank()) {
            val parts = coordStr.trim().split(",")
            if (parts.size >= 2) {
                // KML coordinates are LNG, LAT [, ALT]
                lng = parts[0].trim().toDoubleOrNull()
                lat = parts[1].trim().toDoubleOrNull()
            }
        }

        // Phone resolution from ExtendedData, description or name
        var phone = extendedData["phone"] ?: extendedData["tel"] ?: extendedData["telephone"]
        if (phone.isNullOrBlank()) {
            phone = extractPhone(desc) ?: extractPhone(cleanName)
        }

        // Address resolution
        val address = extendedData["address"] ?: desc

        return House(
            id = "mm_" + UUID.randomUUID().toString().take(12),
            fullName = cleanName,
            phone = phone,
            address = address,
            lat = lat,
            lng = lng,
            note = desc,
            creatorId = creatorId,
            creatorName = creatorName,
            sourceType = "map_marker"
        )
    }

    private fun extractPhone(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = THAI_PHONE_REGEX.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false

        for (c in line) {
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        sb.append(c)
                    } else {
                        result.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString().trim())
        return result
    }
}
