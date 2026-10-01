package com.flightradius.app.data.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import com.flightradius.app.MainActivity
import com.flightradius.app.R
import java.io.File

/** PackageInstaller session wrapper (the only place that talks to the system installer). */
class AndroidPackageInstallGateway(private val context: Context) : PackageInstallGateway {

    override fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun commit(apk: File, version: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("FlightRadius-$version.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            session.commit(resultSender(sessionId))
        }
    }

    // FLAG_MUTABLE is required: the system fills the status extras
    // (EXTRA_STATUS, EXTRA_INTENT, ...) into this PendingIntent's intent before
    // sending it. The intent is explicit and targets our non-exported receiver.
    @SuppressLint("MutableImplicitPendingIntent")
    private fun resultSender(sessionId: Int) = PendingIntent.getBroadcast(
        context, sessionId,
        Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    ).intentSender

    companion object {
        const val ACTION_RESULT = "com.flightradius.app.action.INSTALL_RESULT"
    }
}

/** Low-importance "Updates" channel: download progress and the tap-to-install prompt. */
class AndroidUpdateNotifier(private val context: Context) : UpdateNotifier {

    private val nm get() = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL, context.getString(R.string.updates_channel_name),
                NotificationManager.IMPORTANCE_LOW))
    }

    private fun base() = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_radar)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

    override fun progress(version: String, percent: Int) = runCatching {
        ensureChannel()
        nm.notify(ID, base()
            .setContentTitle(context.getString(R.string.updates_downloading_title, version))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .build())
    }.let { }

    override fun tapToInstall(version: String, confirm: Intent) = runCatching {
        ensureChannel()
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        nm.notify(ID, base()
            .setContentTitle(context.getString(R.string.updates_tap_to_install, version))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 1, confirm,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build())
    }.let { }

    override fun clear() = nm.cancel(ID)

    private companion object {
        const val CHANNEL = "updates"
        const val ID = 7301
    }
}
