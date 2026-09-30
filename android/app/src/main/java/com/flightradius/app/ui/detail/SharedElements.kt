package com.flightradius.app.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

const val SHARED_DURATION_MS = 350

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Which part of an aircraft is shared between a list row / the dial and the details screen. */
enum class SharedPart { CALLSIGN, DISTANCE, CONTAINER }

/** Shared key: one per aircraft id ("t:<aircraftId>" or "n:<icao24>") and part. */
data class SharedKey(val aircraftKey: String, val part: SharedPart)

/**
 * Shared bounds for one aircraft part. A no-op outside a shared transition
 * layout (previews, tests) and when no animated scope is available.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aircraftShared(aircraftKey: String, part: SharedPart): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(transition) {
        this@aircraftShared.sharedBounds(
            sharedContentState = rememberSharedContentState(SharedKey(aircraftKey, part)),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> tween(SHARED_DURATION_MS, easing = FastOutSlowInEasing) },
            resizeMode = if (part == SharedPart.CONTAINER) {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            } else SharedTransitionScope.ResizeMode.scaleToBounds()
        )
    }
}
