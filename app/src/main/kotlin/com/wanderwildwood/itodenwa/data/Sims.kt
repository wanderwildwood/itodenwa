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
