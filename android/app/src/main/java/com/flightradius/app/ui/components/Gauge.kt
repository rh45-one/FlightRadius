package com.flightradius.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.flightradius.app.ui.theme.extended
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Large radial gauge. The arc fills with *proximity*:
 * fill = clamp(1 - d/(2r), 0, 1) - empty at d >= 2r, half full at d = r,
 * full at d = 0. The tick marks the radius (the 50% arc position).
 * Optional faint sweep-gradient wedge inside the ring while monitoring runs.
 */
@Composable
fun RadialGauge(
    distanceKm: Double,
    radiusKm: Double,
    sweeping: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val zone = zoneColor(distanceKm, radiusKm)
    val sweepColor = MaterialTheme.colorScheme.extended.info

    val sweepAngle by if (sweeping) {
        val transition = rememberInfiniteTransition(label = "sweep")
        transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "sweepAngle"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        val strokeW = 10.dp.toPx()
        val r = min(this.size.width, this.size.height) / 2f - strokeW
        val c = Offset(this.size.width / 2f, this.size.height / 2f)

        // Faint sweep wedge inside the ring (drawn under centered content).
        if (sweeping) {
            rotate(sweepAngle, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            sweepColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = c
                    ),
                    radius = r - strokeW,
                    center = c
                )
            }
        }

        // Track ring (empty = far, i.e. distance >= 2r).
        drawCircle(track, radius = r, center = c, style = Stroke(strokeW))

        // Proximity arc, starting at top (-90 deg): fuller = closer.
        val frac = (1.0 - distanceKm / (radiusKm * 2)).coerceIn(0.0, 1.0).toFloat()
        drawArc(
            color = zone,
            startAngle = -90f,
            sweepAngle = frac * 360f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(strokeW, cap = StrokeCap.Round)
        )

        // Radius tick at the 50% arc position (d == r).
        val tickAngle = Math.toRadians(-90.0 + 180.0)
        val inner = Offset(
            c.x + (r - strokeW) * cos(tickAngle).toFloat(),
            c.y + (r - strokeW) * sin(tickAngle).toFloat()
        )
        val outer = Offset(
            c.x + (r + strokeW) * cos(tickAngle).toFloat(),
            c.y + (r + strokeW) * sin(tickAngle).toFloat()
        )
        drawLine(zone, inner, outer, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Small ring gauge for aircraft list cards (fills with proximity: 1 - d/2r). */
@Composable
fun MiniRingGauge(
    distanceKm: Double,
    radiusKm: Double,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val zone = zoneColor(distanceKm, radiusKm)
    Canvas(modifier.size(size)) {
        val strokeW = 4.dp.toPx()
        val r = min(this.size.width, this.size.height) / 2f - strokeW
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(track, radius = r, center = c, style = Stroke(strokeW))
        val frac = (1.0 - distanceKm / (radiusKm * 2)).coerceIn(0.0, 1.0).toFloat()
        drawArc(
            color = zone,
            startAngle = -90f,
            sweepAngle = frac * 360f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(strokeW, cap = StrokeCap.Round)
        )
    }
}

/** Bearing arrow pointing at [deg] (0 = north). */
@Composable
fun BearingArrow(
    deg: Double,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Canvas(modifier.size(size)) {
        val r = min(this.size.width, this.size.height) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val a = Math.toRadians(deg - 90.0)
        val tip = Offset(
            c.x + r * 0.9f * cos(a).toFloat(),
            c.y + r * 0.9f * sin(a).toFloat()
        )
        val left = Offset(
            c.x + r * 0.45f * cos(a + 2.5).toFloat(),
            c.y + r * 0.45f * sin(a + 2.5).toFloat()
        )
        val right = Offset(
            c.x + r * 0.45f * cos(a - 2.5).toFloat(),
            c.y + r * 0.45f * sin(a - 2.5).toFloat()
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(c.x - r * 0.25f * cos(a).toFloat(),
                   c.y - r * 0.25f * sin(a).toFloat())
            lineTo(right.x, right.y)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Empty-state radar illustration: concentric rings, a rotating
 * sweep-gradient wedge and a small aircraft glyph.
 */
@Composable
fun RadarIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    sweeping: Boolean = true
) {
    val ring = MaterialTheme.colorScheme.extended.info.copy(alpha = 0.35f)
    val dot = MaterialTheme.colorScheme.extended.info.copy(alpha = 0.7f)
    val wedge = MaterialTheme.colorScheme.extended.info
    val plane = MaterialTheme.colorScheme.extended.warning

    val sweepAngle by if (sweeping) {
        val transition = rememberInfiniteTransition(label = "illustrationSweep")
        transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(5000, easing = LinearEasing), RepeatMode.Restart),
            label = "illustrationAngle"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(30f) }
    }

    Canvas(modifier.size(size)) {
        val r = min(this.size.width, this.size.height) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        for (f in listOf(1f, 0.66f, 0.33f)) {
            drawCircle(ring, radius = r * f, center = c, style = Stroke(2.dp.toPx()))
        }
        // Cross hairs.
        drawLine(ring.copy(alpha = 0.2f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(ring.copy(alpha = 0.2f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        // Sweep wedge.
        rotate(sweepAngle, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        wedge.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = c
                ),
                startAngle = -50f, sweepAngle = 50f,
                useCenter = true,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
            )
        }
        drawCircle(dot, radius = 4.dp.toPx(), center = c)
        // Small aircraft silhouette NE of center, nose up-right.
        val pr = r * 0.45f
        val pc = Offset(c.x + pr * 0.7f, c.y - pr * 0.7f)
        val pa = Math.toRadians(-45.0)
        val nose = 9.dp.toPx()
        val tail = (-7).dp.toPx()
        val wing = 5.dp.toPx()
        fun pt(dist: Float, off: Float) = Offset(
            pc.x + dist * cos(pa).toFloat() - off * sin(pa).toFloat(),
            pc.y + dist * sin(pa).toFloat() + off * cos(pa).toFloat()
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pt(nose, 0f).x, pt(nose, 0f).y)
            lineTo(pt(tail, wing).x, pt(tail, wing).y)
            lineTo(pt(tail * 0.55f, 0f).x, pt(tail * 0.55f, 0f).y)
            lineTo(pt(tail, -wing).x, pt(tail, -wing).y)
            close()
        }
        drawPath(path, plane)
    }
}
