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

/**
 * Fling behavior for iOS that responds naturally to swipe velocity.
 * Apps como WhatsApp confían en el UIScrollView nativo y solo ajustan
 * la respuesta al fling (velocidad → distancia).
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
