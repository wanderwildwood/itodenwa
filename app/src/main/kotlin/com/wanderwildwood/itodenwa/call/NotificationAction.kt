package com.wanderwildwood.itodenwa.call

import android.app.Activity
import android.os.Bundle
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.Voicemail

/**
 * Call back on a missed call, and a press on the voicemail notification. Both place a call, so
 * they go through this screenless activity rather than a broadcast: with the phone locked,
 * Android asks for the unlock before an activity from a notification starts, and nothing is
 * dialed from the lock screen. Not exported, and shown for no longer than it takes to start
 * the call.
 */
class NotificationAction : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) when (intent.action) {
            CALL_BACK -> intent.data?.schemeSpecificPart?.let { number ->
                MissedCalls.cancel(this)
                // An emergency number is never dialled for anyone: it goes on the keypad to be pressed.
                if (Actions.isEmergency(this, number)) Actions.start(this, Actions.keypad(this, number))
                else Actions.call(this, number)
            }
            CALL_VOICEMAIL -> if (!Voicemail.call(this)) Actions.start(this, Voicemail.settings())
        }
        finish()
    }

    companion object {
        const val CALL_BACK = "com.wanderwildwood.itodenwa.CALL_BACK"
        const val CALL_VOICEMAIL = "com.wanderwildwood.itodenwa.CALL_VOICEMAIL"
    }
}
