package com.wanderwildwood.itodenwa.data

/** What to do with a call a rule is about. */
enum class Action {
    /** Ring as any call does. */
    RING,

    /** Let it come in without a sound or the call screen: it stays in Recents and goes to voicemail. */
    SILENCE,

    /** Turn it away. It is kept in the call log, and listed under Blocked calls. */
    DECLINE,
}

/** Which rule decided a call, so Blocked calls can say why. */
enum class Rule {
    /** A number, or the start of one, on the block list. */
    BLOCK_LIST,
    NOT_IN_CONTACTS,
    HIDDEN,
}

/** What the screener says about one call. */
data class Verdict(val action: Action, val rule: Rule? = null, val pattern: String? = null)

/** The rules as they are set. Every rule is off until it is turned on. */
data class RuleSet(
    val unknown: Action = Action.RING,
    val hidden: Action = Action.RING,
    val alwaysFavourites: Boolean = false,
    val alwaysCalled: Boolean = false,
    val alwaysRepeat: Boolean = false,
)

/** What is known about the caller when the rules are asked. */
data class Caller(
    /** Null for a hidden number. */
    val number: String?,
    /**
     * True for someone in the contacts, false only when the contacts were read and the number is
     * definitely not in them, null when they could not be read. A null is never treated as
     * "not in Contacts": a lookup that failed must not turn a call away.
     */
    val inContacts: Boolean?,
    val starred: Boolean = false,
    val calledBefore: Boolean = false,
    /** The same number called within the last three minutes. */
    val repeat: Boolean = false,
    /** The block-list prefix this number starts with, if any. */
    val prefix: String? = null,
    /** An emergency call went out a short time ago: nothing is turned away, so a call back gets through. */
    val afterEmergency: Boolean = false,
)

/**
 * The rules, in order. A pure function, so the order can be tested without a phone.
 *
 * 1. Shortly after an emergency call, every call rings.
 * 2. A favourite, or a number you have called, rings past every rule below, where "Always ring"
 *    says so.
 * 3. A prefix on the block list declines. (Whole numbers on the list are turned away by the
 *    phone itself, before this is asked.) A second call within three minutes does not get past
 *    the list: a caller put on it calling twice is still a caller put on it.
 * 4. A second call within three minutes rings past the rules below, where "Always ring" says so.
 * 5. A hidden number gets the hidden-number rule.
 * 6. A number definitely not in the contacts gets that rule. Anyone in them, or a number whose
 *    lookup failed, rings.
 */
object Rules {
    fun decide(set: RuleSet, caller: Caller): Verdict {
        if (caller.afterEmergency) return Verdict(Action.RING)
        val known = (set.alwaysFavourites && caller.starred) || (set.alwaysCalled && caller.calledBefore)
        if (known) return Verdict(Action.RING)
        if (caller.prefix != null) return Verdict(Action.DECLINE, Rule.BLOCK_LIST, caller.prefix)
        if (set.alwaysRepeat && caller.repeat) return Verdict(Action.RING)
        if (caller.number == null) {
            return if (set.hidden == Action.RING) Verdict(Action.RING) else Verdict(set.hidden, Rule.HIDDEN)
        }
        if (caller.inContacts == false && set.unknown != Action.RING) return Verdict(set.unknown, Rule.NOT_IN_CONTACTS)
        return Verdict(Action.RING)
    }

    /** Whether any rule could turn a call away or silence it: when none can, nothing is asked at all. */
    fun anyOn(set: RuleSet, prefixes: List<String>): Boolean =
        set.unknown != Action.RING || set.hidden != Action.RING || prefixes.isNotEmpty()
}
