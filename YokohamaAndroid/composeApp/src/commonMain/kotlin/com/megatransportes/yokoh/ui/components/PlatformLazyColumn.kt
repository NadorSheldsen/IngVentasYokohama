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
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.exp
import kotlinx.coroutines.ensureActive

/**
 * Fling behavior para iOS siguiendo el estándar de UIKit:
 * - Decaimiento exponencial: v *= exp(-k * dt)
 * - Umbral de velocidad: para cuando |v| < 20
 * - Delta mínimo: ignora movimientos < 0.5px
 * - Cancela inmediatamente al tocar (vía coroutine cancellation)
 */
class PlatformFlingBehavior : FlingBehavior {
    private val k = 3.0f // constante de decaimiento (mayor = para más rápido)

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val clamped = initialVelocity.coerceIn(-5000f, 5000f)
        if (abs(clamped) < 50f) return clamped

        var v = clamped
        var lastFrameTimeNs = 0L

        while (abs(v) > 20f) {
            // Verificar cancelación (nuevo touch cancela el fling inmediatamente)
            coroutineContext.ensureActive()

            val frameTimeNs = withFrameNanos { it }
            if (lastFrameTimeNs == 0L) {
                lastFrameTimeNs = frameTimeNs
                // Primer frame: aplicar decaimiento sin mover (evita salto inicial)
                val dt = 0.016f
                v *= exp(-k * dt)
                continue
            }

            val dt = (frameTimeNs - lastFrameTimeNs) / 1_000_000_000f
            lastFrameTimeNs = frameTimeNs
            val dtClamped = dt.coerceIn(0.008f, 0.033f)

            // Decaimiento exponencial basado en tiempo real
            v *= exp(-k * dtClamped)

            val scrollDelta = v * dtClamped * 60f

            // Ignorar deltas sub-pixel (solo generan vibración)
            if (abs(scrollDelta) < 0.5f) break

            val consumed = scrollBy(scrollDelta)

            // Si chocó con un límite, parar
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

    var finalModifier = modifier
    if (isIos) {
        val density = LocalDensity.current
        val touchSlopPx = with(density) { 8.dp.toPx() }
        // Acumula movimiento total; solo deja pasar al LazyColumn
        // cuando se supera el umbral de 8dp (tap vs drag)
        var accumulatedDrag by remember { mutableFloatStateOf(0f) }
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Si el origen NO es el usuario (fling, side-effect), reseteamos el acumulador
                    // y dejamos pasar sin absorber
                    if (source != NestedScrollSource.UserInput) {
                        accumulatedDrag = 0f
                        return Offset.Zero
                    }

                    val newAcc = accumulatedDrag + available.y
                    if (abs(newAcc) <= touchSlopPx) {
                        // Aún no supera el umbral → absorber todo
                        accumulatedDrag = newAcc
                        return Offset(0f, available.y)
                    }
                    // Superó el umbral: dejar pasar este delta sin modificar
                    // y capar el acumulador para no re‑absorber en el futuro
                    accumulatedDrag = touchSlopPx * newAcc.sign
                    return Offset.Zero
                }
            }
        }
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
