package com.flightradius.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.format.RadiusInput
import com.flightradius.app.ui.theme.NumericFeatures
import kotlin.math.abs

/** Default alert radius: stepped slider plus tap-the-value for an exact number. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultRadiusRow(
    radiusKm: Double,
    unit: DistanceUnit,
    onChange: (Double) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val steps = RadiusInput.steps(unit)
    val index = RadiusInput.nearestIndex(radiusKm, unit)

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_alert_radius),
                    style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_alert_radius_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                Format.radius(radiusKm, unit),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFeatureSettings = NumericFeatures),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .testTag("default-radius-value")
                    .clickable { showDialog = true }
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { v ->
                val km = RadiusInput.kmAt(v.toInt(), unit)
                if (abs(km - radiusKm) > 1e-6) onChange(km)
            },
            valueRange = 0f..(steps.size - 1).toFloat(),
            steps = steps.size - 2,
            track = { state ->
                // 80 tick marks are noise; the value label shows the position.
                SliderDefaults.Track(state, drawStopIndicator = null, drawTick = { _, _ -> })
            }
        )
    }

    if (showDialog) {
        RadiusDialog(
            initialKm = radiusKm,
            unit = unit,
            onDismiss = { showDialog = false },
            onSave = { km -> onChange(km); showDialog = false }
        )
    }
}

@Composable
private fun RadiusDialog(
    initialKm: Double,
    unit: DistanceUnit,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text by remember { mutableStateOf(RadiusInput.toFieldText(initialKm, unit)) }
    val parsed = RadiusInput.parseKm(text, unit)
    var showError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_alert_radius)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; showError = false },
                    singleLine = true,
                    label = {
                        Text(stringResource(R.string.radius_field_label,
                            Format.distanceUnitLabel(unit)))
                    },
                    isError = showError && parsed == null,
                    supportingText = {
                        if (showError && parsed == null) {
                            Text(stringResource(
                                R.string.radius_error_range,
                                Format.radius(RadiusInput.MIN_KM, unit),
                                Format.radius(RadiusInput.MAX_KM, unit)))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("radius-dialog-field")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (parsed != null) onSave(parsed) else showError = true
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
