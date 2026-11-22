package com.pocketdev.dropway

import com.pocketdev.dropway.data.model.LabelFields
import com.pocketdev.dropway.util.LabelValidator
import org.junit.Assert.*
import org.junit.Test

class LabelValidatorTest {

    @Test
    fun testNormalizePhoneFormats() {
        assertEquals("0812345678", LabelValidator.normalizePhone("081-234-5678"))
        assertEquals("0812345678", LabelValidator.normalizePhone("+66812345678"))
        assertEquals("0812345678", LabelValidator.normalizePhone("66812345678"))
        assertEquals("0812345678", LabelValidator.normalizePhone("0066812345678"))
        assertEquals("055123456", LabelValidator.normalizePhone("055-123456")) // landline 9 digits
        assertEquals("", LabelValidator.normalizePhone("12345")) // too short
        assertEquals("", LabelValidator.normalizePhone("abcdefghij")) // not digits
    }

    @Test
    fun testNormalizePostal() {
        assertEquals("63110", LabelValidator.normalizePostal("แม่สอด จ.ตาก 63110"))
        assertEquals("10110", LabelValidator.normalizePostal("10110"))
        assertEquals("", LabelValidator.normalizePostal("ไม่มีรหัสไปรษณีย์ 1234"))
    }

    @Test
    fun testNormalizeHouseNo() {
        assertEquals("1442/3", LabelValidator.normalizeHouseNo("1442/3 ม.2"))
        assertEquals("590", LabelValidator.normalizeHouseNo("590 แม่ตาว"))
        assertEquals("", LabelValidator.normalizeHouseNo("ไม่มีเลขที่บ้าน"))
    }

    @Test
    fun testNormalizeTracking() {
        assertEquals("TH0123456789A", LabelValidator.normalizeTracking("th-0123-4567-89a"))
        assertEquals("SPXTH12345678", LabelValidator.normalizeTracking("SPX_TH_12345678"))
        assertEquals("", LabelValidator.normalizeTracking("ABC")) // too short (< 8)
        assertEquals("", LabelValidator.normalizeTracking("ABCDEFGHIJKL")) // no digits
    }

    @Test
    fun testNormalizeName() {
        assertEquals("สมชาย เข็มกลัด", LabelValidator.normalizeName("ผู้รับ: สมชาย เข็มกลัด ~*"))
        assertEquals("มานะ ชูใจ", LabelValidator.normalizeName("To: มานะ ชูใจ"))
    }

    @Test
    fun testInvalidFieldsDetection() {
        val badFields = LabelFields(
            name = "12345", // digits only
            address = "สั้น", // < 8 chars
            houseNo = "abc", // invalid houseNo
            tracking = "short" // < 8
        )
        val invalids = LabelValidator.invalidFields(badFields)
        assertTrue(invalids.contains("name"))
        assertTrue(invalids.contains("address"))
        assertTrue(invalids.contains("houseNo"))
        assertTrue(invalids.contains("tracking"))
    }

    @Test
    fun testCriticalGapsDetection() {
        val incompleteFields = LabelFields(
            name = "",
            phone = "",
            address = "บ้านเลขที่ 1442/3 ม.2 ต.แม่ตาว อ.แม่สอด จ.ตาก",
            tracking = ""
        )
        val gaps = LabelValidator.criticalGaps(incompleteFields)
        assertTrue(gaps.contains("name"))
        assertTrue(gaps.contains("phone"))
        assertTrue(gaps.contains("tracking"))
        assertFalse(gaps.contains("address"))
    }
}
