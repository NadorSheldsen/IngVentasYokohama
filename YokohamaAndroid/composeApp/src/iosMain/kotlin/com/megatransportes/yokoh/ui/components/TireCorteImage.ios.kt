package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TireCorteImagePlatform(
    modifier: Modifier
) {
    val imageBitmap = loadImageFromBundle("llanta_corte_t")
    
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Llanta corte",
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadImageFromBundle(name: String): ImageBitmap? {
    val uiImage = UIImage.imageNamed(name)
    
    return if (uiImage != null) {
        val pngData = UIImagePNGRepresentation(uiImage)
        
        if (pngData != null) {
            val length = pngData.length.toInt()
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                pngData.bytes?.let { source ->
                    memcpy(pinned.addressOf(0), source, length.toULong())
                }
            }
            byteArrayToImageBitmap(bytes)
        } else {
            null
        }
    } else {
        null
    }
}
