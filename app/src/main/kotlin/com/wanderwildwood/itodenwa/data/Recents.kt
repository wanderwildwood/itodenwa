package com.wanderwildwood.itodenwa.data

import android.content.ContentResolver
import android.provider.CallLog.Calls
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One call in the phone's call log. */
data class LoggedCall(
    val id: Long,
    /** Null when the caller hid their number, or the network did not give one. */
    val number: String?,
    val kind: Kind,
    val at: Long,
    val seconds: Long,
) {
    enum class Kind { IN, OUT, MISSED, DECLINED, BLOCKED }
}

/** A row of Recents: one call, or several in a row with the same number on the same day. */
data class Recent(val calls: List<LoggedCall>) {
    val last: LoggedCall get() = calls.first()
    val number: String? get() = last.number
    val count: Int get() = calls.size
}

object Recents {

    /** The newest calls in the log, at most [limit]. */
    fun read(resolver: ContentResolver, limit: Int = 300): List<LoggedCall> {
        val out = mutableListOf<LoggedCall>()
        val uri = Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        resolver.query(
            uri,
            arrayOf(Calls._ID, Calls.NUMBER, Calls.TYPE, Calls.DATE, Calls.DURATION, Calls.NUMBER_PRESENTATION),
            null,
            null,
            "${Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val presentation = c.getInt(5)
                val raw = c.getString(1)
                val number = raw?.takeIf { presentation == Calls.PRESENTATION_ALLOWED && Numbers.digits(it).isNotEmpty() && it != "-1" }
                out += LoggedCall(c.getLong(0), number, kindOf(c.getInt(2)), c.getLong(3), c.getLong(4))
            }
        }
        return out
    }

    /** The calls with any of [numbers], newest first. */
    fun with(all: List<LoggedCall>, numbers: List<String>): List<LoggedCall> =
        all.filter { call -> call.number != null && numbers.any { Numbers.same(it, call.number) } }

    /**
     * Calls one after another with the same number, on the same day, make one row, as the
     * phone's own Recents and Fossify's both do. Hidden numbers are never put together: two
     * calls with no number may be two different people.
     */
    fun group(calls: List<LoggedCall>, zone: ZoneId = ZoneId.systemDefault()): List<Recent> {
        val out = mutableListOf<Recent>()
        var run = mutableListOf<LoggedCall>()
        fun day(c: LoggedCall): LocalDate = Instant.ofEpochMilli(c.at).atZone(zone).toLocalDate()
        for (call in calls) {
            val prev = run.lastOrNull()
            val joins = prev != null && prev.number != null && call.number != null &&
                Numbers.same(prev.number, call.number) && day(prev) == day(call) &&
                (prev.kind == LoggedCall.Kind.MISSED) == (call.kind == LoggedCall.Kind.MISSED)
            if (joins) {
                run += call
            } else {
                if (run.isNotEmpty()) out += Recent(run)
                run = mutableListOf(call)
            }
        }
        if (run.isNotEmpty()) out += Recent(run)
        return out
    }

    private fun kindOf(type: Int): LoggedCall.Kind = when (type) {
        Calls.OUTGOING_TYPE -> LoggedCall.Kind.OUT
        Calls.MISSED_TYPE -> LoggedCall.Kind.MISSED
        Calls.REJECTED_TYPE -> LoggedCall.Kind.DECLINED
        Calls.BLOCKED_TYPE -> LoggedCall.Kind.BLOCKED
        else -> LoggedCall.Kind.IN
    }

    /**
     * When a call was, as the phone's own call history says it: the time today, "Yesterday",
     * the weekday within the week, the date before that, and the year once it is another.
     * The same words as Contacts' call history.
     */
    fun whenSaid(
        at: Long,
        now: Long,
        today: String,
        yesterday: String,
        hours24: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val then = Instant.ofEpochMilli(at).atZone(zone)
        val todayDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day: LocalDate = then.toLocalDate()
        val time = if (hours24) then.format(DateTimeFormatter.ofPattern("H:mm", locale))
        else then.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)).replace(' ', ' ')
        val dayWord = when {
            day == todayDate -> today
            day == todayDate.minusDays(1) -> yesterday
            day.isAfter(todayDate.minusDays(7)) -> then.format(DateTimeFormatter.ofPattern("EEEE", locale))
            day.year == todayDate.year -> then.format(DateTimeFormatter.ofPattern("MMM d", locale))
            else -> then.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
        return "$dayWord, $time"
    }

    /** How long a call went on: "7 sec", "2 min 5 sec", "1 h 5 min". Nothing for one never answered. */
    fun howLong(seconds: Long): String? = when {
        seconds <= 0 -> null
        seconds < 60 -> "$seconds sec"
        seconds < 3600 -> "${seconds / 60} min ${seconds % 60} sec"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }
}
