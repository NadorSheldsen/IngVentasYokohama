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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.getPlatformName
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Referencia compartida al Job de la corrutina del fling.
 * El gesture detector cancela este Job cuando el usuario toca,
 * lo que deregistra el frame callback de withFrameNanos al instante.
 */
class FlingJobRef {
    var job: Job? = null
}

class PlatformFlingBehavior(
    private val flingJobRef: FlingJobRef,
) : FlingBehavior {
    private val k = 0.36f

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        flingJobRef.job = currentCoroutineContext()[Job]
        try {
            val clamped = initialVelocity.coerceIn(-5000f, 5000f)
            if (abs(clamped) < 50f) return clamped

            var v = clamped
            var lastFrameTimeNs = 0L

            while (abs(v) > 20f) {
                currentCoroutineContext().ensureActive()

                val frameTimeNs = withFrameNanos { it }
                // Si el Job fue cancelado, withFrameNanos lanza CancellationException
                // y nunca llegamos aquí

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
        } finally {
            flingJobRef.job = null
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
    val flingJobRef = remember { FlingJobRef() }
    val flingBehavior: FlingBehavior = if (isIos) {
        remember { PlatformFlingBehavior(flingJobRef) }
    } else {
        ScrollableDefaults.flingBehavior()
    }

    var finalModifier = modifier
    if (isIos) {
        val density = LocalDensity.current
        val noiseFloorPx = with(density) { 2.dp.toPx() }
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && abs(available.y) <= noiseFloorPx) {
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
            }
        }
        finalModifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            flingJobRef.job?.cancel()
                        }
                    }
                }
            }
            .nestedScroll(connection)
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
