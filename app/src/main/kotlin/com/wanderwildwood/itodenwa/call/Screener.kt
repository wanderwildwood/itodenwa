package com.wanderwildwood.itodenwa.call

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import com.wanderwildwood.itodenwa.data.Action
import com.wanderwildwood.itodenwa.data.BlockList
import com.wanderwildwood.itodenwa.data.CallLogs
import com.wanderwildwood.itodenwa.data.Caller
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Prefs
import com.wanderwildwood.itodenwa.data.Quiet
import com.wanderwildwood.itodenwa.data.Rules
import com.wanderwildwood.itodenwa.data.Screened
import com.wanderwildwood.itodenwa.data.ScreenedCall
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Asked by the phone about every call coming in, before it rings, while this is the phone app.
 * From Fossify Phone's SimpleCallScreeningService, which the fork had dropped, grown into the
 * rules in Settings: a prefix on the block list, numbers not in Contacts, hidden numbers, and
 * the callers that always ring. Whole numbers on the block list never get here: the phone turns
 * those away itself.
 *
 * It needs no role of its own: Android 12 asks the phone app's screener whenever no other app
 * has been made the caller ID and spam app. Everything is read on the phone, nothing leaves it.
 *
 * The phone waits five seconds for an answer and then lets the call ring. The answer here is
 * given well inside that, and a lookup that fails or runs long rings the call: only a definite
 * "not in Contacts" counts as one.
 */
class Screener : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        lastAsked = System.currentTimeMillis()
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val answered = AtomicBoolean(false)
        val number = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        // Every answer leaves its decision, the plain "ring" too, so it replaces any older one
        // for the same number (or for "hidden") that a call which never came in left behind.
        val rings: List<() -> Unit> = listOf({ Decisions.put(number, Decision.Rings(null)) })
        // What a decision leaves behind (the call service's note, the blocked-calls list) is kept
        // only if that decision is the answer given: one too late for the deadline leaves nothing,
        // so a call the phone rang is not then treated as silenced.
        fun answer(response: CallResponse, effects: List<() -> Unit> = emptyList()) {
            if (answered.compareAndSet(false, true)) {
                effects.forEach { runCatching { it() } }
                runCatching { respondToCall(details, response) }
            }
        }
        // Whatever happens below, the call rings by the deadline.
        Handler(Looper.getMainLooper()).postDelayed({ answer(CallResponse.Builder().build(), rings) }, DEADLINE_MS)
        thread(name = "screen-call") {
            val effects = mutableListOf<() -> Unit>()
            val response = runCatching { decide(details, effects) }.getOrNull()
            if (response == null) answer(CallResponse.Builder().build(), rings) else answer(response, effects)
        }
    }

    private fun decide(details: Call.Details, effects: MutableList<() -> Unit>): CallResponse {
        val context = applicationContext
        val prefs = Prefs(context)
        val set = prefs.rules
        val blocks = BlockList(context)
        val prefixes = blocks.allPrefixes()
        val number = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }

        val needsCaller = Rules.anyOn(set, prefixes) || prefs.unknownRingtone != null ||
            prefs.favouritesThroughQuiet || prefs.repeatThroughQuiet
        if (!needsCaller) {
            effects += { Decisions.put(number, Decision.Rings(null)) }
            return CallResponse.Builder().build()
        }

        val check = number?.let { Lookup.get(context).check(it) }
        val who = (check as? Lookup.Check.Found)?.who
        val inContacts = when (check) {
            is Lookup.Check.Found -> true
            Lookup.Check.NotFound -> false
            else -> null
        }
        val repeat = number != null && CallLogs.calledRecently(context, number, REPEAT_MS)
        val caller = Caller(
            number = number,
            inContacts = inContacts,
            starred = who?.starred == true,
            calledBefore = number != null && set.alwaysCalled && CallLogs.calledBefore(context, number),
            repeat = repeat,
            prefix = number?.let { blocks.prefixFor(it, prefixes) },
            afterEmergency = System.currentTimeMillis() - Prefs(context).lastEmergencyCall in 0 until EMERGENCY_MS ||
                CallLogs.emergencyRecently(context, EMERGENCY_MS),
        )
        val verdict = Rules.decide(set, caller)

        if (verdict.action != Action.RING && verdict.rule != null) {
            val screened = ScreenedCall(System.currentTimeMillis(), number, verdict.action, verdict.rule, verdict.pattern)
            effects += { Screened(context).add(screened) }
        }
        return when (verdict.action) {
            Action.DECLINE -> CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                // The phone app's screener always has the call logged; the notification is not wanted.
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
            Action.SILENCE -> {
                effects += { Decisions.put(number, Decision.Silenced) }
                CallResponse.Builder().setSilenceCall(true).build()
            }
            Action.RING -> ringing(context, prefs, number, who, inContacts, repeat, effects)
        }
    }

    /**
     * A call that rings. The phone rings it, unless the app rings it instead: for a caller not in
     * Contacts when another ringtone is chosen for them, and for a favourite or a second call
     * while Do Not Disturb would keep it quiet, where Settings says they ring through.
     */
    private fun ringing(
        context: android.content.Context,
        prefs: Prefs,
        number: String?,
        who: com.wanderwildwood.itodenwa.data.Who?,
        inContacts: Boolean?,
        repeat: Boolean,
        effects: MutableList<() -> Unit>,
    ): CallResponse {
        val ring = CallResponse.Builder().build()
        val starred = who?.starred == true
        val quiet = Quiet.isOn(context)
        if (quiet) {
            // Never under Total silence: it mutes the very sound and vibration the app rings with.
            val mayThrough = Quiet.mayRingThrough(Quiet.filter(context))
            val favourite = prefs.favouritesThroughQuiet && starred
            val through = mayThrough && (favourite || (prefs.repeatThroughQuiet && repeat))
            val heard = Quiet.phoneWouldRing(context, inContacts == true, starred, repeat)
            if (through && !heard) {
                val tone = who?.let { Ringer.contactTone(context, it.contactId) }
                effects += { Decisions.put(number, Decision.RingThroughQuiet(tone)) }
            } else {
                effects += { Decisions.put(number, Decision.Rings(heard)) }
            }
            return ring
        }
        val tone: Uri? = prefs.unknownRingtone
        if (tone != null && number != null && inContacts == false) {
            // The phone's ringer is told to stay quiet, and the app rings the other tone.
            effects += { Decisions.put(number, Decision.RingWith(tone)) }
            return CallResponse.Builder().setSilenceCall(true).build()
        }
        effects += { Decisions.put(number, Decision.Rings(true)) }
        return ring
    }

    companion object {
        private const val DEADLINE_MS = 4000L
        private const val REPEAT_MS = 3 * 60 * 1000L
        private const val EMERGENCY_MS = 15 * 60 * 1000L

        /** When the phone last asked, so Settings can tell whether another app is screening instead. */
        @Volatile
        var lastAsked = 0L
    }
}

/** What the screener decided for a call that still comes in, for the call service to act on. */
sealed class Decision {
    /** Silenced by a rule: no sound and no call screen, only the notification. */
    object Silenced : Decision()

    /** The app rings it, with this ringtone, because the phone's ringer was told to stay quiet. */
    data class RingWith(val tone: Uri) : Decision()

    /** The app rings it through Do Not Disturb, with the contact's own ringtone if they have one. */
    data class RingThroughQuiet(val tone: Uri?) : Decision()

    /**
     * The phone's own ringer rings it. [heard]: whether Do Not Disturb lets that ring be heard,
     * or null when the screener did not look.
     */
    data class Rings(val heard: Boolean?) : Decision()
}

/**
 * The screener's answers, kept a short while for the call they were about: the screener and the
 * call service run in the same process, a moment apart.
 */
object Decisions {
    private const val KEEP_MS = 30_000L
    private val map = HashMap<String, Pair<Long, Decision>>()

    private fun key(number: String?) = number?.let { com.wanderwildwood.itodenwa.data.Numbers.digits(it) } ?: "hidden"

    @Synchronized
    fun put(number: String?, decision: Decision) {
        map[key(number)] = System.currentTimeMillis() to decision
    }

    /** The decision for this number, once: taking it removes it. */
    @Synchronized
    fun take(number: String?): Decision? {
        val now = System.currentTimeMillis()
        map.entries.removeAll { now - it.value.first > KEEP_MS }
        return map.remove(key(number))?.second
    }
}
