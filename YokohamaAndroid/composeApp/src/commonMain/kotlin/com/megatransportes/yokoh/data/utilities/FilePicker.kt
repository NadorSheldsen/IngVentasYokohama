// commonMain
package com.megatransportes.yokoh.utils

/**
 * Interfaz común para selección de imágenes entre plataformas.
 */
interface FilePicker {
    suspend fun pickImage(): ByteArray? // o String si prefieres ruta/base64
}
