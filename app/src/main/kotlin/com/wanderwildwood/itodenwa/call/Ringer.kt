package com.wanderwildwood.itodenwa.call

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.provider.Settings

/**
 * The app's own ringer, for the two calls the phone's ringer is not used for: a caller not in
 * Contacts, when another ringtone is chosen for them in Settings, and a favourite or a second
 * call ringing through Do Not Disturb. Every other call is rung by the phone itself — which
 * already plays a contact's own ringtone, chosen on their card.
 *
 * It keeps the phone's ringer mode: silent stays silent, vibrate only vibrates — except the
 * silence "alarms only" sets by itself, which is what a call ringing through is meant to pass. Through Do Not Disturb it plays as an alarm does, since Do Not Disturb mutes the ringtone's own channel for
 * every app but the system's.
 */
object Ringer {
    private var playing: Ringtone? = null
    private var vibrating: Vibrator? = null

    @Synchronized
    fun start(context: Context, tone: Uri?, throughQuiet: Boolean) {
        stop()
        val audio = context.getSystemService(AudioManager::class.java)
        var mode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        // "Alarms only" and "total silence" set the ringer to silent themselves; through Do Not
        // Disturb that silence is the very thing being rung through, not the person's own.
        val zen = context.getSystemService(android.app.NotificationManager::class.java)?.currentInterruptionFilter
        if (throughQuiet && mode == AudioManager.RINGER_MODE_SILENT &&
            (zen == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS || zen == android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
        ) {
            mode = AudioManager.RINGER_MODE_NORMAL
        }
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        val vibrateToo = mode == AudioManager.RINGER_MODE_VIBRATE ||
            runCatching { Settings.System.getInt(context.contentResolver, "vibrate_when_ringing", 0) == 1 }.getOrDefault(false)
        if (vibrateToo) vibrate(context)
        if (mode != AudioManager.RINGER_MODE_NORMAL) return

        val uri = tone ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_RINGTONE_URI
        val ringtone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()
            ?: runCatching { RingtoneManager.getRingtone(context, Settings.System.DEFAULT_RINGTONE_URI) }.getOrNull()
            ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(if (throughQuiet) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.isLooping = true
        if (throughQuiet && audio != null) {
            // As loud as the ring volume says, not as loud as the alarm volume happens to be.
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
            ringtone.volume = (audio.getStreamVolume(AudioManager.STREAM_RING).coerceAtLeast(1).toFloat() / max).coerceIn(0.1f, 1f)
        }
        runCatching { ringtone.play() }
        playing = ringtone
    }

    private fun vibrate(context: Context) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 1),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
        )
        vibrating = v
    }

    @Synchronized
    fun stop() {
        runCatching { playing?.stop() }
        playing = null
        runCatching { vibrating?.cancel() }
        vibrating = null
    }

    val isRinging: Boolean get() = playing != null || vibrating != null

    /** The ringtone chosen on a contact's card, or null for the phone's own. */
    fun contactTone(context: Context, contactId: Long): Uri? = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE),
            null,
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.let(Uri::parse) else null }
    }.getOrNull()
}
