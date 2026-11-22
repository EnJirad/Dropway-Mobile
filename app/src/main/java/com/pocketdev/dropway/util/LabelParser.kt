package com.pocketdev.dropway.util

import com.pocketdev.dropway.data.model.LabelFields

object LabelParser {

    private val THAI_DIGITS = mapOf(
        '๐' to '0', '๑' to '1', '๒' to '2', '๓' to '3', '๔' to '4',
        '๕' to '5', '๖' to '6', '๗' to '7', '๘' to '8', '๙' to '9'
    )

    private val SENDER_REGEX = Regex("""ผู้ส่ง|ต้นทาง|\bfrom\b|\bsender\b""", RegexOption.IGNORE_CASE)
    private val RECIPIENT_REGEX = Regex("""ผู้รับ|ผู้รับปลายทาง|ปลายทาง|ส่งถึง|\bto\b|recipient|ship\s*to""", RegexOption.IGNORE_CASE)
    private val TITLE_LINE_REGEX = Regex("""(?:นางสาว|นาง|นาย|น\.ส\.|นส\.|คุณ|ด\.ช\.|ด\.ญ\.|\bmr\.?|\bmrs\.?|\bmiss\b|\bms\.?)\s*\S+""", RegexOption.IGNORE_CASE)
    private val ADDRESS_HINT_REGEX = Regex("""ม\.|หมู่|ต\.|อ\.|จ\.|ตำบล|อำเภอ|จังหวัด|ซ\.|ซอย|ถ\.|ถนน|แขวง|เขต|หมู่บ้าน|บ\.""")
    private val PHONE_REGEX = Regex("""0[689]\d{8}|0[2-7]\d{7}""")

    fun thaiDigitsToArabic(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(THAI_DIGITS[c] ?: c)
        }
        return sb.toString()
    }

    fun normalizeNumericRuns(text: String): String {
        val tokens = text.split(" ")
        return tokens.joinToString(" ") { token ->
            val digitCount = token.count { it.isDigit() }
            val letterOorL = token.count { it == 'O' || it == 'o' || it == 'l' || it == 'I' || it == '|' }
            if (digitCount >= 2 && (digitCount + letterOorL).toFloat() / token.length.coerceAtLeast(1) >= 0.7f) {
                token.replace('O', '0')
                    .replace('o', '0')
                    .replace('l', '1')
                    .replace('I', '1')
                    .replace('|', '1')
            } else {
                token
            }
        }
    }

    fun normalizeOcrText(text: String): String {
        val noZeroWidth = text.replace(Regex("""[\u200B\u200C\u200D\uFEFF]"""), "")
        val arabic = thaiDigitsToArabic(noZeroWidth)
        return normalizeNumericRuns(arabic)
    }

    private fun findPhone(line: String): String {
        val compact = normalizeNumericRuns(line).replace(Regex("""[\s-]"""), "")
        return PHONE_REGEX.find(compact)?.value ?: ""
    }

    private fun stripLabelPrefix(line: String): String {
        return line.replace(
            Regex("""^(?:ผู้รับ(?:ปลายทาง)?|ส่งถึง|ถึง|ชื่อ|ที่อยู่|เบอร์โทร|โทร|tel\.?|to|recipient|ship\s*to)\s*[:：]?\s*""", RegexOption.IGNORE_CASE),
            ""
        ).trim()
    }

    fun extractTracking(text: String): String? {
        val upper = normalizeNumericRuns(thaiDigitsToArabic(text))
            .replace(Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""), " ")
            .uppercase()

        val tokens = Regex("""\b[A-Z0-9]{8,24}\b""").findAll(upper).map { it.value }.toList()
        var best: String? = null
        var bestRank = 0

        for (token in tokens) {
            val digitsOnly = token.filter { it.isDigit() }
            if (digitsOnly.length < 6) continue
            if (Regex("""^0\d{9}$""").matches(token)) continue // Phone number

            var rank = 1
            if (Regex("""^[A-Z]{2}\d{9}[A-Z]{2}$""").matches(token) ||
                token.startsWith("TH") || token.startsWith("SPX") || token.startsWith("KEX") ||
                token.startsWith("FLASH") || token.startsWith("JT") || token.startsWith("LEX") ||
                token.startsWith("SHP") || token.startsWith("NIM") || token.startsWith("BEST") ||
                token.startsWith("DHL")) {
                rank = 3
            } else if (token.any { it.isLetter() }) {
                rank = 2
            }

            if (rank > bestRank) {
                best = token
                bestRank = rank
            }
        }
        return best
    }

    fun parseLabel(text: String): LabelFields {
        val clean = normalizeOcrText(text)
        val lines = clean.lines()
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() }

        val recipientLines = mutableListOf<String>()
        var mode = "unknown" // "unknown", "sender", "recipient"

        for (line in lines) {
            if (SENDER_REGEX.containsMatchIn(line) && !RECIPIENT_REGEX.containsMatchIn(line)) {
                mode = "sender"
            } else if (RECIPIENT_REGEX.containsMatchIn(line)) {
                mode = "recipient"
            }
            if (mode != "sender") {
                recipientLines.add(line)
            }
        }

        val tracking = extractTracking(clean) ?: ""

        var phone = ""
        for (line in recipientLines) {
            phone = findPhone(line)
            if (phone.isNotBlank()) break
        }

        var name = ""
        for (line in recipientLines) {
            if (!TITLE_LINE_REGEX.containsMatchIn(line) && !RECIPIENT_REGEX.containsMatchIn(line)) continue
            val cleaned = stripLabelPrefix(line.replace(PHONE_REGEX, "")).replace(Regex("""[\d/]+$"""), "").trim()
            val m = TITLE_LINE_REGEX.find(cleaned)
            val candidate = (if (m != null) cleaned.substring(m.range.first) else cleaned).trim()
            val firstWord = candidate.split(" ").firstOrNull() ?: ""
            if (candidate.length >= 2 && !ADDRESS_HINT_REGEX.containsMatchIn(firstWord)) {
                name = candidate
                break
            }
        }

        val addressLines = recipientLines
            .filter { ADDRESS_HINT_REGEX.containsMatchIn(it) || Regex("""^\d{1,5}(?:/\d{1,4})?\s""").containsMatchIn(it) }
            .map { stripLabelPrefix(it.replace(PHONE_REGEX, "")).trim() }
            .filter { it.isNotBlank() && it != name }

        val rawAddress = addressLines.joinToString(" ").trim()
        val head = rawAddress.split(Regex("""ม\.|หมู่|\bmoo\b""", RegexOption.IGNORE_CASE)).firstOrNull() ?: rawAddress
        val houseNo = Regex("""\d{1,5}(?:/\d{1,4})?""").find(head)?.value ?: ""

        // Extract 5-digit postal code
        val postalMatches = Regex("""(?:^|\D)([1-9]\d{4})(?!\d)""").findAll(rawAddress).toList()
        val fallbackPostalMatches = Regex("""(?:^|\D)([1-9]\d{4})(?!\d)""").findAll(recipientLines.joinToString(" ")).toList()
        val postalCode = postalMatches.lastOrNull()?.groupValues?.get(1)
            ?: fallbackPostalMatches.lastOrNull()?.groupValues?.get(1)
            ?: ""

        return LabelFields(
            name = name,
            phone = phone,
            houseNo = houseNo,
            address = rawAddress,
            postalCode = postalCode,
            tracking = tracking
        )
    }

    fun composeAddress(houseNo: String, address: String, postalCode: String): String {
        val parts = mutableListOf<String>()
        val trimmedAddress = address.trim()
        val trimmedHouseNo = houseNo.trim()
        val trimmedPostal = postalCode.trim()

        if (trimmedHouseNo.isNotEmpty() && !trimmedAddress.contains(trimmedHouseNo)) {
            parts.add(trimmedHouseNo)
        }
        if (trimmedAddress.isNotEmpty()) {
            parts.add(trimmedAddress)
        }
        if (trimmedPostal.isNotEmpty() && !trimmedAddress.contains(trimmedPostal)) {
            parts.add(trimmedPostal)
        }
        return parts.joinToString(" ")
    }
}
