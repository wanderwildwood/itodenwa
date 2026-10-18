package com.wanderwildwood.itodenwa.call

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import androidx.compose.runtime.mutableIntStateOf
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Every call the phone has right now, and what can be done to them. From Fossify Phone's
 * CallManager (itself inspired by github.com/Chooloo/call_manage), with its listeners kept and
 * a counter added that the in-call screen reads, so any change to any call redraws it.
 *
 * Telecom hands calls to [CallService] on the main thread, and every change arrives there too.
 */
object CallManager {
    @SuppressLint("StaticFieldLeak")
    var inCallService: InCallService? = null
    private var call: Call? = null
    private val calls = mutableListOf<Call>()
    private val listeners = CopyOnWriteArraySet<Listener>()

    /** Calls a rule silenced: they come in without the call screen, as a notification only. */
    val silenced: MutableSet<Call> = java.util.Collections.newSetFromMap(java.util.WeakHashMap())

    /** The call the app's own ringer is ringing for, if any. */
    var ringingOurselves: Call? = null

    /** Bumped on every change to any call, its audio, or which call is in front. */
    val changes = mutableIntStateOf(0)

    interface Listener {
        fun onChanged()
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = update()
        override fun onDetailsChanged(call: Call, details: Call.Details) = update()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = update()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = update()
    }

    fun onCallAdded(call: Call) {
        this.call = call
        calls.add(call)
        call.registerCallback(callback)
        update()
    }

    fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        update()
    }

    fun onAudioStateChanged() = notifyChanged()

    /** No call, one call, or two — the one in front and the one waiting or held. */
    fun phoneState(): PhoneState = when (calls.size) {
        0 -> NoCall
        1 -> SingleCall(calls.first())
        2 -> {
            val active = calls.find { it.stateCompat() == Call.STATE_ACTIVE }
            val newCall = calls.find { it.stateCompat() in NEW_STATES }
            val onHold = calls.find { it.stateCompat() == Call.STATE_HOLDING }
            when {
                active != null && newCall != null -> TwoCalls(newCall, active)
                newCall != null && onHold != null -> TwoCalls(newCall, onHold)
                active != null && onHold != null -> TwoCalls(active, onHold)
                else -> TwoCalls(calls[0], calls[1])
            }
        }
        else -> {
            val conference = calls.find { it.isConference() }
            if (conference == null) {
                TwoCalls(calls.last(), calls.first())
            } else {
                val other = calls.filter { !it.isConference() }.subtract(conference.children.toSet()).firstOrNull()
                when {
                    other == null -> SingleCall(conference)
                    other.stateCompat() == Call.STATE_ACTIVE || other.stateCompat() in NEW_STATES -> TwoCalls(other, conference)
                    else -> TwoCalls(conference, other)
                }
            }
        }
    }

    private fun update() {
        val primary = when (val s = phoneState()) {
            is NoCall -> null
            is SingleCall -> s.call
            is TwoCalls -> s.active
        }
        call = primary
        // A call that has ended can linger in the list; it is not a call any more.
        calls.removeAll { it.stateCompat() == Call.STATE_DISCONNECTED && it != primary }
        notifyChanged()
    }

    private fun notifyChanged() {
        changes.intValue++
        listeners.forEach { it.onChanged() }
    }

    fun primary(): Call? = call

    fun state(): Int? = call?.stateCompat()

    fun hasCalls(): Boolean = calls.any { it.stateCompat() != Call.STATE_DISCONNECTED }

    fun answer() {
        Ringer.stop()
        call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Declines a ringing call, or ends one that is going. */
    fun hangUp() {
        Ringer.stop()
        val c = call ?: return
        when (c.stateCompat()) {
            Call.STATE_RINGING -> c.reject(false, null)
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> Unit
            else -> c.disconnect()
        }
    }

    fun canHold(): Boolean = call?.has(Call.Details.CAPABILITY_HOLD) == true

    fun isHeld(): Boolean = call?.stateCompat() == Call.STATE_HOLDING

    fun toggleHold() {
        val c = call ?: return
        if (c.stateCompat() == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    /** With two calls, brings the held one to the front. */
    fun swap() {
        if (calls.size > 1) calls.find { it.stateCompat() == Call.STATE_HOLDING }?.unhold()
    }

    fun canMerge(): Boolean {
        val c = call ?: return false
        return c.conferenceableCalls.isNotEmpty() || c.has(Call.Details.CAPABILITY_MERGE_CONFERENCE)
    }

    fun merge() {
        val c = call ?: return
        val others = c.conferenceableCalls
        if (others.isNotEmpty()) c.conference(others.first())
        else if (c.has(Call.Details.CAPABILITY_MERGE_CONFERENCE)) c.mergeConference()
    }

    fun chooseAccount(handle: PhoneAccountHandle) {
        call?.phoneAccountSelected(handle, false)
    }

    // ------------------------------------------------------------------ audio

    private fun audio(): CallAudioState? = inCallService?.callAudioState

    fun isMuted(): Boolean = audio()?.isMuted == true

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    fun route(): Int = audio()?.route ?: CallAudioState.ROUTE_EARPIECE

    fun routes(): Int = audio()?.supportedRouteMask ?: 0

    fun setRoute(route: Int) {
        inCallService?.setAudioRoute(route)
    }

    fun isSpeaker(): Boolean = route() == CallAudioState.ROUTE_SPEAKER

    // ------------------------------------------------------------------ the keypad

    private val main = Handler(Looper.getMainLooper())

    fun keypad(c: Char) {
        val now = call ?: return
        now.playDtmfTone(c)
        main.postDelayed({ now.stopDtmfTone() }, TONE_MS)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private const val TONE_MS = 150L
    private val NEW_STATES = setOf(Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_RINGING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT)
}

sealed class PhoneState
object NoCall : PhoneState()
class SingleCall(val call: Call) : PhoneState()
class TwoCalls(val active: Call, val onHold: Call) : PhoneState()

fun Call?.stateCompat(): Int = this?.details?.state ?: Call.STATE_DISCONNECTED

fun Call.isOutgoing(): Boolean = details.callDirection == Call.Details.DIRECTION_OUTGOING

fun Call.has(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true

/** The number this call is with, or null when the other side hid it. */
fun Call.number(): String? {
    val handle = runCatching { details.handle }.getOrNull() ?: return null
    return handle.schemeSpecificPart?.takeIf { it.isNotBlank() }
}

/** How long the call has been connected, in seconds; 0 before it is. */
fun Call.seconds(): Long {
    val at = details.connectTimeMillis
    return if (at <= 0L) 0L else (System.currentTimeMillis() - at) / 1000
}
