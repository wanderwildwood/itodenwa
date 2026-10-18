package com.wanderwildwood.itodenwa.data

import android.app.NotificationManager.Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val unknownDecline = RuleSet(unknown = Action.DECLINE)

    @Test
    fun everythingRingsByDefault() {
        val set = RuleSet()
        assertEquals(Action.RING, Rules.decide(set, Caller("5550101", inContacts = false)).action)
        assertEquals(Action.RING, Rules.decide(set, Caller(null, inContacts = null)).action)
        assertFalse(Rules.anyOn(set, emptyList()))
    }

    @Test
    fun onlyADefiniteNotInContactsCounts() {
        assertEquals(Action.DECLINE, Rules.decide(unknownDecline, Caller("5550101", inContacts = false)).action)
        assertEquals(Rule.NOT_IN_CONTACTS, Rules.decide(unknownDecline, Caller("5550101", inContacts = false)).rule)
        // The lookup failed: never turned away.
        assertEquals(Action.RING, Rules.decide(unknownDecline, Caller("5550101", inContacts = null)).action)
        assertEquals(Action.RING, Rules.decide(unknownDecline, Caller("5550101", inContacts = true)).action)
    }

    @Test
    fun hiddenNumbersHaveTheirOwnRule() {
        val set = RuleSet(unknown = Action.DECLINE, hidden = Action.SILENCE)
        val v = Rules.decide(set, Caller(null, inContacts = null))
        assertEquals(Action.SILENCE, v.action)
        assertEquals(Rule.HIDDEN, v.rule)
        assertEquals(Action.RING, Rules.decide(RuleSet(unknown = Action.DECLINE), Caller(null, inContacts = null)).action)
    }

    @Test
    fun alwaysRingOverridesTheRules() {
        val set = unknownDecline.copy(alwaysCalled = true, alwaysRepeat = true, alwaysFavourites = true)
        assertEquals(Action.RING, Rules.decide(set, Caller("5550101", inContacts = false, calledBefore = true)).action)
        assertEquals(Action.RING, Rules.decide(set, Caller("5550101", inContacts = false, repeat = true)).action)
        // Switched off, they do nothing.
        assertEquals(Action.DECLINE, Rules.decide(unknownDecline, Caller("5550101", inContacts = false, calledBefore = true, repeat = true)).action)
    }

    @Test
    fun aPrefixDeclinesButNotAFavouriteAndNotTwiceInARow() {
        val set = RuleSet(alwaysFavourites = true, alwaysRepeat = true)
        val v = Rules.decide(set, Caller("5550101", inContacts = false, prefix = "555*"))
        assertEquals(Action.DECLINE, v.action)
        assertEquals(Rule.BLOCK_LIST, v.rule)
        assertEquals("555*", v.pattern)
        assertEquals(Action.RING, Rules.decide(set, Caller("5550101", inContacts = true, starred = true, prefix = "555*")).action)
        assertEquals(Action.DECLINE, Rules.decide(set, Caller("5550101", inContacts = false, repeat = true, prefix = "555*")).action)
    }

    @Test
    fun afterAnEmergencyCallEverythingRings() {
        val set = RuleSet(unknown = Action.DECLINE, hidden = Action.DECLINE)
        assertEquals(Action.RING, Rules.decide(set, Caller(null, inContacts = null, afterEmergency = true)).action)
        assertEquals(Action.RING, Rules.decide(set, Caller("5550101", inContacts = false, prefix = "555*", afterEmergency = true)).action)
    }
}

class PatternsTest {
    @Test
    fun tidy() {
        assertEquals("765*", Patterns.tidy(" 765 * "))
        assertEquals("+322888*", Patterns.tidy("+32 2888*"))
        assertEquals("5550101", Patterns.tidy("(555) 0101"))
        assertNull(Patterns.tidy("*"))
        assertNull(Patterns.tidy("  "))
    }

    @Test
    fun aHomePrefixFindsBothForms() {
        assertTrue(Patterns.matches("765*", "7655550100", "+17655550100", "(765) 555-0100"))
        assertTrue(Patterns.matches("765*", "+17655550100", "+17655550100", "(765) 555-0100"))
        assertFalse(Patterns.matches("765*", "+17665550100", "+17665550100", "(766) 555-0100"))
        // A trunk zero is set aside on both sides.
        assertTrue(Patterns.matches("07700*", "+447700900123", "+447700900123", "07700 900123"))
    }

    @Test
    fun anInternationalPrefixNeedsTheInternationalForm() {
        assertTrue(Patterns.matches("+322888*", "022888123", "+322888123", "02 2888 123"))
        assertFalse(Patterns.matches("+322888*", "5550101", "+15550101", "555-0101"))
    }

    @Test
    fun fossifyFormatRoundTrips() {
        val lines = listOf("5550101", "765*", "+322888*")
        assertEquals("5550101,765*,+322888*", Patterns.write(lines))
        assertEquals(lines, Patterns.read(Patterns.write(lines)))
        assertEquals(listOf("5550101", "5550102", "765*"), Patterns.read("5550101\n5550102,\n765*\n5550101"))
    }
}

class ScreenedTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun keepsThirtyDaysAndTwoHundred() {
        val now = 100 * day
        val calls = (0 until 300).map { ScreenedCall(now - it * 60_000L, "555010$it", Action.DECLINE, Rule.BLOCK_LIST) } +
            ScreenedCall(now - 31 * day, "5550199", Action.DECLINE, Rule.HIDDEN)
        val kept = Screened.prune(calls, now)
        assertEquals(200, kept.size)
        assertEquals(now, kept.first().at)
        assertTrue(kept.none { it.number == "5550199" })
    }
}

class QuietTest {
    private val calls = Policy.PRIORITY_CATEGORY_CALLS
    private val repeat = Policy.PRIORITY_CATEGORY_REPEAT_CALLERS

    @Test
    fun priorityOnlyLetsTheRightCallersThrough() {
        assertTrue(Quiet.allows(calls, Policy.PRIORITY_SENDERS_STARRED, inContacts = true, starred = true, repeat = false))
        assertFalse(Quiet.allows(calls, Policy.PRIORITY_SENDERS_STARRED, inContacts = true, starred = false, repeat = false))
        assertTrue(Quiet.allows(calls, Policy.PRIORITY_SENDERS_CONTACTS, inContacts = true, starred = false, repeat = false))
        assertFalse(Quiet.allows(0, Policy.PRIORITY_SENDERS_ANY, inContacts = true, starred = true, repeat = false))
        assertTrue(Quiet.allows(repeat, Policy.PRIORITY_SENDERS_STARRED, inContacts = false, starred = false, repeat = true))
    }
}
