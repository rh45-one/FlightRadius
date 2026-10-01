package com.flightradius.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.GroupIcon

/** A group's icon in a circle tinted with the group colour. */
@Composable
fun GroupBadge(
    icon: GroupIcon,
    colorArgb: Int,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val color = Color(colorArgb)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon.vector(),
            contentDescription = null,
            tint = onGroupColor(colorArgb),
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

/**
 * "Group" dropdown: None, every group (badge + name), a divider and "New group…".
 * [selectedId] null = no group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPicker(
    groups: List<Fleet>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = groups.find { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name ?: stringResource(R.string.group_none),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.group_label)) },
            leadingIcon = selected?.let { { GroupBadge(it.icon, it.colorArgb, 24.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("group-picker")
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_none)) },
                onClick = { onSelect(null); expanded = false },
                modifier = Modifier.testTag("group-option-none")
            )
            for (g in groups) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GroupBadge(g.icon, g.colorArgb, 24.dp)
                            Text(g.name, modifier = Modifier.padding12())
                        }
                    },
                    onClick = { onSelect(g.id); expanded = false },
                    modifier = Modifier.testTag("group-option-${g.id}")
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_new)) },
                leadingIcon = {
                    Icon(Icons.Filled.Add, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                },
                onClick = { expanded = false; onCreateNew() },
                modifier = Modifier.testTag("group-option-new")
            )
        }
    }
}

private fun Modifier.padding12(): Modifier =
    this.then(Modifier.padding(start = 12.dp))
