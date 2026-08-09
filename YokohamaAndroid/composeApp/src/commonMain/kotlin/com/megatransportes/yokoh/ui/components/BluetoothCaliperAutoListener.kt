package com.megatransportes.yokoh.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.megatransportes.yokoh.utils.BluetoothCaliperManager
import com.megatransportes.yokoh.utils.getPlatformContext
import kotlinx.coroutines.launch

/**
 * Escucha de calibrador Bluetooth sin UI.
 * Inicia la escucha al componerse, reenvía las mediciones a [onMeasurementReceived]
 * y detiene la escucha al salir de la pantalla.
 */
@Composable
fun BluetoothCaliperAutoListener(
    onMeasurementReceived: (Float) -> Unit
) {
    val platformContext = getPlatformContext()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        BluetoothCaliperManager.startListening(platformContext)
        BluetoothCaliperManager.measurementFlow.collect { measurement ->
            if (measurement != null) {
                onMeasurementReceived(measurement)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { BluetoothCaliperManager.stopAndGet() }
        }
    }
}