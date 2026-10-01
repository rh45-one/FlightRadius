package com.flightradius.app.ui.update

import com.flightradius.app.ui.util.startActivitySafely
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.update.AvailableUpdate
import kotlinx.coroutines.delay

const val BANNER_AUTO_DISMISS_MS = 10_000L
private const val TICK_MS = 50L
private const val EXIT_MS = 250L

private val DownloadIcon: ImageVector = ImageVector.Builder(
    "Download", 24.dp, 24.dp, 24f, 24f
).addPath(
    pathData = addPathNodes("M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"),
    fill = SolidColor(androidx.compose.ui.graphics.Color.Black)
).build()

/** Shows the pending-update banner once per launch (never while the alert sheet is up). */
@Composable
fun UpdateBannerHost(alertActive: Boolean, modifier: Modifier = Modifier) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val candidate by viewModel.candidate.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<AvailableUpdate?>(null) }
    val context = LocalContext.current

    LaunchedEffect(candidate, alertActive) {
        if (alertActive) {
            shown = null
        } else if (shown == null && candidate != null) {
            shown = candidate
            viewModel.markShown()
        }
    }
    shown?.let { update ->
        UpdateBanner(
            version = update.version,
            onOpen = {
                context.startActivitySafely(
                    Intent(Intent.ACTION_VIEW, Uri.parse(update.url)))
            },
            onDismiss = { shown = null },
            modifier = modifier
        )
    }
}

/**
 * Pill at the top of the screen: icon, "Version X is available" / "Tap to
 * download", a close button and a thin countdown line. Auto-dismisses after
 * [autoDismissMs]; the countdown pauses while a finger is down on it.
 */
@Composable
fun UpdateBanner(
    version: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = BANNER_AUTO_DISMISS_MS
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableFloatStateOf(autoDismissMs.toFloat()) }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(if (reducedMotion) 0 else EXIT_MS)
            onDismiss()
        }
    }
    // delay() is unaffected by the animator-duration scale, so this also works with reduced motion.
    LaunchedEffect(pressed, closing) {
        while (!pressed && !closing && remainingMs > 0f) {
            delay(TICK_MS)
            remainingMs -= TICK_MS
        }
        if (remainingMs <= 0f) closing = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) fadeIn() else slideInVertically { -it } + fadeIn(),
        exit = if (reducedMotion) fadeOut() else slideOutVertically { -it } + fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        pressed = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        pressed = false
                    }
                }
        ) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(start = 16.dp, end = 4.dp)
                ) {
                    Icon(
                        DownloadIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.update_banner_title, version),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        Text(
                            stringResource(R.string.update_banner_tap),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { closing = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.update_banner_dismiss)
                        )
                    }
                }
                if (!reducedMotion) {
                    val fraction = (remainingMs / autoDismissMs).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .layout { measurable, constraints ->
                                val p = measurable.measure(
                                    constraints.copy(
                                        minWidth = (constraints.maxWidth * fraction).toInt(),
                                        maxWidth = (constraints.maxWidth * fraction).toInt()))
                                layout(constraints.maxWidth, p.height) { p.place(0, 0) }
                            }
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
