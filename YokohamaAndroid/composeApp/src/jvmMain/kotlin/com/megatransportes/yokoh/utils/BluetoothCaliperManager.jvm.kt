package com.megatransportes.yokoh.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

actual object BluetoothCaliperManager {
    private val _measurementFlow = MutableStateFlow<Float?>(null)

    actual val measurementFlow: Flow<Float?> = _measurementFlow

    actual fun startListening(platformContext: Any?) {
        // No-op on JVM target.
    }

    actual suspend fun stopAndGet(): Float? = _measurementFlow.value

    actual fun isConnected(): Boolean = false
}
