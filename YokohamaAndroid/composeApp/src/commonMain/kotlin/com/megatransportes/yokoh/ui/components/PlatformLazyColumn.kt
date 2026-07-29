package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.getPlatformName
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PlatformFlingBehavior : FlingBehavior {
    private val k = 0.36f

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val clamped = initialVelocity.coerceIn(-5000f, 5000f)
        if (abs(clamped) < 50f) return clamped

        var v = clamped
        var lastFrameTimeNs = 0L

        while (abs(v) > 20f) {
            currentCoroutineContext().ensureActive()

            val frameTimeNs = withFrameNanos { it }
            if (lastFrameTimeNs == 0L) {
                lastFrameTimeNs = frameTimeNs
                v *= exp(-k * 0.016f)
                continue
            }

            val dt = (frameTimeNs - lastFrameTimeNs) / 1_000_000_000f
            lastFrameTimeNs = frameTimeNs
            val dtClamped = dt.coerceIn(0.008f, 0.033f)

            v *= exp(-k * dtClamped)

            val scrollDelta = v * dtClamped * 60f
            if (abs(scrollDelta) < 0.5f) break

            val consumed = scrollBy(scrollDelta)
            if (abs(consumed) < abs(scrollDelta) * 0.5f) return 0f
        }

        return 0f
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

    val currentState = rememberUpdatedState(state)

    val finalModifier = if (isIos) {
        val scrollableState = rememberScrollableState { delta ->
            currentState.value.dispatchRawDelta(delta)
        }
        modifier.scrollable(
            state = scrollableState,
            orientation = Orientation.Vertical,
            flingBehavior = flingBehavior,
        )
    } else {
        modifier
    }

    LazyColumn(
        modifier = finalModifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = if (isIos) false else userScrollEnabled,
        content = content,
    )
}
