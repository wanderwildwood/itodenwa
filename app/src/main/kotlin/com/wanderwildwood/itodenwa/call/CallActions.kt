package com.wanderwildwood.itodenwa.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The buttons on the app's notifications that need no screen: Decline and Hang up on a call,
 * and a missed one swiped away. Answer goes to the call screen instead, and Call back and the
 * voicemail press go through [NotificationAction], so a locked phone asks for the unlock first.
 * From Fossify Phone's CallActionReceiver. Not exported.
 */
class CallActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ANSWER -> CallManager.answer()
            DECLINE, HANG_UP -> CallManager.hangUp()
            DECLINE_WAITING -> CallManager.declineWaiting()
            // The missed-call notification swiped away: the calls have been seen, as AOSP's dialer
            // takes it, so the log stops counting them and every copy of the notice goes.
            MISSED_DISMISSED -> {
                val pending = goAsync()
                kotlin.concurrent.thread(name = "missed-seen") {
                    runCatching { com.wanderwildwood.itodenwa.data.CallLogs.markMissedSeen(context) }
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ANSWER = "com.wanderwildwood.itodenwa.ANSWER"
        const val DECLINE = "com.wanderwildwood.itodenwa.DECLINE"
        const val HANG_UP = "com.wanderwildwood.itodenwa.HANG_UP"

        /** Answer and Decline on a silenced call waiting behind the call in progress. */
        const val ANSWER_WAITING = "com.wanderwildwood.itodenwa.ANSWER_WAITING"
        const val DECLINE_WAITING = "com.wanderwildwood.itodenwa.DECLINE_WAITING"
        const val MISSED_DISMISSED = "com.wanderwildwood.itodenwa.MISSED_DISMISSED"
    }
}
