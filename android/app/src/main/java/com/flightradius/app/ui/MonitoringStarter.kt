package com.flightradius.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.flightradius.app.data.api.LocalNetwork
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.service.MonitoringController

/**
 * Ordered permission chain before starting monitoring:
 * 1. POST_NOTIFICATIONS (33+; continue regardless of result).
 * 2. FINE+COARSE location when in GPS mode (denied -> offer manual / abort).
 * 3. ACCESS_LOCAL_NETWORK (37+, LAN backend; denied -> abort).
 * 4. controller.start(fromUser = true) + refreshIfNeeded().
 */
class MonitoringStarter(
    private val activity: Activity?,
    private val settings: () -> AppSettings,
    private val controller: MonitoringController,
    private val locationRepository: LocationRepository,
    private val localNetworkGuard: LocalNetworkGuard,
    private val onGoToLocationSettings: () -> Unit
) {
    sealed interface DialogSpec {
        data object NotificationsRationale : DialogSpec
        data object LocationDenied : DialogSpec
        data object LanRationale : DialogSpec
        data object LanDenied : DialogSpec
    }

    var dialog by mutableStateOf<DialogSpec?>(null)
        private set

    /** Set by the composable wrapper — launches a runtime-permission request. */
    var launchRequest: (Array<String>) -> Unit = {}

    private var pendingStage: Stage? = null

    private enum class Stage { NOTIFICATIONS, LOCATION, LAN, DONE }

    private fun has(permission: String): Boolean =
        activity != null && ContextCompat.checkSelfPermission(
            activity, permission) == PackageManager.PERMISSION_GRANTED

    fun begin() {
        advance(Stage.NOTIFICATIONS)
    }

    private fun advance(stage: Stage) {
        when (stage) {
            Stage.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    !has(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    pendingStage = Stage.NOTIFICATIONS
                    dialog = DialogSpec.NotificationsRationale
                } else {
                    advance(Stage.LOCATION)
                }
            }
            Stage.LOCATION -> {
                if (settings().locationMode == LocationMode.GPS &&
                    !locationRepository.hasPermission()
                ) {
                    pendingStage = Stage.LOCATION
                    launchRequest(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    advance(Stage.LAN)
                }
            }
            Stage.LAN -> {
                if (localNetworkGuard.isBlocked()) {
                    pendingStage = Stage.LAN
                    dialog = DialogSpec.LanRationale
                } else {
                    advance(Stage.DONE)
                }
            }
            Stage.DONE -> {
                pendingStage = null
                dialog = null
                controller.start(fromUser = true)
                locationRepository.refreshIfNeeded()
            }
        }
    }

    fun onDialogContinue() {
        val stage = pendingStage
        dialog = null
        when (stage) {
            Stage.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) {
                    launchRequest(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            Stage.LAN -> launchRequest(arrayOf(LocalNetwork.PERMISSION))
            else -> Unit
        }
    }

    fun onDialogDismiss() {
        when (pendingStage) {
            // Notifications are optional — keep going.
            Stage.NOTIFICATIONS -> advance(Stage.LOCATION)
            else -> pendingStage = null
        }
        dialog = null
    }

    /** Called by the permission launcher regardless of grants. */
    fun onPermissionsResult(grants: Map<String, Boolean>) {
        when (pendingStage) {
            Stage.NOTIFICATIONS -> advance(Stage.LOCATION)
            Stage.LOCATION -> {
                if (locationRepository.hasPermission()) {
                    advance(Stage.LAN)
                } else {
                    dialog = DialogSpec.LocationDenied
                }
            }
            Stage.LAN -> {
                if (!localNetworkGuard.isBlocked()) {
                    advance(Stage.DONE)
                } else {
                    dialog = DialogSpec.LanDenied
                }
            }
            else -> Unit
        }
    }

    fun useManualLocation() {
        dialog = null
        pendingStage = null
        onGoToLocationSettings()
    }

    fun openAppSettings() {
        dialog = null
        pendingStage = null
        activity?.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
        )
    }

    fun shouldShowRationale(permission: String): Boolean =
        activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

/**
 * Registers the permission launcher and renders the rationale/abort dialogs.
 * Returns the starter; call [MonitoringStarter.begin] to run the chain.
 */
@Composable
fun rememberMonitoringStarter(
    controller: MonitoringController,
    locationRepository: LocationRepository,
    localNetworkGuard: LocalNetworkGuard,
    settings: AppSettings,
    onGoToLocationSettings: () -> Unit
): MonitoringStarter {
    val context = LocalContext.current
    val settingsState = androidx.compose.runtime.rememberUpdatedState(settings)
    val starter = remember {
        MonitoringStarter(
            activity = context as? Activity,
            settings = { settingsState.value },
            controller = controller,
            locationRepository = locationRepository,
            localNetworkGuard = localNetworkGuard,
            onGoToLocationSettings = onGoToLocationSettings
        )
    }
    // Keep the settings lambda fresh.
    val fresh = remember(starter) { starter }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> fresh.onPermissionsResult(grants) }
    fresh.launchRequest = { launcher.launch(it) }

    when (val d = fresh.dialog) {
        MonitoringStarter.DialogSpec.NotificationsRationale -> StarterDialog(
            title = "Proximity alert notifications",
            body = "Proximity alerts are delivered as notifications. " +
                "Allow notifications so you don't miss an aircraft entering " +
                "your alert radius.",
            confirm = "Continue",
            onConfirm = { fresh.onDialogContinue() },
            onDismiss = { fresh.onDialogDismiss() }
        )
        MonitoringStarter.DialogSpec.LocationDenied -> StarterDialog(
            title = "Location permission needed",
            body = "GPS monitoring needs location access. Grant it in system " +
                "settings, or switch to a manual location instead.",
            confirm = "Open settings",
            dismiss = "Use manual location",
            onConfirm = { fresh.openAppSettings() },
            onDismiss = { fresh.useManualLocation() }
        )
        MonitoringStarter.DialogSpec.LanRationale -> StarterDialog(
            title = "Nearby devices access",
            body = "Your backend is on your local network. On Android 17+ " +
                "reaching it requires the 'Nearby devices' permission.",
            confirm = "Continue",
            onConfirm = { fresh.onDialogContinue() },
            onDismiss = { fresh.onDialogDismiss() }
        )
        MonitoringStarter.DialogSpec.LanDenied -> StarterDialog(
            title = "Backend unreachable",
            body = "Without 'Nearby devices' access the app can't reach your " +
                "local-network backend. Grant it in app settings to monitor.",
            confirm = "Open settings",
            onConfirm = { fresh.openAppSettings() },
            onDismiss = { fresh.onDialogDismiss() }
        )
        null -> Unit
    }
    return fresh
}

@Composable
private fun StarterDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = dismiss?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        }
    )
}
