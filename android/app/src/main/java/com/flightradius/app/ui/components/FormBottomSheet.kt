package com.flightradius.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Leftover *upward* scroll/fling (the content already hit its end) must not reach
 * the sheet: it has nowhere to go above its expanded anchor, so the sheet used to
 * be yanked past the anchor and spring back (a visible bounce). Downward leftovers
 * still pass through so swipe-down-to-dismiss keeps working.
 */
internal object SwallowUpwardLeftover : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset = if (available.y < 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}

/** Bottom sheet for forms: no half-expanded state, no overscroll bounce. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormBottomSheet(
    onDismiss: () -> Unit,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(Modifier.nestedScroll(SwallowUpwardLeftover)) { content() }
    }
}
