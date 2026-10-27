package com.wanderwildwood.itodenwa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.BlockList
import com.wanderwildwood.itodenwa.data.CardNumber
import com.wanderwildwood.itodenwa.data.LoggedCall
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Recents
import com.wanderwildwood.itodenwa.data.Who
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One number, opened from Recents: who it is, if anyone in the contacts, and three buttons —
 * Call, Message, and the person's card in Contacts (or Add, to put the number on one). Under
 * them, every call with that number.
 */
@Composable
fun NumberPage(model: PhoneModel, number: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var who by remember(number) { mutableStateOf<Who?>(model.names[number]) }
    LaunchedEffect(number, model.people) {
        who = withContext(Dispatchers.IO) { Lookup.get(context).who(number) }
    }
    val w = who
    val shown = Lookup.display(context, number)
    val calls = remember(model.calls, number) { Recents.with(model.calls, listOf(number)) }
    DetailPage(
        title = w?.name ?: shown,
        lines = listOf(kindAndValue(w?.kind?.lowercase().orEmpty(), shown)),
        numbers = listOf(CardNumber(number, w?.kind.orEmpty())),
        calls = calls,
        showNumberOnCalls = false,
        contactTile = if (w != null) {
            Triple(Icons.Person, stringResource(R.string.contact)) { Actions.openContact(context, w.contactId, w.lookupKey) }
        } else {
            Triple(Icons.PersonAdd, stringResource(R.string.add)) { Actions.addToContacts(context, number) }
        },
        onBack = onBack,
        actions = { BlockWord(model, number, onBlocked = onBack) },
    )
}

/**
 * Block, at the end of a number's bar. The first press arms it — "Block — tap again" — and a
 * second within six seconds blocks the number: its calls leave Recents, and the page closes.
 * A number already blocked says Unblock, and asks the same way.
 */
@Composable
private fun BlockWord(model: PhoneModel, number: String, onBlocked: () -> Unit) {
    val context = LocalContext.current
    val line = model.blocking(number)
    val cannot = stringResource(R.string.blocked_add_failed)
    val (armed, press) = rememberArmed(line) {
        if (line != null) {
            BlockList(context).remove(line)
            model.refresh()
        } else if (BlockList(context).add(number)) {
            model.refresh()
            onBlocked()
        } else {
            android.widget.Toast.makeText(context, cannot, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val word = when {
        line != null && armed -> R.string.unblock_armed
        line != null -> R.string.unblock
        armed -> R.string.block_armed
        else -> R.string.block
    }
    BarWord(stringResource(word), onClick = press)
}

/** One person, opened from Favourites or Contacts: every number of theirs, the same three buttons, and their calls. */
@Composable
fun PersonPage(model: PhoneModel, id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val person = model.person(id)
    if (person == null) {
        BackHandler(onBack = onBack)
        if (model.peopleLoaded) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = { Bar(title = {}, navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) }) },
            ) { padding -> Quiet(stringResource(R.string.contact_gone), Modifier.padding(padding).fillMaxSize()) }
        }
        return
    }
    val numbers = person.numbers
    val calls = remember(model.calls, numbers) { Recents.with(model.calls, numbers.map { it.value }) }
    DetailPage(
        title = person.name.ifBlank { stringResource(R.string.no_name) },
        lines = numbers.map { kindAndValue(it.kind.lowercase(), Lookup.display(context, it.value)) },
        numbers = numbers,
        calls = calls,
        showNumberOnCalls = numbers.size > 1,
        contactTile = Triple(Icons.Person, stringResource(R.string.contact)) { Actions.openContact(context, person.id, person.lookupKey) },
        onBack = onBack,
    )
}

/** "mobile **(555) 555-0143**", as the phone's own Details page sets a number. */
private fun kindAndValue(kind: String, value: String): AnnotatedString = buildAnnotatedString {
    if (kind.isNotBlank()) append("$kind ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
}

@Composable
private fun DetailPage(
    title: String,
    lines: List<AnnotatedString>,
    numbers: List<CardNumber>,
    calls: List<LoggedCall>,
    showNumberOnCalls: Boolean,
    contactTile: Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Boolean>,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var notice by remember { mutableStateOf<String?>(null) }
    // A person with several numbers: which one to call, or to message.
    var choosing by remember { mutableStateOf<Boolean?>(null) }
    val nothing = stringResource(R.string.nothing_opens)
    val cannotCall = stringResource(R.string.cannot_call)

    val call = rememberCaller { notice = cannotCall }
    fun text(n: String) {
        if (!Actions.message(context, n)) notice = nothing
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Bar(
                title = { BarTitle(stringResource(R.string.details_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = actions,
            )
        },
        bottomBar = { notice?.let { NoticeStrip(it) { notice = null } } },
    ) { padding ->
        val now = System.currentTimeMillis()
        val hours24 = android.text.format.DateFormat.is24HourFormat(context)
        val today = stringResource(R.string.calls_today)
        val yesterday = stringResource(R.string.calls_yesterday)
        PagedColumn(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            item(key = "who") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TextMMD(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(6.dp))
                    lines.forEach {
                        TextMMD(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            item(key = "tiles") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
                ) {
                    ActionTile(Icons.Call, stringResource(R.string.call)) {
                        if (numbers.size == 1) call(numbers[0].value) else choosing = true
                    }
                    ActionTile(Icons.Sms, stringResource(R.string.message)) {
                        if (numbers.size == 1) text(numbers[0].value) else choosing = false
                    }
                    ActionTile(contactTile.first, contactTile.second) {
                        if (!contactTile.third()) notice = nothing
                    }
                }
            }
            if (calls.isNotEmpty()) {
                item(key = "heading") { Heading(stringResource(R.string.calls_heading)) }
                calls.forEach { c ->
                    item(key = c.id) {
                        val kind = stringResource(
                            when (c.kind) {
                                LoggedCall.Kind.IN -> R.string.calls_in
                                LoggedCall.Kind.OUT -> R.string.calls_out
                                LoggedCall.Kind.MISSED -> R.string.calls_missed
                                LoggedCall.Kind.DECLINED, LoggedCall.Kind.BLOCKED -> R.string.calls_declined
                            },
                        )
                        val note = listOfNotNull(Recents.howLong(c.seconds), kind, c.number?.takeIf { showNumberOnCalls }?.let { Lookup.display(context, it) })
                        CallLine(c, Recents.whenSaid(c.at, now, today, yesterday, hours24), note.joinToString(" · "))
                    }
                }
            }
        }
    }

    choosing?.let { calling ->
        EInkDialog(onDismiss = { choosing = null }) {
            TextMMD(
                text = stringResource(if (calling) R.string.choose_number_call else R.string.choose_number_message),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            numbers.forEach { n ->
                NameRow(AnnotatedString(Lookup.display(context, n.value)), note = n.kind) {
                    choosing = null
                    if (calling) call(n.value) else text(n.value)
                }
            }
            Spacer(Modifier.height(12.dp))
            FootButton(stringResource(R.string.cancel), Modifier.fillMaxWidth()) { choosing = null }
        }
    }
}

/** One call in a page's history. Nothing happens on a press: the Call button is above. */
@Composable
private fun CallLine(c: LoggedCall, title: String, note: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 17.5.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(arrowFor(c.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(15.5.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextMMD(text = note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    DottedRule(start = 59.dp, end = 0.dp)
}
