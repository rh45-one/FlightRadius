package com.flightradius.app.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives PackageInstaller session results (non-exported; see the manifest entry). */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject lateinit var installer: UpdateInstaller

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        @Suppress("DEPRECATION")
        val confirm = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        } else null
        installer.onInstallStatus(status, confirm)
    }
}
