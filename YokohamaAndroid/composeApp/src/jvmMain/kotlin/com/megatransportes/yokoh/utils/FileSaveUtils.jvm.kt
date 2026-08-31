package com.megatransportes.yokoh.utils

import java.io.File

actual object FileSaveUtils {
    actual fun saveBytesToCache(filename: String, bytes: ByteArray, platformContext: Any?): String {
        val dir = System.getProperty("java.io.tmpdir") ?: "."
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
