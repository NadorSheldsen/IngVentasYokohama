package com.megatransportes.yokoh.utils

import android.content.Context
import java.io.File
import com.megatransportes.yokoh.platform.ActivityHolder

/**
 * Android actual implementation for FileSaveUtils.
 * Callers should pass an Android Context as the platformContext parameter, or rely on ActivityHolder.
 */
actual object FileSaveUtils {
    actual fun saveBytesToCache(filename: String, bytes: ByteArray, platformContext: Any?): String {
        val context = when (platformContext) {
            is Context -> platformContext
            else -> ActivityHolder.activity ?: throw IllegalArgumentException("On Android you must pass a Context as platformContext to FileSaveUtils.saveBytesToCache or have ActivityHolder initialized")
        }

        val cacheDir = context.cacheDir
        val outFile = File(cacheDir, filename)
        outFile.outputStream().use { it.write(bytes) }
        return outFile.absolutePath
    }
}
