package com.wanderwildwood.itodenwa.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.wanderwildwood.itodenwa.MainActivity
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.data.Prefs
import com.wanderwildwood.itodenwa.data.Voicemail
import com.wanderwildwood.itodenwa.data.Lookup
import kotlin.concurrent.thread

/**
 * The missed-call notification, and the voicemail one. The phone hands both to the phone app
 * when it has a place to take them, as AOSP's dialer does with its MissedCallNotifier: who
 * called, or how many did, Call back and Message on it, and a press opens Recents — this app's,
 * named, so no "open with" question comes up beside the Kompakt's own Phone. Opening Recents
 * clears it. A count of none, which the phone sends once calls are seen, clears it too.
 *
 * Voicemail is the network's message-waiting signal, when the carrier sends one: a press calls
 * voicemail, and calling voicemail clears it.
 */
class MissedCalls : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
                val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
                if (count <= 0) {
                    cancel(context)
                    return
                }
                val pending = goAsync()
                thread(name = "missed-call") {
                    runCatching { showMissed(context, count, number) }
                    pending.finish()
                }
            }
            TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION -> {
                val count = intent.getIntExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, 0)
                Prefs(context).voicemailWaiting = Voicemail.waitingFromCount(count)
                if (count <= 0) cancelVoicemail(context) else showVoicemail(context, count)
            }
        }
    }

    companion object {
        private const val MISSED_ID = 43
        private const val VOICEMAIL_ID = 44
        private const val MISSED = "calls_missed"
        private const val VOICEMAIL = "voicemail"

        /** Recents, in this app. */
        fun recentsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setType("vnd.android.cursor.dir/calls")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun channel(context: Context, id: String, name: Int) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(true)
                },
            )
        }

        fun showMissed(context: Context, count: Int, number: String?) {
            channel(context, MISSED, R.string.channel_missed)
            val known = number?.takeIf { it.isNotBlank() }
            val who = known?.let { Lookup.get(context).who(it) }
            val title = if (count > 1) {
                context.resources.getQuantityString(R.plurals.missed_calls, count, count)
            } else {
                context.getString(R.string.missed_call)
            }
            val text = when {
                count > 1 -> null
                who != null -> who.name
                known != null -> Lookup.display(context, known)
                else -> context.getString(R.string.call_hidden_number)
            }
            val builder = Notification.Builder(context, MISSED)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentIntent(recentsIntent(context))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setNumber(count)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
            text?.let { builder.setContentText(it) }
            if (count == 1 && known != null) {
                // Call back and Message, each a deliberate press in the shade; a press on the
                // notification itself only opens Recents.
                val callBack = PendingIntent.getBroadcast(
                    context,
                    11,
                    Intent(context, CallActions::class.java).setAction(CallActions.CALL_BACK).setData(Uri.fromParts("tel", known, null)),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val message = PendingIntent.getActivity(
                    context,
                    12,
                    Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", known, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(Notification.Action.Builder(null, context.getString(R.string.missed_call_back), callBack).build())
                builder.addAction(Notification.Action.Builder(null, context.getString(R.string.message), message).build())
            }
            context.getSystemService(NotificationManager::class.java).notify(MISSED_ID, builder.build())
        }

        fun cancel(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(MISSED_ID)
        }

        fun showVoicemail(context: Context, count: Int) {
            channel(context, VOICEMAIL, R.string.channel_voicemail)
            val call = PendingIntent.getBroadcast(
                context,
                13,
                Intent(context, CallActions::class.java).setAction(CallActions.CALL_VOICEMAIL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(context, VOICEMAIL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(context.getString(R.string.voicemail_new))
                .setContentText(context.resources.getQuantityString(R.plurals.voicemail_count, count, count))
                .setContentIntent(call)
                .setCategory(Notification.CATEGORY_STATUS)
                .setNumber(count)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(VOICEMAIL_ID, n)
        }

        fun cancelVoicemail(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(VOICEMAIL_ID)
        }
    }
}
