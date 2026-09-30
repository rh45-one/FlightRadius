package com.flightradius.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.ui.components.ProximityDial
import com.flightradius.app.ui.theme.InterDisplay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WelcomeScreen(
    onAddFirstAircraft: () -> Unit,
    onNotNow: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    Modifier
                        .widthIn(max = 480.dp)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProximityDial(
                        size = 220.dp,
                        markerBearingDeg = 45.0,
                        markerHeadingDeg = 45.0
                    )
                    Spacer(Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = InterDisplay),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.welcome_tagline),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.size(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        FeatureRow(
                            Icons.Filled.Search,
                            stringResource(R.string.welcome_follow_title),
                            stringResource(R.string.welcome_follow_body)
                        )
                        FeatureRow(
                            null,
                            stringResource(R.string.welcome_glance_title),
                            stringResource(R.string.welcome_glance_body)
                        )
                        FeatureRow(
                            Icons.Filled.Notifications,
                            stringResource(R.string.welcome_alerts_title),
                            stringResource(R.string.welcome_alerts_body)
                        )
                    }
                }
            }
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onAddFirstAircraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        stringResource(R.string.welcome_add_first),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TextButton(
                    onClick = onNotNow,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.welcome_not_now)) }
                Text(
                    stringResource(R.string.welcome_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DialGlyph() {
    val tint = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val r = size.minDimension / 2f - stroke / 2f - 1.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(tint, radius = r, center = c, style = Stroke(stroke))
        val a = Math.toRadians(-45.0)
        drawCircle(
            tint, radius = 3.dp.toPx(),
            center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector?, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            DialGlyph()
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
