package com.megatransportes.yokoh.utils

import java.awt.Desktop
import java.net.URI

actual object OpenFileUtils {
    actual fun openFile(filePath: String, mimeType: String, platformContext: Any?) {
        val file = java.io.File(filePath)
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
    }

    actual fun shareFile(filePath: String, mimeType: String, platformContext: Any?) {
        // No-op on JVM target.
    }

    actual fun openUrl(url: String, platformContext: Any?) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
