package com.megatransportes.yokoh.utils

/**
 * Fuerza la aparición del teclado en pantalla (soft keyboard) aunque haya un
 * teclado físico/HID conectado (como el calibrador Bluetooth Mitutoyo), que
 * normalmente hace que el sistema lo suprima.
 * @param platformContext Contexto de la plataforma (Context en Android, UIViewController en iOS)
 * @return true si se pudo solicitar mostrar el teclado
 */
expect fun forceShowSoftwareKeyboard(platformContext: Any?): Boolean
