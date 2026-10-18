package com.wanderwildwood.itodenwa.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.Voicemail

/**
 * The buttons on the app's notifications that need no screen: Decline and Hang up on a call,
 * Call back on a missed one, and a press on the voicemail one. Answer goes to the call screen
 * instead. From Fossify Phone's CallActionReceiver. Not exported: nothing outside the app can
 * ask it to place a call.
 */
class CallActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ANSWER -> CallManager.answer()
            DECLINE, HANG_UP -> CallManager.hangUp()
            CALL_BACK -> {
                val number = intent.data?.schemeSpecificPart ?: return
                MissedCalls.cancel(context)
                // An emergency number is never dialled for anyone: it goes on the keypad to be pressed.
                if (Actions.isEmergency(context, number)) Actions.start(context, Actions.keypad(context, number))
                else Actions.call(context, number)
            }
            CALL_VOICEMAIL -> if (!Voicemail.call(context)) Actions.start(context, Voicemail.settings())
        }
    }

    companion object {
        const val ANSWER = "com.wanderwildwood.itodenwa.ANSWER"
        const val DECLINE = "com.wanderwildwood.itodenwa.DECLINE"
        const val HANG_UP = "com.wanderwildwood.itodenwa.HANG_UP"
        const val CALL_BACK = "com.wanderwildwood.itodenwa.CALL_BACK"
        const val CALL_VOICEMAIL = "com.wanderwildwood.itodenwa.CALL_VOICEMAIL"
    }
}
