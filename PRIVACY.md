# Privacy

Handset sends nothing anywhere. It cannot: it has no permission to use the internet.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## What it asks for, and why

`app/src/main/AndroidManifest.xml` declares eleven permissions, and `INTERNET` is not one of them.

- **Contacts** (`READ_CONTACTS`) — to put a name to every number, list favorites and contacts,
  and tell whether a caller is in Contacts when a blocking rule asks. Read only: Handset never
  changes a contact. Android asks you for this.
- **Call log** (`READ_CALL_LOG`, `WRITE_CALL_LOG`) — Recents reads the phone's call log. Writing
  is for deleting a call from Recents, clearing the lot, and marking missed calls seen so the
  missed-call notification goes. The screener reads the log too, for "numbers you have called"
  and "the same number twice within three minutes".
- **Phone calls** (`CALL_PHONE`) — to place a call when Call is pressed.
- **Phone state** (`READ_PHONE_STATE`) — the names of the SIMs, for a phone with two and no
  default, the voicemail number the SIM gives, and whether the carrier says a voicemail is
  waiting (a small dot on the keypad; only that yes-or-no is kept).
- **Notifications** (`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`) — a call's notification,
  which is also how the call screen comes up over the lock screen, and the missed-call and
  voicemail notifications.
- **Keeping the screen awake** (`WAKE_LOCK`) — to turn the panel off at the ear during a call.
- **Running during a call** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`) — so the
  call keeps running and its notification stays while a call lasts. Nothing is sent.
- **Vibration** (`VIBRATE`) — only for the few calls the app rings itself: a caller not in
  Contacts with a ringtone of their own, and a favorite ringing through Do Not Disturb.

As the phone app it is also handed every call by Android, and asked about each call coming in
before it rings. Both happen on the phone. The calls themselves go through your mobile carrier,
as with any phone app; what the carrier keeps about them is covered by its own privacy policy.

## What it keeps

- **Settings**: the blocking rules, speed dial, the ringtone for unknown callers, and whether
  favorites ring through Do Not Disturb.
- **The block list.** Whole numbers go into Android's own blocked-numbers list, where the phone
  itself and messaging apps read them. Numbers ending in `*` are kept in the app's settings.
- **Blocked calls**: when a rule turned a call away or silenced it, the time, the number and the
  rule, kept for thirty days and at most two hundred calls.

All of it stays in the app's private storage and is left out of Android's backups
(`allowBackup="false"`). The block list goes into a file only when you export it, to the place
you choose.

## Checking this

Every line of the app is in this repository. Search it for `INTERNET` — the only hit is the
comment in the manifest saying it is not there.

<!-- shared-about privacy section -->
## Who we are

Handset is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Handset keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Handset is sold or shared.

Since Handset keeps what it stores only on your phone, you can exercise most of these rights yourself: delete calls in Recents or clear the list, unblock numbers in Blocked calls, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->
