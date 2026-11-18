package com.wanderwildwood.itodenwa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.itodenwa.call.CallActions
import com.wanderwildwood.itodenwa.call.CallManager
import com.wanderwildwood.itodenwa.call.CallNotifier
import com.wanderwildwood.itodenwa.ui.InCallScreen
import com.wanderwildwood.itodenwa.ui.houseTypography
import com.wanderwildwood.itodenwa.ui.monochrome
import com.wanderwildwood.itodenwa.ui.ArmedScope
import com.wanderwildwood.itodenwa.ui.DarkSelection

/**
 * The call screen, over the lock screen when the phone is locked — it shows there and wakes the
 * panel, but does not unlock the phone. Back leaves the call going; the notification, or the
 * line at the top of the app, brings this back.
 */
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // The panel stays on for the whole call, unless the proximity sensor turns it off at the
        // ear; the volume keys set the call's own volume, the earpiece's, at the phone's full range.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = android.media.AudioManager.STREAM_VOICE_CALL
        // Only a fresh launch: an Answer that opened this screen is not answered again when the
        // screen is made anew (a change of dark mode, font size or language).
        if (savedInstanceState == null) handle(intent)
        if (!CallManager.hasCalls()) {
            finish()
            return
        }
        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
                ArmedScope { InCallScreen(opened = opened.intValue, onDone = { finishAndRemoveTask() }) }
            } }
        }
    }

    override fun onResume() {
        super.onResume()
        shown = true
        // Put the call's notification back without its heads-up, which would sit over the name.
        if (CallManager.hasCalls()) CallNotifier(this).apply { cancel(); show() }
    }

    override fun onPause() {
        super.onPause()
        shown = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
        opened.intValue++
    }

    /** Counts the times this screen is brought up again, so it shows the call it was brought up for. */
    private val opened = androidx.compose.runtime.mutableIntStateOf(0)

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            CallActions.ANSWER -> CallManager.answer()
            CallActions.ANSWER_WAITING -> CallManager.answerWaiting()
        }
    }

    companion object {
        /** Whether the call screen is in front, so a ringing call needs no heads-up. */
        @Volatile
        var shown = false

        fun intent(context: Context): Intent = Intent(context, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
}
