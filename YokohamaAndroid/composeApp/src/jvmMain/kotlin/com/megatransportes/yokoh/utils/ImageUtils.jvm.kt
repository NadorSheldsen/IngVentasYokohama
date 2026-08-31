package com.megatransportes.yokoh.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual fun byteArrayToImageBitmap(data: ByteArray): ImageBitmap {
    val bufferedImage = ImageIO.read(ByteArrayInputStream(data)) ?: error("No se pudo decodificar la imagen")
    return bufferedImage.toComposeImageBitmap()
}
