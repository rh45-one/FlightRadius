package com.flightradius.app.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Starts [intent] without ever crashing. Some OEMs lack the system settings
 * activities we point at, which throws [ActivityNotFoundException].
 *
 * @param fallbackToAppDetails when the intent can't be launched, open this app's
 *   system "App info" page instead (sensible for settings intents).
 * @return true when [intent] itself was launched.
 */
fun Context.startActivitySafely(intent: Intent, fallbackToAppDetails: Boolean = false): Boolean {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
        return true
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    if (fallbackToAppDetails && intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        if (this !is Activity) details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(details)
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    return false
}
