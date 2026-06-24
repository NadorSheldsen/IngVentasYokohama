package com.megatransportes.yokoh.utils

import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual fun platformEncodeBase64(data: ByteArray): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()

    var index = 0
    while (index < data.size) {
        val first = data[index].toInt() and 0xFF
        val second = if (index + 1 < data.size) data[index + 1].toInt() and 0xFF else 0
        val third = if (index + 2 < data.size) data[index + 2].toInt() and 0xFF else 0

        val bitmap = (first shl 16) or (second shl 8) or third

        result.append(chars[(bitmap shr 18) and 0x3F])
        result.append(chars[(bitmap shr 12) and 0x3F])
        result.append(if (index + 1 < data.size) chars[(bitmap shr 6) and 0x3F] else '=')
        result.append(if (index + 2 < data.size) chars[bitmap and 0x3F] else '=')

        index += 3
    }

    return result.toString()
}
