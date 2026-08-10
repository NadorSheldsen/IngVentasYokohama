package com.megatransportes.yokoh.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

/**
 * Deteccion de teclado fisico (HID) en iOS.
 *
 * `GCKeyboard.coalesced` no esta exportado en los platform libs de
 * Kotlin/Native 1.9.22, asi que se usa en su lugar el par de notificaciones
 * del framework GameController por nombre (string) sin referenciar simbolos:
 *   - "GCKeyboardDidConnectNotification"
 *   - "GCKeyboardDidDisconnectNotification"
 * La documentacion de Apple indica que la notificacion de conexion se
 * dispara tambien al registrarse si ya hay un teclado adjunto.
 */
@OptIn(ExperimentalForeignApi::class)
private object PhysicalKeyboardWatcher {
    var connected: Boolean = false
    private var registered = false

    private const val CONNECT_NAME = "GCKeyboardDidConnectNotification"
    private const val DISCONNECT_NAME = "GCKeyboardDidDisconnectNotification"

    fun ensureRegistered() {
        if (registered) return
        registered = true
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(CONNECT_NAME, null, queue) { connected = true }
        center.addObserverForName(DISCONNECT_NAME, null, queue) { connected = false }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun isPhysicalKeyboardConnected(platformContext: Any?): Boolean {
    PhysicalKeyboardWatcher.ensureRegistered()
    return PhysicalKeyboardWatcher.connected
}