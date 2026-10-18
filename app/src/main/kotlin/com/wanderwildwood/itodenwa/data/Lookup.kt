package com.wanderwildwood.itodenwa.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/** Who a number belongs to: the person, and how their own card names that number. */
data class Who(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    /** "Mobile", "Work" — the kind of number it is on their card. */
    val kind: String,
    val starred: Boolean,
)

/**
 * Names for numbers, asked of the contacts on the phone, in three tries.
 *
 * 1. **Android's own lookup**, [PhoneLookup], with the number as it came. It compares numbers
 *    loosely, by their last digits, and finds most.
 * 2. **The number in its international form** (E.164, "+15555550143"), worked out from the
 *    country the phone is in, and the lookup asked again. A card holding the full international
 *    number and a call log holding the national one, or the other way round, is the commonest
 *    reason a name goes missing.
 * 3. **Every number on every card**, compared by [Numbers.match]: digits only, trunk zero and
 *    country code set aside, the last digits compared. This catches what the provider's own
 *    matching did not — a card written with a "0" the call log lacks, or one stored by an app
 *    that never filled in the provider's normalized column.
 *
 * Answers are kept until a contact changes.
 */
class Lookup private constructor(private val context: Context) {
    private val cache = HashMap<String, Who?>()
    private var everyone: List<Row>? = null
    private var byKey: Map<String, List<Row>> = emptyMap()

    private data class Row(val who: Who, val number: String, val normalized: String?)

    private var watching = false

    /** Android 12 refuses to watch the contacts before the app may read them, so this waits until it may. */
    private fun watch() {
        if (watching) return
        watching = runCatching {
            context.contentResolver.registerContentObserver(
                ContactsContract.AUTHORITY_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = forget()
                },
            )
        }.isSuccess
    }

    @Synchronized
    fun forget() {
        cache.clear()
        everyone = null
        byKey = emptyMap()
    }

    fun allowed(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Who [number] belongs to, or null for nobody in the contacts. Reads the provider: not on the main thread. */
    @Synchronized
    fun who(number: String): Who? {
        if (!allowed() || Numbers.digits(number).isEmpty()) return null
        watch()
        if (cache.containsKey(number)) return cache[number]
        // A lookup that failed is not kept: it says nothing about whether the number is anyone's.
        val found = runCatching { find(number, cached = true) }.getOrElse { return null }
        cache[number] = found
        return found
    }

    /** The answer to "is this number in the contacts?" that a rule may act on. */
    sealed class Check {
        data class Found(val who: Who) : Check()
        object NotFound : Check()
        /** The contacts could not be read. Never to be taken as "not in Contacts". */
        object Failed : Check()
    }

    /**
     * Whether [number] is in the contacts, read fresh from the provider with nothing kept from
     * before, for the screener: a contact added a minute ago must count. Not on the main thread.
     */
    fun check(number: String): Check {
        if (!allowed()) return Check.Failed
        if (Numbers.digits(number).isEmpty()) return Check.Failed
        return runCatching { find(number, cached = false) }.fold(
            onSuccess = { if (it != null) Check.Found(it) else Check.NotFound },
            onFailure = { Check.Failed },
        )
    }

    private fun find(number: String, cached: Boolean): Who? {
        phoneLookup(number)?.let { return it }
        val iso = country(context)
        val e164 = PhoneNumberUtils.formatNumberToE164(number, iso)
        if (e164 != null && e164 != number) phoneLookup(e164)?.let { return it }
        val index = if (cached) synchronized(this) { if (everyone == null) load(); byKey } else index(read())
        return byDigits(number, index) ?: e164?.let { byDigits(it, index) }
    }

    private fun phoneLookup(number: String): Who? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(PhoneLookup._ID, PhoneLookup.LOOKUP_KEY, PhoneLookup.DISPLAY_NAME, PhoneLookup.TYPE, PhoneLookup.LABEL, PhoneLookup.STARRED, PhoneLookup.NUMBER),
            null,
            null,
            null,
        )?.use { c ->
            // When several cards hold the number, the one whose saved number agrees most wins.
            var best: Who? = null
            var bestScore = -1
            while (c.moveToNext()) {
                val name = c.getString(2) ?: continue
                val saved = c.getString(6).orEmpty()
                val score = Numbers.match(number, saved)
                if (score > bestScore) {
                    bestScore = score
                    best = Who(c.getLong(0), c.getString(1).orEmpty(), name, kindOf(c.getInt(3), c.getString(4)), c.getInt(5) == 1)
                }
            }
            return best
        } ?: throw IllegalStateException("contacts provider gave no answer")
    }

    private fun byDigits(number: String, index: Map<String, List<Row>>): Who? {
        val candidates = index[Numbers.key(number)] ?: return null
        var best: Row? = null
        var bestScore = 0
        for (row in candidates) {
            val score = maxOf(Numbers.match(number, row.number), row.normalized?.let { Numbers.match(number, it) } ?: 0)
            if (score > bestScore) {
                bestScore = score
                best = row
            }
        }
        return best?.who
    }

    private fun load() {
        val rows = read()
        everyone = rows
        byKey = index(rows)
    }

    private fun read(): List<Row> {
        val rows = mutableListOf<Row>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.TYPE, Phone.LABEL, Phone.STARRED, Phone.NUMBER, Phone.NORMALIZED_NUMBER),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2) ?: continue
                val number = c.getString(6) ?: continue
                rows += Row(Who(c.getLong(0), c.getString(1).orEmpty(), name, kindOf(c.getInt(3), c.getString(4)), c.getInt(5) == 1), number, c.getString(7))
            }
        } ?: throw IllegalStateException("contacts provider gave no answer")
        return rows
    }

    private fun index(rows: List<Row>): Map<String, List<Row>> {
        val map = HashMap<String, MutableList<Row>>()
        rows.forEach { row ->
            map.getOrPut(Numbers.key(row.number)) { mutableListOf() } += row
            row.normalized?.let { n -> Numbers.key(n).takeIf { it != Numbers.key(row.number) }?.let { map.getOrPut(it) { mutableListOf() } += row } }
        }
        return map
    }

    private fun kindOf(type: Int, label: String?): String =
        Phone.getTypeLabel(context.resources, type, label).toString()

    companion object {
        @Volatile
        private var instance: Lookup? = null

        fun get(context: Context): Lookup =
            instance ?: synchronized(this) { instance ?: Lookup(context.applicationContext).also { instance = it } }

        /** The country the phone is in, for reading national numbers: the network's, else the SIM's, else the language's. */
        fun country(context: Context): String {
            val tm = context.getSystemService(TelephonyManager::class.java)
            return listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
                .firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT) ?: "US"
        }

        /** A number as the phone writes one in its own country: "(555) 555-0143". */
        fun display(context: Context, number: String): String =
            PhoneNumberUtils.formatNumber(number, country(context)) ?: number
    }
}
