package com.wanderwildwood.itodenwa.data

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context

/**
 * Do Not Disturb, as far as a call is concerned: whether it is on, and whether the phone's own
 * settings for it let this caller ring anyway. Read only; the app never changes them.
 */
object Quiet {

    /** Whether Do Not Disturb is keeping calls quiet in any way at all. */
    fun isOn(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
    }

    /**
     * Whether the phone itself would ring for this caller right now. With Do Not Disturb off it
     * always does; on "priority only" it does for whoever its call settings let through; on
     * "alarms only" and "total silence" for no one.
     */
    fun phoneWouldRing(context: Context, inContacts: Boolean, starred: Boolean, repeat: Boolean): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                val policy = runCatching { nm.notificationPolicy }.getOrNull() ?: return false
                allows(policy.priorityCategories, policy.priorityCallSenders, inContacts, starred, repeat)
            }
            else -> false
        }
    }

    /**
     * [phoneWouldRing] for a caller known only by [number]: under "priority only" the caller is
     * looked up in Contacts and the call log ([repeatMs] for a repeat caller); under the other
     * filters there is nothing to look up. A lookup that fails counts as not in Contacts and not
     * starred. Reads the contacts and the call log: not on the main thread.
     */
    fun phoneWouldRingFor(context: Context, number: String?, repeatMs: Long): Boolean {
        if (filter(context) != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            return phoneWouldRing(context, inContacts = false, starred = false, repeat = false)
        }
        val check = number?.let { runCatching { Lookup.get(context).check(it) }.getOrNull() }
        val who = (check as? Lookup.Check.Found)?.who
        val repeat = number != null && runCatching { CallLogs.calledRecently(context, number, repeatMs) }.getOrDefault(false)
        return phoneWouldRing(context, check is Lookup.Check.Found, who?.starred == true, repeat)
    }

    /** The phone's Do Not Disturb filter right now. */
    fun filter(context: Context): Int =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    /**
     * Whether a favorite or a repeat caller may ring through Do Not Disturb set to [filter]: under
     * Priority only and Alarms only, never under Total silence, as Settings and the README say.
     * Total silence mutes the alarm sound and the ring vibration the app would ring with.
     */
    fun mayRingThrough(filter: Int): Boolean = filter != NotificationManager.INTERRUPTION_FILTER_NONE

    /** [mayRingThrough], for a favorite. */
    fun favouriteMayRingThrough(filter: Int): Boolean = mayRingThrough(filter)

    /** The priority-only rule, apart from the phone so it can be tested. */
    fun allows(categories: Int, callSenders: Int, inContacts: Boolean, starred: Boolean, repeat: Boolean): Boolean {
        if (repeat && categories and Policy.PRIORITY_CATEGORY_REPEAT_CALLERS != 0) return true
        if (categories and Policy.PRIORITY_CATEGORY_CALLS == 0) return false
        return when (callSenders) {
            Policy.PRIORITY_SENDERS_ANY -> true
            Policy.PRIORITY_SENDERS_CONTACTS -> inContacts
            Policy.PRIORITY_SENDERS_STARRED -> starred
            else -> false
        }
    }
}
