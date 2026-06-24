package com.megatransportes.yokoh.utils

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import kotlin.math.abs

private const val Friction: Float = 0.015f
private const val MinVelocity: Float = 25f
private const val MaxVelocity: Float = 8000f

@Stable
fun smoothFlingBehavior(): FlingBehavior = SmoothFlingBehavior()

@Stable
private class SmoothFlingBehavior(
    private val friction: Float = Friction,
    private val minVelocity: Float = MinVelocity,
    private val maxVelocity: Float = MaxVelocity,
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val clampedVelocity = initialVelocity.coerceIn(-maxVelocity, maxVelocity)
        if (abs(clampedVelocity) < minVelocity) return clampedVelocity

        var velocity = clampedVelocity
        var remainingScroll = 0f

        while (abs(velocity) > minVelocity) {
            val drag = velocity * friction
            val previousVelocity = velocity
            velocity -= drag

            val scrollDelta = (previousVelocity + velocity) / 2f
            remainingScroll += scrollDelta

            if (abs(remainingScroll) > 0.5f) {
                val consumed = scrollBy(remainingScroll)
                remainingScroll -= consumed
            } else {
                break
            }

            if (abs(velocity) <= minVelocity || velocity.sign != previousVelocity.sign) {
                break
            }
        }

        return velocity
    }
}

@Composable
fun rememberSmoothFlingBehavior(): FlingBehavior = smoothFlingBehavior()
