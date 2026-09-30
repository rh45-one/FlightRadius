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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
                            Text(
                                "\u26a0",
                                color = danger,
                                fontSize = 26.sp)
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
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            Format.callsign(obs),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp),
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                            color = danger)
                        Text(
                            stringResource(
                                R.string.alert_within,
                                Format.distance(obs.effectiveRadiusKm, unit)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(Modifier.height(28.dp))
                        val cp = Format.closingParts(obs, unit)
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                AlertStat(
                                    label = stringResource(R.string.alert_stat_bearing),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        BearingArrow(obs.bearingDeg, danger, size = 40.dp)
                                        Text(
                                            Format.bearing(obs.bearingDeg),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text
                                                .style.TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium
                                                .copy(fontFeatureSettings = "tnum"),
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                AlertStat(
                                    label = cp?.direction
                                        ?: stringResource(R.string.alert_stat_closing),
                                    labelColor = when (cp?.trend) {
                                        Format.ClosingTrend.APPROACHING -> danger
                                        Format.ClosingTrend.RECEDING ->
                                            MaterialTheme.colorScheme.extended.success
                                        else -> null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        cp?.speed ?: "\u2014",
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style
                                            .TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                            .copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                AlertStat(
                                    label = stringResource(R.string.alert_stat_altitude),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        Format.altitude(obs.altitudeM, unit) ?: "\u2014",
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style
                                            .TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                            .copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Row(Modifier.fillMaxWidth()) {
                                AlertStat(
                                    label = stringResource(R.string.alert_stat_speed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        Format.speed(obs.velocityMps, unit) ?: "\u2014",
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style
                                            .TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                            .copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                AlertStat(
                                    label = stringResource(R.string.alert_stat_heading),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        Format.heading(obs.headingDeg) ?: "\u2014",
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style
                                            .TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                            .copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                AlertStat(
                                    label = stringResource(R.string.alert_stat_radius),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        Format.distance(obs.effectiveRadiusKm, unit),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style
                                            .TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium
                                            .copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurface)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSnooze(15) },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.action_snooze_15)) }
                            OutlinedButton(
                                onClick = { onSnooze(60) },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.action_snooze_60)) }
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
            label.uppercase(),
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
