package com.wanderwildwood.itodenwa.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog.Calls
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

/**
 * What the screener asks of the call log, and the changes Recents makes to it: deleting a row,
 * clearing the lot, and marking missed calls seen.
 */
object CallLogs {

    private fun mayRead(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun mayWrite(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Calls in the log since [since], newest first; empty when the log cannot be read. */
    private fun since(context: Context, since: Long, types: List<Int>? = null): List<Pair<String?, Int>> {
        if (!mayRead(context)) return emptyList()
        val out = mutableListOf<Pair<String?, Int>>()
        runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls.NUMBER, Calls.TYPE),
                "${Calls.DATE} >= ?",
                arrayOf(since.toString()),
                "${Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(1)
                    if (types == null || type in types) out += c.getString(0) to type
                }
            }
        }
        return out
    }

    /** Whether [number] has been called from this phone, as far back as the log goes. */
    fun calledBefore(context: Context, number: String): Boolean {
        if (!mayRead(context)) return false
        return runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls.NUMBER),
                "${Calls.TYPE} = ?",
                arrayOf(Calls.OUTGOING_TYPE.toString()),
                "${Calls.DATE} DESC",
            )?.use { c ->
                var found = false
                while (!found && c.moveToNext()) found = c.getString(0)?.let { Numbers.same(it, number) } == true
                found
            } ?: false
        }.getOrDefault(false)
    }

    /** Whether [number] called within the last [windowMs] — answered, missed or turned away. */
    fun calledRecently(context: Context, number: String, windowMs: Long, now: Long = System.currentTimeMillis()): Boolean =
        since(context, now - windowMs).any { (n, type) -> type != Calls.OUTGOING_TYPE && n != null && Numbers.same(n, number) }

    /**
     * Whether an emergency call went out in the last [windowMs]. After one, nothing is turned
     * away for a while, as Android does with its own block list, so the call back gets through.
     */
    fun emergencyRecently(context: Context, windowMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        return since(context, now - windowMs, listOf(Calls.OUTGOING_TYPE)).any { (n, _) ->
            n != null && runCatching { tm.isEmergencyNumber(n) }.getOrDefault(false)
        }
    }

    /** Deletes these calls from the log. */
    fun delete(context: Context, ids: List<Long>): Boolean {
        if (!mayWrite(context) || ids.isEmpty()) return false
        return runCatching {
            ids.chunked(200).forEach { chunk ->
                context.contentResolver.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${chunk.joinToString(",")})", null)
            }
            true
        }.getOrDefault(false)
    }

    /** Deletes every call in the log. */
    fun clear(context: Context): Boolean {
        if (!mayWrite(context)) return false
        return runCatching { context.contentResolver.delete(Calls.CONTENT_URI, null, null); true }.getOrDefault(false)
    }

    /**
     * Missed calls have been seen: the log stops counting them as new, and the missed-call
     * notification goes, so no badge is left standing once Recents has been opened.
     */
    fun markMissedSeen(context: Context) {
        if (mayWrite(context)) {
            runCatching {
                val values = ContentValues().apply {
                    put(Calls.NEW, 0)
                    put(Calls.IS_READ, 1)
                }
                context.contentResolver.update(
                    Calls.CONTENT_URI,
                    values,
                    "${Calls.TYPE} = ? AND (${Calls.NEW} = 1 OR ${Calls.IS_READ} = 0)",
                    arrayOf(Calls.MISSED_TYPE.toString()),
                )
            }
        }
        runCatching { context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification() }
        com.wanderwildwood.itodenwa.call.MissedCalls.cancel(context)
    }
}
