package com.megatransportes.yokoh.utils

actual fun platformEncodeBase64(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)
