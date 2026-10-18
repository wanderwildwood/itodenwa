package com.wanderwildwood.itodenwa.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.InCallActivity
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.call.CallManager
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.CallLogs
import com.wanderwildwood.itodenwa.data.DuraSpeed
import com.wanderwildwood.itodenwa.data.Prefs
import com.wanderwildwood.itodenwa.data.Voicemail

/** Where the app was asked to open: a tab, and a number to put on the keypad. */
data class Opening(val tab: Int, val number: String? = null, val serial: Long = System.nanoTime())

const val TAB_FAVOURITES = 0
const val TAB_RECENTS = 1
const val TAB_CONTACTS = 2
const val TAB_KEYPAD = 3

/** A page open over the tabs. */
sealed class Page {
    data class OfNumber(val number: String) : Page()
    data class OfPerson(val id: Long) : Page()
    data object Settings : Page()
    data object Blocking : Page()
    data object BlockedNumbers : Page()
    data object BlockedCalls : Page()
    data object Ringing : Page()
    data object SpeedDial : Page()
    data class SpeedDialPick(val key: Int) : Page()
}

/**
 * The whole app: four tabs as the phone's own Phone app has them — Favourites, Recents,
 * Contacts, Keypad — and a page for a number or a person opening over them. Settings is behind
 * the mark at the top right; Recents adds a ⋮ beside it for voicemail, blocked calls and
 * clearing the list.
 *
 * Nothing in a list calls. A press on a recent call, a favourite or a contact opens their page,
 * and the call is placed from the Call button there, or from the keypad's.
 */
@Composable
fun PhoneApp(opening: Opening?, onOpened: () -> Unit) {
    val model: PhoneModel = viewModel()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(TAB_RECENTS) }
    // Pages open over the tabs, the last in front; Back closes the one in front.
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var typed by rememberSaveable { mutableStateOf("") }
    var isPhoneApp by remember { mutableStateOf(Actions.isPhoneApp(context)) }
    var recentsMenu by remember { mutableStateOf(false) }
    var askDuraSpeed by remember { mutableStateOf(false) }
    var resumed by remember { mutableIntStateOf(0) }

    var confirmDuraSpeed by remember { mutableStateOf(false) }

    // Once the phone app, on a Kompakt: the one step for DuraSpeed, however Phone became the
    // phone app — through the line here or in the phone's own Default apps — asked until it is
    // answered. Coming back from DuraSpeed, opened from here, asks whether Phone is on in it now.
    fun maybeAskDuraSpeed() {
        val prefs = Prefs(context)
        if (!DuraSpeed.atRisk(context)) return
        if (DuraSpeed.wasOpened(context)) confirmDuraSpeed = true
        else if (isPhoneApp && !prefs.duraSpeedAnswered) askDuraSpeed = true
    }

    fun open(p: Page) {
        pages = pages + p
    }
    fun back() {
        pages = pages.dropLast(1)
    }

    LifecycleResumeEffect(Unit) {
        isPhoneApp = Actions.isPhoneApp(context)
        maybeAskDuraSpeed()
        model.start()
        resumed++
        onPauseOrDispose { }
    }

    // Recents in front, and really seen — the window has the focus and the phone is unlocked,
    // not merely resumed behind the lock screen when a call screen closes: its missed calls
    // have been seen.
    val view = androidx.compose.ui.platform.LocalView.current
    var focused by remember { mutableStateOf(view.hasWindowFocus()) }
    androidx.compose.runtime.DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    LaunchedEffect(tab, pages.isEmpty(), resumed, focused) {
        val locked = context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        if (tab == TAB_RECENTS && pages.isEmpty() && focused && !locked) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CallLogs.markMissedSeen(context) }
        }
    }

    LaunchedEffect(opening) {
        val o = opening ?: return@LaunchedEffect
        pages = emptyList()
        tab = o.tab
        if (o.number != null) typed = o.number
        onOpened()
    }

    val asked = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isPhoneApp = Actions.isPhoneApp(context)
        model.refresh()
        maybeAskDuraSpeed()
    }

    when (val p = pages.lastOrNull()) {
        is Page.OfNumber -> NumberPage(model, p.number, onBack = ::back)
        is Page.OfPerson -> PersonPage(model, p.id, onBack = ::back)
        Page.Settings -> SettingsScreen(model, onBack = ::back, onOpen = ::open, onDuraSpeed = { askDuraSpeed = true })
        Page.Ringing -> RingingScreen(onBack = ::back)
        Page.Blocking -> BlockingScreen(model, onBack = ::back, onOpen = ::open)
        Page.BlockedNumbers -> BlockedNumbersScreen(model, onBack = ::back)
        Page.BlockedCalls -> BlockedCallsScreen(model, onBack = ::back)
        Page.SpeedDial -> SpeedDialScreen(model, onBack = ::back, onPick = { open(Page.SpeedDialPick(it)) })
        is Page.SpeedDialPick -> SpeedDialPicker(model, p.key, onBack = ::back)
        null -> {
            BackHandler(enabled = tab != TAB_RECENTS) { tab = TAB_RECENTS }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    Bar(
                        title = { BarTitle(stringResource(R.string.app_name)) },
                        actions = {
                            if (tab == TAB_RECENTS) BarButton(Icons.More, stringResource(R.string.cd_more)) { recentsMenu = true }
                            BarButton(Icons.Settings, stringResource(R.string.cd_settings)) { open(Page.Settings) }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    // A call going on: the way back to it, at the top, only while there is one.
                    val changes = CallManager.changes.intValue
                    if (remember(changes) { CallManager.hasCalls() }) {
                        Banner(stringResource(R.string.banner_return_to_call)) {
                            Actions.start(context, InCallActivity.intent(context))
                        }
                    } else if (!isPhoneApp) {
                        Banner(stringResource(R.string.banner_make_phone_app), stringResource(R.string.banner_make_phone_app_note)) {
                            Actions.askToBePhoneApp(context)?.let { asked.launch(it) }
                        }
                    }
                    StoneTabs(
                        labels = listOf(R.string.tab_favourites, R.string.tab_recents, R.string.tab_contacts, R.string.tab_keypad).map { stringResource(it) },
                        selected = tab,
                        onSelect = { tab = it },
                    )
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            TAB_FAVOURITES -> FavouritesList(model) { open(Page.OfPerson(it.id)) }
                            TAB_RECENTS -> RecentsList(model) { number -> open(Page.OfNumber(number)) }
                            TAB_CONTACTS -> ContactsList(model) { open(Page.OfPerson(it.id)) }
                            else -> KeypadScreen(model, typed, onTyped = { typed = it }, onSpeedDialUnset = { open(Page.SpeedDial) })
                        }
                    }
                }
            }
        }
    }
    if (recentsMenu) RecentsMenu(model, onDismiss = { recentsMenu = false }, onBlockedCalls = {
        recentsMenu = false
        open(Page.BlockedCalls)
    })
    if (askDuraSpeed) DuraSpeedAsk(onDismiss = {
        askDuraSpeed = false
        Prefs(context).duraSpeedAnswered = true
    })
    if (confirmDuraSpeed) DuraSpeedConfirm(onDismiss = { confirmDuraSpeed = false })
}

/** A line across the top that asks for one thing, gone once it is done. */
@Composable
private fun Banner(text: String, note: String? = null, onPress: () -> Unit) {
    NameRow(AnnotatedString(text), note = note, onPress = onPress)
}

/**
 * Recents' ⋮: voicemail, the calls the rules turned away, and clearing the whole list — last, and
 * asking first by changing what it says.
 */
@Composable
private fun RecentsMenu(model: PhoneModel, onDismiss: () -> Unit, onBlockedCalls: () -> Unit) {
    val context = LocalContext.current
    val cannotClear = stringResource(R.string.recents_cannot_delete)
    EInkDialog(onDismiss = onDismiss) {
        NameRow(AnnotatedString(stringResource(R.string.recents_voicemail))) {
            onDismiss()
            if (!Voicemail.call(context)) Actions.start(context, Voicemail.settings())
        }
        NameRow(AnnotatedString(stringResource(R.string.blocked_calls)), onPress = onBlockedCalls)
        if (model.recents.isNotEmpty()) {
            val (armed, press) = rememberArmed(Unit) {
                onDismiss()
                if (!CallLogs.clear(context)) android.widget.Toast.makeText(context, cannotClear, android.widget.Toast.LENGTH_SHORT).show()
                model.refresh()
            }
            NameRow(AnnotatedString(stringResource(if (armed) R.string.recents_clear_armed else R.string.recents_clear)), onPress = press)
        }
    }
}

/**
 * Asked on becoming the phone app on a Kompakt, in Messaging's words: DuraSpeed can stop the app,
 * and calls with it. The same is behind DuraSpeed in Settings until it is done.
 */
@Composable
private fun DuraSpeedAsk(onDismiss: () -> Unit) {
    val context = LocalContext.current
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.settings_duraspeed), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TextMMD(text = stringResource(R.string.duraspeed_line, stringResource(R.string.app_label_full)), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FootButton(stringResource(R.string.duraspeed_later), Modifier.weight(1f), onClick = onDismiss)
            FootButton(stringResource(R.string.duraspeed_open), Modifier.weight(1f)) {
                onDismiss()
                if (Actions.start(context, DuraSpeed.appInfo())) DuraSpeed.setOpened(context, true)
            }
        }
    }
}

/** Back from DuraSpeed: is Phone switched on in it now? The person's word is all there is to go on. */
@Composable
private fun DuraSpeedConfirm(onDismiss: () -> Unit) {
    val context = LocalContext.current
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.duraspeed_ask_done), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FootButton(stringResource(R.string.duraspeed_not_yet), Modifier.weight(1f)) {
                DuraSpeed.setOpened(context, false)
                onDismiss()
            }
            FootButton(stringResource(R.string.duraspeed_yes), Modifier.weight(1f)) {
                DuraSpeed.done(context)
                onDismiss()
            }
        }
    }
}
