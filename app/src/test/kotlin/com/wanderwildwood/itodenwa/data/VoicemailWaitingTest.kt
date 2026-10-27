package com.wanderwildwood.itodenwa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicemailWaitingTest {
    @Test fun noMessagesIsNotWaiting() = assertFalse(Voicemail.waitingFromCount(0))

    @Test fun someMessagesIsWaiting() {
        assertTrue(Voicemail.waitingFromCount(1))
        assertTrue(Voicemail.waitingFromCount(7))
    }

    @Test fun anUnknownCountIsStillWaiting() = assertTrue(Voicemail.waitingFromCount(-1))
}
