package com.megatransportes.yokoh.utils

actual fun platformEncodeBase64(data: ByteArray): String {
    // Use Android's native Base64 implementation with NO_WRAP to avoid newlines
    return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
}
