package com.flightradius.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.ui.theme.extended
import kotlinx.coroutines.delay

/**
 * Recomposes every [periodMs] with the current epoch millis — shared ticker
 * for "12 s ago" labels.
 */
@Composable
fun rememberNow(periodMs: Long = 1000): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(periodMs) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

/** Non-blocking issue/warning card with an optional action. */
@Composable
fun IssueCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.extended.warning,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = tone.copy(alpha = 0.10f)
        )
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tone)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

/** Zone color: inside = danger, near = warning, clear = neutral. */
@Composable
fun zoneColor(distanceKm: Double, radiusKm: Double): Color {
    val ext = MaterialTheme.colorScheme.extended
    return when {
        distanceKm <= radiusKm -> ext.danger
        distanceKm <= radiusKm * 2 -> ext.warning
        else -> MaterialTheme.colorScheme.onSurface
    }
}
