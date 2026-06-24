package com.megatransportes.yokoh.utils

import androidx.compose.runtime.Composable

data class FileData(
    val name: String,
    val size: Long,
    val mimeType: String?,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FileData

        if (name != other.name) return false
        if (size != other.size) return false
        if (mimeType != other.mimeType) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Interfaz común para la selección de archivos en diferentes plataformas
 */
expect class FilePickerUtils {
    suspend fun pickImageFile(): FileData?
    suspend fun pickImageFromCamera(): FileData?
    suspend fun pickFile(vararg extensions: String): FileData?
}

/**
 * Función para crear FilePickerUtils según la plataforma
 */
expect fun createFilePickerUtils(): FilePickerUtils

/**
 * Composable para inicializar FilePicker en plataformas que lo requieren
 */
@Composable
expect fun InitializeFilePickerIfNeeded()

/**
 * Utilidades para convertir archivos a Base64 para almacenamiento como blob
 */
object FileConverter {
    fun fileDataToBase64(fileData: FileData): String {
        // Prefer a platform-provided Base64 encoder when available (Android/JVM),
        // fallback to the pure-Kotlin encoder otherwise.
        return try {
            platformEncodeBase64(fileData.data)
        } catch (e: Throwable) {
            // If platform implementation isn't present, use the Kotlin fallback
            encodeToBase64(fileData.data)
        }
    }
    
    fun base64ToByteArray(base64: String): ByteArray {
        return decodeFromBase64(base64)
    }

    /**
     * Attempts to convert an input string which may be either a base64 string or
     * a JSON-like Buffer object string (e.g. {"type":"Buffer","data":[47,57,....]})
     */
    fun safeStringToByteArray(input: String): ByteArray {
        val trimmed = input.trim()
        // Heuristic: if it starts with '{' and contains "data": [ then parse numbers
        if (trimmed.startsWith("{") && trimmed.contains("\"data\"") && trimmed.contains("[")) {
            try {
                val start = trimmed.indexOf("[", trimmed.indexOf("\"data\""))
                val end = trimmed.indexOf("]", start)
                if (start >= 0 && end > start) {
                    val nums = trimmed.substring(start + 1, end).split(',').mapNotNull {
                        val t = it.trim()
                        if (t.isEmpty()) null else t.toIntOrNull()
                    }
                    return nums.map { it.toByte() }.toByteArray()
                }
            } catch (_: Exception) {
                // fallthrough to base64 decode
            }
        }

        // If it's a data URI like data:image/jpeg;base64,... strip the prefix
        var candidate = trimmed
        val commaIndex = candidate.indexOf(',')
        if (candidate.startsWith("data:") && commaIndex >= 0) {
            candidate = candidate.substring(commaIndex + 1).trim()
        }

        // Try one-pass base64 decode
        try {
            val first = decodeFromBase64(candidate)
            // Check for JPEG (0xFF 0xD8 0xFF) or PNG (0x89 'P' 'N' 'G')
            if (first.size >= 3 && first[0] == 0xFF.toByte() && first[1] == 0xD8.toByte() && first[2] == 0xFF.toByte()) {
                return first
            }
            if (first.size >= 8 && first[0] == 0x89.toByte() && first[1] == 0x50.toByte() && first[2] == 0x4E.toByte() && first[3] == 0x47.toByte()) {
                return first
            }

            // If first decodes to ASCII that looks like base64 (double-encoded), try decoding again
            val asText = try { first.decodeToString().trim() } catch (_: Exception) { "" }
            val base64Regex = Regex("^[A-Za-z0-9+/=\\r\\n]+$")
            if (asText.isNotEmpty() && base64Regex.matches(asText.replace("\n", "").replace("\r", ""))) {
                try {
                    val second = decodeFromBase64(asText)
                    if (second.size >= 3 && second[0] == 0xFF.toByte() && second[1] == 0xD8.toByte() && second[2] == 0xFF.toByte()) {
                        return second
                    }
                    if (second.size >= 8 && second[0] == 0x89.toByte() && second[1] == 0x50.toByte() && second[2] == 0x4E.toByte() && second[3] == 0x47.toByte()) {
                        return second
                    }
                } catch (_: Exception) {
                    // fallthrough
                }
            }

            // If none matched, return the first attempt anyway
            return first
        } catch (e: Exception) {
            // Fallback: try plain base64 decode of original input
            return decodeFromBase64(input)
        }
    }
    
    private fun encodeToBase64(data: ByteArray): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val result = StringBuilder()
        
        var i = 0
        while (i < data.size) {
            val b1 = data[i].toInt() and 0xFF
            val b2 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b3 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            
            val bitmap = (b1 shl 16) or (b2 shl 8) or b3
            
            result.append(chars[(bitmap shr 18) and 0x3F])
            result.append(chars[(bitmap shr 12) and 0x3F])
            
            if (i + 1 < data.size) {
                result.append(chars[(bitmap shr 6) and 0x3F])
            } else {
                result.append('=')
            }
            
            if (i + 2 < data.size) {
                result.append(chars[bitmap and 0x3F])
            } else {
                result.append('=')
            }
            
            i += 3
        }
        
        return result.toString()
    }
    
    private fun decodeFromBase64(base64: String): ByteArray {
        // Support URL-safe base64, ignore whitespace/newlines, and handle padding correctly.
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val lookup = IntArray(256) { -1 }

        for (i in chars.indices) lookup[chars[i].code] = i

        // Normalize: remove whitespace, convert URL-safe chars to standard, keep padding
        var s = base64.replace("\\s".toRegex(), "")
        s = s.replace('-', '+').replace('_', '/')

        // Add padding if needed
        val mod = s.length % 4
        if (mod != 0) {
            val pad = 4 - mod
            s += "=".repeat(pad)
        }

        val out = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c1 = s[i]
            val c2 = if (i + 1 < s.length) s[i + 1] else '='
            val c3 = if (i + 2 < s.length) s[i + 2] else '='
            val c4 = if (i + 3 < s.length) s[i + 3] else '='

            val b1 = if (c1 == '=') 0 else lookup.getOrElse(c1.code) { -1 }.coerceAtLeast(0)
            val b2 = if (c2 == '=') 0 else lookup.getOrElse(c2.code) { -1 }.coerceAtLeast(0)
            val b3 = if (c3 == '=') 0 else lookup.getOrElse(c3.code) { -1 }.coerceAtLeast(0)
            val b4 = if (c4 == '=') 0 else lookup.getOrElse(c4.code) { -1 }.coerceAtLeast(0)

            val bitmap = (b1 shl 18) or (b2 shl 12) or (b3 shl 6) or b4

            out.add(((bitmap shr 16) and 0xFF).toByte())
            if (c3 != '=') out.add(((bitmap shr 8) and 0xFF).toByte())
            if (c4 != '=') out.add((bitmap and 0xFF).toByte())

            i += 4
        }

        return out.toByteArray()
    }
}

// Platform hook: actual implementations can use native encoders (Android/JVM) for
// performance and to rule out issues with the custom encoder above.
expect fun platformEncodeBase64(data: ByteArray): String