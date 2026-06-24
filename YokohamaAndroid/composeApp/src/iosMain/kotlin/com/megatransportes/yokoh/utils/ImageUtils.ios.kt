package com.megatransportes.yokoh.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun byteArrayToImageBitmap(data: ByteArray): ImageBitmap {
    val skiaImage = Image.makeFromEncoded(data)
    return skiaImage.toComposeImageBitmap()
}
