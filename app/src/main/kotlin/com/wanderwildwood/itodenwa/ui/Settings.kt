package com.wanderwildwood.itodenwa.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.data.Action
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.BlockList
import com.wanderwildwood.itodenwa.data.Blocked
import com.wanderwildwood.itodenwa.data.DuraSpeed
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Patterns
import com.wanderwildwood.itodenwa.data.Person
import com.wanderwildwood.itodenwa.data.Prefs
import com.wanderwildwood.itodenwa.data.Rule
import com.wanderwildwood.itodenwa.data.Rules
import com.wanderwildwood.itodenwa.data.Screened
import com.wanderwildwood.itodenwa.data.Voicemail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A page of settings: the library's bar with a back arrow, and its rows. */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    notice: String? = null,
    onNoticeSeen: () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(title) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = actions,
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it, onNoticeSeen) } },
    ) { padding ->
        PagedColumn(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface),
            content = content,
        )
    }
}

/**
 * A settings row as Messaging draws its: the label, the value under it when there is one, and at
 * the end an arrow where a press opens something — a page, a picker, a dialog — or the switch.
 * No rule between rows, the same room around them, so Phone's settings and Messaging's read as a
 * pair. The value is a size under the label, as Messaging's is.
 */
@Composable
internal fun SettingRow(
    title: String,
    summary: String? = null,
    titleLines: Int = 2,
    bold: Boolean = false,
    opens: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** For a switch: whether it is on, so the row says so to TalkBack as a switch. */
    checked: Boolean? = null,
    onPress: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let {
                when {
                    onPress == null -> it
                    checked != null -> it.toggleable(value = checked, role = Role.Switch, onValueChange = { onPress() })
                    else -> it.clickable(onClick = onPress)
                }
            },
    ) {
        Column(Modifier.weight(1f).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)) {
            TextMMD(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else null,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrEmpty()) {
                TextMMD(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp)) { trailing() }
        } else if (opens && onPress != null) {
            androidx.compose.material3.Icon(
                imageVector = Icons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
            )
        }
    }
}

/** A heading inside a settings page: the name in bold, set apart by space, no rule above it. */
@Composable
internal fun SettingsHeading(text: String) {
    Spacer(Modifier.height(8.dp))
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** A row with MMD's switch at its end; the row takes the press. */
@Composable
internal fun SwitchRow(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, summary = note, opens = false, checked = checked, trailing = {
        SwitchMMD(checked = checked, onCheckedChange = null)
    }, onPress = { onChange(!checked) })
}

/** One of a few choices in a dialog, pressed in place: MMD's radio button, the row taking the press. */
@Composable
private fun ChoiceRow(title: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButtonMMD(selected = chosen, onClick = null)
        Spacer(Modifier.width(8.dp))
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun actionWord(a: Action): String = stringResource(
    when (a) {
        Action.RING -> R.string.rule_ring
        Action.SILENCE -> R.string.rule_silence
        Action.DECLINE -> R.string.rule_decline
    },
)

/**
 * Settings, behind the cog, laid out as Messaging's are, the two being a pair: a list of doors —
 * Blocking, Speed dial, Voicemail, Ringing — then DuraSpeed on a Kompakt, and last the way to
 * stop being the phone app, as it is the one that undoes something. About is the i in the bar.
 */
@Composable
fun SettingsScreen(model: PhoneModel, onBack: () -> Unit, onOpen: (Page) -> Unit, onDuraSpeed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var v by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isPhoneApp by remember { mutableStateOf(Actions.isPhoneApp(context)) }
    var voicemail by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        isPhoneApp = Actions.isPhoneApp(context)
        voicemail = Voicemail.number(context)
        v++
        onPauseOrDispose { }
    }
    val duraSpeed = remember(v, DuraSpeed.changes.intValue) { DuraSpeed.atRisk(context) }
    val nothing = stringResource(R.string.nothing_opens)

    val rules = remember(v) { prefs.rules }
    val blockingOn = remember(v, model.blocked) { Rules.anyOn(rules, model.blocked.filter { it.isPrefix }.map { it.text }) || model.blocked.isNotEmpty() }
    val speedSet = remember(v) { Prefs.SPEED_KEYS.count { prefs.speedDial(it) != null } }

    SettingsPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        notice = notice,
        onNoticeSeen = { notice = null },
        actions = { BarButton(Icons.Info, stringResource(R.string.cd_about), 22.dp) { onOpen(Page.About) } },
    ) {
        item(key = "top") { Spacer(Modifier.height(8.dp)) }
        item(key = "blocking") {
            SettingRow(
                stringResource(R.string.settings_blocking),
                summary = stringResource(if (blockingOn) R.string.settings_on else R.string.settings_off),
                onPress = { onOpen(Page.Blocking) },
            )
        }
        item(key = "speed") {
            SettingRow(
                stringResource(R.string.settings_speed_dial),
                summary = if (speedSet == 0) stringResource(R.string.settings_speed_none) else pluralStringResource(R.plurals.settings_speed_some, speedSet, speedSet),
                onPress = { onOpen(Page.SpeedDial) },
            )
        }
        item(key = "voicemail") {
            SettingRow(
                stringResource(R.string.settings_voicemail),
                summary = voicemail?.let { Lookup.display(context, it) } ?: stringResource(R.string.settings_voicemail_none),
                onPress = { if (!Actions.start(context, Voicemail.settings())) notice = nothing },
            )
        }
        item(key = "ringing") {
            SettingRow(stringResource(R.string.settings_ringing), onPress = { onOpen(Page.Ringing) })
        }
        item(key = "vibrate") {
            val prefs = remember { Prefs(context) }
            var vibrate by remember { mutableStateOf(prefs.vibrate) }
            SwitchRow(stringResource(R.string.settings_vibrate), vibrate, note = stringResource(R.string.settings_vibrate_note)) {
                vibrate = it
                prefs.vibrate = it
            }
        }
        if (DuraSpeed.present(context)) {
            // Until the person says Phone is switched on in DuraSpeed, the row carries the one
            // step left and opens the explanation. After that it is a plain door to DuraSpeed,
            // kept for the day its list needs looking at again, with nothing more to remind.
            item(key = "duraspeed") {
                SettingRow(
                    stringResource(R.string.settings_duraspeed),
                    summary = if (duraSpeed) stringResource(R.string.settings_duraspeed_todo) else null,
                    onPress = if (duraSpeed) {
                        onDuraSpeed
                    } else {
                        { if (!Actions.start(context, DuraSpeed.appInfo())) notice = nothing }
                    },
                )
            }
        }
        if (isPhoneApp && !Actions.canFillScreen(context)) {
            item(key = "fullscreen") {
                SettingRow(
                    stringResource(R.string.banner_full_screen),
                    summary = stringResource(R.string.banner_full_screen_note),
                    onPress = { if (!Actions.start(context, Actions.fullScreenSettings(context))) notice = nothing },
                )
            }
        }
        if (isPhoneApp) {
            item(key = "stop") {
                SettingRow(stringResource(R.string.settings_stop_phone_app), onPress = {
                    if (!Actions.start(context, Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) notice = nothing
                })
            }
        }
    }
}

/**
 * Ringing, as Messaging's Notifications page is to texts: the ringtone for numbers not in
 * Contacts, and who rings through Do Not Disturb.
 */
@Composable
fun RingingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var v by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val nothing = stringResource(R.string.nothing_opens)
    val sameTone = stringResource(R.string.settings_tone_same)
    val tone = remember(v) { prefs.unknownRingtone }
    val toneName = remember(tone) { tone?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() } ?: sameTone }

    var picking by remember { mutableStateOf(false) }
    if (picking) {
        ToneScreen(tone = tone, onPick = { prefs.unknownRingtone = it; v++ }, onBack = { picking = false })
        return
    }

    SettingsPage(stringResource(R.string.settings_ringing), onBack, notice = notice, onNoticeSeen = { notice = null }) {
        item(key = "top") { Spacer(Modifier.height(8.dp)) }
        item(key = "tone-unknown") {
            SettingRow(stringResource(R.string.settings_tone_unknown), summary = toneName, onPress = { picking = true })
        }
        item(key = "quiet") { SettingsHeading(stringResource(R.string.settings_quiet)) }
        item(key = "quiet-favourites") {
            SwitchRow(stringResource(R.string.settings_quiet_favourites), remember(v) { prefs.favouritesThroughQuiet }, note = stringResource(R.string.settings_favourites_note)) {
                prefs.favouritesThroughQuiet = it
                v++
            }
        }
        item(key = "quiet-repeat") {
            SwitchRow(stringResource(R.string.settings_quiet_repeat), remember(v) { prefs.repeatThroughQuiet }, note = stringResource(R.string.settings_repeat_note)) {
                prefs.repeatThroughQuiet = it
                v++
            }
        }
    }
}

/**
 * The ringtone for numbers not in Contacts, chosen from the phone's own ringtones in a list of
 * this app's own: a press chooses it and plays a few seconds of it. Android's picker is not
 * used, because on a Kompakt it has no OK button and a sound picked there is lost on going back.
 */
@Composable
private fun ToneScreen(tone: Uri?, onPick: (Uri?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val tones = remember {
        buildList {
            val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_RINGTONE) }
            rm.cursor?.use { c ->
                while (c.moveToNext()) add(c.getString(RingtoneManager.TITLE_COLUMN_INDEX) to rm.getRingtoneUri(c.position))
            }
        }
    }
    var playing by remember { mutableStateOf<android.media.Ringtone?>(null) }
    fun preview(uri: Uri?) {
        playing?.stop()
        playing = (uri ?: AndroidSettings.System.DEFAULT_RINGTONE_URI)?.let { u ->
            RingtoneManager.getRingtone(context, u)?.apply {
                audioAttributes = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
                play()
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(playing) {
        if (playing != null) {
            kotlinx.coroutines.delay(5_000L)
            playing?.stop()
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { playing?.stop() } }

    SettingsPage(stringResource(R.string.settings_tone_unknown), { playing?.stop(); onBack() }) {
        item(key = "same") {
            Box(Modifier.padding(horizontal = 24.dp)) {
                ChoiceRow(stringResource(R.string.settings_tone_same), tone == null) { onPick(null); preview(null) }
            }
        }
        tones.forEachIndexed { i, (title, uri) ->
            item(key = "t$i") {
                Box(Modifier.padding(horizontal = 24.dp)) {
                    ChoiceRow(title, tone == uri) { onPick(uri); preview(uri) }
                }
            }
        }
    }
}

/**
 * Blocking: what happens to numbers not in Contacts and to hidden numbers, who always rings, and
 * the block list. Everything is off until it is turned on.
 */
@Composable
fun BlockingScreen(model: PhoneModel, onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var v by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf<Boolean?>(null) } // true: unknown numbers, false: hidden
    var isPhoneApp by remember { mutableStateOf(Actions.isPhoneApp(context)) }
    var mayReadContacts by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        isPhoneApp = Actions.isPhoneApp(context)
        mayReadContacts = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        model.refresh()
        v++
        onPauseOrDispose { }
    }
    val askContacts = rememberPermissionAsker(Manifest.permission.READ_CONTACTS) { granted, _ -> mayReadContacts = granted }
    var roleBlocked by rememberSaveable { mutableStateOf(false) }
    val askPhoneApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isPhoneApp = Actions.isPhoneApp(context)
        roleBlocked = !isPhoneApp && RestrictedSettings.blocked(context, since = 35)
    }
    val rules = remember(v) { prefs.rules }
    val elsewhere = remember(v, isPhoneApp) { screenedElsewhere(context, prefs) }

    SettingsPage(stringResource(R.string.settings_blocking), onBack) {
        item(key = "top") { Spacer(Modifier.height(8.dp)) }
        if (!isPhoneApp) {
            item(key = "not-phone") {
                SettingRow(stringResource(R.string.blocking_not_phone_app), titleLines = 4, opens = false, onPress = {
                    Actions.askToBePhoneApp(context)?.let { askPhoneApp.launch(it) }
                })
            }
            if (roleBlocked) item(key = "restricted") { RestrictedHint(Modifier.padding(horizontal = 16.dp)) }
        } else if (elsewhere != null) {
            item(key = "elsewhere") {
                SettingRow(elsewhere, titleLines = 6, opens = false, onPress = {
                    Actions.start(context, Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                })
            }
        }
        if (!mayReadContacts && rules.unknown != Action.RING) {
            item(key = "contacts") {
                SettingRow(stringResource(R.string.blocking_needs_contacts), titleLines = 4, opens = false, onPress = { askContacts() })
            }
        }
        item(key = "unknown") {
            SettingRow(stringResource(R.string.blocking_unknown), summary = actionWord(rules.unknown), onPress = { choosing = true })
        }
        item(key = "hidden") {
            SettingRow(stringResource(R.string.blocking_hidden), summary = actionWord(rules.hidden), onPress = { choosing = false })
        }

        item(key = "always") { SettingsHeading(stringResource(R.string.blocking_always)) }
        item(key = "always-favourites") {
            SwitchRow(stringResource(R.string.blocking_always_favourites), rules.alwaysFavourites) {
                prefs.rules = rules.copy(alwaysFavourites = it)
                v++
            }
        }
        item(key = "always-called") {
            SwitchRow(stringResource(R.string.blocking_always_called), rules.alwaysCalled) {
                prefs.rules = rules.copy(alwaysCalled = it)
                v++
            }
        }
        item(key = "always-repeat") {
            SwitchRow(stringResource(R.string.blocking_always_repeat), rules.alwaysRepeat) {
                prefs.rules = rules.copy(alwaysRepeat = it)
                v++
            }
        }

        item(key = "list") { SettingsHeading(stringResource(R.string.blocking_list)) }
        item(key = "numbers") {
            val n = model.blocked.size
            SettingRow(
                stringResource(R.string.blocking_numbers),
                summary = if (n == 0) stringResource(R.string.blocking_numbers_none) else pluralStringResource(R.plurals.blocking_numbers_count, n, n),
                onPress = { onOpen(Page.BlockedNumbers) },
            )
        }
    }

    choosing?.let { unknown ->
        val now = if (unknown) rules.unknown else rules.hidden
        EInkDialog(onDismiss = { choosing = null }) {
            TextMMD(
                text = stringResource(if (unknown) R.string.blocking_unknown else R.string.blocking_hidden),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Action.entries.forEach { a ->
                ChoiceRow(actionWord(a), a == now) {
                    prefs.rules = if (unknown) rules.copy(unknown = a) else rules.copy(hidden = a)
                    choosing = null
                    v++
                }
            }
            Spacer(Modifier.height(8.dp))
            TextMMD(text = stringResource(R.string.blocking_choice_note), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * The line saying another app screens calls in Phone's place, or null when nothing says so.
 * Android tells no app who holds the caller ID and spam role, so this goes by what happened: the
 * last call that came in was never put to Phone's screener. Before any call has, it goes by
 * which other apps could screen calls at all.
 */
private fun screenedElsewhere(context: android.content.Context, prefs: Prefs): String? {
    val roles = context.getSystemService(RoleManager::class.java)
    if (roles?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true) return null
    val others = runCatching {
        context.packageManager.queryIntentServices(Intent("android.telecom.CallScreeningService"), 0)
            .map { it.serviceInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { pkg -> context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
    }.getOrDefault(emptyList())
    return when (prefs.lastCallScreened) {
        false -> if (others.isEmpty()) context.getString(R.string.blocking_elsewhere)
        else context.getString(R.string.blocking_elsewhere_named, others.joinToString(", "))
        null -> others.takeIf { it.isNotEmpty() }?.let { context.getString(R.string.blocking_elsewhere_maybe, it.joinToString(", ")) }
        true -> null
    }
}

/**
 * The block list: whole numbers, and starts of numbers ending in `*`. A press on one unblocks it,
 * asking first. The list goes out to a file and comes back from one in Fossify's format.
 */
@Composable
fun BlockedNumbersScreen(model: PhoneModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val list = remember { BlockList(context) }
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val canWrite = remember(model.blocked) { list.canWrite() }

    fun refresh() = model.refresh()
    val doneExport = stringResource(R.string.blocked_exported)
    val failed = stringResource(R.string.blocked_file_failed)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Patterns.write(list.all().map { b -> b.text }).toByteArray()) } != null
                }.getOrDefault(false)
            }
            notice = if (ok) doneExport else failed
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching -1
                    Patterns.read(text).count { list.add(it) }
                }.getOrDefault(-1)
            }
            notice = if (count < 0) failed else context.resources.getQuantityString(R.plurals.blocked_imported, count, count)
            refresh()
        }
    }

    val state = rememberLazyListState()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.blocking_numbers)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        PagedColumn(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface), state = state) {
            if (!canWrite) {
                item(key = "cannot") { SettingRow(stringResource(R.string.blocked_needs_phone_app), titleLines = 4, onPress = null) }
            }
            item(key = "add") { SettingRow(stringResource(R.string.blocked_add), onPress = { adding = true }) }
            items(model.blocked, key = { "${it.id}:${it.text}" }) { b -> BlockedRow(b) { list.remove(b); refresh() } }
            item(key = "file") { SettingsHeading(stringResource(R.string.blocked_file)) }
            item(key = "import") { SettingRow(stringResource(R.string.blocked_import), onPress = { import.launch(arrayOf("text/*", "application/octet-stream")) }) }
            item(key = "export") {
                SettingRow(stringResource(R.string.blocked_export), onPress = { export.launch("blocked-numbers.txt") })
            }
        }
    }

    if (adding) {
        var typed by remember { mutableStateOf("") }
        val cannot = stringResource(R.string.blocked_add_failed)
        val already = stringResource(R.string.blocked_already)
        EInkDialog(onDismiss = { adding = false }) {
            TextMMD(text = stringResource(R.string.blocked_add), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            BareField(typed, { typed = it }, Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone, hint = stringResource(R.string.blocked_add_hint), focusNow = true)
            Spacer(Modifier.height(6.dp))
            TextMMD(text = stringResource(R.string.blocked_add_note), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                FootButton(stringResource(R.string.cancel), Modifier.weight(1f)) { adding = false }
                Spacer(Modifier.width(12.dp))
                FootButton(stringResource(R.string.block), Modifier.weight(1f), enabled = Patterns.tidy(typed) != null) {
                    if (list.has(typed)) {
                        notice = already
                        adding = false
                    } else if (list.add(typed)) {
                        adding = false
                        refresh()
                    } else {
                        notice = cannot
                        adding = false
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(b: Blocked, onUnblock: () -> Unit) {
    val context = LocalContext.current
    val (armed, press) = rememberArmed(b) { onUnblock() }
    val shown = if (b.isPrefix) b.text else Lookup.display(context, b.text)
    PlainRow(
        title = if (armed) stringResource(R.string.unblock_armed) else shown,
        note = if (armed) shown else if (b.isPrefix) stringResource(R.string.blocked_prefix_note, b.text.trimEnd('*')) else null,
        bold = armed,
        onPress = press,
    )
}

/** One call a rule acted on, or the phone's own list did, as Blocked calls lists it. */
data class BlockedCallRow(val at: Long, val number: String?, val why: String)

/**
 * Blocked calls, from Recents' ⋮: every call turned away or silenced in the last thirty days,
 * newest first — when, who, and which rule did it. A press offers Unblock, where the block list
 * is why, and Add to Contacts.
 */
@Composable
fun BlockedCallsScreen(model: PhoneModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<BlockedCallRow>?>(null) }
    var menu by remember { mutableStateOf<BlockedCallRow?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val nothing = stringResource(R.string.nothing_opens)
    LaunchedEffect(model.blocked, model.calls) {
        rows = withContext(Dispatchers.IO) { blockedCalls(context) }
    }
    val state = rememberLazyListState()
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.blocked_calls)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        val r = rows ?: return@Scaffold
        if (r.isEmpty()) {
            Quiet(stringResource(R.string.blocked_calls_none), Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        val now = System.currentTimeMillis()
        val hours24 = android.text.format.DateFormat.is24HourFormat(context)
        val today = stringResource(R.string.calls_today)
        val yesterday = stringResource(R.string.calls_yesterday)
        val hidden = stringResource(R.string.call_hidden_number)
        PagedColumn(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface), state = state) {
            items(r, key = { "${it.at}:${it.number}" }) { row ->
                val who = row.number?.let { model.names[it] }
                val title = who?.name ?: row.number?.let { Lookup.display(context, it) } ?: hidden
                val whenSaid = com.wanderwildwood.itodenwa.data.Recents.whenSaid(row.at, now, today, yesterday, hours24)
                PlainRow(title, note = "$whenSaid · ${row.why}", onPress = { menu = row })
            }
        }
    }
    menu?.let { row ->
        val number = row.number
        val line = number?.let { model.blocking(it) }
        val who = number?.let { model.names[it] }
        EInkDialog(onDismiss = { menu = null }) {
            TextMMD(
                text = who?.name ?: number?.let { Lookup.display(context, it) } ?: stringResource(R.string.call_hidden_number),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (line != null) {
                val shown = if (line.isPrefix) line.text else Lookup.display(context, line.text)
                NameRow(AnnotatedString(stringResource(R.string.unblock_this, shown))) {
                    BlockList(context).remove(line)
                    menu = null
                    model.refresh()
                }
            }
            if (number != null && who == null) {
                NameRow(AnnotatedString(stringResource(R.string.call_menu_add_contact))) {
                    menu = null
                    if (!Actions.addToContacts(context, number)) notice = nothing
                }
            }
            if (line == null && (number == null || who != null)) {
                TextMMD(text = stringResource(R.string.blocked_calls_nothing_to_do), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** The rule's words: "Not in Contacts, declined". */
private fun why(context: android.content.Context, rule: Rule, action: Action, pattern: String?): String {
    val r = when (rule) {
        Rule.BLOCK_LIST -> pattern?.let { context.getString(R.string.why_list_prefix, it) } ?: context.getString(R.string.why_list)
        Rule.NOT_IN_CONTACTS -> context.getString(R.string.why_unknown)
        Rule.HIDDEN -> context.getString(R.string.why_hidden)
    }
    val a = context.getString(if (action == Action.SILENCE) R.string.why_silenced else R.string.why_declined)
    return context.getString(R.string.why_joined, r, a)
}

/** The app's own record, and the calls the phone turned away itself for a number on its list. */
private fun blockedCalls(context: android.content.Context): List<BlockedCallRow> {
    val now = System.currentTimeMillis()
    val ours = Screened.prune(Screened(context).all(), now).map { BlockedCallRow(it.at, it.number, why(context, it.rule, it.action, it.pattern)) }
    val theirs = mutableListOf<BlockedCallRow>()
    if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
        val since = now - Screened.KEEP_DAYS * 24L * 60 * 60 * 1000
        runCatching {
            context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.DATE, android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.NUMBER_PRESENTATION),
                "${android.provider.CallLog.Calls.TYPE} = ? AND ${android.provider.CallLog.Calls.BLOCK_REASON} = ? AND ${android.provider.CallLog.Calls.DATE} >= ?",
                arrayOf(
                    android.provider.CallLog.Calls.BLOCKED_TYPE.toString(),
                    android.provider.CallLog.Calls.BLOCK_REASON_BLOCKED_NUMBER.toString(),
                    since.toString(),
                ),
                "${android.provider.CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val listed = context.getString(R.string.why_joined, context.getString(R.string.why_list), context.getString(R.string.why_declined))
                while (c.moveToNext()) {
                    val n = c.getString(1)?.takeIf { c.getInt(2) == android.provider.CallLog.Calls.PRESENTATION_ALLOWED && it.isNotBlank() }
                    theirs += BlockedCallRow(c.getLong(0), n, listed)
                }
            }
        }
    }
    return (ours + theirs).sortedByDescending { it.at }.take(Screened.KEEP_MOST)
}

/**
 * Speed dial: the keys 2 to 9, each held down on the keypad to call the number set on it. A press
 * on a key here picks someone for it; a key already set offers to change or clear it.
 */
@Composable
fun SpeedDialScreen(model: PhoneModel, onBack: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var v by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<Int?>(null) }
    SettingsPage(stringResource(R.string.settings_speed_dial), onBack) {
        item(key = "top") { Spacer(Modifier.height(8.dp)) }
        // 1 is voicemail's, as on every phone: shown, and not for setting.
        item(key = "one") { SettingRow(stringResource(R.string.speed_key, 1), summary = stringResource(R.string.speed_voicemail), onPress = null) }
        Prefs.SPEED_KEYS.forEach { key ->
            item(key = key) {
                val number = remember(v) { prefs.speedDial(key) }
                val who = number?.let { model.names[it]?.name ?: model.people.firstOrNull { p -> p.numbers.any { n -> com.wanderwildwood.itodenwa.data.Numbers.same(n.value, it) } }?.let { p -> p.name } }
                val note = when {
                    number == null -> stringResource(R.string.speed_not_set)
                    who != null -> "$who · ${Lookup.display(context, number)}"
                    else -> Lookup.display(context, number)
                }
                SettingRow(stringResource(R.string.speed_key, key), summary = note, onPress = { if (number == null) onPick(key) else menu = key })
            }
        }
    }
    menu?.let { key ->
        EInkDialog(onDismiss = { menu = null }) {
            TextMMD(text = stringResource(R.string.speed_key, key), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            NameRow(AnnotatedString(stringResource(R.string.speed_change))) {
                menu = null
                onPick(key)
            }
            val (armed, press) = rememberArmed(key) {
                prefs.setSpeedDial(key, null)
                menu = null
                v++
            }
            NameRow(AnnotatedString(stringResource(if (armed) R.string.speed_clear_armed else R.string.speed_clear)), onPress = press)
        }
    }
}

/** Someone for a speed-dial key: the people with a number, found by name, letters down the edge. */
@Composable
fun SpeedDialPicker(model: PhoneModel, key: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var choosing by remember { mutableStateOf<Person?>(null) }
    var find by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    fun set(number: String) {
        prefs.setSpeedDial(key, number)
        onBack()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.speed_pick, key)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        val shown = remember(model.people, find) {
            val f = find.trim().lowercase()
            if (f.isEmpty()) model.people else model.people.filter { it.name.lowercase().contains(f) }
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            BareField(find, { find = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), hint = stringResource(R.string.contacts_find))
            if (shown.isEmpty()) {
                Quiet(stringResource(if (model.people.isEmpty()) R.string.contacts_none else R.string.contacts_none_found), Modifier.fillMaxSize())
            } else {
                PeopleRows(shown) { p -> if (p.numbers.size == 1) set(p.numbers[0].value) else choosing = p }
            }
        }
    }
    choosing?.let { p ->
        EInkDialog(onDismiss = { choosing = null }) {
            TextMMD(text = p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            p.numbers.forEach { n ->
                NameRow(AnnotatedString(Lookup.display(context, n.value)), note = n.kind) {
                    choosing = null
                    set(n.value)
                }
            }
        }
    }
}
