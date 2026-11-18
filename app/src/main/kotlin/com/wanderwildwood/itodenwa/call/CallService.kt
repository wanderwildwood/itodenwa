package com.wanderwildwood.itodenwa.call

import android.app.KeyguardManager
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.wanderwildwood.itodenwa.InCallActivity
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.Prefs
import com.wanderwildwood.itodenwa.data.Quiet

/**
 * What the phone hands every call to while this is the phone app. From Fossify Phone's
 * CallService: a call that comes in or goes out is given to [CallManager], a notification is
 * kept for it, and the call screen is opened over whatever is in front.
 *
 * The ringing itself is the phone's own: Telecom rings and vibrates for a call, on the ring
 * volume and with the ringtone chosen in the phone's settings or on the caller's card. The app
 * rings only the calls the screener kept the phone's ringer quiet for — an unknown caller with a
 * ringtone of their own, a favourite through Do Not Disturb — and lets a call it silenced by a
 * rule come in with nothing but its notification.
 */
class CallService : InCallService() {
    private val notifier by lazy { CallNotifier(this) }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING && CallManager.ringingOurselves == call) {
                Ringer.stop()
                CallManager.ringingOurselves = null
            }
            if (state != Call.STATE_RINGING && CallManager.ringLater?.call == call) CallManager.ringLater = null
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                if (!CallManager.hasCalls()) notifier.cancel()
            } else {
                notifier.show()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.inCallService = this
        CallManager.onCallAdded(call)
        call.registerCallback(callback)

        val incoming = !call.isOutgoing()
        if (!incoming && call.number()?.let { Actions.isEmergency(this, it) } == true) {
            Prefs(this).lastEmergencyCall = System.currentTimeMillis()
        }
        if (incoming) {
            // Whether the phone asked the screener about this call: if not, another app screens.
            Prefs(this).lastCallScreened = System.currentTimeMillis() - Screener.lastAsked < 15_000L
        }
        var throughQuiet = false
        var heardThroughQuiet = false
        if (incoming && call.stateCompat() == Call.STATE_RINGING) {
            when (val d = Decisions.take(call.number())) {
                is Decision.Silenced -> CallManager.silenced += call
                is Decision.RingWith -> ringOurselves(call, d.tone, false)
                is Decision.RingThroughQuiet -> {
                    throughQuiet = true
                    ringOurselves(call, d.tone, true)
                }
                // Rung by the phone; whether Do Not Disturb lets that ring be heard.
                is Decision.Rings -> heardThroughQuiet = d.heard == true
                null -> Unit
            }
        }
        if (call in CallManager.silenced) {
            // Silenced by a rule: it comes in quietly, a notification and nothing more.
            notifier.show()
            return
        }

        // A call ringing on a locked or sleeping phone comes up through the notification's
        // full-screen intent, which is what wakes the screen and shows over the lock. On a phone
        // in use, and for a call going out, the call screen is simply opened.
        //
        // The full-screen intent does nothing when its channel has been switched off, or when Do
        // Not Disturb holds the notification back from a call that is ringing through it. Then
        // the screen is opened directly, as a phone app's call service may do from behind; a
        // call that Do Not Disturb keeps quiet is left to wait in the shade, as it asks. One its
        // Priority settings let ring is not kept quiet, and comes up like any other.
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = !power.isInteractive || keyguard.isKeyguardLocked
        notifier.show(fullScreen = incoming)
        // Not allowed to fill the screen (Android 14 on, an app installed from a file): opened directly.
        val fullScreenWorks = notifier.canAlert() && Actions.canFillScreen(this) && !throughQuiet
        val quietHoldsIt = incoming && Quiet.isOn(this) && !throughQuiet && !heardThroughQuiet
        if (!(incoming && locked) || (!fullScreenWorks && !quietHoldsIt)) {
            runCatching { startActivity(InCallActivity.intent(this)) }
        }
    }

    private fun ringOurselves(call: Call, tone: android.net.Uri?, throughQuiet: Boolean) {
        // A second call during one in progress: one short buzz, never a full ring in the ear.
        // It is rung in full if it is still ringing when that call ends.
        if (CallManager.otherCallGoing(call)) {
            Ringer.nudge(this)
            CallManager.ringLater = CallManager.RingLater(call, tone, throughQuiet)
            return
        }
        CallManager.ringLater = null
        CallManager.ringingOurselves = call
        Ringer.start(this, tone, throughQuiet)
    }

    /** A volume key or the power key pressed while ringing: the app's own ringer stops too. */
    override fun onSilenceRinger() {
        super.onSilenceRinger()
        Ringer.stop()
        CallManager.ringingOurselves = null
        CallManager.ringLater = null
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callback)
        CallManager.silenced -= call
        if (CallManager.ringingOurselves == call) {
            Ringer.stop()
            CallManager.ringingOurselves = null
        }
        if (CallManager.ringLater?.call == call) CallManager.ringLater = null
        val wasFront = call == CallManager.primary()
        CallManager.onCallRemoved(call)
        // A call only buzzed for while this one was going, still ringing: now it rings.
        CallManager.ringLater?.let { later ->
            if (later.call.stateCompat() == Call.STATE_RINGING && !CallManager.otherCallGoing(later.call)) {
                ringOurselves(later.call, later.tone, later.throughQuiet)
            }
        }
        if (CallManager.phoneState() == NoCall) {
            CallManager.inCallService = null
            notifier.cancel()
        } else {
            notifier.show()
            // Not for a call a rule silenced, left ringing in the shade after the other one ended.
            if (wasFront && !CallManager.quietlyRinging(CallManager.primary())) {
                runCatching { startActivity(InCallActivity.intent(this)) }
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        Ringer.stop()
        notifier.cancel()
    }
}
