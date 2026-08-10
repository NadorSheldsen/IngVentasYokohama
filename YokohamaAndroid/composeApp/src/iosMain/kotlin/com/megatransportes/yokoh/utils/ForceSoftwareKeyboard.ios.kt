package com.megatransportes.yokoh.utils

/**
 * En iOS no existe una API pública para forzar el teclado en pantalla cuando hay
 * un teclado HID externo conectado: el sistema lo suprime a nivel de OS y sólo el
 * ajuste "Show On-Screen Keyboard" (Ajustes > General > Teclado) lo permite.
 * Se devuelve false para indicar que el toggle no pudo forzarlo; el usuario debe
 * activar ese ajuste de sistema.
 */
actual fun forceShowSoftwareKeyboard(platformContext: Any?): Boolean {
    return false
}