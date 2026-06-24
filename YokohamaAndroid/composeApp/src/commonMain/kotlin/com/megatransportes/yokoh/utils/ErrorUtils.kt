package com.megatransportes.yokoh.utils

object ErrorUtils {
    fun userMessage(throwable: Throwable?, fallback: String = "Ocurrió un error") : String {
        if (throwable == null) return fallback
        val raw = (throwable.message ?: "").trim()
        if (raw.isEmpty()) return fallback

        // Try extract JSON "message" field FIRST (server-provided message is most important)
        try {
            val jsonMsgRegex = "\"message\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val m = jsonMsgRegex.find(raw)
            if (m != null) return m.groupValues[1]

            val singleQuoteRegex = "'message'\\s*:\\s*'([^']+)'".toRegex()
            val m2 = singleQuoteRegex.find(raw)
            if (m2 != null) return m2.groupValues[1]
        } catch (_: Exception) { }

        // Known technical errors -> user-friendly
        val lower = raw.lowercase()
        
        // === HTTP ERROR CODES ===
        if ("401" in lower || "unauthorized" in lower) 
            return "No autorizado. Verifica tus credenciales."
        
        if ("403" in lower || "forbidden" in lower) 
            return "Acceso denegado. No tienes permisos para realizar esta acción."
        
        if ("404" in lower || "not found" in lower) 
            return "Recurso no encontrado. Verifica los datos e intenta nuevamente."
        
        if ("409" in lower || "conflict" in lower) 
            return "El recurso ya existe o está siendo modificado. Recarga e intenta nuevamente."
        
        if ("500" in lower || "internal server error" in lower) 
            return "Error en el servidor. Intenta más tarde o contacta al soporte."
        
        if ("502" in lower || "bad gateway" in lower) 
            return "El servidor no está disponible. Intenta más tarde."
        
        if ("503" in lower || "unavailable" in lower || "service unavailable" in lower) 
            return "El servidor está temporalmente no disponible. Intenta más tarde."
        
        if ("504" in lower || "gateway timeout" in lower) 
            return "El servidor tardó demasiado en responder. Intenta nuevamente."
        
        if ("400" in lower || "bad request" in lower) 
            return "Solicitud inválida. Verifica los datos ingresados."
        
        if ("429" in lower || "too many requests" in lower || "rate limit" in lower) 
            return "Demasiadas solicitudes. Intenta de nuevo en unos momentos."
        
        // === CONNECTION ERRORS ===
        if ("connection refused" in lower || "failed to connect" in lower || "econnrefused" in lower) 
            return "No se pudo conectar al servidor. Revisa tu conexión a internet."
        
        if ("connection timeout" in lower || "socket timeout" in lower || "timeout" in lower) 
            return "La conexión tardó demasiado. Verifica tu conexión e intenta nuevamente."
        
        if ("ssl" in lower || "certificate" in lower) 
            return "Error de seguridad con el servidor. Intenta más tarde."
        
        if ("network" in lower && "unreachable" in lower) 
            return "No se puede alcanzar el servidor. Revisa tu conexión a internet."
        
        // === AUTHENTICATION ERRORS ===
        if ("password" in lower && ("incorrect" in lower || "invalid" in lower)) 
            return "Correo o contraseña incorrecta"
        
        if ("token" in lower && ("expired" in lower || "invalid" in lower)) 
            return "Tu sesión ha expirado. Inicia sesión nuevamente."
        
        // === PARSING/FORMAT ERRORS ===
        if ("unexpected json token" in lower || "jsonconvertexception" in lower || ("unexpected" in lower && "json" in lower)) 
            return "Respuesta inválida del servidor. Intenta nuevamente más tarde."

        if ("illegal input" in lower || "are required for type" in lower || "required for type" in lower) 
            return "Respuesta inesperada del servidor. Intenta de nuevo. Si el problema persiste, contacta al soporte."
        
        // === DATABASE/VALIDATION ERRORS ===
        if ("constraint" in lower || "unique" in lower) 
            return "El recurso ya existe. Intenta con valores diferentes."
        
        if ("not null" in lower) 
            return "Datos incompletos. Verifica que llenes todos los campos requeridos."
        
        // === GENERIC FALLBACKS ===
        if (raw.startsWith("{") || raw.startsWith("[")) 
            return fallback

        // Return trimmed message if under 200 chars, otherwise truncate
        return if (raw.length > 200) raw.substring(0, 197) + "..." else raw
    }
}
