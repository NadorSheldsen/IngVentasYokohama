package com.megatransportes.yokoh.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual object BluetoothCaliperManager {
    private var _measurementFlow = MutableStateFlow<Float?>(null)
    private var isListening = false
    private var context: Context? = null
    private var inputBuffer = StringBuilder()
    private var bluetoothReceiver: BroadcastReceiver? = null
    
    actual val measurementFlow: kotlinx.coroutines.flow.Flow<Float?> = _measurementFlow
    
    actual fun startListening(platformContext: Any?) {
        val ctx = platformContext as? Context ?: return
        context = ctx
        
        // Verificar permisos Bluetooth
        val bluetoothConnectPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
        val bluetoothScanPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN)
        
        if (bluetoothConnectPermission != PackageManager.PERMISSION_GRANTED || 
            bluetoothScanPermission != PackageManager.PERMISSION_GRANTED) {
            println("[BluetoothCaliperManager] Bluetooth permissions not granted")
            return
        }
        
        isListening = true
        inputBuffer.clear()
        
        // Registrar receiver para detectar conexión de dispositivos Bluetooth HID
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        println("[BluetoothCaliperManager] Device connected: ${device?.name}")
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        println("[BluetoothCaliperManager] Device disconnected: ${device?.name}")
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ctx.registerReceiver(bluetoothReceiver, filter)
        
        println("[BluetoothCaliperManager] Started listening for Bluetooth caliper input")
    }
    
    actual suspend fun stopAndGet(): Float? {
        isListening = false
        val result = _measurementFlow.value
        _measurementFlow.value = null
        inputBuffer.clear()
        
        context?.unregisterReceiver(bluetoothReceiver)
        bluetoothReceiver = null
        context = null
        
        println("[BluetoothCaliperManager] Stopped listening, returning: $result")
        return result
    }
    
    actual fun isConnected(): Boolean {
        return isListening
    }
    
    /**
     * Función para procesar entrada de teclado (debe ser llamada desde el dispatchKeyEvent)
     * Esta función detecta patrones típicos de calibradores Bluetooth HID (números seguidos de Enter)
     */
    fun processKeyEvent(event: KeyEvent): Boolean {
        if (!isListening) return false
        
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Si es un dígito o punto decimal, agregar al buffer
            if (event.isPrintingKey) {
                val keyChar = event.unicodeChar.toChar()
                if (keyChar in '0'..'9' || keyChar == '.') {
                    inputBuffer.append(keyChar)
                    return true
                }
            }
            
            // Si es Enter o similar, procesar el buffer como medición
            if (event.keyCode == KeyEvent.KEYCODE_ENTER || 
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                val value = inputBuffer.toString().toFloatOrNull()
                if (value != null) {
                    _measurementFlow.value = value
                    println("[BluetoothCaliperManager] Measurement received: $value")
                }
                inputBuffer.clear()
                return true
            }
            
            // Si es backspace, eliminar del buffer
            if (event.keyCode == KeyEvent.KEYCODE_DEL || 
                event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                if (inputBuffer.isNotEmpty()) {
                    inputBuffer.deleteCharAt(inputBuffer.length - 1)
                }
                return true
            }
        }
        
        return false
    }
    
    /**
     * Obtiene la lista de dispositivos Bluetooth HID conectados
     */
    fun getConnectedHIDDevices(): List<InputDevice> {
        val ctx = context ?: return emptyList()
        val inputManager = ctx.getSystemService(Context.INPUT_SERVICE) as InputManager
        val devices = inputManager.inputDeviceIds.map { inputManager.getInputDevice(it) }.filterNotNull()
        return devices.filter { 
            it.sources and InputDevice.SOURCE_CLASS_JOYSTICK != 0 ||
            it.sources and InputDevice.SOURCE_KEYBOARD != 0
        }
    }
}
