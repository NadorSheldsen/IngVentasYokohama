package com.megatransportes.yokoh.utils

import androidx.compose.runtime.Composable

actual class FilePickerUtils {
    actual suspend fun pickImageFile(): FileData? = null
    actual suspend fun pickImageFromCamera(): FileData? = null
    actual suspend fun pickFile(vararg extensions: String): FileData? = null
}

actual fun createFilePickerUtils(): FilePickerUtils = FilePickerUtils()

@Composable
actual fun InitializeFilePickerIfNeeded() {
    // No-op on JVM target.
}
