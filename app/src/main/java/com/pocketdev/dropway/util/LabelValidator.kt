package com.pocketdev.dropway.util

import com.pocketdev.dropway.data.model.LabelFields

object LabelValidator {

    /**
     * เบอร์โทร: เก็บตัวเลข, แปลง +66/66 นำหน้าเป็น 0, คืนค่าเมื่อได้ 9-10 หลักขึ้นต้น 0
     */
    fun normalizePhone(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var digits = raw.filter { it.isDigit() }
        if (digits.length == 11 && digits.startsWith("66")) {
            digits = "0" + digits.substring(2)
        } else if (digits.length == 12 && digits.startsWith("660")) {
            digits = "0" + digits.substring(3)
        } else if (digits.length == 13 && digits.startsWith("0066")) {
            digits = "0" + digits.substring(4)
        }
        return if (Regex("""^0\d{8,9}$""").matches(digits)) digits else ""
    }

    /**
     * ไปรษณีย์ไทย 10xxx-96xxx — เลือกกลุ่ม 5 หลักแรกที่เจอ
     */
    fun normalizePostal(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val direct = raw.filter { it.isDigit() }
        if (Regex("""^[1-9]\d{4}$""").matches(direct)) return direct
        val match = Regex("""(?:^|\D)([1-9]\d{4})(?!\d)""").find(raw)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * บ้านเลขที่: 1-5 หลัก มี / ได้ 1-4 หลัก เช่น 12/3, 247/147
     */
    fun normalizeHouseNo(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val match = Regex("""\d{1,5}(?:/\d{1,4})?""").find(raw)
        return match?.value ?: ""
    }

    /**
     * Tracking: ตัวพิมพ์ใหญ่ ไม่มีช่องว่าง/ขีด, 8-40 ตัว, ต้องมีตัวเลข
     */
    fun normalizeTracking(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.uppercase()
            .replace(Regex("""[\s\-_]+"""), "")
            .replace(Regex("""[^A-Z0-9]"""), "")
        if (cleaned.length < 8 || cleaned.length > 40) return ""
        if (!cleaned.any { it.isDigit() }) return ""
        return cleaned
    }

    /**
     * ชื่อผู้รับ: ตัดคำกำกับ/เครื่องหมายตกแต่ง ไม่ให้เกิน 80 ตัวอักษร
     */
    fun normalizeName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.replace(Regex("""\s+"""), " ")
            .replace(Regex("""^[\s~*•·\-]+|[\s~*•·\-]+$"""), "")
            .replace(Regex("""^(?:ผู้รับ(?:ปลายทาง)?|ชื่อผู้รับ|ถึง|ส่งถึง|to|recipient)\s*[:：]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (s.length > 80) s = s.substring(0, 80).trim()
        return s
    }

    /**
     * ที่อยู่: ยุบบรรทัด/ช่องว่างซ้ำ แต่ "ไม่" ตัดส่วนใดทิ้ง
     */
    fun normalizeAddress(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .replace(Regex("""^[\s,;]+|[\s,;]+$"""), "")
            .trim()
    }

    /**
     * แปลงทุกช่องให้เป็นรูปแบบมาตรฐาน (ค่าที่ผิดรูปจะกลายเป็น "")
     */
    fun cleanFields(fields: LabelFields): LabelFields {
        return LabelFields(
            name = normalizeName(fields.name),
            phone = normalizePhone(fields.phone),
            houseNo = normalizeHouseNo(fields.houseNo),
            address = normalizeAddress(fields.address),
            postalCode = normalizePostal(fields.postalCode),
            tracking = normalizeTracking(fields.tracking)
        )
    }

    /**
     * ช่องที่ "มีค่าแต่ใช้ไม่ได้" — ใช้ตัดสินว่าจะเชื่อ AI หรือใช้ OCR แทน
     */
    fun invalidFields(f: LabelFields): List<String> {
        val bad = mutableListOf<String>()
        if (f.name.isNotBlank() && (f.name.length < 2 || f.name.all { it.isDigit() })) {
            bad.add("name")
        }
        if (f.address.isNotBlank() && f.address.length < 8) {
            bad.add("address")
        }
        if (f.houseNo.isNotBlank() && !Regex("""^\d{1,5}(?:/\d{1,4})?$""").matches(f.houseNo)) {
            bad.add("houseNo")
        }
        if (f.tracking.isNotBlank() && normalizeTracking(f.tracking) != f.tracking) {
            bad.add("tracking")
        }
        return bad
    }

    /**
     * ช่องสำคัญที่ยังว่างหรือผิดรูป — ใช้สั่งอ่านซ้ำเฉพาะจุด
     */
    fun criticalGaps(f: LabelFields): List<String> {
        val bad = invalidFields(f)
        val gaps = mutableListOf<String>()
        if (f.name.isBlank() || bad.contains("name")) gaps.add("name")
        if (f.address.isBlank() || bad.contains("address")) gaps.add("address")
        if (f.phone.isBlank()) gaps.add("phone")
        if (f.tracking.isBlank() || bad.contains("tracking")) gaps.add("tracking")
        return gaps
    }
}
