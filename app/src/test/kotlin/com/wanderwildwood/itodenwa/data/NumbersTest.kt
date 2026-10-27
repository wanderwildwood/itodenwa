package com.wanderwildwood.itodenwa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class NumbersTest {

    @Test
    fun pastedTextKeepsOnlyDialableCharacters() {
        assertEquals("+15555550143", Numbers.pasteable(" +1 (555) 555-0143\n"))
        assertEquals("*675555550143", Numbers.pasteable("*67 555.555.0143"))
        assertEquals("5555550143,1234#", Numbers.pasteable("555-555-0143,1234#"))
        assertEquals("5551234;5", Numbers.pasteable("555 1234;5"))
        // A plus only leads.
        assertEquals("15555550143", Numbers.pasteable("1+555-555-0143"))
    }

    @Test
    fun pastedTextThatIsNotANumberGivesNothing() {
        assertEquals("", Numbers.pasteable(null))
        assertEquals("", Numbers.pasteable("hello"))
        assertEquals("", Numbers.pasteable("12"))
        assertEquals("", Numbers.pasteable("1".repeat(65)))
    }

    @Test
    fun internationalAndNationalAreTheSameNumber() {
        assertTrue(Numbers.same("+1 555-555-0143", "5555550143"))
        assertTrue(Numbers.same("(555) 555-0143", "+15555550143"))
        assertTrue(Numbers.same("1-555-555-0143", "555.555.0143"))
        // A trunk zero on one side only.
        assertTrue(Numbers.same("+44 7700 900123", "07700 900123"))
        assertTrue(Numbers.same("+33 6 12 34 56 78", "06 12 34 56 78"))
        // Nine significant digits, no trunk prefix.
        assertTrue(Numbers.same("+48 512 345 678", "512 345 678"))
        // "00" written for "+".
        assertTrue(Numbers.same("0044 7700 900123", "+447700900123"))
        // Italy keeps its zero after the country code.
        assertTrue(Numbers.same("+39 06 1234 5678", "06 1234 5678"))
    }

    @Test
    fun differentNumbersAreNot() {
        assertFalse(Numbers.same("+1 555-555-0143", "+1 555-555-0144"))
        assertFalse(Numbers.same("+1 555-555-0143", "+1 555-556-0143"))
        assertFalse(Numbers.same("+33 6 12 34 56 78", "+44 6 12 34 56 78"))
        assertFalse(Numbers.same("", "5555550143"))
    }

    @Test
    fun shortCodesMatchOnlyThemselves() {
        assertTrue(Numbers.same("911", "911"))
        assertFalse(Numbers.same("911", "+1 555 555 0911"))
        assertFalse(Numbers.same("112", "0112"))
    }

    @Test
    fun aLongerAgreementIsTheSurerMatch() {
        val ten = Numbers.match("+1 555 555 0143", "5555550143")
        val seven = Numbers.match("555-0143", "5555550143")
        assertEquals(10, ten)
        assertEquals(7, seven)
    }

    @Test
    fun keysFileTheSameNumberTogether() {
        assertEquals(Numbers.key("+1 555-555-0143"), Numbers.key("(555) 555-0143"))
        assertEquals(Numbers.key("+44 7700 900123"), Numbers.key("07700 900123"))
    }

    @Test
    fun dialableKeepsOnlyWhatCanBeDialled() {
        assertEquals("+15555550143", Numbers.dialable("+1 (555) 555-0143"))
        assertEquals("*21#", Numbers.dialable("*21#"))
        assertEquals("5555550143", Numbers.dialable("555+555+0143"))
    }

    @Test
    fun theKeysSpellNames() {
        assertEquals("232", Numbers.onKeys("Ada"))
        assertEquals("532", Numbers.onKeys("Léa"))
        assertTrue(Numbers.typedFinds("94", "Ada Whitlock", emptyList()))
        assertTrue(Numbers.typedFinds("232", "Ada Whitlock", emptyList()))
        assertFalse(Numbers.typedFinds("233", "Ada Whitlock", emptyList()))
        assertTrue(Numbers.typedFinds("5550", "Ada Whitlock", listOf("+1 555 555 0143")))
    }

    @Test
    fun callsInARowWithOneNumberMakeOneRow() {
        val day = 1_700_000_000_000L
        fun c(id: Long, n: String?, at: Long, kind: LoggedCall.Kind = LoggedCall.Kind.IN) = LoggedCall(id, n, kind, at, 0)
        val rows = Recents.group(
            listOf(
                c(1, "+15555550143", day),
                c(2, "5555550143", day - 60_000),
                c(3, null, day - 120_000),
                c(4, null, day - 180_000),
                c(5, "5555550143", day - 240_000, LoggedCall.Kind.MISSED),
                c(6, "5555550143", day - 3 * 86_400_000L),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(2, 1, 1, 1, 1), rows.map { it.count })
    }
}
