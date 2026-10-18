package com.wanderwildwood.itodenwa.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import com.wanderwildwood.itodenwa.BuildConfig
import java.io.File

/**
 * DuraSpeed, MediaTek's background manager on the Kompakt, force-stops installed apps a few
 * minutes after the screen goes dark, and an app it has stopped is passed over for what the
 * phone sends it until somebody opens it again: here, the calls Telecom hands to the phone app.
 * Apps switched on in its list are left alone, but that list cannot be read by another app, and
 * Settings has no way into it. Its App info page can be opened, and has an Open button, so that
 * is where the line goes; whether it was done is the person's word, asked on the way back, as
 * Messaging asks it.
 */
object DuraSpeed {

    private const val PACKAGE = "com.mediatek.duraspeed"
    private const val PREFS = "duraspeed"
    private const val ALLOWED = "allowed"
    private const val OPENED = "opened"

    /**
     * A phone with DuraSpeed on it, or, in a debug build only, an emulator told to act as one by
     * a file named `pretend-kompakt` in the app's files, so the line can be seen without the phone.
     */
    fun present(context: Context): Boolean =
        Actions.installed(context, PACKAGE) ||
            (BuildConfig.DEBUG && File(context.filesDir, "pretend-kompakt").exists())

    /**
     * Whether the one step is still to do. Not gated on DuraSpeed's own switch: its list of
     * stopped apps outlasts the switch being turned off, and with the switch read as off a
     * Kompakt that had DuraSpeed never asked the question at all.
     */
    fun atRisk(context: Context): Boolean =
        present(context) && !prefs(context).getBoolean(ALLOWED, false)

    /** Bumped when the answer changes, so a screen showing it reads it again. */
    val changes = mutableIntStateOf(0)

    /** The person says Phone is switched on in DuraSpeed's list. */
    fun done(context: Context) {
        prefs(context).edit().putBoolean(ALLOWED, true).putBoolean(OPENED, false).apply()
        changes.intValue++
    }

    /**
     * DuraSpeed was opened from here; on the way back Phone asks whether it is switched on now,
     * as Messaging does.
     */
    fun wasOpened(context: Context): Boolean = prefs(context).getBoolean(OPENED, false)

    fun setOpened(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(OPENED, value).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun appInfo(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:$PACKAGE"))
}
