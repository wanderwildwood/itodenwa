package com.wanderwildwood.itodenwa.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.pluralStringResource
import com.wanderwildwood.itodenwa.data.BlockList
import com.wanderwildwood.itodenwa.data.CallLogs
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.R
import com.wanderwildwood.itodenwa.data.Actions
import com.wanderwildwood.itodenwa.data.LoggedCall
import com.wanderwildwood.itodenwa.data.Lookup
import com.wanderwildwood.itodenwa.data.Person
import com.wanderwildwood.itodenwa.data.Recent
import com.wanderwildwood.itodenwa.data.Recents

/** A row that asks for a permission the list needs, in place of the list. */
@Composable
private fun AskRow(text: String, note: String, permission: String, model: PhoneModel) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    Column(Modifier.fillMaxSize()) {
        NameRow(AnnotatedString(text), note = note) { ask.launch(permission) }
    }
}

/**
 * The calls, newest first, as the phone's own Recents lists them: an arrow for the way each
 * went, who it was — the name from the contacts whichever way the number was written — and
 * when. A press opens the number's page. **A press never calls**: the phone's own Recents dials
 * on a single tap, which is how calls get made by a thumb meant for scrolling.
 *
 * A long press holds what can be done to the row: Open, Text back on a missed call (in the
 * phone's messaging app, where there is one), Block, and Delete. Block and Delete each ask by
 * changing what they say, and a second press does it. Calls turned away are not here: they are
 * under Blocked calls, in the ⋮. Page dots down the edge once the list is long.
 */
@Composable
fun RecentsList(model: PhoneModel, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    if (model.peopleLoaded && !model.mayReadCalls) {
        AskRow(stringResource(R.string.recents_allow), stringResource(R.string.recents_allow_note), Manifest.permission.READ_CALL_LOG, model)
        return
    }
    if (!model.callsLoaded) return
    if (model.recents.isEmpty()) {
        Quiet(stringResource(R.string.recents_none), Modifier.fillMaxSize())
        return
    }
    val now = System.currentTimeMillis()
    val hours24 = android.text.format.DateFormat.is24HourFormat(context)
    val today = stringResource(R.string.calls_today)
    val yesterday = stringResource(R.string.calls_yesterday)
    val hidden = stringResource(R.string.call_hidden_number)
    val canText = remember { Actions.canMessage(context) }
    // A call to voicemail says so, rather than showing the carrier's number for it.
    val voicemail = remember { com.wanderwildwood.itodenwa.data.Voicemail.number(context) }
    val voicemailWord = stringResource(R.string.recents_voicemail)
    var menu by remember { mutableStateOf<Pair<String, Recent>?>(null) }
    val state = rememberLazyListState()
    // A new call lands above the first row. A list keeps its place by the row it was showing,
    // which would leave the new call just out of sight; at the top, the top is kept instead.
    val newest = model.recents.firstOrNull()?.last?.id
    androidx.compose.runtime.LaunchedEffect(newest) {
        if (state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
    }
    ListJumpBox(state, ListJump.Pages) {
        PagedColumn(Modifier.fillMaxSize(), state = state) {
            items(model.recents, key = { it.last.id }) { r ->
                val number = r.number
                val who = number?.let { model.names[it] }
                val title = who?.name
                    ?: number?.takeIf { voicemail != null && com.wanderwildwood.itodenwa.data.Numbers.same(it, voicemail) }?.let { voicemailWord }
                    ?: number?.let { Lookup.display(context, it) } ?: hidden
                val note = listOfNotNull(who?.kind, Recents.whenSaid(r.last.at, now, today, yesterday, hours24)).joinToString(" · ")
                RecentRow(
                    r,
                    title,
                    note,
                    onPress = number?.let { { onOpen(it) } },
                    onLongPress = { menu = title to r },
                )
            }
        }
    }
    menu?.let { (title, r) ->
        val number = r.number
        val missed = r.last.kind == LoggedCall.Kind.MISSED
        val gone = stringResource(R.string.recents_cannot_delete)
        EInkDialog(onDismiss = { menu = null }) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            if (number != null) {
                NameRow(AnnotatedString(stringResource(R.string.recents_open))) {
                    menu = null
                    onOpen(number)
                }
                if (missed && canText) {
                    NameRow(AnnotatedString(stringResource(R.string.recents_text_back))) {
                        menu = null
                        Actions.message(context, number)
                    }
                }
                val (blockArmed, block) = rememberArmed(r.last.id) {
                    menu = null
                    BlockList(context).add(number)
                    model.refresh()
                }
                NameRow(AnnotatedString(stringResource(if (blockArmed) R.string.block_armed else R.string.block)), onPress = block)
            }
            val (deleteArmed, delete) = rememberArmed(r.last.id) {
                menu = null
                if (!CallLogs.delete(context, r.calls.map { it.id })) android.widget.Toast.makeText(context, gone, android.widget.Toast.LENGTH_SHORT).show()
                model.refresh()
            }
            NameRow(
                AnnotatedString(stringResource(if (deleteArmed) R.string.delete_armed else R.string.delete)),
                note = if (r.count > 1) pluralStringResource(R.plurals.recents_delete_count, r.count, r.count) else null,
                onPress = delete,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(r: Recent, title: String, note: String, onPress: (() -> Unit)?, onLongPress: (() -> Unit)? = null) {
    val missed = r.last.kind == LoggedCall.Kind.MISSED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onPress != null || onLongPress != null) it.combinedClickable(onClick = onPress ?: {}, onLongClick = onLongPress) else it }
            .padding(start = 17.5.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(arrowFor(r.last.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(15.5.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText),
                    fontWeight = if (missed) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.count > 1) {
                    TextMMD(text = " (${r.count})", style = MaterialTheme.typography.bodyLarge.copy(fontSize = rowText), maxLines = 1)
                }
            }
            TextMMD(text = note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    DottedRule(start = 59.dp, end = 0.dp)
}

internal fun arrowFor(kind: LoggedCall.Kind) = when (kind) {
    LoggedCall.Kind.IN -> Icons.CallIn
    LoggedCall.Kind.OUT -> Icons.CallOut
    else -> Icons.CallMissed
}

/** The people starred in Contacts, as the phone's own Phone app lists them first. */
@Composable
fun FavouritesList(model: PhoneModel, onOpen: (Person) -> Unit) {
    if (model.peopleLoaded && !model.mayReadContacts) {
        AskRow(stringResource(R.string.contacts_allow), stringResource(R.string.contacts_allow_note), Manifest.permission.READ_CONTACTS, model)
        return
    }
    if (!model.peopleLoaded) return
    val starred = model.people.filter { it.starred }
    if (starred.isEmpty()) {
        Quiet(stringResource(R.string.favourites_none), Modifier.fillMaxSize())
        return
    }
    PeopleRows(starred, onOpen)
}

/** Everyone with a number, and a box above them to find someone by name. */
@Composable
fun ContactsList(model: PhoneModel, onOpen: (Person) -> Unit) {
    if (model.peopleLoaded && !model.mayReadContacts) {
        AskRow(stringResource(R.string.contacts_allow), stringResource(R.string.contacts_allow_note), Manifest.permission.READ_CONTACTS, model)
        return
    }
    if (!model.peopleLoaded) return
    var find by rememberSaveable { mutableStateOf("") }
    val shown = remember(model.people, find) {
        val f = find.trim().lowercase()
        if (f.isEmpty()) model.people
        else model.people.filter { p -> p.name.lowercase().split(' ').any { it.startsWith(f) } || p.name.lowercase().contains(f) }
    }
    Column(Modifier.fillMaxSize()) {
        BareField(find, { find = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), hint = stringResource(R.string.contacts_find))
        when {
            model.people.isEmpty() -> Quiet(stringResource(R.string.contacts_none), Modifier.fillMaxSize())
            shown.isEmpty() -> Quiet(stringResource(R.string.contacts_none_found), Modifier.fillMaxSize())
            else -> PeopleRows(shown, onOpen)
        }
    }
}

/** People in the contacts' own order, with the letters down the edge once the list is long. */
@Composable
internal fun PeopleRows(people: List<Person>, onOpen: (Person) -> Unit) {
    val noName = stringResource(R.string.no_name)
    val state = rememberLazyListState()
    ListJumpBox(state, ListJump.Letters(people.map { it.name })) {
        PagedColumn(Modifier.fillMaxSize(), state = state) {
            items(people, key = { it.id }) { p ->
                NameRow(boldSurname(p.name.ifBlank { noName }, p.family)) { onOpen(p) }
            }
        }
    }
}
