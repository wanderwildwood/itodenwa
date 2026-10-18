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
