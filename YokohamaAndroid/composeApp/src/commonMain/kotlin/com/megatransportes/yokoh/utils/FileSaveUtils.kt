package com.megatransportes.yokoh.utils

/**
 * Platform-agnostic file save helpers.
 * The platformContext parameter is optional but on Android should be the Android Context instance
 * (you can pass LocalContext.current from a composable).
 */
expect object FileSaveUtils {
    fun saveBytesToCache(filename: String, bytes: ByteArray, platformContext: Any? = null): String
}
