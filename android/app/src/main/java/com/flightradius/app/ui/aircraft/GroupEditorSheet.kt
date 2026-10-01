package com.flightradius.app.ui.aircraft

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.repo.FLEET_COLOR_PALETTE
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.GroupIcon
import com.flightradius.app.ui.components.onGroupColor
import com.flightradius.app.ui.components.vector
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.settings.SteppedRadiusSlider
import com.flightradius.app.ui.theme.extended

/**
 * Create / edit a group: name (unique), colour, one of 16 icons and an
 * optional alert radius. Membership is managed from the aircraft themselves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupEditorSheet(
    group: Fleet?,
    existing: List<Fleet>,
    settings: AppSettings,
    onSave: (name: String, colorArgb: Int, icon: GroupIcon, radiusKm: Double?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var color by remember { mutableStateOf(group?.colorArgb ?: FLEET_COLOR_PALETTE[0]) }
    var icon by remember { mutableStateOf(group?.icon ?: GroupIcon.PLANE) }
    var useRadius by remember { mutableStateOf(group?.alertRadiusKm != null) }
    var radiusKm by remember {
        mutableStateOf(group?.alertRadiusKm ?: settings.globalAlertRadiusKm)
    }
    val unit = settings.distanceUnit
    val trimmed = name.trim()
    val taken = existing.any { it.name.equals(trimmed, true) && it.id != group?.id }

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(
                if (group == null) R.string.fleets_add_title else R.string.fleets_edit_title),
            style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("group-name"),
            label = { Text(stringResource(R.string.fleets_name)) },
            singleLine = true,
            isError = taken,
            supportingText = if (taken) {
                { Text(stringResource(R.string.group_name_taken)) }
            } else null
        )

        Text(
            stringResource(R.string.fleets_color),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in FLEET_COLOR_PALETTE) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { color = c },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                    )
                    if (c == color) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(onGroupColor(color).copy(alpha = 0.9f))
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.group_editor_icon),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp))
        // 4 x 4 grid of 48dp targets.
        Column {
            GroupIcon.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in row) {
                        val selected = i == icon
                        // Equal-width cells; the 48dp target is centred in each.
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color(color) else Color.Transparent)
                                    .clickable { icon = i }
                                    .semantics {
                                        contentDescription = i.name
                                        role = Role.RadioButton
                                        this.selected = selected
                                    }
                                    .testTag("group-icon-${i.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    i.vector(),
                                    contentDescription = null,
                                    tint = if (selected) onGroupColor(color)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                stringResource(R.string.fleets_radius_override),
                Modifier.weight(1f))
            Switch(checked = useRadius, onCheckedChange = { useRadius = it })
        }
        if (useRadius) {
            Text(
                Format.radius(radiusKm, unit),
                style = MaterialTheme.typography.bodyMedium)
            SteppedRadiusSlider(radiusKm, unit, { radiusKm = it })
        }

        Row(Modifier.padding(top = 12.dp)) {
            Spacer(Modifier.weight(1f))
            Button(
                enabled = trimmed.isNotEmpty() && !taken,
                onClick = { onSave(trimmed, color, icon, if (useRadius) radiusKm else null) }
            ) { Text(stringResource(R.string.action_save)) }
        }
        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    stringResource(R.string.fleets_delete),
                    color = MaterialTheme.colorScheme.extended.danger)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
