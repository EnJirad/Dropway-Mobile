package com.pocketdev.dropway

import com.pocketdev.dropway.util.LabelParser
import org.junit.Assert.*
import org.junit.Test

class LabelParserTest {

    @Test
    fun testValidShippingLabelParsing() {
        val labelText = """
            ผู้ส่ง: ร้านค้าออนไลน์ 02-123-4567 กทม.
            ผู้รับ: คุณสมชาย ใจดี
            โทร: 081-234-5678
            บ้านเลขที่ 1442/3 หมู่ 2 ต.แม่ตาว อ.แม่สอด จ.ตาก 63110
            เลขพัสดุ: TH0123456789A
        """.trimIndent()

        val parsed = LabelParser.parseLabel(labelText)

        assertEquals("คุณสมชาย ใจดี", parsed.name)
        assertEquals("0812345678", parsed.phone)
        assertEquals("1442/3", parsed.houseNo)
        assertEquals("63110", parsed.postalCode)
        assertEquals("TH0123456789A", parsed.tracking)
        assertTrue(parsed.address.contains("แม่ตาว"))
    }

    @Test
    fun testThaiDigitsConversion() {
        val textWithThaiDigits = "บ้านเลขที่ ๑๔๔๒/๓ โทร ๐๘๑๒๓๔๕๖๗๘ รหัส ๖๓๑๑๐"
        val parsed = LabelParser.parseLabel(textWithThaiDigits)

        assertEquals("0812345678", parsed.phone)
        assertEquals("1442/3", parsed.houseNo)
        assertEquals("63110", parsed.postalCode)
    }

    @Test
    fun testOcrConfusionCorrectionInNumericRuns() {
        // OCR often confuses 'O' with '0' and 'l'/'I' with '1' in numeric strings
        val noisyText = "โทร O8l2345678 บ้านเลขที่ l442/3 63llO"
        val parsed = LabelParser.parseLabel(noisyText)

        assertEquals("0812345678", parsed.phone)
        assertEquals("1442/3", parsed.houseNo)
        assertEquals("63110", parsed.postalCode)
    }

    @Test
    fun testCourierTrackingExtraction() {
        val flashTracking = LabelParser.extractTracking("FLASH EXPRESS TRACKING: TH019283746592B")
        assertEquals("TH019283746592B", flashTracking)

        val spxTracking = LabelParser.extractTracking("SPX Delivery: SPXTH01234567899")
        assertEquals("SPXTH01234567899", spxTracking)

        val kexTracking = LabelParser.extractTracking("Kerry Express: KEX01234567899")
        assertEquals("KEX01234567899", kexTracking)

        val jtTracking = LabelParser.extractTracking("J&T Express: JT889900112233")
        assertEquals("JT889900112233", jtTracking)

        val postTracking = LabelParser.extractTracking("Thailand Post: ED123456789TH")
        assertEquals("ED123456789TH", postTracking)
    }

    @Test
    fun testIncompleteAndMissingInformation() {
        // Only house number and subdistrict, missing name and phone
        val partialText = "12/5 ม.3 ต.แม่ตาว อ.แม่สอด"
        val parsed = LabelParser.parseLabel(partialText)

        assertEquals("", parsed.name)
        assertEquals("", parsed.phone)
        assertEquals("12/5", parsed.houseNo)
        assertTrue(parsed.address.contains("แม่ตาว"))
    }

    @Test
    fun testInvalidAndGarbageText() {
        val garbage = "!!@@##$\$%^&*()---===+++ ไม่ใช่ฉลาก"
        val parsed = LabelParser.parseLabel(garbage)

        assertEquals("", parsed.phone)
        assertEquals("", parsed.houseNo)
        assertEquals("", parsed.postalCode)
        assertEquals("", parsed.tracking)
    }
}
