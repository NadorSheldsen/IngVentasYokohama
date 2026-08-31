package com.megatransportes.yokoh.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

actual object MicPlatformOverlay {
    actual fun setHost(host: Any?) {}
    actual fun show(centerWindow: Offset, circleDp: Dp) {}
    actual fun hide() {}
}
