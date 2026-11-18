package com.wanderwildwood.itodenwa.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionsTest {
    @Test fun theNewestAnswerReplacesAnOlderOne() {
        Decisions.put("+1 555 010 0143", Decision.Silenced)
        Decisions.put("+1 555 010 0143", Decision.Rings(true))
        assertEquals(Decision.Rings(true), Decisions.take("+1 555 010 0143"))
        assertNull(Decisions.take("+1 555 010 0143"))
    }

    @Test fun hiddenNumbersShareOneKey() {
        Decisions.put(null, Decision.Silenced)
        Decisions.put(null, Decision.Rings(null))
        assertEquals(Decision.Rings(null), Decisions.take(null))
    }

    @Test fun takenOnce() {
        Decisions.put("+1 555 010 0177", Decision.Silenced)
        assertEquals(Decision.Silenced, Decisions.take("+15550100177"))
        assertNull(Decisions.take("+15550100177"))
    }
}
