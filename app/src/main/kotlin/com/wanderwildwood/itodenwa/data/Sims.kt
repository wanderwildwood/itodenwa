package com.wanderwildwood.itodenwa.data

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.wanderwildwood.itodenwa.R

/** Which SIM a call is on, for phones with more than one. */
object Sims {

    /**
     * "SIM 2 · Orange PL" for the call, or null when the phone has only one SIM that can call,
     * or when the call has no SIM yet (an outgoing call still waiting for "Call from").
     */
    @SuppressLint("MissingPermission")
    fun labelFor(context: Context, call: Call): String? = runCatching {
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (telecom.callCapablePhoneAccounts.size < 2) return null
        val handle = call.details.accountHandle ?: return null
        val name = telecom.getPhoneAccount(handle)?.label?.toString()
        val subId = context.getSystemService(TelephonyManager::class.java).getSubscriptionId(handle)
        val slot = SubscriptionManager.getSlotIndex(subId).takeIf { it >= 0 }?.plus(1)
        format(slot, name, context.getString(R.string.call_sim))
    }.getOrNull()

    /** The slot (1, 2) the call is on, or null when that is not known. */
    @SuppressLint("MissingPermission")
    fun slotOf(context: Context, call: Call): Int? = runCatching {
        val handle = call.details.accountHandle ?: return null
        val subId = context.getSystemService(TelephonyManager::class.java).getSubscriptionId(handle)
        SubscriptionManager.getSlotIndex(subId).takeIf { it >= 0 }?.plus(1)
    }.getOrNull()

    /**
     * The slot (1, 2) of each SIM that can call, by its account id as the call log keeps it
     * (PHONE_ACCOUNT_ID); empty on a phone with only one, where Recents says nothing of SIMs.
     */
    @SuppressLint("MissingPermission")
    fun slots(context: Context): Map<String, Int> = runCatching {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val handles = telecom.callCapablePhoneAccounts
        if (handles.size < 2) return emptyMap()
        val tm = context.getSystemService(TelephonyManager::class.java)
        handles.mapNotNull { h ->
            SubscriptionManager.getSlotIndex(tm.getSubscriptionId(h)).takeIf { it >= 0 }?.let { h.id to it + 1 }
        }.toMap()
    }.getOrDefault(emptyMap())

    /** "SIM 1 · Orange PL" for a row of "Call from", so two SIMs from one carrier are told apart. */
    @SuppressLint("MissingPermission")
    fun labelFor(context: Context, handle: android.telecom.PhoneAccountHandle): String = runCatching {
        val name = context.getSystemService(TelecomManager::class.java).getPhoneAccount(handle)?.label?.toString()
        val subId = context.getSystemService(TelephonyManager::class.java).getSubscriptionId(handle)
        val slot = SubscriptionManager.getSlotIndex(subId).takeIf { it >= 0 }?.plus(1)
        format(slot, name, context.getString(R.string.call_sim))
    }.getOrNull() ?: handle.id

    /**
     * The ringtone set for SIM 2 in the phone's own settings, where the phone keeps one (MuditaOS
     * 2.0 and later), or null to ring with the phone's usual one.
     */
    fun secondSimTone(context: Context): android.net.Uri? = runCatching {
        android.provider.Settings.System.getString(context.contentResolver, SIM_2_RINGTONE)
            ?.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse)
    }.getOrNull()

    private const val SIM_2_RINGTONE = "ringtone_2"

    /**
     * [slotWord] is "SIM %1$d". The SIM's own name is left out when it only repeats the slot
     * ("SIM 1"), and stands alone when the slot is unknown.
     */
    fun format(slot: Int?, name: String?, slotWord: String): String? {
        val clean = name?.trim().orEmpty()
        if (slot == null) return clean.ifEmpty { null }
        val slotText = String.format(slotWord, slot)
        val same = clean.replace(" ", "").equals(slotText.replace(" ", ""), ignoreCase = true)
        return if (clean.isEmpty() || same) slotText else "$slotText · $clean"
    }
}
