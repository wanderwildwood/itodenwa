package com.wanderwildwood.itodenwa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.itodenwa.call.CallManager
import com.wanderwildwood.itodenwa.data.Numbers
import com.wanderwildwood.itodenwa.ui.Opening
import com.wanderwildwood.itodenwa.ui.PhoneApp
import com.wanderwildwood.itodenwa.ui.TAB_KEYPAD
import com.wanderwildwood.itodenwa.ui.TAB_RECENTS
import com.wanderwildwood.itodenwa.ui.monochrome

/**
 * The phone app, from the launcher and from every other app that asks for a keypad: a number
 * to dial (`DIAL`, a `tel:` link) opens the keypad with it typed and waiting for Call — never
 * calling by itself — and "show the call log" opens Recents. During a call, the launcher's icon
 * goes straight back to the call screen.
 */
class MainActivity : ComponentActivity() {
    private var opening by mutableStateOf<Opening?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            opening = openingFor(intent)
            backToCall(intent)
        }
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                PhoneApp(opening, onOpened = { opening = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        opening = openingFor(intent)
        backToCall(intent)
    }

    /**
     * Opened from the home screen during a call, the phone goes back to the call. Mudita's status
     * bar draws its own call icon, which opens nothing, and leaves out Android's call chip, so
     * this is the way back to a call left for another app. Only the launcher's own intent does
     * it: coming back from the call screen to the lists, or a number sent to dial, stays here.
     */
    private fun backToCall(intent: Intent?) {
        val fromLauncher = intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (fromLauncher && CallManager.hasCalls()) startActivity(InCallActivity.intent(this))
    }

    private fun openingFor(intent: Intent?): Opening? = when (intent?.action) {
        Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
            val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(Numbers::dialable)
            if (number == null && intent.type == "vnd.android.cursor.dir/calls") Opening(TAB_RECENTS)
            else Opening(TAB_KEYPAD, number.orEmpty())
        }
        else -> null
    }
}
