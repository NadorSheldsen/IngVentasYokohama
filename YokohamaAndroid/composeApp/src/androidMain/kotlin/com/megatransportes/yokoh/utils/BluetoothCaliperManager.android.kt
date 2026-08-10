package com.megatransportes.yokoh.utils

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual object BluetoothCaliperManager {
    private val _measurementFlow = MutableSharedFlow<Float?>(extraBufferCapacity = 1)
    private var isListening = false
    private var context: Context? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private val inputBuffer = StringBuilder()
    private val flushHandler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { commitBuffer() }
    
    actual val measurementFlow: Flow<Float?> = _measurementFlow
    
    actual fun startListening(platformContext: Any?) {
        val ctx = platformContext as? Context ?: return
        context = ctx
        
        isListening = true
        inputBuffer.clear()
        flushHandler.removeCallbacks(flushRunnable)
        
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
        try {
            ctx.registerReceiver(bluetoothReceiver, filter)
        } catch (_: SecurityException) {
            // On Android 12+ ACTION_ACL_CONNECTED requires BLUETOOTH_CONNECT; the receiver is only for logging.
        }
        
        println("[BluetoothCaliperManager] Started listening for Bluetooth caliper input")
    }
    
    actual suspend fun stopAndGet(): Float? {
        isListening = false
        flushHandler.removeCallbacks(flushRunnable)
        inputBuffer.clear()
        
        context?.unregisterReceiver(bluetoothReceiver)
        bluetoothReceiver = null
        context = null
        
        println("[BluetoothCaliperManager] Stopped listening")
        return null
    }
    
    actual fun isConnected(): Boolean {
        return isListening
    }
    
    /**
     * Procesa entrada de teclado (debe ser llamada desde el dispatchKeyEvent).
     *
     * Los calibradores Mitutoyo Bluetooth (U-WAVE / U-WAVE fit) se comportan como un
     * teclado HID: escriben la medición (dígitos + punto decimal) y después un terminador
     * (Enter, Tab o CR) que en la mayoría de apps mueve el foco al siguiente campo.
     *
     * Estrategia según la documentación:
     *  - Se consumen los caracteres de la medición (dígitos, punto, signo, borrado) para
     *    que NO se escriban directamente en el campo y no se produzcan entradas dobles.
     *  - Se acumulan en un buffer.
     *  - La medición se publica cuando llega el terminador (Enter/Tab) O cuando dejan de
     *    llegar caracteres durante un breve instante (inactividad), porque algunos
     *    calibradores no envían un terminador reconocible.
     *  - El terminador se consume para que no mueva el foco (evita el salto entre campos).
     *  - El valor publicado se coloca en el campo enfocado (o primero vacío) por la pantalla.
     */
    fun processKeyEvent(event: KeyEvent): Boolean {
        if (!isListening) {
            android.util.Log.d("Mitutoyo", "processKeyEvent: NOT listening")
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Dejar pasar las teclas del teclado virtual/IME (teclado en pantalla):
        // el calibrador HID Bluetooth tiene su propio deviceId, pero las teclas
        // sintetizadas por el soft keyboard usan VIRTUAL_KEYBOARD_ID.
        if (event.deviceId == -1) {
            android.util.Log.d("Mitutoyo", "processKeyEvent: skip software keyboard (deviceId=${event.deviceId})")
            return false
        }

        when (event.keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val digit = ('0'.code + event.keyCode - KeyEvent.KEYCODE_0).toChar()
                inputBuffer.append(digit)
                scheduleFlush()
                return true
            }
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                val digit = ('0'.code + event.keyCode - KeyEvent.KEYCODE_NUMPAD_0).toChar()
                inputBuffer.append(digit)
                scheduleFlush()
                return true
            }
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_NUMPAD_DOT, KeyEvent.KEYCODE_COMMA -> {
                if (!inputBuffer.contains('.')) {
                    if (inputBuffer.isEmpty()) inputBuffer.append('0')
                    inputBuffer.append('.')
                }
                scheduleFlush()
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                if (inputBuffer.isEmpty()) inputBuffer.append('-')
                scheduleFlush()
                return true
            }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (inputBuffer.isNotEmpty()) {
                    inputBuffer.deleteCharAt(inputBuffer.length - 1)
                }
                scheduleFlush()
                return true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_TAB -> {
                android.util.Log.d("Mitutoyo", "processKeyEvent: terminator key, buffer='$inputBuffer'")
                commitBuffer()
                return true
            }
        }

        android.util.Log.d("Mitutoyo", "processKeyEvent: unhandled key ${event.keyCode} (uni=${event.unicodeChar})")
        return false
    }

    private fun scheduleFlush() {
        flushHandler.removeCallbacks(flushRunnable)
        flushHandler.postDelayed(flushRunnable, 400)
    }

    private fun commitBuffer() {
        flushHandler.removeCallbacks(flushRunnable)
        if (inputBuffer.isEmpty()) return
        val raw = inputBuffer.toString()
        val value = raw.toFloatOrNull()
        inputBuffer.clear()
        android.util.Log.d("Mitutoyo", "commitBuffer: raw='$raw' value=$value")
        if (value != null) {
            _measurementFlow.tryEmit(value)
            println("[BluetoothCaliperManager] Measurement received: $value")
        }
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
