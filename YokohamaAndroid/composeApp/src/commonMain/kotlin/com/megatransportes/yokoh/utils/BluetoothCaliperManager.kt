package com.megatransportes.yokoh.utils

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz común para medidores de calibradores Bluetooth.
 * Funciona con calibradores que se comportan como teclados Bluetooth (HID).
 */
expect object BluetoothCaliperManager {
    /**
     * Inicia la escucha de datos del calibrador Bluetooth.
     * @param platformContext Contexto de la plataforma (Context en Android, UIViewController en iOS)
     */
    fun startListening(platformContext: Any?)
    
    /**
     * Detiene la escucha y retorna el último valor recibido.
     * @return El último valor de medición recibido, o null si no hay datos
     */
    suspend fun stopAndGet(): Float?
    
    /**
     * Flujo de valores de medición recibidos en tiempo real.
     */
    val measurementFlow: kotlinx.coroutines.flow.Flow<Float?>
    
    /**
     * Verifica si hay un calibrador conectado.
     */
    fun isConnected(): Boolean
}
