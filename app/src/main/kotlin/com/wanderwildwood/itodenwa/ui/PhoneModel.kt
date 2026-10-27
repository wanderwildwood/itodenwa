package com.wanderwildwood.itodenwa.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.itodenwa.data.BlockList
import com.wanderwildwood.itodenwa.data.Blocked
import com.wanderwildwood.itodenwa.data.LoggedCall
import com.wanderwildwood.itodenwa.data.Numbers
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.People
import com.wanderwildwood.itodenwa.data.Person
import com.wanderwildwood.itodenwa.data.Recent
import com.wanderwildwood.itodenwa.data.Recents
import com.wanderwildwood.itodenwa.data.Who
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.wanderwildwood.itodenwa.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The people and the call log, read again whenever either changes — a call ending, a contact
 * edited in Contacts — so every list shows the phone as it is now.
 */
class PhoneModel(app: Application) : AndroidViewModel(app) {
    var people by mutableStateOf<List<Person>>(emptyList())
        private set
    var calls by mutableStateOf<List<LoggedCall>>(emptyList())
        private set
    var recents by mutableStateOf<List<Recent>>(emptyList())
        private set
    /** The name for each number in the call log; a number missing here belongs to nobody in the contacts. */
    var names by mutableStateOf<Map<String, Who>>(emptyMap())
        private set
    var peopleLoaded by mutableStateOf(false)
        private set
    var callsLoaded by mutableStateOf(false)
        private set
    var mayReadContacts by mutableStateOf(false)
        private set
    var mayReadCalls by mutableStateOf(false)
        private set
    /** The block list, whole numbers and prefixes. */
    var blocked by mutableStateOf<List<Blocked>>(emptyList())
        private set

    /** The carrier says a voicemail is waiting: from the last signal kept, then live while the app is open. */
    var voicemailWaiting by mutableStateOf(false)
        private set

    private var mwiListener: TelephonyCallback? = null
    private var reading: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(soon = true)
    }
    private var watchingContacts = false
    private var watchingCalls = false

    fun start() {
        val app = getApplication<Application>()
        voicemailWaiting = Prefs(app).voicemailWaiting
        watchVoicemail()
        watch()
        refresh()
    }

    private class Waiting(val changed: (Boolean) -> Unit) : TelephonyCallback(), TelephonyCallback.MessageWaitingIndicatorListener {
        override fun onMessageWaitingIndicatorChanged(mwi: Boolean) = changed(mwi)
    }

    /** The message-waiting light, followed while the app is open once the phone state may be read. */
    private fun watchVoicemail() {
        val app = getApplication<Application>()
        if (mwiListener != null || app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val telephony = app.getSystemService(TelephonyManager::class.java) ?: return
        val l = Waiting { on ->
            voicemailWaiting = on
            Prefs(app).voicemailWaiting = on
        }
        mwiListener = l.takeIf { runCatching { telephony.registerTelephonyCallback(app.mainExecutor, it) }.isSuccess }
    }

    private fun watch() {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        // Android 12 refuses to watch what the app may not read, so each is watched once allowed.
        if (!watchingContacts && app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            watchingContacts = runCatching { resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer) }.isSuccess
        }
        if (!watchingCalls && app.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            watchingCalls = runCatching { resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) }.isSuccess
        }
    }

    /** Reads everything again. A burst of changes is read once, after it settles. */
    fun refresh(soon: Boolean = false) {
        val app = getApplication<Application>()
        reading?.cancel()
        reading = viewModelScope.launch {
            if (soon) delay(400)
            mayReadContacts = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            mayReadCalls = app.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            watch()
            val readPeople = withContext(Dispatchers.IO) {
                if (mayReadContacts) runCatching { People.read(app.contentResolver, app.resources) }.getOrDefault(emptyList()) else emptyList()
            }
            people = readPeople
            peopleLoaded = true
            val readCalls = withContext(Dispatchers.IO) {
                if (mayReadCalls) runCatching { Recents.read(app.contentResolver) }.getOrDefault(emptyList()) else emptyList()
            }
            val lookup = Lookup.get(app)
            val readNames = withContext(Dispatchers.IO) {
                readCalls.mapNotNull { it.number }.distinct().mapNotNull { n -> lookup.who(n)?.let { n to it } }.toMap()
            }
            // Calls turned away are on Blocked calls, and a blocked number's calls leave Recents.
            val list = BlockList(app)
            val (readBlocked, hidden) = withContext(Dispatchers.IO) {
                val all = runCatching { list.all() }.getOrDefault(emptyList())
                val whole = all.filter { !it.isPrefix }
                val prefixes = all.filter { it.isPrefix }.map { it.text }
                val gone = readCalls.mapNotNull { it.number }.distinct().filter { n ->
                    whole.any { Numbers.same(it.text, n) } || list.prefixFor(n, prefixes) != null
                }.toSet()
                all to gone
            }
            blocked = readBlocked
            calls = readCalls.filter { it.kind != LoggedCall.Kind.BLOCKED }
            recents = Recents.group(readCalls.filter { it.kind != LoggedCall.Kind.BLOCKED && it.number !in hidden })
            names = readNames
            callsLoaded = true
        }
    }

    fun person(id: Long): Person? = people.firstOrNull { it.id == id }

    /** The line of the block list that stops [number], if any. */
    fun blocking(number: String): Blocked? {
        val whole = blocked.filter { !it.isPrefix }
        whole.firstOrNull { Numbers.same(it.text, number) }?.let { return it }
        val prefixes = blocked.filter { it.isPrefix }.map { it.text }
        val p = BlockList(getApplication()).prefixFor(number, prefixes) ?: return null
        return blocked.firstOrNull { it.text == p }
    }

    override fun onCleared() {
        mwiListener?.let { runCatching { getApplication<Application>().getSystemService(TelephonyManager::class.java)?.unregisterTelephonyCallback(it) } }
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
