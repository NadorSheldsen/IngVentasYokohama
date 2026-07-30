package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.getPlatformName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

@Composable
fun PlatformLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val isIos = getPlatformName() == "iOS"

    if (isIos) {
        val scope = rememberCoroutineScope()
        val k = 0.36f

        LazyColumn(
            modifier = modifier.pointerInput(state) {
                var vt = VelocityTracker()
                var dragging = false
                var flingJob: Job? = null

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        // Nuevo touch → cancelar fling inmediatamente
                        val anyNewPress = event.changes.any { it.pressed && !it.previousPressed }
                        if (anyNewPress) {
                            flingJob?.cancel()
                            flingJob = null
                            vt = VelocityTracker()
                        }

                        val change = event.changes.firstOrNull { it.pressed }

                        if (change != null && change.previousPressed) {
                            val rawDelta = change.position.y - change.previousPosition.y
                            if (abs(rawDelta) > 0.5f) {
                                change.consume()
                                vt.addPosition(change.uptimeMillis, change.position)
                                dragging = true

                                val conn = pullRefreshConnection
                                val atTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0

                                if (atTop && rawDelta > 0) {
                                    conn?.onPostScroll(
                                        Offset.Zero, Offset(0f, rawDelta),
                                        NestedScrollSource.UserInput
                                    )
                                } else {
                                    val preConsumed = if (conn != null) {
                                        conn.onPreScroll(Offset(0f, rawDelta), NestedScrollSource.UserInput)
                                    } else Offset.Zero
                                    val ourDelta = -rawDelta + preConsumed.y
                                    val unconsumed = state.dispatchRawDelta(ourDelta)
                                    if (abs(unconsumed) > 0.5f && conn != null) {
                                        conn.onPostScroll(
                                            consumed = Offset(0f, ourDelta - unconsumed),
                                            available = Offset(0f, -unconsumed),
                                            source = NestedScrollSource.UserInput
                                        )
                                    }
                                }
                            }
                        } else if (change == null && dragging) {
                            dragging = false
                            val velocity = vt.calculateVelocity().y
                            if (abs(velocity) > 50f) {
                                flingJob = scope.launch {
                                    var v = (-velocity).coerceIn(-5000f, 5000f)
                                    while (abs(v) > 20f) {
                                        delay(16L)
                                        if (!isActive) break
                                        val dt = 0.016f
                                        v *= exp(-k * dt)
                                        val scrollDelta = v * dt
                                        if (abs(scrollDelta) < 0.5f) break
                                        state.dispatchRawDelta(scrollDelta)
                                    }
                                }
                            }
                            vt = VelocityTracker()
                        }
                    }
                }
            },
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            userScrollEnabled = false,
            flingBehavior = ScrollableDefaults.flingBehavior(),
            content = content,
        )
    } else {
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}
