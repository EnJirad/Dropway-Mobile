package com.pocketdev.dropway

import com.pocketdev.dropway.data.model.House
import com.pocketdev.dropway.util.SearchMatcher
import org.junit.Assert.*
import org.junit.Test

class SearchMatcherTest {

    private val sampleHouses = listOf(
        House(
            id = "house-1",
            fullName = "คุณสมชาย ใจดี",
            phone = "081-234-5678",
            address = "1442/3 หมู่ 2 ต.แม่ตาว อ.แม่สอด จ.ตาก 63110",
            note = "บ้านสีฟ้า รั้วสีขาว"
        ),
        House(
            id = "house-2",
            fullName = "นางสาวศรุตา วงศ์สวัสดิ์",
            phone = "089-999-8888",
            address = "590 หมู่ 1 ต.แม่ตาว อ.แม่สอด จ.ตาก 63110",
            note = "ร้านขายของชำ"
        ),
        House(
            id = "house-3",
            fullName = "นายมานะ อดทน",
            phone = "062-111-2222",
            address = "12/5 หมู่ 3 ต.แม่ปะ อ.แม่สอด จ.ตาก 63110"
        )
    )

    @Test
    fun testExactHouseNumberAndPhoneMatch() {
        val labelText = "ส่งถึง สมชาย ใจดี โทร 0812345678 บ้านเลขที่ 1442/3 ม.2 แม่ตาว"
        val matches = SearchMatcher.matchHouses(sampleHouses, labelText)

        assertFalse(matches.isEmpty())
        val top = matches.first()
        assertEquals("house-1", top.house.id)
        assertEquals("exact", top.level)
        assertTrue(top.reasons.contains("บ้านเลขที่ตรง"))
        assertTrue(top.reasons.contains("เบอร์โทรตรง"))
    }

    @Test
    fun testHighConfidenceMatchByHouseNumberAndName() {
        // Label with correct house number and name, but phone not matching / missing
        val labelText = "คุณศรุตา วงศ์สวัสดิ์ บ้านเลขที่ 590 แม่ตาว"
        val matches = SearchMatcher.matchHouses(sampleHouses, labelText)

        assertFalse(matches.isEmpty())
        val top = matches.first()
        assertEquals("house-2", top.house.id)
        assertTrue(top.level == "high" || top.level == "exact")
        assertTrue(top.reasons.contains("บ้านเลขที่ตรง"))
    }

    @Test
    fun testConflictDetectionOnMismatchedPhone() {
        // Same house number 1442/3 but completely different phone on label
        val labelText = "1442/3 ม.2 แม่ตาว โทร 0998887777"
        val matches = SearchMatcher.matchHouses(sampleHouses, labelText)

        assertFalse(matches.isEmpty())
        val top = matches.first()
        assertEquals("house-1", top.house.id)
        assertTrue(top.conflicts.contains("เบอร์บนฉลากไม่ตรงกับเบอร์ในคลัง"))
    }

    @Test
    fun testSearchFilteringByHouseNumber() {
        val score = SearchMatcher.matchHouseWithSearch(sampleHouses[0], "1442")
        assertTrue(score > 1_000_000)

        val exactScore = SearchMatcher.matchHouseWithSearch(sampleHouses[0], "1442/3")
        assertTrue(exactScore >= 1_500_000)
    }

    @Test
    fun testExtractDistrict() {
        val district = SearchMatcher.extractDistrict(sampleHouses[0].address)
        assertNotNull(district)
        assertTrue(district!!.contains("แม่ตาว") || district.contains("แม่สอด"))
    }
}
