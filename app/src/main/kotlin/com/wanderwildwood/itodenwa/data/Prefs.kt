package com.wanderwildwood.itodenwa.data

import android.content.Context
import android.net.Uri

/**
 * What is set in Settings. Everything starts off: no rule turns a call away, nothing rings
 * through Do Not Disturb, and every call rings with the phone's own ringtone, until it is
 * turned on here.
 */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var rules: RuleSet
        get() = RuleSet(
            unknown = action(p.getString(UNKNOWN, null)),
            hidden = action(p.getString(HIDDEN, null)),
            alwaysFavourites = p.getBoolean(ALWAYS_FAVOURITES, false),
            alwaysCalled = p.getBoolean(ALWAYS_CALLED, false),
            alwaysRepeat = p.getBoolean(ALWAYS_REPEAT, false),
        )
        set(v) {
            p.edit()
                .putString(UNKNOWN, v.unknown.name)
                .putString(HIDDEN, v.hidden.name)
                .putBoolean(ALWAYS_FAVOURITES, v.alwaysFavourites)
                .putBoolean(ALWAYS_CALLED, v.alwaysCalled)
                .putBoolean(ALWAYS_REPEAT, v.alwaysRepeat)
                .apply()
        }

    private fun action(name: String?): Action = Action.entries.firstOrNull { it.name == name } ?: Action.RING

    /** Favourites ring while Do Not Disturb keeps other calls quiet. */
    var favouritesThroughQuiet: Boolean
        get() = p.getBoolean(THROUGH_FAVOURITES, false)
        set(v) = p.edit().putBoolean(THROUGH_FAVOURITES, v).apply()

    /** And so does someone calling a second time within three minutes. */
    var repeatThroughQuiet: Boolean
        get() = p.getBoolean(THROUGH_REPEAT, false)
        set(v) = p.edit().putBoolean(THROUGH_REPEAT, v).apply()

    /** The ringtone for numbers not in the contacts; null rings them like everyone else. */
    var unknownRingtone: Uri?
        get() = p.getString(UNKNOWN_TONE, null)?.let(Uri::parse)
        set(v) = p.edit().putString(UNKNOWN_TONE, v?.toString()).apply()

    /**
     * Whether each key press on the keypad gives a short tick. Off to begin with; the tick is
     * the phone's own keyboard tap, so its touch feedback setting still has the last word.
     */
    var vibrate: Boolean
        get() = p.getBoolean(VIBRATE, Buzz.DEFAULT)
        set(v) = p.edit().putBoolean(VIBRATE, v).apply()

    /** The number on each of the keys 2 to 9, held down on the keypad to call it. */
    fun speedDial(key: Int): String? = p.getString("$SPEED$key", null)

    fun setSpeedDial(key: Int, number: String?) {
        p.edit().apply { if (number == null) remove("$SPEED$key") else putString("$SPEED$key", number) }.apply()
    }

    /**
     * Whether the DuraSpeed question, put on becoming the phone app, has been answered — Not now
     * or Open DuraSpeed. Marked on the answer, not when it is shown, so a question that never
     * reached the screen is put again on the next launch.
     */
    var duraSpeedAnswered: Boolean
        get() = p.getBoolean(DURASPEED_ANSWERED, false)
        set(v) = p.edit().putBoolean(DURASPEED_ANSWERED, v).apply()

    /**
     * Whether the phone asked this app about the last call that came in: true, false, or null
     * before any has. A false means another app is the caller ID and spam app, and the rules
     * here were passed over.
     */
    var lastCallScreened: Boolean?
        get() = if (p.contains(LAST_SCREENED)) p.getBoolean(LAST_SCREENED, true) else null
        set(v) = p.edit().apply { if (v == null) remove(LAST_SCREENED) else putBoolean(LAST_SCREENED, v) }.apply()

    /**
     * Whether the carrier last said a voicemail is waiting. Kept so the keypad can show it from
     * the moment it opens; the carrier's next signal, or a call to voicemail, changes it.
     */
    var voicemailWaiting: Boolean
        get() = p.getBoolean(VOICEMAIL_WAITING, false)
        set(v) = p.edit().putBoolean(VOICEMAIL_WAITING, v).apply()

    companion object {
        private const val VOICEMAIL_WAITING = "voicemail_waiting"
        private const val LAST_SCREENED = "last_call_screened"
        private const val UNKNOWN = "rule_unknown"
        private const val HIDDEN = "rule_hidden"
        private const val ALWAYS_FAVOURITES = "always_favourites"
        private const val ALWAYS_CALLED = "always_called"
        private const val ALWAYS_REPEAT = "always_repeat"
        private const val THROUGH_FAVOURITES = "quiet_favourites"
        private const val THROUGH_REPEAT = "quiet_repeat"
        private const val UNKNOWN_TONE = "ringtone_unknown"
        private const val VIBRATE = "vibrate"
        private const val SPEED = "speed_"
        private const val DURASPEED_ANSWERED = "duraspeed_answered"

        val SPEED_KEYS = 2..9
    }
}
