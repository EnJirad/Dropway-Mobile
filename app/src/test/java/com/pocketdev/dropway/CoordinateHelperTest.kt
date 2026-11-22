package com.pocketdev.dropway

import com.pocketdev.dropway.util.CoordinateHelper
import org.junit.Assert.*
import org.junit.Test

class CoordinateHelperTest {

    @Test
    fun testValidCoordinateParsing() {
        val parsed = CoordinateHelper.parse("16.66474,98.57846")
        assertNotNull(parsed)
        assertEquals(16.66474, parsed!!.lat, 0.000001)
        assertEquals(98.57846, parsed.lng, 0.000001)
    }

    @Test
    fun testWhitespaceNormalization() {
        val parsed = CoordinateHelper.parse("  16.664740 ,  98.578460  ")
        assertNotNull(parsed)
        assertEquals(16.66474, parsed!!.lat, 0.000001)
        assertEquals(98.57846, parsed.lng, 0.000001)
    }

    @Test
    fun testInvalidRanges() {
        // Latitude > 90
        assertNull(CoordinateHelper.parse("95.0, 98.0"))
        // Latitude < -90
        assertNull(CoordinateHelper.parse("-91.0, 98.0"))
        // Longitude > 180
        assertNull(CoordinateHelper.parse("16.0, 185.0"))
        // Longitude < -180
        assertNull(CoordinateHelper.parse("16.0, -181.0"))
    }

    @Test
    fun testMalformedInputs() {
        assertNull(CoordinateHelper.parse(""))
        assertNull(CoordinateHelper.parse("   "))
        assertNull(CoordinateHelper.parse(null))
        assertNull(CoordinateHelper.parse("16.66474"))
        assertNull(CoordinateHelper.parse("abc,def"))
        assertNull(CoordinateHelper.parse("16.66474,98.57846,100"))
    }

    @Test
    fun testFormatting() {
        val str = CoordinateHelper.format(16.66474, 98.57846)
        assertEquals("16.664740,98.578460", str)
        assertEquals("", CoordinateHelper.format(null, 98.0))
        assertEquals("", CoordinateHelper.format(16.0, null))
    }
}
