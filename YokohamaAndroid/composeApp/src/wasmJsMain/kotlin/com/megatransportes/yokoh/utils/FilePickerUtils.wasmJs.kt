package com.megatransportes.yokoh.utils

import androidx.compose.runtime.Composable

actual class FilePickerUtils {
    
    actual suspend fun pickImageFile(): FileData? {
        // Implementación WASM simplificada - devuelve null por ahora
        return null
    }
    
    actual suspend fun pickFile(vararg extensions: String): FileData? {
        // Implementación WASM simplificada - devuelve null por ahora
        return null
    }
}

actual fun createFilePickerUtils(): FilePickerUtils {
    return FilePickerUtils()
}

@Composable
actual fun InitializeFilePickerIfNeeded() {
    // No se requiere inicialización en WASM
}