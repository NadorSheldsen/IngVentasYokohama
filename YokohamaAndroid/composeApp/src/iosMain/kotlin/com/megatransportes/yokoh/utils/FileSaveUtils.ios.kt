package com.megatransportes.yokoh.utils

import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSearchPathDomainMask
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSUserDomainMask
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.fclose

@OptIn(ExperimentalForeignApi::class)
actual object FileSaveUtils {
    actual fun saveBytesToCache(filename: String, bytes: ByteArray, platformContext: Any?): String {
        val dirs = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        )
        val cacheDir = dirs.firstOrNull() as? String ?: ""
        val fullPath = if (cacheDir.isNotBlank()) "$cacheDir/$filename" else filename
        // Write bytes directly using POSIX to avoid NSData interop issues
        val file = fopen(fullPath, "wb")
        if (file != null) {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
            }
            fclose(file)
        }
        return fullPath
    }
}
