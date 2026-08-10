package com.megatransportes.yokoh.utils

/**
 * Indica si hay un teclado físico (HID) conectado al dispositivo.
 * El calibrador Bluetooth Mitutoyo se registra como teclado HID, así que
 * Android/iOS ocultan el teclado en pantalla mientras está conectado.
 *
 * @param platformContext Context (Android) / UIViewController (iOS)
 */
expect fun isPhysicalKeyboardConnected(platformContext: Any?): Boolean