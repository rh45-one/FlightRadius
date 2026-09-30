package com.flightradius.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flightradius.app.ui.theme.extended
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val MarkerSize = 24.dp
private val RingStroke = 2.dp
private val TickLength = 6.dp

/**
 * North-up proximity dial: a ring with cardinal ticks and an aircraft glyph
 * on the ring at [markerBearingDeg]. [content] is centred inside the ring.
 * The whole dial is one semantics node described by [description].
 */
@Composable
fun ProximityDial(
    modifier: Modifier = Modifier,
    size: Dp = 340.dp,
    markerBearingDeg: Double? = null,
    markerHeadingDeg: Double? = null,
    markerColor: Color = MaterialTheme.colorScheme.onSurface,
    description: String? = null,
    scope: DialScope? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val tick = MaterialTheme.colorScheme.outline
    val canvasColor = MaterialTheme.colorScheme.surface
    val blipColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val blipMatchColor = MaterialTheme.colorScheme.extended.danger

    val bearing = markerBearingDeg?.toFloat()
    val animated = remember { Animatable(bearing ?: 0f) }
    LaunchedEffect(bearing) {
        if (bearing != null) {
            val delta = ((bearing - animated.value) % 360f + 540f) % 360f - 180f
            animated.animateTo(animated.value + delta, tween(600))
        }
    }

    val semanticsModifier = if (description != null) {
        Modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        }
    } else Modifier

    Box(modifier.size(size).then(semanticsModifier), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val inset = MarkerSize.toPx() / 2f + 2.dp.toPx()
            val r = min(this.size.width, this.size.height) / 2f - inset
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(ring, radius = r, center = c, style = Stroke(RingStroke.toPx()))
            for (deg in listOf(0f, 90f, 180f, 270f)) {
                val a = Math.toRadians(deg - 90.0)
                val outer = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                val inner = Offset(
                    c.x + (r - TickLength.toPx()) * cos(a).toFloat(),
                    c.y + (r - TickLength.toPx()) * sin(a).toFloat()
                )
                drawLine(tick, inner, outer, strokeWidth = RingStroke.toPx(), cap = StrokeCap.Round)
            }
            if (scope != null) {
                val markers = ScopeProjection.markers(scope, r, markerBearingDeg)
                // Plain dots first, rule matches on top with a canvas-coloured stroke.
                for (m in markers.filter { !it.matchesRule }) {
                    drawCircle(blipColor, 2.5.dp.toPx(), Offset(c.x + m.dx, c.y + m.dy))
                }
                for (m in markers.filter { it.matchesRule }) {
                    val at = Offset(c.x + m.dx, c.y + m.dy)
                    drawCircle(canvasColor, 6.5.dp.toPx(), at)
                    drawCircle(blipMatchColor, 4.5.dp.toPx(), at)
                }
            }
            if (bearing != null) {
                val a = Math.toRadians(animated.value - 90.0)
                val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                drawMarker(p, markerHeadingDeg?.toFloat(), markerColor, canvasColor)
            }
        }
        Text(
            "N",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = MarkerSize / 2 + 2.dp + TickLength + 4.dp)
        )
        Column(
            Modifier.fillMaxWidth(0.64f),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

private fun DrawScope.drawMarker(
    center: Offset,
    headingDeg: Float?,
    color: Color,
    halo: Color
) {
    if (headingDeg == null) {
        drawCircle(halo, radius = 5.dp.toPx() + 3.dp.toPx(), center = center)
        drawCircle(color, radius = 5.dp.toPx(), center = center)
        return
    }
    val nose = 12.dp.toPx()
    val tail = 10.dp.toPx()
    val wing = 9.dp.toPx()
    val path = Path().apply {
        moveTo(center.x, center.y - nose)
        lineTo(center.x + wing, center.y + tail)
        lineTo(center.x, center.y + tail * 0.55f)
        lineTo(center.x - wing, center.y + tail)
        close()
    }
    rotate(headingDeg, pivot = center) {
        drawPath(
            path, halo,
            style = Stroke(6.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
        drawPath(path, color)
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
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(
                c.x - r * 0.25f * cos(a).toFloat(),
                c.y - r * 0.25f * sin(a).toFloat()
            )
            lineTo(right.x, right.y)
            close()
        }
        drawPath(path, color)
    }
}
