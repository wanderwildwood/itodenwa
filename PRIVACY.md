# Privacy

Phone sends nothing anywhere. It cannot: it has no permission to use the internet.

That is the whole policy. The rest of this page is the evidence for it, because a privacy
policy that cannot be checked is just a promise.

## What it asks for, and why

`app/src/main/AndroidManifest.xml` declares eleven permissions, and `INTERNET` is not one of them.

- **Contacts** (`READ_CONTACTS`) — to put a name to every number, list favourites and contacts,
  and tell whether a caller is in Contacts when a blocking rule asks. Read only: Phone never
  changes a contact. Android asks you for this.
- **Call log** (`READ_CALL_LOG`, `WRITE_CALL_LOG`) — Recents reads the phone's call log. Writing
  is for deleting a call from Recents, clearing the lot, and marking missed calls seen so the
  missed-call notification goes. The screener reads the log too, for "numbers you have called"
  and "the same number twice within three minutes".
- **Phone calls** (`CALL_PHONE`) — to place a call when Call is pressed.
- **Phone state** (`READ_PHONE_STATE`) — the names of the SIMs, for a phone with two and no
  default, and the voicemail number the SIM gives.
- **Notifications** (`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`) — a call's notification,
  which is also how the call screen comes up over the lock screen, and the missed-call and
  voicemail notifications.
- **Keeping the screen awake** (`WAKE_LOCK`) — to turn the panel off at the ear during a call.
- **Running during a call** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`) — while a
  call lasts, the call's notification is held in the foreground, which is what puts the call in
  the status bar, where pressing it brings the call screen back.
- **Vibration** (`VIBRATE`) — only for the few calls the app rings itself: a caller not in
  Contacts with a ringtone of their own, and a favourite ringing through Do Not Disturb.

As the phone app it is also handed every call by Android, and asked about each call coming in
before it rings. Both happen on the phone.

## What it keeps

- **Settings**: the blocking rules, speed dial, the ringtone for unknown callers, and whether
  favourites ring through Do Not Disturb.
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
