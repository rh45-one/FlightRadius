package com.flightradius.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.flightradius.app.ui.components.groupedSegmentedColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.data.api.ApiSettingsStatusDto
import com.flightradius.app.data.opensky.CreditState
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.secure.StoredCredentials
import com.flightradius.app.domain.CreditPlanner
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.components.GroupedTextField
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended

@Composable
internal fun AccentRow(
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    GroupedRow(
        title = title,
        titleColor = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        onClick = if (enabled) onClick else null
    )
}

@Composable
internal fun SecretField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    GroupedTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false)
    )
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun MessageRow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    GroupedRow(title = text, titleColor = color, titleStyle = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun DataSourceSection(selected: DataSource, onSelect: (DataSource) -> Unit) {
    GroupedSection(
        header = stringResource(R.string.settings_data_source),
        footer = stringResource(
            if (selected == DataSource.DIRECT) R.string.data_source_direct_body
            else R.string.data_source_backend_body
        )
    ) {
        val options = listOf(
            DataSource.DIRECT to stringResource(R.string.data_source_direct),
            DataSource.BACKEND to stringResource(R.string.data_source_backend)
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            options.forEachIndexed { index, (source, label) ->
                SegmentedButton(
                    selected = selected == source,
                    onClick = { onSelect(source) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = groupedSegmentedColors()
                ) { Text(label) }
            }
        }
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

    GroupedSection(
        header = stringResource(R.string.settings_opensky_account),
        footer = stringResource(R.string.creds_direct_hint)
    ) {
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
        GroupedRow(
            title = statusText,
            titleStyle = MaterialTheme.typography.bodyMedium,
            leading = { StatusDot(statusColor) }
        )
        GroupedDivider()
        SecretField(stringResource(R.string.creds_client_id), clientId) { clientId = it }
        SecretField(stringResource(R.string.creds_client_secret), clientSecret) { clientSecret = it }
        GroupedDivider()
        AccentRow(
            title = stringResource(R.string.creds_save_verify),
            enabled = clientId.isNotBlank() && clientSecret.isNotBlank() &&
                check != CredentialCheck.Checking,
            onClick = {
                onSave(clientId, clientSecret)
                clientId = ""
                clientSecret = ""
            }
        )
        if (stored is StoredCredentials.Present) {
            GroupedDivider()
            AccentRow(
                title = stringResource(R.string.creds_verify),
                enabled = check != CredentialCheck.Checking,
                onClick = onVerify
            )
        }
        if (stored !is StoredCredentials.None) {
            GroupedDivider()
            AccentRow(
                title = stringResource(R.string.creds_remove),
                onClick = { confirmRemove = true }
            )
        }
        when (check) {
            CredentialCheck.Checking -> {
                GroupedDivider()
                MessageRow(stringResource(R.string.checking))
            }
            is CredentialCheck.Failed -> if (!check.rejected) {
                GroupedDivider()
                MessageRow(check.message, MaterialTheme.colorScheme.extended.danger)
            }
            CredentialCheck.Removed -> {
                GroupedDivider()
                MessageRow(stringResource(R.string.creds_removed))
            }
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

    GroupedSection(
        header = stringResource(R.string.settings_opensky_backend),
        footer = stringResource(R.string.settings_creds_hint)
    ) {
        apiStatus?.api?.let { api ->
            val configured = api.clientConfigured == true
            GroupedRow(
                title = stringResource(
                    R.string.creds_auth_mode,
                    if (configured) "OAuth2" else stringResource(R.string.creds_auth_anonymous)
                ),
                titleStyle = MaterialTheme.typography.bodyMedium,
                leading = {
                    StatusDot(
                        if (configured) MaterialTheme.colorScheme.extended.success
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            GroupedDivider()
        }
        apiStatusError?.let {
            MessageRow(it, MaterialTheme.colorScheme.extended.danger)
            GroupedDivider()
        }
        SecretField(stringResource(R.string.creds_client_id), clientId) { clientId = it }
        SecretField(stringResource(R.string.creds_client_secret), clientSecret) { clientSecret = it }
        GroupedDivider()
        AccentRow(
            title = stringResource(R.string.creds_send),
            enabled = clientId.isNotBlank() || clientSecret.isNotBlank(),
            onClick = {
                onSend(clientId, clientSecret) { ok, msg ->
                    message = if (ok) sent else msg
                    if (ok) { clientId = ""; clientSecret = "" }
                }
            }
        )
        GroupedDivider()
        AccentRow(
            title = stringResource(R.string.creds_clear),
            onClick = { confirmClear = true }
        )
        message?.let {
            GroupedDivider()
            MessageRow(it)
        }
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

/** Credit balance and current pace. */
@Composable
internal fun CreditsSection(
    credits: CreditState,
    monitoring: MonitoringState,
    userIntervalSec: Int,
    nowMs: Long
) {
    GroupedSection(header = stringResource(R.string.settings_credits)) {
        val remaining = credits.remaining
        val quota = credits.dailyQuota
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (remaining == null) {
                Text(stringResource(R.string.credits_unknown), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.credits_remaining, remaining, quota),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFeatureSettings = NumericFeatures)
                )
                LinearProgressIndicator(
                    progress = { (remaining.toFloat() / quota).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
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
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = NumericFeatures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            credits.blockedUntilMs?.takeIf { it > nowMs }?.let { until ->
                Text(
                    stringResource(R.string.credits_blocked, Format.duration((until - nowMs) / 1000)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.extended.danger
                )
            }
        }
    }
}
