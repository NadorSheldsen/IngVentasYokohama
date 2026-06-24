package com.megatransportes.yokoh.utils

// Extrae sólo la parte de fecha de un datetime (ej. "2025-10-21T12:34:56" -> "2025-10-21")
fun formatDateOnly(dateTime: String?): String {
    if (dateTime.isNullOrBlank()) return "Sin fecha"
    // Manejar formatos comunes: ISO (T), espacio separado, o timestamp simple
    return dateTime.split('T', ' ').firstOrNull() ?: dateTime
}
