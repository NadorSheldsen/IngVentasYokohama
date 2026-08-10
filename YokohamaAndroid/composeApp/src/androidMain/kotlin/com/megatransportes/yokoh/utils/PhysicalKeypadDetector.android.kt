package com.megatransportes.yokoh.utils

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice

actual fun isPhysicalKeyboardConnected(platformContext: Any?): Boolean {
    val context = platformContext as? Context ?: return false
    val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return false
    return try {
        inputManager.inputDeviceIds.any { id ->
            val device = inputManager.getInputDevice(id) ?: return@any false
            val isKeyboard = (device.sources and InputDevice.SOURCE_KEYBOARD) != 0 ||
                (device.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_CLASS_BUTTON)) != 0
            // Excluir teclados virtuales/sintéticos del sistema
            if (!isKeyboard) return@any false
            val name = device.name.orEmpty()
            !name.contains("Virtual", ignoreCase = true) &&
                !name.contains("on-screen", ignoreCase = true) &&
                !name.contains("onscreen", ignoreCase = true)
        }
    } catch (_: Exception) {
        false
    }
}