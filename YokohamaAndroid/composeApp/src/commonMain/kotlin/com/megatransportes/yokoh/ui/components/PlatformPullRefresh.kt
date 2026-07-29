package com.megatransportes.yokoh.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun PlatformPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }

    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    val deadZonePx = with(density) { 3.dp.toPx() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > deadZonePx) {
                    pullDistance += available.y
                    println("[DEBUG PTR] onPostScroll available.y=${available.y} pullDistance=$pullDistance source=$source")
                    return Offset(0f, available.y)
                }
                if (available.y <= deadZonePx && available.y > 0f) {
                    println("[DEBUG PTR] onPostScroll BLOCKED by deadZone: available.y=${available.y} deadZone=$deadZonePx")
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && pullDistance > 0f && available.y < 0f) {
                    val consumed = maxOf(available.y, -pullDistance)
                    pullDistance += consumed
                    println("[DEBUG PTR] onPreScroll available.y=${available.y} consumed=$consumed pullDistance=$pullDistance")
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            delay(100)
            if (pullDistance > 0f) println("[DEBUG PTR] reset pullDistance $pullDistance -> 0 (isRefreshing=false)")
            pullDistance = 0f
        }
    }

    val animatedPullDistance by animateFloatAsState(
        targetValue = if (isRefreshing) thresholdPx else pullDistance,
        animationSpec = tween(300),
        label = "pullOffset"
    )

    val offsetPx = if (isRefreshing && animatedPullDistance < thresholdPx) thresholdPx else animatedPullDistance

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(nestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetPx.roundToInt()) }
        ) {
            content()
        }

        if (offsetPx > 0f || isRefreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { (offsetPx / thresholdPx).coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        var lastPull = 0f
        while (true) {
            delay(50)
            if (pullDistance > 0f && pullDistance == lastPull && !currentIsRefreshing) {
                if (pullDistance >= thresholdPx) {
                    println("[DEBUG PTR] TRIGGER REFRESH (pullDistance=$pullDistance)")
                    currentOnRefresh()
                    pullDistance = 0f
                } else {
                    println("[DEBUG PTR] reset pullDistance $pullDistance -> 0 (drag ended, below threshold)")
                    pullDistance = 0f
                }
            } else if (pullDistance != lastPull) {
                println("[DEBUG PTR] poll: pullDistance=$pullDistance lastPull=$lastPull (still pulling)")
            }
            lastPull = pullDistance
        }
    }
}
