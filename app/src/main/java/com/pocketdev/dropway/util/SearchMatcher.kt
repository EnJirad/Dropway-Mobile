package com.pocketdev.dropway.util

import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.data.model.MatchResult

object SearchMatcher {

    private val TITLE_REGEX = Regex("^(?:นางสาว|นาง|นาย|น\\.ส\\.|น\\.ส|นส\\.|คุณ|เด็กชาย|เด็กหญิง|ด\\.ช\\.|ด\\.ญ\\.|mr\\.?|mrs\\.?|miss|ms\\.?)\\s*", RegexOption.IGNORE_CASE)
    private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val PHONE_REGEX = Regex("0[689]\\d{8}|0[2-7]\\d{7}")
    private val SKELETON_NON_ALPHANUM_REGEX = Regex("[^\\p{L}\\p{N}]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val MOO_SPLIT_REGEX = Regex("""ม\.|หมู่|moo|\bm\.""", RegexOption.IGNORE_CASE)
    private val HOUSE_NO_REGEX = Regex("""\d{1,5}(?:/\d{1,4})?""")
    private val MOO_CLEAN_REGEX = Regex("""(?:ม\.|หมู่(?:ที่)?|moo\.?)\s*\d+""", RegexOption.IGNORE_CASE)
    private val TEXT_NUMBERS_REGEX = Regex("""(?:^|\D)(\d{1,5}(?:/\d{1,4})?)(?!\d)""")
    private val DASH_WHITESPACE_REGEX = Regex("[\\s-]")
    private val DISTRICT_REGEX = Regex("""(?:ต\.|ตำบล|อ\.|อำเภอ)\s*([^ \t\n]+)""")
    private val PROVINCE_STRIP_REGEX = Regex("""(?:จ\.|จังหวัด).*$""")
    private val HOUSE_NUMBER_EXACT_REGEX = Regex("""^\d+(?:/\d+)?$""")

    fun skeleton(value: String): String {
        return value.lowercase()
            .replace(UUID_REGEX, " ")
            .replace(SKELETON_NON_ALPHANUM_REGEX, "")
    }

    private fun bigrams(value: String): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until value.length - 1) {
            out.add(value.substring(i, i + 2))
        }
        return out
    }

    fun nameScore(fullName: String?, textSkel: String): Float {
        if (fullName.isNullOrBlank()) return 0f
        val base = fullName.trim()
        val noTitle = base.replace(TITLE_REGEX, "")
        val name = if (noTitle.length >= 2) noTitle else base
        val full = skeleton(name)
        if (full.length < 3) return 0f
        if (textSkel.contains(full)) return 1f

        var best = 0f
        val tokens = name.split(WHITESPACE_REGEX)
            .map { skeleton(it) }
            .filter { it.length >= 3 }
        if (tokens.isNotEmpty()) {
            val hits = tokens.count { textSkel.contains(it) }
            best = hits.toFloat() / tokens.size
        }
        if (full.length >= 5) {
            val grams = bigrams(full)
            if (grams.isNotEmpty()) {
                val pool = bigrams(textSkel).toSet()
                val coverage = grams.count { pool.contains(it) }.toFloat() / grams.size
                if (coverage >= 0.85f) {
                    best = maxOf(best, 0.8f)
                }
            }
        }
        return best
    }

    private fun usableNumber(n: String): Boolean {
        return n.length >= 2 || n.contains("/")
    }

    fun houseNumbers(address: String?): List<String> {
        if (address.isNullOrBlank()) return emptyList()
        val head = address.split(MOO_SPLIT_REGEX, 2).firstOrNull() ?: address
        return HOUSE_NO_REGEX.findAll(head)
            .map { it.value }
            .filter { usableNumber(it) }
            .toList()
    }

    fun extractHouseNo(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val head = address.split(MOO_SPLIT_REGEX, 2).firstOrNull() ?: address
        return HOUSE_NO_REGEX.find(head)?.value
    }

    fun textNumbers(text: String): Set<String> {
        val cleaned = text.replace(MOO_CLEAN_REGEX, " ")
        val found = mutableSetOf<String>()
        val matches = TEXT_NUMBERS_REGEX.findAll(cleaned)
        for (m in matches) {
            val num = m.groupValues[1]
            if (usableNumber(num)) found.add(num)
        }
        return found
    }

    fun phonesInText(text: String): List<String> {
        val compact = text.replace(DASH_WHITESPACE_REGEX, "")
        return PHONE_REGEX.findAll(compact).map { it.value }.toList()
    }

    /**
     * Ported directly from Dropway's parcelMatch.ts
     * Matches parcel label text (or OCR text) against houses in the database.
     */
    fun matchHouses(houses: List<House>, text: String): List<MatchResult> {
        val cleanText = LabelParser.normalizeOcrText(text)
        val textSkel = skeleton(cleanText)
        val nums = textNumbers(cleanText)
        val digits = cleanText.filter { it.isDigit() }
        val labelPhones = phonesInText(cleanText)
        val trackingCand = LabelParser.extractTracking(cleanText)
        val out = mutableListOf<MatchResult>()

        for (house in houses) {
            val n = nameScore(house.fullName, textSkel)
            val nameMatched = n >= 0.6f
            val houseNoMatched = houseNumbers(house.address).any { nums.contains(it) }
            val phoneDigits = (house.phone ?: "").filter { it.isDigit() }.takeLast(9)
            val phoneMatched = phoneDigits.length == 9 && digits.contains(phoneDigits)

            val score = (if (phoneMatched) 60 else 0) +
                    (if (nameMatched) (n * 50).toInt() else 0) +
                    (if (houseNoMatched) 40 else 0)

            if (score == 0) continue

            val reasons = mutableListOf<String>()
            if (nameMatched) reasons.add(if (n >= 0.99f) "ชื่อตรง" else "ชื่อใกล้เคียง")
            if (phoneMatched) reasons.add("เบอร์โทรตรง")
            if (houseNoMatched) reasons.add("บ้านเลขที่ตรง")

            val conflicts = mutableListOf<String>()
            if (phoneDigits.length == 9 && !phoneMatched && labelPhones.isNotEmpty()) {
                conflicts.add("เบอร์บนฉลากไม่ตรงกับเบอร์ในคลัง")
            }

            var level = "medium"
            if ((n >= 0.99f && houseNoMatched) || (phoneMatched && nameMatched)) {
                level = "exact"
            } else if (phoneMatched || (nameMatched && houseNoMatched)) {
                level = "high"
            }
            if (conflicts.isNotEmpty() && level != "medium") {
                level = "medium"
            }

            out.add(
                MatchResult(
                    house = house,
                    score = score,
                    level = level,
                    reasons = reasons,
                    conflicts = conflicts,
                    trackingCandidate = trackingCand
                )
            )
        }

        return out.sortedByDescending { it.score }.take(8)
    }

    fun extractDistrict(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val match = DISTRICT_REGEX.find(address) ?: return null
        val raw = match.groupValues.getOrNull(1) ?: return null
        val name = PROVINCE_STRIP_REGEX.replace(raw, "").trim()
        return if (name.isNotEmpty()) name else null
    }

    fun isHouseNumber(term: String): Boolean {
        return HOUSE_NUMBER_EXACT_REGEX.matches(term.trim())
    }

    fun rankHouseNumberMatch(address: String?, term: String): Int {
        if (address.isNullOrBlank()) return 0
        val search = term.trim()
        if (!isHouseNumber(search)) return 0

        val head = address.split(MOO_SPLIT_REGEX, 2).firstOrNull() ?: address
        val houseNo = HOUSE_NO_REGEX.find(head)?.value ?: return 0
        val mainPart = houseNo.split("/")[0]
        val hasFraction = houseNo.contains("/")

        return when {
            houseNo == search -> 1_500_000
            mainPart == search && hasFraction -> 1_400_000
            mainPart.startsWith(search) -> 1_300_000 - mainPart.length
            mainPart.contains(search) -> 1_200_000 - mainPart.length
            else -> 0
        }
    }

    fun matchHouseWithSearch(house: House, term: String): Int {
        val clean = term.trim()
        if (clean.isBlank()) return 100

        var score = rankHouseNumberMatch(house.address, clean)
        if (score > 0) return score

        val phone = house.phone?.replace("-", "")?.trim() ?: ""
        val cleanPhone = clean.replace("-", "").trim()
        if (cleanPhone.length >= 4 && phone.contains(cleanPhone)) {
            return 800_000 + if (phone == cleanPhone) 100_000 else 0
        }

        val name = house.fullName?.trim() ?: ""
        if (name.contains(clean, ignoreCase = true)) {
            score += 500_000 + (100 - name.length.coerceAtMost(99))
        }

        val address = house.address?.trim() ?: ""
        if (address.contains(clean, ignoreCase = true)) {
            score += 300_000
        }

        val note = house.note?.trim() ?: ""
        if (note.contains(clean, ignoreCase = true)) {
            score += 100_000
        }

        return score
    }
}
