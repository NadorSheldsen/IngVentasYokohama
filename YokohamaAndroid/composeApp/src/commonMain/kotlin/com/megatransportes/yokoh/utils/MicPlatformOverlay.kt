package com.megatransportes.yokoh.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

/**
 * Multiplatform hook for displaying a top-level native overlay for the mic recording indicator.
 *
 * Android implementation will attach a view to the Activity decorView; other platforms are no-ops.
 */
expect object MicPlatformOverlay {
    fun setHost(host: Any?)
    fun show(centerWindow: Offset, circleDp: Dp)
    fun hide()
}
