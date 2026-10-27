package com.wanderwildwood.itodenwa.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.telephony.PhoneNumberUtils

/** One line of the block list: a whole number, or the start of numbers ending in `*`. */
data class Blocked(val id: Long?, val text: String) {
    val isPrefix: Boolean get() = Patterns.isPrefix(text)
}

/**
 * The parts of the block list that need no phone: what counts as a prefix, whether a number
 * starts with one, and the file the list is kept in.
 */
object Patterns {

    /** `765*`, `+322888*`: digits, perhaps a leading "+", and a star at the end. */
    fun isPrefix(text: String): Boolean = text.trim().endsWith("*")

    /** A line as it is typed, tidied: spaces and dashes gone, one star at most, at the end. */
    fun tidy(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val star = t.endsWith("*")
        val body = Numbers.dialable(t.trimEnd('*')).filter { it != '*' && it != '#' && it != ',' && it != ';' }
        if (Numbers.digits(body).isEmpty()) return null
        return if (star) "$body*" else body
    }

    /**
     * Whether a number starts with [prefix] (`765*`). A prefix with a "+" is compared with the
     * number in its international form; one without, with the number as it came and as it is
     * written at home — so `765*` finds +1 765 555 0100 as well as 765 555 0100, and `07700*`
     * finds +44 7700 900123. The leading zeros of a home number are set aside on both sides.
     *
     * [international] is the number as "+15555550100", [national] as the phone would write it in
     * its own country; either may be null where it could not be worked out.
     */
    fun matches(prefix: String, raw: String, international: String?, national: String?): Boolean {
        val p = prefix.trim().trimEnd('*')
        val digits = Numbers.digits(p)
        if (digits.isEmpty()) return false
        if (p.startsWith("+")) {
            val forms = listOfNotNull(international, raw.takeIf { it.trim().startsWith("+") })
            return forms.any { Numbers.digits(it).startsWith(digits) }
        }
        val sig = digits.trimStart('0')
        if (sig.isEmpty()) return false
        val forms = listOfNotNull(raw.takeIf { !it.trim().startsWith("+") && !Numbers.digits(it).startsWith("00") }, national?.takeIf { !it.trim().startsWith("+") })
        return forms.any { Numbers.significant(it).startsWith(sig) }
    }

    /** The first of [prefixes] the number starts with. */
    fun firstMatch(prefixes: List<String>, raw: String, international: String?, national: String?): String? =
        prefixes.firstOrNull { matches(it, raw, international, national) }

    /**
     * The block list as a file, in Fossify's own format, so a list exported from Fossify Phone
     * comes in and one from here goes back: the lines joined by commas. Reading also takes one a
     * line, as a list typed by hand would be.
     */
    const val DELIMITER = ","

    fun write(lines: List<String>): String = lines.joinToString(DELIMITER)

    fun read(text: String): List<String> =
        text.split(',', '\n', '\r', ';').mapNotNull { tidy(it) }.distinct()
}

/**
 * The block list. Whole numbers go into Android's own list of blocked numbers, which the phone
 * app may write and the phone itself honours before a call reaches any app — and which messaging
 * apps read too. Prefixes Android's list cannot hold, so they are kept here and answered by the
 * screener.
 */
class BlockList(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the phone lets this app write its list: only while it is the phone app. */
    fun canWrite(): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun prefixes(): List<String> = prefs.getString(PREFIXES, "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun savePrefixes(list: List<String>) {
        prefs.edit().putString(PREFIXES, list.distinct().joinToString("\n")).apply()
    }

    /** Whole numbers on Android's list; empty when it may not be read. */
    fun numbers(): List<Blocked> {
        if (!canWrite()) return emptyList()
        val out = mutableListOf<Blocked>()
        runCatching {
            context.contentResolver.query(
                BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1) ?: continue
                    // A Fossify list may have put a pattern in Android's; it is shown, and read as one.
                    out += Blocked(c.getLong(0), n)
                }
            }
        }
        return out
    }

    /** Every line, prefixes after the whole numbers. */
    fun all(): List<Blocked> = numbers() + prefixes().map { Blocked(null, it) }

    /** Every prefix, those in Android's list included. */
    fun allPrefixes(): List<String> = prefixes() + numbers().filter { it.isPrefix }.map { it.text }

    /** Whether [raw], as it would be added, is already a line on the list. */
    fun has(raw: String): Boolean {
        val line = Patterns.tidy(raw) ?: return false
        if (Patterns.isPrefix(line)) return line in allPrefixes()
        return numbers().any { !it.isPrefix && Numbers.same(it.text, line) }
    }

    fun add(raw: String): Boolean {
        val line = Patterns.tidy(raw) ?: return false
        if (Patterns.isPrefix(line)) {
            savePrefixes(prefixes() + line)
            return true
        }
        if (!canWrite()) return false
        return runCatching {
            val values = ContentValues().apply {
                put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, line)
                PhoneNumberUtils.formatNumberToE164(line, Lookup.country(context))?.let { put(BlockedNumbers.COLUMN_E164_NUMBER, it) }
            }
            context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null
        }.getOrDefault(false)
    }

    fun remove(line: Blocked) {
        if (line.id == null) {
            savePrefixes(prefixes().filter { it != line.text })
        } else {
            runCatching { context.contentResolver.delete(BlockedNumbers.CONTENT_URI, "${BlockedNumbers.COLUMN_ID} = ?", arrayOf(line.id.toString())) }
        }
    }

    /** Whether a number is turned away, by Android's list or a prefix here. Reads the provider: not on the main thread. */
    fun isBlocked(number: String): Boolean = whatBlocks(number) != null

    /** The line that blocks [number], or null. */
    fun whatBlocks(number: String): Blocked? {
        val whole = numbers()
        whole.firstOrNull { !it.isPrefix && Numbers.same(it.text, number) }?.let { return it }
        if (whole.isNotEmpty() && runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)) {
            return whole.firstOrNull { Numbers.same(it.text, number) } ?: Blocked(null, number)
        }
        val prefix = prefixFor(number, allPrefixes()) ?: return null
        return all().firstOrNull { it.text == prefix } ?: Blocked(null, prefix)
    }

    /** The prefix [number] starts with, among [prefixes]. */
    fun prefixFor(number: String, prefixes: List<String>): String? {
        if (prefixes.isEmpty()) return null
        val iso = Lookup.country(context)
        val e164 = PhoneNumberUtils.formatNumberToE164(number, iso)
        val national = e164?.let { PhoneNumberUtils.formatNumber(it, iso) }
        return Patterns.firstMatch(prefixes, number, e164, national)
    }

    companion object {
        private const val PREFS = "blocking"
        private const val PREFIXES = "prefixes"
    }
}
