package com.wanderwildwood.itodenwa.ui

import android.annotation.SuppressLint
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.call.CallManager
import com.wanderwildwood.itodenwa.call.NoCall
import com.wanderwildwood.itodenwa.call.SingleCall
import com.wanderwildwood.itodenwa.call.TwoCalls
import com.wanderwildwood.itodenwa.call.isConference
import com.wanderwildwood.itodenwa.call.number
import com.wanderwildwood.itodenwa.call.seconds
import com.wanderwildwood.itodenwa.call.stateCompat
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Voicemail
import com.wanderwildwood.itodenwa.data.Who
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.rotate

/**
 * The call screen. Who it is, large; what the call is doing; and the buttons, large and with
 * their words on them, since a call is no time to guess at a glyph.
 *
 * Ringing: **Decline** outlined on the left, **Answer** filled on the right — never alike, and
 * each held down, not tapped, so a phone taken out of a pocket does not answer by itself. A hold
 * rather than a slide: on this panel a thumb sliding across is a smear of half-drawn frames that
 * lags the finger, where a hold needs nothing to move — the button fills in three steps and acts
 * on the third, and lifting early undoes it. While it rings, a covered proximity sensor turns
 * every touch away.
 *
 * Going: Mute, Speaker, Keypad and Hold, each black when it is on, and **End call** across the
 * foot. With the keys up, Mute and Speaker stay beside them. With a second call: who is held or
 * waiting, and Swap.
 */
@Composable
fun InCallScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    // Read so that every change to any call draws this again.
    @Suppress("UNUSED_VARIABLE")
    val changes = CallManager.changes.intValue
    val state = CallManager.phoneState()
    val call: Call? = when (state) {
        is NoCall -> null
        is SingleCall -> state.call
        is TwoCalls -> state.active
    }
    val other: Call? = (state as? TwoCalls)?.onHold

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // What was last shown, so the screen can say "Call ended" over it for a moment.
    var lastTitle by remember { mutableStateOf("") }
    var lastLine by remember { mutableStateOf<AnnotatedString?>(null) }
    var lastSeconds by remember { mutableLongStateOf(0L) }

    val callState = call.stateCompat()
    val ended = call == null || callState == Call.STATE_DISCONNECTED
    LaunchedEffect(ended) {
        if (ended) {
            delay(1800)
            onDone()
        }
    }

    val number = call?.number()
    var who by remember(number) { mutableStateOf<Who?>(null) }
    var toVoicemail by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        who = number?.let { n -> withContext(Dispatchers.IO) { Lookup.get(context).who(n) } }
        toVoicemail = call?.let { c -> withContext(Dispatchers.IO) { Voicemail.isCallTo(context, c) } } == true
    }
    val otherNumber = other?.number()
    var otherWho by remember(otherNumber) { mutableStateOf<Who?>(null) }
    LaunchedEffect(otherNumber) {
        otherWho = otherNumber?.let { n -> withContext(Dispatchers.IO) { Lookup.get(context).who(n) } }
    }

    val hidden = stringResource(R.string.call_hidden_number)
    val conference = stringResource(R.string.call_conference)
    val voicemailWord = stringResource(R.string.recents_voicemail)
    if (call != null && !ended) {
        lastTitle = when {
            call.isConference() -> conference
            who != null -> who!!.name
            toVoicemail -> voicemailWord
            number != null -> Lookup.display(context, number)
            else -> hidden
        }
        lastLine = when {
            number == null -> null
            who != null -> kindAndNumber(who!!.kind, Lookup.display(context, number))
            toVoicemail -> kindAndNumber("", Lookup.display(context, number))
            else -> null
        }
        lastSeconds = call.seconds()
    }

    ProximityWhile(!ended && callState != Call.STATE_RINGING && !CallManager.isSpeaker())

    var keypad by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf("") }
    var choosingRoute by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val canNote = remember { Actions.canNote(context) }

    val status = when {
        ended -> listOfNotNull(stringResource(R.string.call_ended), lastSeconds.takeIf { it > 0 }?.let(::clock)).joinToString(" · ")
        callState == Call.STATE_RINGING -> stringResource(R.string.call_incoming)
        callState == Call.STATE_HOLDING -> stringResource(R.string.call_on_hold)
        callState == Call.STATE_ACTIVE -> clock(call!!.seconds().also { now })
        callState == Call.STATE_DISCONNECTING -> stringResource(R.string.call_ending)
        else -> stringResource(R.string.call_calling)
    }

    // While it rings, a covered proximity sensor means a pocket or a bag: every touch is ignored.
    val covered = rememberCovered(callState == Call.STATE_RINGING)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (other != null && !ended) {
                val otherName = otherWho?.name ?: otherNumber?.let { Lookup.display(context, it) } ?: hidden
                val held = other.stateCompat() == Call.STATE_HOLDING
                TextMMD(
                    text = stringResource(if (held) R.string.call_held_line else R.string.call_waiting_line, otherName),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            // The overflow sits at the end of the status line: the person's card, and a note about
            // the call in Notes. Only while a call is going on and has a number.
            val live = !ended && callState != Call.STATE_RINGING && number != null && call?.isConference() != true
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                TextMMD(text = status, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp), textAlign = TextAlign.Center)
                if (live) {
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        BarButton(Icons.More, stringResource(R.string.cd_more)) { menu = true }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextMMD(
                text = lastTitle,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            lastLine?.let {
                TextMMD(text = it, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }

            if (keypad && !ended && callState != Call.STATE_RINGING) {
                Spacer(Modifier.weight(1f))
                TextMMD(
                    text = if (sent.length > 18) "…" + sent.takeLast(17) else sent,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                KeyGrid(onKey = { c -> CallManager.keypad(c); sent += c }, onPlus = null, keyHeight = 52.dp)
                Spacer(Modifier.height(8.dp))
                // Mute and the speaker stay in reach while the keys are up: a menu of options read
                // out on a call is often listened to on the speaker.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallToggle(stringResource(R.string.call_mute), CallManager.isMuted(), Modifier.weight(1f)) { CallManager.setMuted(!CallManager.isMuted()) }
                    SmallToggle(stringResource(R.string.call_speaker), CallManager.isSpeaker(), Modifier.weight(1f)) {
                        CallManager.setRoute(if (CallManager.isSpeaker()) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                    }
                    SmallToggle(stringResource(R.string.call_hide_keys), false, Modifier.weight(1f)) { keypad = false }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }

            when {
                ended -> Spacer(Modifier.height(72.dp))
                callState == Call.STATE_RINGING -> {
                    TextMMD(text = stringResource(R.string.call_hold_to), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HoldButton(Icons.Call, stringResource(R.string.call_decline), filled = false, modifier = Modifier.weight(1f), height = 84.dp, iconRotation = 135f) {
                            CallManager.hangUp()
                        }
                        HoldButton(Icons.Call, stringResource(R.string.call_answer), filled = true, modifier = Modifier.weight(1f), height = 84.dp) {
                            CallManager.answer()
                        }
                    }
                }
                else -> {
                    if (!keypad) {
                        val routes = CallManager.routes()
                        val bluetooth = routes and CallAudioState.ROUTE_BLUETOOTH != 0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Toggle(Icons.Mute, stringResource(R.string.call_mute), CallManager.isMuted(), Modifier.weight(1f)) {
                                CallManager.setMuted(!CallManager.isMuted())
                            }
                            Toggle(Icons.Speaker, stringResource(if (bluetooth) R.string.call_audio else R.string.call_speaker), CallManager.isSpeaker(), Modifier.weight(1f)) {
                                if (bluetooth) choosingRoute = true
                                else CallManager.setRoute(if (CallManager.isSpeaker()) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Toggle(Icons.Keypad, stringResource(R.string.call_keypad), false, Modifier.weight(1f)) { keypad = true }
                            if (other != null) {
                                Toggle(Icons.Swap, stringResource(R.string.call_swap), false, Modifier.weight(1f)) { CallManager.swap() }
                            } else {
                                Toggle(Icons.Hold, stringResource(R.string.call_hold), CallManager.isHeld(), Modifier.weight(1f), enabled = CallManager.canHold() || CallManager.isHeld()) {
                                    CallManager.toggleHold()
                                }
                            }
                        }
                        if (other != null && CallManager.canMerge()) {
                            Spacer(Modifier.height(12.dp))
                            FootButton(stringResource(R.string.call_merge), Modifier.fillMaxWidth()) { CallManager.merge() }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    BigButton(Icons.Call, stringResource(R.string.call_end), filled = true, modifier = Modifier.fillMaxWidth(), height = 72.dp, iconRotation = 135f) {
                        CallManager.hangUp()
                    }
                }
            }
        }

        if (covered) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            )
        }
    }

    if (menu && number != null && call != null) {
        val title = lastTitle
        val at = call.details.connectTimeMillis.takeIf { it > 0 } ?: call.details.creationTimeMillis
        EInkDialog(onDismiss = { menu = false }) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            val w = who
            NameRow(AnnotatedString(stringResource(if (w != null) R.string.call_menu_contact else R.string.call_menu_add_contact))) {
                menu = false
                if (w != null) Actions.openContact(context, w.contactId, w.lookupKey) else Actions.addToContacts(context, number)
            }
            if (canNote) {
                val noteTitle = stringResource(R.string.call_note_title, title, whenCalled(context, at))
                NameRow(AnnotatedString(stringResource(R.string.call_menu_note))) {
                    menu = false
                    Actions.note(context, noteTitle)
                }
            }
        }
    }
    if (choosingRoute) RouteDialog { choosingRoute = false }
    if (callState == Call.STATE_SELECT_PHONE_ACCOUNT) AccountDialog()
}

private fun kindAndNumber(kind: String, number: String): AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    if (kind.isNotBlank()) append(kind.lowercase() + " ")
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
    append(number)
    pop()
}

/** When a call began, for a note's title: "Oct 17, 2:05 PM", or "17 Oct, 14:05". */
private fun whenCalled(context: android.content.Context, at: Long): String {
    val locale = java.util.Locale.getDefault()
    val skeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "MMMdHmm" else "MMMdhmma"
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(at))
}

/** "4:07", "1:02:33". */
private fun clock(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * A big square button that is on or off: black with a white glyph when on, a black edge when off.
 * The word is on the button, so it is never a guess.
 */
@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, enabled: Boolean = true, onPress: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(84.dp)
            .clip(shape)
            .let { if (on) it.background(ink, shape) else it.border(if (enabled) 2.5.dp else 1.dp, ink, shape) }
            .clickable(enabled = enabled, onClick = onPress),
    ) {
        Icon(icon, contentDescription = null, tint = if (on) paper else ink, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
            fontWeight = if (on) FontWeight.Bold else null,
            color = if (on) paper else ink,
            maxLines = 1,
        )
    }
}

/** Where the call's sound goes, when a headset joins the choice: the earpiece, the speaker, Bluetooth, a wired headset. */
@Composable
private fun RouteDialog(onDismiss: () -> Unit) {
    val routes = CallManager.routes()
    val current = CallManager.route()
    val options = listOf(
        CallAudioState.ROUTE_EARPIECE to R.string.route_earpiece,
        CallAudioState.ROUTE_SPEAKER to R.string.route_speaker,
        CallAudioState.ROUTE_BLUETOOTH to R.string.route_bluetooth,
        CallAudioState.ROUTE_WIRED_HEADSET to R.string.route_wired,
    ).filter { routes and it.first != 0 }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.call_audio), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        options.forEach { (route, label) ->
            val chosen = route == current
            NameRow(AnnotatedString(stringResource(label)), note = if (chosen) stringResource(R.string.route_now) else null) {
                CallManager.setRoute(route)
                onDismiss()
            }
        }
    }
}

/** Which SIM to call from, when the phone has more than one and no default is set. */
@SuppressLint("MissingPermission")
@Composable
private fun AccountDialog() {
    val context = LocalContext.current
    val telecom = context.getSystemService(TelecomManager::class.java)
    val accounts: List<Pair<PhoneAccountHandle, String>> = remember {
        runCatching {
            telecom.callCapablePhoneAccounts.map { h -> h to (telecom.getPhoneAccount(h)?.label?.toString() ?: h.id) }
        }.getOrDefault(emptyList())
    }
    EInkDialog(onDismiss = { CallManager.hangUp() }) {
        TextMMD(text = stringResource(R.string.call_choose_sim), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        accounts.forEach { (handle, label) ->
            NameRow(AnnotatedString(label)) { CallManager.chooseAccount(handle) }
        }
        Spacer(Modifier.height(12.dp))
        FootButton(stringResource(R.string.cancel), Modifier.fillMaxWidth()) { CallManager.hangUp() }
    }
}

/**
 * Turns the panel off while the phone is held to the ear, so a cheek does not press Mute or End
 * call. Not while ringing, and not on the speaker. From Fossify Phone's CallActivity.
 */
@Composable
private fun ProximityWhile(wanted: Boolean) {
    val context = LocalContext.current
    DisposableEffect(wanted) {
        val power = context.getSystemService(PowerManager::class.java)
        val lock = if (wanted && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "itodenwa:proximity").apply { acquire(60 * 60 * 1000L) }
        } else {
            null
        }
        onDispose { if (lock?.isHeld == true) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
    }
}

/**
 * Whether the proximity sensor is covered, while [watching]. False where the phone has none, so
 * a phone without one is never locked out of its own call screen.
 */
@Composable
private fun rememberCovered(watching: Boolean): Boolean {
    val context = LocalContext.current
    var covered by remember { mutableStateOf(false) }
    DisposableEffect(watching) {
        val sensors = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)
        if (!watching || sensor == null) {
            covered = false
            onDispose { }
        } else {
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(e: android.hardware.SensorEvent) {
                    covered = e.values.firstOrNull()?.let { it < sensor.maximumRange && it < 5f } == true
                }
                override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) = Unit
            }
            sensors.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            onDispose {
                sensors.unregisterListener(listener)
                covered = false
            }
        }
    }
    return covered
}

/**
 * A big button that acts only when held: it fills from the left in three steps over 0.6 seconds
 * and acts on the last; let go before then and it empties again. The steps are whole repaints,
 * not an animation, which this panel draws cleanly.
 */
@Composable
private fun HoldButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    iconRotation: Float = 0f,
    onHeld: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(14.dp)
    var steps by remember { mutableIntStateOf(0) }
    val action by androidx.compose.runtime.rememberUpdatedState(onHeld)
    // The filled button empties as it is held and the outlined one fills, so either way the
    // part that has changed is the part that is done.
    val base = if (filled) ink else paper
    val fill = if (filled) paper else ink
    val fraction = steps / HOLD_STEPS.toFloat()
    val textOnFill = fraction >= 0.5f
    val fg = if (textOnFill) base else fill
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(base, shape)
            .drawBehind {
                if (fraction > 0f) drawRect(fill, size = Size(size.width * fraction, size.height))
            }
            .border(2.5.dp, ink, shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.changes.none { it.pressed }) continue
                        down.changes.forEach { it.consume() }
                        steps = 0
                        var done = false
                        val start = System.currentTimeMillis()
                        while (!done) {
                            val event = withTimeoutOrNull(HOLD_MS / HOLD_STEPS) { awaitPointerEvent() }
                            if (event != null) {
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                            val held = System.currentTimeMillis() - start
                            steps = (held * HOLD_STEPS / HOLD_MS).toInt().coerceAtMost(HOLD_STEPS)
                            if (steps >= HOLD_STEPS) {
                                done = true
                                action()
                            }
                        }
                        if (!done) steps = 0
                    }
                }
            },
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(30.dp).rotate(iconRotation))
        Spacer(Modifier.width(12.dp))
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private const val HOLD_MS = 600L
private const val HOLD_STEPS = 3

/** A smaller on-off button for the row under the in-call keys. */
@Composable
private fun SmallToggle(label: String, on: Boolean, modifier: Modifier, onPress: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .let { if (on) it.background(ink, shape) else it.border(2.dp, ink, shape) }
            .clickable(onClick = onPress),
    ) {
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (on) FontWeight.Bold else null,
            color = if (on) paper else ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
