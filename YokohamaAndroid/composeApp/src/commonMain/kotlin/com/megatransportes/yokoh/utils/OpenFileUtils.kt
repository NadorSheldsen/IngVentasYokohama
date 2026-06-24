package com.megatransportes.yokoh.utils

/**
 * Platform-agnostic helper to open a file path with the platform's viewer.
 * On Android the path should be accessible via FileProvider (cache dir allowed).
 */
expect object OpenFileUtils {
    fun openFile(filePath: String, mimeType: String = "application/pdf", platformContext: Any? = null)
    fun shareFile(filePath: String, mimeType: String = "application/pdf", platformContext: Any? = null)
    fun openUrl(url: String, platformContext: Any? = null)
}
