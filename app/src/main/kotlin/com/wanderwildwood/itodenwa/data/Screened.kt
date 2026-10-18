package com.wanderwildwood.itodenwa.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A call a rule acted on: when, who, what was done, and why. */
data class ScreenedCall(
    val at: Long,
    /** Null for a hidden number. */
    val number: String?,
    val action: Action,
    val rule: Rule,
    /** The prefix that matched, for the block list. */
    val pattern: String? = null,
)

/**
 * The calls the rules turned away or silenced, for Blocked calls to show. A small file of the
 * app's own, kept to the last thirty days and two hundred calls. Calls the phone turned away
 * itself, for a whole number on its list, are in the call log instead, and the page reads
 * them from there.
 */
class Screened(context: Context) {
    private val file = File(context.applicationContext.filesDir, "screened.json")

    @Synchronized
    fun all(): List<ScreenedCall> = runCatching { decode(file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun add(call: ScreenedCall, now: Long = System.currentTimeMillis()) {
        val kept = prune(listOf(call) + all(), now)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encode(kept))
            tmp.renameTo(file)
        }
    }

    @Synchronized
    fun forget(numberMatches: (String?) -> Boolean) {
        val kept = all().filterNot { numberMatches(it.number) }
        runCatching { file.writeText(encode(kept)) }
    }

    companion object {
        const val KEEP_DAYS = 30
        const val KEEP_MOST = 200
        private const val DAY = 24L * 60 * 60 * 1000

        /** Newest first, nothing older than thirty days, and no more than two hundred. */
        fun prune(calls: List<ScreenedCall>, now: Long): List<ScreenedCall> =
            calls.filter { now - it.at <= KEEP_DAYS * DAY }.sortedByDescending { it.at }.take(KEEP_MOST)

        fun encode(calls: List<ScreenedCall>): String = JSONArray().apply {
            calls.forEach { c ->
                put(
                    JSONObject()
                        .put("at", c.at)
                        .put("number", c.number ?: JSONObject.NULL)
                        .put("action", c.action.name)
                        .put("rule", c.rule.name)
                        .put("pattern", c.pattern ?: JSONObject.NULL),
                )
            }
        }.toString()

        fun decode(text: String): List<ScreenedCall> {
            val a = JSONArray(text)
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val action = Action.entries.firstOrNull { it.name == o.optString("action") } ?: return@mapNotNull null
                val rule = Rule.entries.firstOrNull { it.name == o.optString("rule") } ?: return@mapNotNull null
                ScreenedCall(
                    at = o.optLong("at"),
                    number = if (o.isNull("number")) null else o.optString("number"),
                    action = action,
                    rule = rule,
                    pattern = if (o.isNull("pattern")) null else o.optString("pattern"),
                )
            }
        }
    }
}
