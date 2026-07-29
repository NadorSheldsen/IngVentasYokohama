package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.getPlatformName
import kotlin.math.abs

/**
 * Fling behavior for iOS that:
 * - Maps swipe velocity smoothly to scroll distance
 * - Allows gentle flings (low threshold)
 * - Doesn't jerk on tiny movements
 * - Decays naturally
 */
class PlatformFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val clamped = initialVelocity.coerceIn(-4000f, 4000f)

        if (abs(clamped) < 80f) return clamped

        var v = clamped
        var frame = 0

        while (abs(v) > 20f) {
            withFrameNanos { }
            val friction = if (abs(v) > 1000f) 0.92f else if (abs(v) > 200f) 0.94f else 0.96f
            v *= friction
            val scrollDelta = v * 0.016f
            if (abs(scrollDelta) < 0.5f) break
            val consumed = scrollBy(scrollDelta)
            if (abs(consumed) < abs(scrollDelta)) return 0f
            frame++
        }
        return v
    }
}

/**
 * Nested scroll connection that absorbs tiny pre-scroll deltas on iOS.
 * The first [deadZoneDp] pixels of each scroll delta are consumed,
 * preventing unintentional micro-scrolls when the user intends to tap.
 */
private fun scrollDeadZoneConnection(deadZonePx: Float): NestedScrollConnection {
    return object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val absY = abs(available.y)
            if (absY <= deadZonePx) {
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
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

    var finalModifier = modifier
    if (isIos) {
        val density = LocalDensity.current
        val deadZonePx = with(density) { 3.dp.toPx() }
        val connection = remember { scrollDeadZoneConnection(deadZonePx) }
        finalModifier = finalModifier.nestedScroll(connection)
    }

    LazyColumn(
        modifier = finalModifier,
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
