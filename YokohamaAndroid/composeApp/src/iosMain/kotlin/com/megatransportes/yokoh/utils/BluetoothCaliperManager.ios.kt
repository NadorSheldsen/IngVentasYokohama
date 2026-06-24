package com.megatransportes.yokoh.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNotification
import platform.UIKit.UIViewController
import platform.UIKit.UIResponder
import platform.UIKit.UIEvent
import platform.UIKit.UIPress
import platform.UIKit.UIPressPhase
import platform.UIKit.UIKeyCommand
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIKeyboardWillShowNotification
import platform.UIKit.UIApplication

actual object BluetoothCaliperManager {
    private var _measurementFlow = MutableStateFlow<Float?>(null)
    private var isListening = false
    private var currentViewController: UIViewController? = null
    private var inputBuffer = StringBuilder()
    
    actual val measurementFlow: Flow<Float?> = _measurementFlow
    
    actual fun startListening(platformContext: Any?) {
        val viewController = platformContext as? UIViewController
        if (viewController == null) {
            println("[BluetoothCaliperManager] Invalid context: expected UIViewController")
            return
        }
        
        currentViewController = viewController
        isListening = true
        inputBuffer.clear()
        
        println("[BluetoothCaliperManager] Started listening for Bluetooth caliper input")
    }
    
    actual suspend fun stopAndGet(): Float? {
        isListening = false
        val result = _measurementFlow.value
        _measurementFlow.value = null
        inputBuffer.clear()
        
        currentViewController = null
        
        println("[BluetoothCaliperManager] Stopped listening, returning: $result")
        return result
    }
    
    actual fun isConnected(): Boolean {
        return isListening
    }
    
    /**
     * Función para procesar entrada de teclado (debe ser llamada desde el delegado de texto)
     * Esta función detecta patrones típicos de calibradores Bluetooth (números seguidos de Enter)
     */
    fun processKeyPress(key: String): Boolean {
        if (!isListening) return false
        
        // Si es un dígito o punto decimal, agregar al buffer
        if (key.matches(Regex("[0-9.]"))) {
            inputBuffer.append(key)
            return true
        }
        
        // Si es Enter o similar, procesar el buffer como medición
        if (key == "\n" || key == "\r" || key == "Enter") {
            val value = inputBuffer.toString().toFloatOrNull()
            if (value != null) {
                _measurementFlow.value = value
                println("[BluetoothCaliperManager] Measurement received: $value")
            }
            inputBuffer.clear()
            return true
        }
        
        // Si es backspace, eliminar del buffer
        if (key == "\b" || key == "Backspace") {
            if (inputBuffer.isNotEmpty()) {
                inputBuffer.deleteAt(inputBuffer.length - 1)
            }
            return true
        }
        
        return false
    }
}
