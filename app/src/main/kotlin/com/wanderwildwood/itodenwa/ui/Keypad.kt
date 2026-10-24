package com.wanderwildwood.itodenwa.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.Buzz
import com.wanderwildwood.itodenwa.data.LoggedCall
import com.wanderwildwood.itodenwa.data.Prefs
import com.wanderwildwood.itodenwa.data.Voicemail
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Numbers
import com.wanderwildwood.itodenwa.data.Person
import com.wanderwildwood.itodenwa.data.Who
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The twelve keys, with the letters each stands for. */
private val KEYS = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

/**
 * The keypad: the number as it is typed, the name it belongs to when it is someone's, and the
 * people whose name or number the keys spell; under them the keys, and Call.
 *
 * A person found by the keys fills in their number when pressed — it does not call. With
 * nothing typed, Call puts the last number called back in the box, as phones do.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(model: PhoneModel, typed: String, onTyped: (String) -> Unit, onSpeedDialUnset: () -> Unit) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    val cannotCall = stringResource(R.string.cannot_call)
    val call = rememberCaller { notice = cannotCall }
    val tones = rememberTones()
    val buzz = rememberKeyBuzz()
    val prefs = remember { Prefs(context) }
    var unset by remember { mutableStateOf<Int?>(null) }

    var who by remember { mutableStateOf<Who?>(null) }
    LaunchedEffect(typed, model.people) {
        who = if (Numbers.digits(typed).length >= Numbers.SHORTEST) withContext(Dispatchers.IO) { Lookup.get(context).who(typed) } else null
    }
    val found: List<Person> = remember(typed, model.people, who) {
        if (Numbers.digits(typed).isEmpty() || who != null) emptyList()
        else model.people.filter { Numbers.typedFinds(typed, it.name, it.numbers.map { n -> n.value }) }.take(2)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            // Who the number is, or who the keys find, in the room above the number; the number
            // itself always shows, and whatever does not fit above it is cut, not the number.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.BottomStart) {
                Column {
                    if (who != null) {
                        TextMMD(text = who!!.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        found.forEach { p ->
                            Found(boldSurname(p.name, p.family), p.numbers.firstOrNull()?.let { Lookup.display(context, it.value) }) {
                                p.numbers.firstOrNull()?.let { onTyped(Numbers.dialable(it.value)) }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                TextMMD(
                    // The end of a long number is the part being typed; the start gives way.
                    text = if (typed.length > 15) "…" + typed.takeLast(14) else typed,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (typed.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .combinedClickable(onClick = { buzz(); onTyped(typed.dropLast(1)) }, onLongClick = { buzz(); onTyped("") }),
                    ) {
                        Icon(Icons.Backspace, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                    }
                }
            }
            DottedRule(start = 0.dp, end = 0.dp)
        }
        KeyGrid(
            onKey = { c ->
                tones.start(c)
                onTyped(typed + c)
            },
            onPlus = { onTyped(typed + "+") },
            // Held, with nothing typed yet: 1 calls voicemail, 2 to 9 the number set on them.
            // An emergency number is never dialled by a held key; it is put on the keypad.
            onHold = { c ->
                if (typed.isNotEmpty() || c !in '1'..'9') return@KeyGrid false
                if (c == '1') {
                    if (!Voicemail.call(context)) Actions.start(context, Voicemail.settings())
                    return@KeyGrid true
                }
                val number = prefs.speedDial(c - '0')
                when {
                    number == null -> unset = c - '0'
                    Actions.isEmergency(context, number) -> onTyped(Numbers.dialable(number))
                    else -> call(number)
                }
                true
            },
        )
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            BigButton(Icons.Call, stringResource(R.string.call), filled = true, modifier = Modifier.fillMaxWidth()) {
                val number = Numbers.dialable(typed)
                if (number.isEmpty()) {
                    model.calls.firstOrNull { it.kind == LoggedCall.Kind.OUT && it.number != null }?.number?.let { onTyped(Numbers.dialable(it)) }
                } else {
                    call(number)
                }
            }
        }
        notice?.let { NoticeStrip(it) { notice = null } }
    }
    unset?.let { key ->
        EInkDialog(onDismiss = { unset = null }) {
            TextMMD(text = stringResource(R.string.speed_unset, key), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                FootButton(stringResource(R.string.cancel), Modifier.weight(1f)) { unset = null }
                Spacer(Modifier.width(12.dp))
                FootButton(stringResource(R.string.settings_speed_dial), Modifier.weight(1f)) {
                    unset = null
                    onSpeedDialUnset()
                }
            }
        }
    }
}

/** Someone the keys found: a press fills in their number. */
@Composable
private fun Found(name: androidx.compose.ui.text.AnnotatedString, number: String?, onPress: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onPress).padding(vertical = 6.dp)) {
        TextMMD(text = name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (number != null) TextMMD(text = number, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
    DottedRule(start = 0.dp, end = 0.dp)
}

/**
 * The twelve keys in four rows. Long-pressing 0 types a "+"; any other key held is handed to
 * [onHold], which says whether it took it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyGrid(
    onKey: (Char) -> Unit,
    onPlus: (() -> Unit)?,
    keyHeight: androidx.compose.ui.unit.Dp = 56.dp,
    onHold: ((Char) -> Boolean)? = null,
) {
    val buzz = rememberKeyBuzz()
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (digit, letters) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .combinedClickable(
                                onClick = { buzz(); onKey(digit) },
                                onLongClick = when {
                                    digit == '0' && onPlus != null -> { -> buzz(); onPlus() }
                                    onHold != null && digit in '1'..'9' -> { -> onHold(digit); Unit }
                                    else -> null
                                },
                            ),
                    ) {
                        TextMMD(text = digit.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 32.sp), fontWeight = FontWeight.Medium)
                        if (letters.isNotEmpty()) {
                            TextMMD(text = letters, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A large button with a glyph and a word: Call, Answer, End call. Filled black for the thing the
 * screen is for, outlined for the other choice, so the two never look alike.
 */
@Composable
fun BigButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 60.dp,
    iconRotation: Float = 0f,
    onPress: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .let { if (filled) it.background(ink, RoundedCornerShape(14.dp)) else it.border(2.5.dp, ink, RoundedCornerShape(14.dp)) }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPress),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) paper else ink,
            modifier = Modifier.size(30.dp).rotate(iconRotation),
        )
        Spacer(Modifier.width(12.dp))
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            color = if (filled) paper else ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The keys' tones, quiet when the phone is on silent or vibrate. From Fossify Phone's ToneGeneratorHelper. */
class Tones(private val audio: AudioManager?) {
    private val generator: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 80) }.getOrNull()

    fun start(c: Char) {
        val tone = TONES[c] ?: return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        generator?.startTone(tone, 150)
    }

    fun release() = generator?.release()

    companion object {
        private val TONES = mapOf(
            '0' to ToneGenerator.TONE_DTMF_0, '1' to ToneGenerator.TONE_DTMF_1, '2' to ToneGenerator.TONE_DTMF_2,
            '3' to ToneGenerator.TONE_DTMF_3, '4' to ToneGenerator.TONE_DTMF_4, '5' to ToneGenerator.TONE_DTMF_5,
            '6' to ToneGenerator.TONE_DTMF_6, '7' to ToneGenerator.TONE_DTMF_7, '8' to ToneGenerator.TONE_DTMF_8,
            '9' to ToneGenerator.TONE_DTMF_9, '*' to ToneGenerator.TONE_DTMF_S, '#' to ToneGenerator.TONE_DTMF_P,
        )
    }
}

@Composable
private fun rememberTones(): Tones {
    val context = LocalContext.current
    val tones = remember { Tones(context.getSystemService(AudioManager::class.java)) }
    DisposableEffect(Unit) { onDispose { tones.release() } }
    return tones
}

/**
 * Asks the phone for its keyboard tap at a key press when Settings has the buzz on: through the
 * view rather than the vibrator, so the phone's own touch feedback setting is respected and no
 * permission is needed. The setting is read at each press, so turning it on needs no restart.
 */
@Composable
fun rememberKeyBuzz(): () -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { Prefs(context) }
    return { Buzz.press(prefs.vibrate) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } }
}
