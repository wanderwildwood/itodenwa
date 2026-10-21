package com.wanderwildwood.itodenwa.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.util.Log
import com.wanderwildwood.itodenwa.InCallActivity
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Voicemail
import kotlin.concurrent.thread

/**
 * The notification a call keeps while it lasts. From Fossify Phone's CallNotificationManager,
 * drawn with the system's own notification rather than a layout of its own: who it is, what the
 * call is doing, and Answer and Decline while it rings, Hang up once it is going.
 *
 * A ringing call's notification carries the full-screen intent, which is how the call screen
 * comes up over the lock screen and wakes the panel.
 */
class CallNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(fullScreen: Boolean = false) {
        val call = CallManager.primary() ?: return
        val state = call.stateCompat()
        val number = call.number()
        thread(name = "call-notification") {
            val who = number?.let { Lookup.get(context).who(it) }
            val title = when {
                call.isConference() -> context.getString(R.string.call_conference)
                who != null -> who.name
                Voicemail.isCallTo(context, call) -> context.getString(R.string.recents_voicemail)
                number != null -> Lookup.display(context, number)
                else -> context.getString(R.string.call_hidden_number)
            }
            post(call, state, title, number, fullScreen && state == Call.STATE_RINGING && call !in CallManager.silenced)
        }
    }

    /**
     * Whether a ringing call's notification can put the call screen up: notifications on for the
     * app, and the incoming-call channel not switched off.
     */
    fun canAlert(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        channels()
        val channel = manager.getNotificationChannel(RINGING) ?: return false
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    private fun post(call: Call, state: Int, title: String, number: String?, fullScreen: Boolean) {
        val ringing = state == Call.STATE_RINGING
        // With the call screen already in front, a heads-up over it would only cover the name;
        // a call a rule silenced is not to put itself in front at all.
        val channel = if (ringing && !InCallActivity.shown && call !in CallManager.silenced) RINGING else ONGOING
        channels()

        val open = PendingIntent.getActivity(context, 0, InCallActivity.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = context.getString(
            when (state) {
                Call.STATE_RINGING -> R.string.call_incoming
                Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT -> R.string.call_calling
                Call.STATE_HOLDING -> R.string.call_on_hold
                Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> R.string.call_ended
                else -> R.string.call_ongoing
            },
        )

        // While the call is going, the notification is the system's call style: Android 12 draws
        // the call chip in the status bar for it, and pressing the chip opens the call screen.
        // MuditaOS leaves the chip out of its status bar and draws a call icon of its own that
        // opens nothing; there the Phone icon on the home screen goes back to the call
        // (MainActivity.backToCall). The call style is
        // only taken from a call app's own foreground service, so the call service holds the
        // notification while there is a call; should that be refused, the plain one is posted.
        val callStyle = !ringing && state != Call.STATE_DISCONNECTING && state != Call.STATE_DISCONNECTED
        fun build(styled: Boolean): Notification {
            val builder = Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(!ringing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
            // Who is calling, as Do Not Disturb reads it: a favourite it lets through is known by this.
            if (number != null) {
                @Suppress("DEPRECATION")
                builder.addPerson(android.net.Uri.fromParts("tel", number, null).toString())
            }

            if (state == Call.STATE_ACTIVE && call.details.connectTimeMillis > 0) {
                builder.setUsesChronometer(true).setWhen(call.details.connectTimeMillis).setShowWhen(true)
            } else {
                builder.setShowWhen(false)
            }

            if (ringing) {
                builder.addAction(action(CallActions.DECLINE, R.string.call_decline))
                // Answer opens the call screen, which answers: Android 12 does not let a broadcast
                // from a notification open an activity, and the screen is wanted at once.
                val answer = PendingIntent.getActivity(
                    context,
                    1,
                    InCallActivity.intent(context).setAction(CallActions.ANSWER),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(Notification.Action.Builder(null, context.getString(R.string.call_answer), answer).build())
            } else if (styled) {
                val person = Person.Builder().setName(title).build()
                builder.setStyle(Notification.CallStyle.forOngoingCall(person, pending(CallActions.HANG_UP)))
            } else {
                builder.addAction(action(CallActions.HANG_UP, R.string.call_hang_up))
            }
            if (fullScreen) builder.setFullScreenIntent(open, true)
            return builder.build()
        }

        // The call may have moved on while its name was being looked up.
        if (CallManager.primary() != call || CallManager.state() != state) return
        val service = CallManager.inCallService
        if (callStyle && service != null) {
            try {
                service.startForeground(ID, build(styled = true))
                return
            } catch (e: Exception) {
                Log.w(TAG, "call style refused: ${e.message}")
            }
        }
        manager.notify(ID, build(styled = false))
    }

    private fun action(what: String, label: Int): Notification.Action =
        Notification.Action.Builder(null, context.getString(label), pending(what)).build()

    private fun pending(what: String): PendingIntent {
        val intent = Intent(context, CallActions::class.java).setAction(what)
        return PendingIntent.getBroadcast(context, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun channels() {
        // No sound on either: the phone's own ringer rings for the call.
        manager.createNotificationChannel(
            NotificationChannel(RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** Off the shade, and the call service out of the foreground if it was holding it. */
    fun cancel() {
        (CallManager.inCallService ?: context as? Service)?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        manager.cancel(ID)
    }

    companion object {
        private const val TAG = "CallNotifier"
        private const val ID = 42
        private const val RINGING = "calls_ringing"
        private const val ONGOING = "calls_ongoing"
    }
}
