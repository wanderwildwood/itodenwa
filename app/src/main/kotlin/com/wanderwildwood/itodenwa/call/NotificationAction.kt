package com.wanderwildwood.itodenwa.call

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.CallLogs
import com.wanderwildwood.itodenwa.data.Voicemail
import kotlin.concurrent.thread

/**
 * Call back and Message on a missed call, and a press on the voicemail notification. They go
 * through this screenless activity rather than a broadcast: with the phone locked,
 * Android asks for the unlock before an activity from a notification starts, and nothing is
 * dialed from the lock screen. Not exported, and shown for no longer than it takes to start
 * the call.
 */
class NotificationAction : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) when (intent.action) {
            CALL_BACK -> intent.data?.schemeSpecificPart?.let { number ->
                seen()
                // An emergency number is never dialled for anyone: it goes on the keypad to be pressed.
                if (Actions.isEmergency(this, number)) Actions.start(this, Actions.keypad(this, number))
                else Actions.call(this, number)
            }
            MESSAGE -> intent.data?.schemeSpecificPart?.let { number ->
                seen()
                Actions.start(this, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            CALL_VOICEMAIL -> if (!Voicemail.call(this)) Actions.start(this, Voicemail.settings())
        }
        finish()
    }

    /**
     * The missed call answered from its notification has been seen: the log and Telecom stop
     * counting it, so the next missed call is "Missed call" with its own buttons again, and the
     * call does not come back after a restart. Off the main thread; it outlives this screen.
     */
    private fun seen() {
        val app = applicationContext
        MissedCalls.cancel(app)
        thread(name = "missed-seen") { runCatching { CallLogs.markMissedSeen(app) } }
    }

    companion object {
        const val CALL_BACK = "com.wanderwildwood.itodenwa.CALL_BACK"
        const val CALL_VOICEMAIL = "com.wanderwildwood.itodenwa.CALL_VOICEMAIL"
        const val MESSAGE = "com.wanderwildwood.itodenwa.MESSAGE"
    }
}
