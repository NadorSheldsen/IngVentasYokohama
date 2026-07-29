package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.getPlatformName
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fling behavior for iOS that responds naturally to swipe velocity.
 */
class PlatformFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val clamped = initialVelocity.coerceIn(-8000f, 8000f)
        if (abs(clamped) < 40f) return clamped
        var v = clamped
        var lastFrameTimeNs = 0L
        while (abs(v) > 10f) {
            val frameTimeNs = withFrameNanos { it }
            val dt = if (lastFrameTimeNs == 0L) 0.016f else (frameTimeNs - lastFrameTimeNs) / 1_000_000_000f
            lastFrameTimeNs = frameTimeNs
            val dtClamped = dt.coerceIn(0.008f, 0.033f)
            val friction = when {
                abs(v) > 2000f -> 0.85f
                abs(v) > 800f -> 0.90f
                else -> 0.95f
            }
            v *= friction
            val scrollDelta = v * dtClamped * 60f
            if (abs(scrollDelta) < 0.5f) break
            val consumed = scrollBy(scrollDelta)
            if (abs(consumed) < abs(scrollDelta) * 0.5f) return 0f
        }
        return v
    }
}

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
    val flingBehavior: FlingBehavior = if (isIos) {
        remember { PlatformFlingBehavior() }
    } else {
        ScrollableDefaults.flingBehavior()
    }

    if (isIos) {
        val density = LocalDensity.current
        val touchSlopPx = with(density) { 12.dp.toPx() }
        val coroutineScope = rememberCoroutineScope()

        Box(
            modifier = modifier
                .pointerInput(state, touchSlopPx) {
                    iosScrollGesture(state, touchSlopPx, coroutineScope)
                }
        ) {
            LazyColumn(
                state = state,
                userScrollEnabled = false,
                contentPadding = contentPadding,
                reverseLayout = reverseLayout,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}

data class Sample(val timeMillis: Long, val y: Float)

private suspend fun PointerInputScope.iosScrollGesture(
    scrollState: LazyListState,
    touchSlopPx: Float,
    coroutineScope: CoroutineScope
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var lastY = down.position.y
        var totalDrag = 0f
        var isScrolling = false

        val samples = mutableListOf<Sample>()
        fun addSample(timeMillis: Long, y: Float) {
            samples.add(Sample(timeMillis, y))
            if (samples.size > 10) samples.removeAt(0)
        }
        addSample(down.uptimeMillis, lastY)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                if (isScrolling) {
                    addSample(change.uptimeMillis, change.position.y)
                    val velocity = calculateReleaseVelocity(samples)
                    coroutineScope.launch {
                        performFling(scrollState, velocity)
                    }
                }
                break
            }

            val posY = change.position.y
            addSample(change.uptimeMillis, posY)
            val deltaY = lastY - posY
            lastY = posY

            if (!isScrolling) {
                totalDrag += deltaY
                if (abs(totalDrag) > touchSlopPx) {
                    isScrolling = true
                    change.consume()
                    scrollState.dispatchRawDelta(totalDrag)
                }
            } else {
                change.consume()
                scrollState.dispatchRawDelta(deltaY)
            }
        }
    }
}

private fun calculateReleaseVelocity(samples: List<Sample>): Float {
    if (samples.size < 3) return 0f
    val recent = samples.takeLast(5)
    val windowMillis = recent.last().timeMillis - recent.first().timeMillis
    if (windowMillis <= 0) return 0f
    val dist = recent.last().y - recent.first().y
    return dist / (windowMillis / 1000f)
}

private suspend fun performFling(
    scrollState: LazyListState,
    initialVelocity: Float
) {
    val clamped = initialVelocity.coerceIn(-8000f, 8000f)
    if (abs(clamped) < 40f) return

    var v = clamped
    var lastFrameTimeNs = 0L
    while (abs(v) > 10f) {
        val frameTimeNs = withFrameNanos { it }
        val dt = if (lastFrameTimeNs == 0L) 0.016f else (frameTimeNs - lastFrameTimeNs) / 1_000_000_000f
        lastFrameTimeNs = frameTimeNs
        val dtClamped = dt.coerceIn(0.008f, 0.033f)
        val friction = when {
            abs(v) > 2000f -> 0.85f
            abs(v) > 800f -> 0.90f
            else -> 0.95f
        }
        v *= friction
        val scrollDelta = v * dtClamped * 60f
        if (abs(scrollDelta) < 0.5f) break
        val unconsumed = scrollState.dispatchRawDelta(scrollDelta)
        if (abs(unconsumed) >= abs(scrollDelta) * 0.5f) break
    }
}
