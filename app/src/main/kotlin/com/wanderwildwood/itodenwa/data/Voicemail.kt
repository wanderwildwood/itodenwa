package com.wanderwildwood.itodenwa.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.wanderwildwood.itodenwa.call.MissedCalls

/** Voicemail: the number the SIM gives for it, a call to it, and the carrier's settings for it. */
object Voicemail {

    /** The voicemail number of the phone's calling account, or null where the SIM gives none. */
    @SuppressLint("MissingPermission")
    fun number(context: Context): String? = runCatching {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        val account = telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            ?: telecom.callCapablePhoneAccounts.firstOrNull()
        val fromTelecom = account?.let { telecom.getVoiceMailNumber(it) }
        fromTelecom?.takeIf { it.isNotBlank() }
            ?: context.getSystemService(TelephonyManager::class.java)?.voiceMailNumber?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Whether a call is to voicemail: placed through Telecom's "voicemail:" address, or to the
     * number the SIM gives for it. The call screen then says "Voicemail", as Recents does.
     */
    fun isCallTo(context: Context, call: Call): Boolean {
        val handle = runCatching { call.details.handle }.getOrNull() ?: return false
        if (handle.scheme == PhoneAccount.SCHEME_VOICEMAIL) return true
        val dialled = handle.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return false
        val voicemail = number(context) ?: return false
        return Numbers.same(dialled, voicemail)
    }

    /** The carrier's voicemail settings, where a number can be set. */
    fun settings(): Intent = Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)

    /**
     * Calls voicemail through Telecom's own "voicemail:" address, which dials the SIM's number
     * for it. False when there is none to call; the caller then opens [settings].
     */
    fun call(context: Context): Boolean {
        if (number(context) == null || !Actions.mayCall(context)) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), Bundle())
            MissedCalls.cancelVoicemail(context)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
