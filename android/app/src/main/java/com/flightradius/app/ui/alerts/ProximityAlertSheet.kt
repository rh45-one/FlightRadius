package com.flightradius.app.ui.alerts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flightradius.app.R
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.components.BearingArrow
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.Inter
import com.flightradius.app.ui.theme.InterDisplay
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended

/**
 * Full-screen cockpit-style proximity alert. [obs] should be the freshest
 * observation for the alerting aircraft (live distance updates); pass the
 * alert event's observation as fallback. [extraCount] = additional pending
 * alerts shown as "+N more".
 */
@Composable
fun ProximityAlertSheet(
    obs: AircraftObservation,
    extraCount: Int,
    unit: DistanceUnit,
    vibrationEnabled: Boolean,
    onDismiss: () -> Unit,
    onSnooze: (minutes: Long) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val columns = if (LocalDensity.current.fontScale < 1.5f) 3 else 2

    LaunchedEffect(obs.aircraftId) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (vibrationEnabled) vibrateAlert(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pulse by rememberInfiniteTransition(label = "alertPulse")
            .animateFloat(
                initialValue = 0.25f, targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    tween(900, easing = LinearEasing), RepeatMode.Reverse),
                label = "pulse"
            )
        val danger = MaterialTheme.colorScheme.extended.danger

        // ~0.85 black scrim behind an opaque surface.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
        ) {
            Surface(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .drawBehind {
                        drawRoundRect(
                            danger.copy(alpha = pulse),
                            cornerRadius = CornerRadius(28.dp.toPx()),
                            style = Stroke(width = 6.dp.toPx())
                        )
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = danger,
                                modifier = Modifier.size(28.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.alert_title),
                                color = danger,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                        }
                        if (extraCount > 0) {
                            Text(
                                stringResource(R.string.alert_more, extraCount),
                                color = MaterialTheme.colorScheme.extended.warning,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Centered content: callsign -> hero distance -> 2x3 grid.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Format.callsign(obs),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = Inter, fontWeight = FontWeight.Bold,
                                fontSize = 48.sp, fontFeatureSettings = CodeFeatures),
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(16.dp))
                        val animatedDist by animateFloatAsState(
                            targetValue = Format.kmToUnit(obs.distanceKm, unit).toFloat(),
                            animationSpec = tween(600),
                            label = "alertDist"
                        )
                        Text(
                            "%.1f %s".format(
                                animatedDist, Format.distanceUnitLabel(unit)),
                            fontSize = 64.sp,
                            fontFamily = InterDisplay,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
                                fontSize = 64.sp, fontFeatureSettings = NumericFeatures),
                            color = danger)
                        Text(
                            stringResource(
                                R.string.alert_within,
                                Format.distance(obs.effectiveRadiusKm, unit)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(Modifier.height(28.dp))
                        val cp = Format.closingParts(obs, unit)
                        val onSurface = MaterialTheme.colorScheme.onSurface
                        val valueStyle = MaterialTheme.typography.titleMedium
                            .copy(fontFeatureSettings = NumericFeatures)
                        val dash = "\u2014"
                        val stats = listOf<@Composable (Modifier) -> Unit>(
                            { m ->
                                AlertStat(stringResource(R.string.alert_stat_bearing), m) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        BearingArrow(obs.bearingDeg, danger, size = 40.dp)
                                        Text(
                                            Format.bearing(obs.bearingDeg),
                                            style = MaterialTheme.typography.bodyMedium
                                                .copy(fontFeatureSettings = NumericFeatures),
                                            color = onSurface)
                                    }
                                }
                            },
                            { m ->
                                AlertStat(
                                    label = cp?.direction
                                        ?: stringResource(R.string.alert_stat_closing),
                                    modifier = m,
                                    labelColor = when (cp?.trend) {
                                        Format.ClosingTrend.APPROACHING -> danger
                                        Format.ClosingTrend.RECEDING ->
                                            MaterialTheme.colorScheme.extended.success
                                        else -> null
                                    }
                                ) { Text(cp?.speed ?: dash, style = valueStyle, color = onSurface) }
                            },
                            { m ->
                                AlertStat(stringResource(R.string.alert_stat_altitude), m) {
                                    Text(
                                        Format.altitude(obs.altitudeM, unit) ?: dash,
                                        style = valueStyle, color = onSurface)
                                }
                            },
                            { m ->
                                AlertStat(stringResource(R.string.alert_stat_speed), m) {
                                    Text(
                                        Format.speed(obs.velocityMps, unit) ?: dash,
                                        style = valueStyle, color = onSurface)
                                }
                            },
                            { m ->
                                AlertStat(stringResource(R.string.alert_stat_heading), m) {
                                    Text(
                                        Format.heading(obs.headingDeg) ?: dash,
                                        style = valueStyle, color = onSurface)
                                }
                            },
                            { m ->
                                AlertStat(stringResource(R.string.alert_stat_radius), m) {
                                    Text(
                                        Format.distance(obs.effectiveRadiusKm, unit),
                                        style = valueStyle, color = onSurface)
                                }
                            }
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            for (rowItems in stats.chunked(columns)) {
                                Row(Modifier.fillMaxWidth()) {
                                    for (stat in rowItems) stat(Modifier.weight(1f))
                                    repeat(columns - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    }

                    Column {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = danger,
                                contentColor = MaterialTheme.colorScheme.onError)
                        ) {
                            Text(
                                stringResource(R.string.action_dismiss),
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        val snoozeButtons: @Composable (Modifier) -> Unit = { m ->
                            OutlinedButton(
                                onClick = { onSnooze(15) },
                                modifier = m,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(stringResource(R.string.action_snooze_15),
                                    maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = { onSnooze(60) },
                                modifier = m,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(stringResource(R.string.action_snooze_60),
                                    maxLines = 1, softWrap = false)
                            }
                        }
                        if (columns == 3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                snoozeButtons(Modifier.weight(1f))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                snoozeButtons(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One labeled tile in the alert stat grid. */
@Composable
private fun AlertStat(
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    sub: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

private fun vibrateAlert(context: Context) {
    try {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 800)
        val effect = VibrationEffect.createWaveform(pattern, -1)
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as VibratorManager
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                .vibrate(effect)
        }
    } catch (_: Exception) {
    }
}
