package com.wanderwildwood.itodenwa.data

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * What the app hands to the phone and to the other apps: a call to Telecom, a message to the
 * phone's messaging app, a person to the Contacts app.
 */
object Actions {

    /** Contacts — enishi — named when it is on the phone, so no "open with" question comes up beside the stock app. */
    const val CONTACTS_APP = "com.wanderwildwood.enishi"

    /** Notes — oboegaki — which takes a shared text with a subject as a new note of that title. */
    const val NOTES_APP = "com.wanderwildwood.oboegaki"

    /**
     * Whether a ringing call may fill the screen and wake the phone. Android 14 on takes this from
     * an app installed from a file until it is allowed in the phone's settings.
     */
    fun canFillScreen(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

    fun fullScreenSettings(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + context.packageName))

    fun isPhoneApp(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    /** The phone's own question, "make Phone your default phone app?". */
    fun askToBePhoneApp(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER) || roles.isRoleHeld(RoleManager.ROLE_DIALER)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }

    /**
     * Whether [number] is an emergency number where the phone is. Such a number is never dialled
     * by a long press, a speed dial or a notification: it is put on the keypad, and the Call button
     * there is pressed for it.
     */
    fun isEmergency(context: Context, number: String): Boolean = runCatching {
        context.getSystemService(android.telephony.TelephonyManager::class.java)?.isEmergencyNumber(number) == true
    }.getOrDefault(false) || Numbers.digits(number) in setOf("112", "911", "999", "000", "110", "119", "100", "101", "102", "118")

    /** The keypad with [number] typed on it, waiting for Call. */
    fun keypad(context: Context, number: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).setPackage(context.packageName)

    fun mayCall(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /**
     * Places a call to [number], through Telecom, which then hands it back to this app's call
     * screen while this is the phone app, or to whichever app is. False when it could not.
     */
    fun call(context: Context, number: String): Boolean {
        if (!mayCall(context)) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** A new message to [number], in the phone's messaging app — Messaging, where it is the default. */
    fun message(context: Context, number: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        Telephony.Sms.getDefaultSmsPackage(context)?.let { pkg ->
            if (start(context, Intent(intent).setPackage(pkg))) return true
        }
        return start(context, intent)
    }

    /** Whether any app on the phone writes a message to a number. */
    fun canMessage(context: Context): Boolean =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", "0", null)), 0).isNotEmpty()

    private fun toNotes(title: String): Intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .setPackage(NOTES_APP)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "")

    /** Whether Notes is on the phone to take a note. */
    fun canNote(context: Context): Boolean =
        context.packageManager.queryIntentActivities(toNotes(""), 0).isNotEmpty()

    /**
     * A new note in Notes titled [title], open and waiting for the first word. In a task of its
     * own, so the call screen stays where it was, a press away in the recent apps.
     */
    fun note(context: Context, title: String): Boolean =
        start(context, toNotes(title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** The person's own page in the Contacts app, where they are edited. */
    fun openContact(context: Context, id: Long, lookupKey: String): Boolean {
        val uri = ContactsContract.Contacts.getLookupUri(id, lookupKey) ?: return false
        return toContacts(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /** Asks the Contacts app to put [number] on a new card or on someone already there. */
    fun addToContacts(context: Context, number: String): Boolean {
        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        return toContacts(context, intent)
    }

    private fun toContacts(context: Context, intent: Intent): Boolean {
        if (installed(context, CONTACTS_APP) && start(context, Intent(intent).setPackage(CONTACTS_APP))) return true
        return start(context, intent)
    }

    fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
