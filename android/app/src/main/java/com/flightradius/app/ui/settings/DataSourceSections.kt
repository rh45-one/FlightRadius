package com.flightradius.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.data.api.ApiSettingsStatusDto
import com.flightradius.app.data.opensky.CreditState
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.secure.StoredCredentials
import com.flightradius.app.domain.CreditPlanner
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.ui.components.SectionHeader
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.extended

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
internal fun DataSourceSection(selected: DataSource, onSelect: (DataSource) -> Unit) {
    SectionHeader(stringResource(R.string.settings_data_source))
    SettingsCard {
        val options = listOf(
            DataSource.DIRECT to stringResource(R.string.data_source_direct),
            DataSource.BACKEND to stringResource(R.string.data_source_backend)
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (source, label) ->
                SegmentedButton(
                    selected = selected == source,
                    onClick = { onSelect(source) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (selected == DataSource.DIRECT) R.string.data_source_direct_body
                else R.string.data_source_backend_body
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Direct mode: the API client lives encrypted on this device. */
@Composable
internal fun OpenSkyAccountSection(
    stored: StoredCredentials,
    check: CredentialCheck,
    onSave: (clientId: String, clientSecret: String) -> Unit,
    onVerify: () -> Unit,
    onRemove: () -> Unit
) {
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_opensky_account))
    SettingsCard {
        val (statusText, statusColor) = when {
            check is CredentialCheck.Failed && check.rejected ->
                stringResource(R.string.creds_status_rejected) to MaterialTheme.colorScheme.extended.danger
            stored is StoredCredentials.Unreadable ->
                stringResource(R.string.creds_status_unreadable) to MaterialTheme.colorScheme.extended.warning
            stored is StoredCredentials.Present && check is CredentialCheck.Verified ->
                stringResource(R.string.creds_status_verified) to MaterialTheme.colorScheme.extended.success
            stored is StoredCredentials.Present ->
                stringResource(R.string.creds_status_stored) to MaterialTheme.colorScheme.extended.success
            else -> stringResource(R.string.creds_status_anonymous) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        StatusLine(statusText, statusColor)
        Text(
            stringResource(R.string.creds_direct_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SecretField(stringResource(R.string.creds_client_id), clientId) { clientId = it }
        SecretField(stringResource(R.string.creds_client_secret), clientSecret) { clientSecret = it }
        Row {
            TextButton(
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank() &&
                    check != CredentialCheck.Checking,
                onClick = {
                    onSave(clientId, clientSecret)
                    clientId = ""
                    clientSecret = ""
                }
            ) { Text(stringResource(R.string.creds_save_verify)) }
            if (stored is StoredCredentials.Present) {
                TextButton(enabled = check != CredentialCheck.Checking, onClick = onVerify) {
                    Text(stringResource(R.string.creds_verify))
                }
            }
            if (stored !is StoredCredentials.None) {
                TextButton(onClick = { confirmRemove = true }) {
                    Text(stringResource(R.string.creds_remove))
                }
            }
        }
        when (check) {
            CredentialCheck.Checking -> Text(
                stringResource(R.string.checking), style = MaterialTheme.typography.bodySmall
            )
            is CredentialCheck.Failed -> if (!check.rejected) Text(
                check.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.extended.danger
            )
            CredentialCheck.Removed -> Text(
                stringResource(R.string.creds_removed), style = MaterialTheme.typography.bodySmall
            )
            else -> Unit
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.creds_remove_title)) },
            text = { Text(stringResource(R.string.creds_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text(stringResource(R.string.creds_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** Backend mode: the API client is forwarded to (and stored by) the backend. */
@Composable
internal fun BackendCredentialsSection(
    apiStatus: ApiSettingsStatusDto?,
    apiStatusError: String?,
    onSend: (clientId: String, clientSecret: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onClear: (onDone: (Boolean, String?) -> Unit) -> Unit
) {
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val sent = stringResource(R.string.creds_sent)
    val cleared = stringResource(R.string.creds_cleared)

    SectionHeader(stringResource(R.string.settings_opensky_backend))
    SettingsCard {
        apiStatus?.api?.let { api ->
            val configured = api.clientConfigured == true
            StatusLine(
                stringResource(
                    R.string.creds_auth_mode,
                    if (configured) "OAuth2" else stringResource(R.string.creds_auth_anonymous)
                ),
                if (configured) MaterialTheme.colorScheme.extended.success
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        apiStatusError?.let {
            Text(it, color = MaterialTheme.colorScheme.extended.danger, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.settings_creds_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SecretField(stringResource(R.string.creds_client_id), clientId) { clientId = it }
        SecretField(stringResource(R.string.creds_client_secret), clientSecret) { clientSecret = it }
        Row {
            TextButton(
                enabled = clientId.isNotBlank() || clientSecret.isNotBlank(),
                onClick = {
                    onSend(clientId, clientSecret) { ok, msg ->
                        message = if (ok) sent else msg
                        if (ok) { clientId = ""; clientSecret = "" }
                    }
                }
            ) { Text(stringResource(R.string.creds_send)) }
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.creds_clear)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.creds_clear_title)) },
            text = { Text(stringResource(R.string.creds_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear { ok, msg -> message = if (ok) cleared else msg }
                }) { Text(stringResource(R.string.creds_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** Credit balance, current pace and the adaptive-interval switch. */
@Composable
internal fun CreditsSection(
    credits: CreditState,
    monitoring: MonitoringState,
    userIntervalSec: Int,
    adaptive: Boolean,
    nowMs: Long,
    onAdaptiveChange: (Boolean) -> Unit
) {
    SectionHeader(stringResource(R.string.settings_credits))
    SettingsCard {
        val remaining = credits.remaining
        val quota = credits.dailyQuota
        if (remaining == null) {
            Text(stringResource(R.string.credits_unknown), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(R.string.credits_remaining, remaining, quota),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (remaining.toFloat() / quota).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        val interval = monitoring.plannedIntervalSec ?: userIntervalSec
        val perCycle = monitoring.creditsPerCycle
        val paceLines = buildList {
            add(stringResource(R.string.credits_interval, Format.duration(interval.toLong())))
            if (perCycle > 0) add(stringResource(R.string.credits_per_cycle, perCycle))
            remaining?.let { CreditPlanner.coverageHours(it, perCycle, interval) }?.let {
                add(stringResource(R.string.credits_coverage, Format.hours(it)))
            }
        }
        Text(
            paceLines.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        credits.blockedUntilMs?.takeIf { it > nowMs }?.let { until ->
            Text(
                stringResource(R.string.credits_blocked, Format.duration((until - nowMs) / 1000)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.extended.danger
            )
        }
        SwitchRow(
            title = stringResource(R.string.credits_adaptive),
            body = stringResource(R.string.credits_adaptive_body),
            checked = adaptive,
            onChange = onAdaptiveChange
        )
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text("\u25cf  $text", style = MaterialTheme.typography.bodyMedium, color = color)
    Spacer(Modifier.height(4.dp))
}
