package com.megatransportes.yokoh.utils

import platform.GameController.GCKeyboard

actual fun isPhysicalKeyboardConnected(platformContext: Any?): Boolean {
    // API pública desde iOS 14: GCKeyboard.coalesced es distinto de null
    // cuando hay un teclado físico conectado (incluyendo calibradores HID).
    return try {
        GCKeyboard.coalesced != null
    } catch (_: Exception) {
        false
    }
}