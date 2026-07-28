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
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.getPlatformName
import kotlin.math.abs

class PlatformFlingBehavior(
    private val maxInitialVelocity: Float = 3000f,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val velocity = initialVelocity.coerceIn(-maxInitialVelocity, maxInitialVelocity)
        println("[DEBUG FLING] called with v=$initialVelocity clamped=$velocity")

        if (abs(velocity) < 400f) {
            println("[DEBUG FLING] ignored (below threshold)")
            return velocity
        }

        var v = velocity
        var frame = 0

        while (abs(v) > 50f) {
            withFrameNanos { }
            v *= 0.88f
            val scrollDelta = v * 0.016f
            if (abs(scrollDelta) < 1f) {
                println("[DEBUG FLING] frame $frame: v=$v delta=$scrollDelta -> break (too small)")
                break
            }
            val consumed = scrollBy(scrollDelta)
            println("[DEBUG FLING] frame $frame: v=$v delta=$scrollDelta consumed=$consumed")
            if (abs(consumed) < abs(scrollDelta)) {
                println("[DEBUG FLING] frame $frame: hit boundary, returning 0")
                return 0f
            }
            frame++
        }
        println("[DEBUG FLING] done, remaining v=$v frames=$frame")
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
    val flingBehavior: FlingBehavior = if (getPlatformName() == "iOS") {
        remember { PlatformFlingBehavior() }
    } else {
        ScrollableDefaults.flingBehavior()
    }
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
