package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.utils.BluetoothCaliperManager
import com.megatransportes.yokoh.utils.getPlatformContext
import kotlinx.coroutines.launch

/**
 * Botón para conectar y recibir mediciones de calibradores Bluetooth.
 * 
 * @param onMeasurementReceived Callback cuando se recibe una medición del calibrador
 * @param modifier Modificador para el botón
 */
@Composable
fun BluetoothCaliperButton(
    onMeasurementReceived: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val platformContext = getPlatformContext()
    var isListening by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    
    // Escuchar mediciones en tiempo real
    LaunchedEffect(Unit) {
        BluetoothCaliperManager.measurementFlow.collect { measurement ->
            if (measurement != null) {
                onMeasurementReceived(measurement)
            }
        }
    }
    
    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = {
                if (isListening) {
                    coroutineScope.launch {
                        BluetoothCaliperManager.stopAndGet()
                        isListening = false
                        isConnected = false
                    }
                } else {
                    BluetoothCaliperManager.startListening(platformContext)
                    isListening = true
                    isConnected = BluetoothCaliperManager.isConnected()
                }
            },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = if (isListening) "Detener calibrador" else "Conectar calibrador",
                tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Indicador de estado
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        shape = CircleShape
                    )
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
