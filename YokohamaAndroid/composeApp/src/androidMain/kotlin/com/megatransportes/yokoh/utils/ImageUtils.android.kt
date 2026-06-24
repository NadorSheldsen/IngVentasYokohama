package com.megatransportes.yokoh.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory

actual fun byteArrayToImageBitmap(data: ByteArray): ImageBitmap {
    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
    return bmp.asImageBitmap()
}
