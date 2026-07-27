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

class PlatformFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) < 400f) return initialVelocity

        var velocity = initialVelocity

        while (abs(velocity) > 50f) {
            withFrameNanos { }
            velocity *= 0.90f
            val scrollDelta = velocity * 0.016f
            if (abs(scrollDelta) < 1f) break
            val consumed = scrollBy(scrollDelta)
            if (abs(consumed) < abs(scrollDelta)) break
        }
        return velocity
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
