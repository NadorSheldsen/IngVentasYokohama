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
import androidx.compose.runtime.LaunchedEffect
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
import com.megatransportes.yokoh.disableIosScrollBounce
import com.megatransportes.yokoh.getPlatformName
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class FlingJobRef {
    var job: Job? = null
    var cancelled = false
}

class PlatformFlingBehavior(
    private val flingJobRef: FlingJobRef,
) : FlingBehavior {
    private val k = 0.36f
    private var bounceBackGuard = 0

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        println("[FLING] START velocity=$initialVelocity guard=$bounceBackGuard")

        // Rechazar flings justo después de llegar a un borde (bounce-back)
        if (bounceBackGuard > 0) {
            bounceBackGuard--
            println("[FLING] REJECT (bounce-back guard=$bounceBackGuard)")
            return initialVelocity
        }

        flingJobRef.job = currentCoroutineContext()[Job]
        flingJobRef.cancelled = false
        try {
            val clamped = initialVelocity.coerceIn(-5000f, 5000f)
            if (abs(clamped) < 50f) {
                println("[FLING] SKIP (velocity too low: $clamped)")
                return clamped
            }

            var v = clamped
            var lastFrameTimeNs = 0L
            var frames = 0

            while (abs(v) > 20f) {
                currentCoroutineContext().ensureActive()
                if (flingJobRef.cancelled) {
                    println("[FLING] STOP by cancelled flag v=$v frames=$frames")
                    return 0f
                }
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
                if (abs(scrollDelta) < 0.5f) {
                    println("[FLING] STOP delta below threshold v=$v scrollDelta=$scrollDelta frames=$frames")
                    break
                }

                if (flingJobRef.cancelled) {
                    println("[FLING] STOP by cancelled flag (pre-scrollBy) v=$v frames=$frames")
                    return 0f
                }
                val consumed = scrollBy(scrollDelta)
                frames++
                println("[FLING] FRAME v=$v delta=$scrollDelta consumed=$consumed frames=$frames")

                if (abs(consumed) < abs(scrollDelta) * 0.5f) {
                    bounceBackGuard = 3
                    println("[FLING] STOP at boundary v=$v consumed=$consumed delta=$scrollDelta frames=$frames guard=$bounceBackGuard")
                    return 0f
                }
            }

            println("[FLING] END natural v=$v frames=$frames")
            return 0f
        } catch (e: kotlinx.coroutines.CancellationException) {
            println("[FLING] CANCELLED by Job cancellation")
            throw e
        } finally {
            flingJobRef.job = null
            flingJobRef.cancelled = false
            println("[FLING] CLEANUP job=null cancelled=false")
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
        val connection = remember(state) {
            var flingCooldown = 0
            var lastLogOffset = -1

            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val scrollOffset = state.firstVisibleItemScrollOffset
                    val firstItem = state.firstVisibleItemIndex
                    if (scrollOffset != lastLogOffset) {
                        println("[NESTED] offset=$scrollOffset firstItem=$firstItem cooldown=$flingCooldown")
                        lastLogOffset = scrollOffset
                    }

                    if (source == NestedScrollSource.UserInput) {
                        if (flingCooldown > 0) {
                            flingCooldown--
                            flingJobRef.cancelled = true
                            println("[NESTED] → ABSORB (fling cooldown left=$flingCooldown) delta=${available.y} offset=$scrollOffset")
                            return Offset(0f, available.y)
                        }
                        if (abs(available.y) <= noiseFloorPx) {
                            println("[NESTED] → ABSORB (noise floor) delta=${available.y} offset=$scrollOffset")
                            return Offset(0f, available.y)
                        }
                    } else if (source == NestedScrollSource.Fling || source == NestedScrollSource.SideEffect) {
                        flingCooldown = 10
                        println("[NESTED] → MARK fling (cooldown=10, source=$source) offset=$scrollOffset")
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val scrollOffset = state.firstVisibleItemScrollOffset
                    println("[NESTED] onPostScroll src=$source c.y=${consumed.y} a.y=${available.y} offset=$scrollOffset")
                    return Offset.Zero
                }
            }
        }
        finalModifier = modifier.nestedScroll(connection)

        // Deshabilitar bounce nativo de UIScrollView en iOS
        LaunchedEffect(Unit) {
            withFrameNanos { }
            disableIosScrollBounce()
        }
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
