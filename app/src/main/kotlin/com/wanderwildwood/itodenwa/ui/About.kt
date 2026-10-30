package com.wanderwildwood.itodenwa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.itodenwa.BuildConfig
import com.wanderwildwood.itodenwa.R

/**
 * What this is, what it does with your information, the terms it comes under, whose work it
 * stands on, and where its source is. A page in the same bar and rows as Settings, as the
 * words are too many for a dialog. The base it was made from, Fossify Phone, is named, as its
 * licence asks.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = stringResource(R.string.about_title, BuildConfig.VERSION_NAME), onBack = onBack) {
        item(key = "what") { AboutText(stringResource(R.string.about_what)) }
        item(key = "privacy_h") { SettingsHeading(stringResource(R.string.about_privacy_heading)) }
        item(key = "privacy") { AboutText(stringResource(R.string.about_privacy)) }
        item(key = "terms_h") { SettingsHeading(stringResource(R.string.about_terms_heading)) }
        item(key = "terms") { AboutText(stringResource(R.string.about_terms)) }
        item(key = "made_h") { SettingsHeading(stringResource(R.string.about_made_heading)) }
        item(key = "licence") { AboutText(stringResource(R.string.about_licence)) }
        item(key = "base") { AboutText(stringResource(R.string.about_base)) }
        item(key = "licences") { Box(Modifier.padding(horizontal = 24.dp)) { LicencesLink() } }
        item(key = "source") {
            AboutLink(stringResource(R.string.about_source_text)) { openAddress(context, "https://github.com/wanderwildwood/itodenwa") }
        }
        item(key = "llama") {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { Llama() }
        }
    }
}

@Composable
private fun AboutText(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** A line that opens an address: 48 dp tall to press. */
@Composable
private fun AboutLink(text: String, onPress: () -> Unit) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onPress)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

private fun openAddress(context: android.content.Context, address: String) {
    openLink(context, address, context.getString(R.string.about_no_browser_address, address.removePrefix("https://")))
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to. The site's
 * address sits at the start of the same line and opens the site; the llama and its words
 * open the page.
 *
 * Straight to the checkout: the Donate button on the site only leads there anyway. The short
 * square.link form, which is what the site itself links to, so a regenerated checkout follows
 * it. A phone with nothing that opens a web address says so rather than doing nothing.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The site's address opens the site, the way the llama beside it opens its page.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .clickable { openAddress(context, "https://wanderthe.dev") }
                .heightIn(min = 48.dp),
        ) {
            TextMMD(text = "wanderthe.dev", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    openAddress(context, "https://square.link/u/AGu8oT10")
                }
                .heightIn(min = 48.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = MaterialTheme.typography.labelSmall)
        }
    }
}
