package com.wanderwildwood.itodenwa.data

import android.content.ContentResolver
import android.content.res.Resources
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import com.wanderwildwood.itodenwa.ui.jumpLetterOf

/** A number on a person's card, and what kind it is there. */
data class CardNumber(val value: String, val kind: String)

/** Someone in the contacts with at least one number. */
data class Person(
    val id: Long,
    val lookupKey: String,
    val name: String,
    /** Their surname, to set in bold as the phone's own lists do; empty when the card has none. */
    val family: String,
    val starred: Boolean,
    val numbers: List<CardNumber>,
)

/**
 * The people this app can call: everyone in the contacts with a number, read straight from the
 * phone's contacts. Nothing is kept here; adding and changing people is the Contacts app's job.
 */
object People {

    fun read(resolver: ContentResolver, res: Resources): List<Person> {
        val families = HashMap<Long, String>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(StructuredName.CONTACT_ID, StructuredName.FAMILY_NAME),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val family = c.getString(1)
                if (!family.isNullOrBlank()) families.putIfAbsent(c.getLong(0), family)
            }
        }

        val byId = LinkedHashMap<Long, Person>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.STARRED, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            null,
            null,
            "${Phone.SORT_KEY_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(4)?.takeIf { it.isNotBlank() } ?: continue
                val kind = Phone.getTypeLabel(res, c.getInt(5), c.getString(6)).toString()
                val known = byId[id]
                if (known == null) {
                    byId[id] = Person(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), families[id].orEmpty(), c.getInt(3) == 1, listOf(CardNumber(number, kind)))
                } else {
                    byId[id] = known.copy(numbers = known.numbers + CardNumber(number, kind))
                }
            }
        }
        // The store puts names that start with a figure or a sign after Z; they go first, under
        // #, as Contacts lists them. Otherwise the store's order, accents and case as it sorts them.
        return byId.values.map { it.copy(numbers = distinct(it.numbers)) }
            .sortedBy { jumpLetterOf(it.name) != "#" }
    }

    /**
     * A person's numbers, each once. A person joined from two places — the phone's copy and a
     * synced copy — often holds the same number twice, written two ways.
     */
    fun distinct(numbers: List<CardNumber>): List<CardNumber> {
        val out = mutableListOf<CardNumber>()
        for (n in numbers) if (out.none { Numbers.match(it.value, n.value) >= Numbers.SHORTEST }) out += n
        return out
    }
}
