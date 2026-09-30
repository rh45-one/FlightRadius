package com.flightradius.app.ui.settings

import com.flightradius.app.ui.format.displayName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AirspaceRule
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.components.ScreenTitle
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.format.labelRes
import com.flightradius.app.ui.theme.NumericFeatures
import kotlin.math.roundToInt

private const val MIN_ALT_M = 150f
private const val MAX_ALT_M = 6_000f

@Composable
internal fun ruleSummary(rule: AirspaceRule, unit: DistanceUnit): String = buildList {
    add(
        if (rule.classes.isEmpty()) stringResource(R.string.rules_any_aircraft)
        else rule.classes.sortedBy { it.code }.map { stringResource(it.labelRes()) }.joinToString(", ")
    )
    add(Format.distance(rule.radiusKm, unit))
    rule.maxAltitudeM?.let {
        add(stringResource(R.string.rules_below, Format.altitudeLimit(it, unit)))
    }
}.joinToString(" · ")

@Composable
fun AirspaceRulesScreen(
    onBack: () -> Unit,
    viewModel: AirspaceRulesViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val unit = settings.distanceUnit
    var editing by remember { mutableStateOf<AirspaceRule?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val newName = stringResource(R.string.rules_new_name)

    AirspaceRulesContent(
        rules = settings.airspaceRules,
        unit = unit,
        onBack = onBack,
        onToggle = { id, on -> viewModel.setEnabled(id, on) },
        onEdit = { isNew = false; editing = it },
        onAdd = { isNew = true; editing = viewModel.newRule(newName) }
    )

    editing?.let { rule ->
        RuleEditorSheet(
            rule = rule,
            unit = unit,
            isNew = isNew,
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(rule.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
internal fun AirspaceRulesContent(
    rules: List<AirspaceRule>,
    unit: DistanceUnit,
    onBack: () -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onEdit: (AirspaceRule) -> Unit,
    onAdd: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.rules_back))
            }
            ScreenTitle(stringResource(R.string.rules_title), Modifier.weight(1f))
        }
        GroupedSection(
            header = null,
            footer = stringResource(R.string.rules_footer)
        ) {
            rules.forEachIndexed { i, rule ->
                if (i > 0) GroupedDivider()
                GroupedRow(
                    title = rule.displayName(),
                    subtitle = ruleSummary(rule, unit),
                    subtitleStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = NumericFeatures),
                    trailing = {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule.id, it) }
                        )
                    },
                    onClick = { onEdit(rule) }
                )
            }
            if (rules.isNotEmpty()) GroupedDivider()
            AccentRow(title = stringResource(R.string.rules_add), onClick = onAdd)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RuleEditorSheet(
    rule: AirspaceRule,
    unit: DistanceUnit,
    isNew: Boolean,
    onSave: (AirspaceRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(rule.displayName()) }
    var radiusKm by remember { mutableStateOf(rule.radiusKm) }
    var limitAltitude by remember { mutableStateOf(rule.maxAltitudeM != null) }
    var altitudeM by remember { mutableStateOf(rule.maxAltitudeM ?: 1_500.0) }
    var classes by remember { mutableStateOf(rule.classes) }

    val radiusMin = Format.kmToUnit(1.0, unit).toFloat()
    val radiusMax = Format.kmToUnit(50.0, unit).toFloat()
    val altFactor = if (unit == DistanceUnit.MI) Format.M_TO_FT.toFloat() else 1f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(
                    if (isNew) R.string.rules_add else R.string.rules_edit_title),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.rules_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Text(
                stringResource(R.string.rules_distance, Format.distance(radiusKm, unit)),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFeatureSettings = NumericFeatures),
                modifier = Modifier.padding(top = 16.dp)
            )
            Slider(
                value = Format.kmToUnit(radiusKm, unit).toFloat().coerceIn(radiusMin, radiusMax),
                onValueChange = { v ->
                    radiusKm = (if (unit == DistanceUnit.MI) v / Format.KM_TO_MI.toFloat() else v)
                        .toDouble().coerceIn(1.0, 50.0)
                },
                valueRange = radiusMin..radiusMax
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (limitAltitude) stringResource(
                        R.string.rules_altitude_below, Format.altitudeLimit(altitudeM, unit))
                    else stringResource(R.string.rules_altitude_any),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = NumericFeatures),
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = limitAltitude, onCheckedChange = { limitAltitude = it })
            }
            if (limitAltitude) {
                Slider(
                    value = (altitudeM.toFloat() * altFactor)
                        .coerceIn(MIN_ALT_M * altFactor, MAX_ALT_M * altFactor),
                    onValueChange = { v ->
                        // Snap to 50 of the display unit for readable limits.
                        val snapped = (v / 50f).roundToInt() * 50f
                        altitudeM = (snapped / altFactor).toDouble()
                            .coerceIn(MIN_ALT_M.toDouble(), MAX_ALT_M.toDouble())
                    },
                    valueRange = (MIN_ALT_M * altFactor)..(MAX_ALT_M * altFactor)
                )
            }

            Text(
                stringResource(R.string.rules_classes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (c in AircraftClass.entries.filter { it != AircraftClass.UNKNOWN }) {
                    FilterChip(
                        selected = c in classes,
                        onClick = { classes = if (c in classes) classes - c else classes + c },
                        label = { Text(stringResource(c.labelRes())) }
                    )
                }
            }
            Text(
                stringResource(R.string.rules_classes_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Button(
                onClick = {
                    onSave(
                        rule.copy(
                            name = name.trim().ifEmpty { rule.name },
                            radiusKm = radiusKm,
                            maxAltitudeM = if (limitAltitude) altitudeM else null,
                            classes = classes
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.action_save)) }
            if (!isNew) {
                TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        stringResource(R.string.rules_delete),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
