package com.wanderwildwood.itodenwa.data

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietThroughTest {
    @Test fun favoritesRingThroughPriorityAndAlarmsOnly() {
        assertTrue(Quiet.favouriteMayRingThrough(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(Quiet.favouriteMayRingThrough(NotificationManager.INTERRUPTION_FILTER_ALARMS))
    }

    @Test fun neverThroughTotalSilence() {
        assertFalse(Quiet.favouriteMayRingThrough(NotificationManager.INTERRUPTION_FILTER_NONE))
    }

    @Test fun repeatCallersFollowTheSameRule() {
        assertTrue(Quiet.mayRingThrough(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(Quiet.mayRingThrough(NotificationManager.INTERRUPTION_FILTER_ALARMS))
        assertFalse(Quiet.mayRingThrough(NotificationManager.INTERRUPTION_FILTER_NONE))
    }
}
