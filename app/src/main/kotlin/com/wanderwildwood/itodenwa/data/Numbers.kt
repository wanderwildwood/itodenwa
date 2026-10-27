package com.wanderwildwood.itodenwa.data

/**
 * Whether two ways of writing a phone number are the same number.
 *
 * The phone's own apps leave a number nameless when the contact holds it written one way and the
 * call log another: "+1 555-555-0143" saved, "5555550143" calling, or "+44 7700 900123" saved and
 * "07700 900123" calling. Android's own lookup ([Lookup] asks it first) matches most of those;
 * this is what is asked when it does not.
 *
 * The rule: take the digits, drop the zeros a number starts with (the trunk "0" of a national
 * number, the "00" that stands for "+"), and compare the last of them — ten, or as many as the
 * shorter has, but never fewer than seven. A country code is then only a prefix that one side
 * has and the other has not, and a trunk zero is gone from both. Shorter numbers — 911, 112, a
 * short code — must be the same digits exactly.
 */
object Numbers {

    /** Below this many digits, a number is a short code and is only ever itself. */
    const val SHORTEST = 7

    /** The most digits worth comparing: past ten, every number on earth has started its country code. */
    const val LONGEST = 10

    /** The number's digits, in order, with nothing else. */
    fun digits(raw: String): String = raw.filter { it in '0'..'9' }

    /** The digits that say which number this is: with the trunk zero and the "00" gone. */
    fun significant(raw: String): String = digits(raw).trimStart('0')

    /**
     * How strongly [a] and [b] are the same number: the count of trailing digits they share
     * under the rule above, or 0 when they are different numbers. A larger count is a surer
     * match — ten digits agreeing beats seven.
     */
    fun match(a: String, b: String): Int {
        val x = significant(a)
        val y = significant(b)
        if (x.isEmpty() || y.isEmpty()) return 0
        if (x.length < SHORTEST || y.length < SHORTEST) {
            return if (digits(a) == digits(b)) digits(a).length else 0
        }
        val n = minOf(LONGEST, x.length, y.length)
        return if (x.takeLast(n) == y.takeLast(n)) n else 0
    }

    fun same(a: String, b: String): Boolean = match(a, b) > 0

    /** A key to file a number under for [match] to look through: its last seven significant digits. */
    fun key(raw: String): String {
        val s = significant(raw)
        return if (s.length < SHORTEST) "#" + digits(raw) else s.takeLast(SHORTEST)
    }

    /**
     * What a number could be dialled as from the keypad: digits, and a leading "+", "*" and "#"
     * kept where they stand. Anything else a person pastes in — spaces, dashes, brackets — goes.
     */
    fun dialable(raw: String): String = buildString {
        raw.forEach { c ->
            when {
                c in '0'..'9' || c == '*' || c == '#' || c == ',' || c == ';' -> append(c)
                c == '+' && isEmpty() -> append(c)
            }
        }
    }

    /**
     * What to take from a pasted text for the keypad: only the dialable characters, or nothing
     * when the text is not a number (too long, or fewer than three digits).
     */
    fun pasteable(raw: String?): String {
        val text = raw?.trim() ?: return ""
        if (text.length > 64) return ""
        val kept = dialable(text)
        return if (kept.count { it in '0'..'9' } >= 3) kept else ""
    }

    // ------------------------------------------------------------ the letters on the keys

    private val keyOf: Map<Char, Char> = buildMap {
        "abc".forEach { put(it, '2') }
        "def".forEach { put(it, '3') }
        "ghi".forEach { put(it, '4') }
        "jkl".forEach { put(it, '5') }
        "mno".forEach { put(it, '6') }
        "pqrs".forEach { put(it, '7') }
        "tuv".forEach { put(it, '8') }
        "wxyz".forEach { put(it, '9') }
    }

    /** A word as it is typed on the keys: "Ada" is 232. Letters with accents count as their plain letter. */
    fun onKeys(word: String): String = buildString {
        java.text.Normalizer.normalize(word.lowercase(), java.text.Normalizer.Form.NFD).forEach { c ->
            val k = keyOf[c]
            if (k != null) append(k) else if (c in '0'..'9') append(c)
        }
    }

    /**
     * Whether what was typed on the keypad finds this person: the start of any word of their
     * name on the keys, or any run of a number's digits.
     */
    fun typedFinds(typed: String, name: String, numbers: List<String>): Boolean {
        val t = digits(typed)
        if (t.isEmpty()) return false
        if (name.split(' ', '-', '.', ',').any { it.isNotEmpty() && onKeys(it).startsWith(t) }) return true
        if (t.length >= 3 && numbers.any { digits(it).contains(t) }) return true
        return false
    }
}
