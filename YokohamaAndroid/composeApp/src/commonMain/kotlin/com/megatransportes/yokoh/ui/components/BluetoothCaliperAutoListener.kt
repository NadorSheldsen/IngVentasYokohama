package com.megatransportes.yokoh.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.megatransportes.yokoh.utils.BluetoothCaliperManager
import com.megatransportes.yokoh.utils.getPlatformContext
import kotlinx.coroutines.launch

/**
 * Escucha de calibrador Bluetooth sin UI.
 * Inicia la escucha al componerse, reenvía cada medición a [onMeasurementReceived]
 * (para colocarla en el campo enfocado / primer campo vacío) y detiene la escucha
 * al salir de la pantalla.
 *
 * Se usa [rememberUpdatedState] para que el colector siempre invoque la versión más
 * reciente del callback: de lo contrario `LaunchedEffect` retiene la lambda del primer
 * render (con el primer `data`) y las mediciones posteriores revierten campos previos.
 */
@Composable
fun BluetoothCaliperAutoListener(
    onMeasurementReceived: (Float) -> Unit
) {
    val platformContext = getPlatformContext()
    val scope = rememberCoroutineScope()
    val currentOnMeasurementReceived by rememberUpdatedState(onMeasurementReceived)

    LaunchedEffect(Unit) {
        BluetoothCaliperManager.startListening(platformContext)
        BluetoothCaliperManager.measurementFlow.collect { measurement ->
            if (measurement != null) {
                currentOnMeasurementReceived(measurement)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { BluetoothCaliperManager.stopAndGet() }
        }
    }
}