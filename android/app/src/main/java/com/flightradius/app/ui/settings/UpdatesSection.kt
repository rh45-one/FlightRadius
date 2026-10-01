package com.flightradius.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.flightradius.app.BuildConfig
import com.flightradius.app.R
import com.flightradius.app.data.update.ManualCheckState
import com.flightradius.app.domain.UpdateFrequency
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.components.NavigationChevron
import com.flightradius.app.ui.theme.NumericFeatures

@Composable
private fun UpdateFrequency.label(): String = stringResource(
    when (this) {
        UpdateFrequency.ON_LAUNCH -> R.string.update_freq_on_launch
        UpdateFrequency.DAILY -> R.string.update_freq_daily
        UpdateFrequency.WEEKLY -> R.string.update_freq_weekly
        UpdateFrequency.NEVER -> R.string.update_freq_never
    }
)

@Composable
internal fun UpdatesSection(
    frequency: UpdateFrequency,
    onFrequency: (UpdateFrequency) -> Unit,
    state: ManualCheckState,
    onCheckNow: () -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    GroupedSection(header = stringResource(R.string.settings_updates)) {
        Box {
            GroupedRow(
                title = stringResource(R.string.settings_update_frequency),
                trailing = {
                    Text(
                        frequency.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NavigationChevron()
                },
                onClick = { menu = true }
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for (f in UpdateFrequency.entries) {
                    DropdownMenuItem(
                        text = { Text(f.label(), style = MaterialTheme.typography.bodyLarge) },
                        trailingIcon = {
                            if (f == frequency) {
                                Icon(Icons.Filled.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = { onFrequency(f); menu = false }
                    )
                }
            }
        }
        GroupedDivider()
        val status = when (state) {
            ManualCheckState.Idle -> null
            ManualCheckState.Checking -> stringResource(R.string.update_checking)
            is ManualCheckState.UpToDate -> stringResource(R.string.update_up_to_date, state.current)
            is ManualCheckState.Available -> stringResource(R.string.update_available, state.version)
            is ManualCheckState.Failed -> stringResource(R.string.update_check_failed)
        }
        GroupedRow(
            title = stringResource(R.string.update_check_now),
            trailing = {
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFeatureSettings = NumericFeatures),
                        color = if (state is ManualCheckState.Available) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            onClick = {
                if (state is ManualCheckState.Available) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(state.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else onCheckNow()
            }
        )
        GroupedDivider()
        GroupedRow(
            title = stringResource(R.string.update_current_version),
            trailing = {
                Text(
                    BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = NumericFeatures),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )
    }
}
