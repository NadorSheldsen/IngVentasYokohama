package com.megatransportes.yokoh.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

actual object MicPlatformOverlay {
    actual fun setHost(host: Any?) {
        // No-op on iOS. Compose popup handles overlay.
    }

    actual fun show(centerWindow: Offset, circleDp: Dp) {
        // No-op on iOS.
    }

    actual fun hide() {
        // No-op on iOS.
    }
}
