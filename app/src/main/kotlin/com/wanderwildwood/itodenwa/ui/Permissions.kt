package com.wanderwildwood.itodenwa.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.wanderwildwood.itodenwa.R

/**
 * Asks for a permission so that a refusal for good is never a dead end. Android stops showing
 * the question after it has been refused twice, and then a press does nothing at all; so once a
 * refusal comes back with no further question to ask, the press says why the permission is
 * needed and opens this app's page in Android's settings, where it can be allowed.
 *
 * [onResult] hears every answer; the returned function is what a press calls.
 */
@Composable
internal fun rememberPermissionAsker(permission: String, onResult: (granted: Boolean, stuck: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var stuck by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        stuck = !granted && refusedForGood(context, permission)
        if (stuck) sayAndOpenSettings(context)
        onResult(granted, stuck)
    }
    return {
        if (stuck && !has(context, permission)) sayAndOpenSettings(context) else ask.launch(permission)
    }
}

private fun has(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** True once the permission was refused and Android has no more questions to ask about it. */
private fun refusedForGood(context: Context, permission: String): Boolean {
    val activity = context.findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

internal fun sayAndOpenSettings(context: Context) {
    Toast.makeText(context, context.getString(R.string.permission_open_settings_note), Toast.LENGTH_LONG).show()
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
