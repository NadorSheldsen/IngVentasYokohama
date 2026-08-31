package com.megatransportes.yokoh.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import com.megatransportes.yokoh.data.models.*

class ApiClient(
    // Default baseUrl: use localhost for local development (can be overridden via constructor).
    // If you need to hit the public Plesk host, the previous value was:
    private val baseUrl: String = "https://pensive-saha.207-210-229-77.plesk.page/api"
    //private val baseUrl: String = "http://10.0.2.2:3000/api"
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                // Be lenient globally to accept slightly malformed numbers/strings from older deployments
                isLenient = true
                ignoreUnknownKeys = true
                // Ensure null properties are encoded so updates that set fields to null
                // are sent to the server (important for deleting images)
                explicitNulls = true
            })
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    @Serializable
    private data class VehiculoKmRecorridoDto(
        val vehiculoId: Int,
        val kmRecorrido: Float
    )

    // Try to extract a user-friendly message from a server error body. Works for:
    // - JSON payloads like { message: '...' } or { error: '...', message: '...' }
    // - HTML error pages (strip tags and return the visible text)
    private fun extractServerMessage(status: HttpStatusCode, bodyText: String?): String {
        val raw = bodyText ?: ""
        // Try JSON { message: ... }
        try {
            if (raw.trim().startsWith("{") || raw.trim().startsWith("[")) {
                val elem = Json.parseToJsonElement(raw)
                if (elem is JsonObject) {
                    val msg = elem["message"]?.jsonPrimitive?.contentOrNull
                        ?: elem["error"]?.jsonPrimitive?.contentOrNull
                    if (!msg.isNullOrBlank()) return msg
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        // If the server returned an IIS/HTML error page, try to extract concise readable text
        try {
            // Try common HTML locations in order of usefulness: <h2>, <p class="lead">, <title>
            val h2Match = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.IGNORE_CASE).find(raw)
            if (h2Match != null) {
                val txt = h2Match.groupValues[1].replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                if (txt.isNotBlank()) return txt
            }

            val leadMatch = Regex("<p[^>]*class=['\"]lead['\"][^>]*>(.*?)</p>", RegexOption.IGNORE_CASE).find(raw)
            if (leadMatch != null) {
                val txt = leadMatch.groupValues[1].replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                if (txt.isNotBlank()) return txt
            }

            val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(raw)
            if (titleMatch != null) {
                val txt = titleMatch.groupValues[1].replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                if (txt.isNotBlank()) return txt
            }

            // common English phrase seen in some hosting error pages — keep some friendly fallbacks
            val known = listOf(
                "The page was not displayed because there was a conflict",
                "conflict",
                "Error",
                "Bad Request",
                "Server Error"
            )
            known.forEach { k ->
                if (raw.contains(k, ignoreCase = true)) {
                    return when (status) {
                        HttpStatusCode.Conflict -> "Recurso en conflicto (409)"
                        HttpStatusCode.BadRequest -> "Solicitud inválida (400)"
                        HttpStatusCode.InternalServerError -> "Error interno del servidor (500)"
                        else -> "HTTP ${status.value}: ${status.description}"
                    }
                }
            }

            // Fallback: strip HTML tags and collapse whitespace; truncate to avoid huge blobs
            val stripped = raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            if (stripped.isNotBlank()) return if (stripped.length > 500) stripped.take(500) + "..." else stripped
        } catch (_: Exception) {}

        return "HTTP ${status.value}: ${status.description}"
    }

    // Prefer a server-provided header (X-Error-Message) for error text, then JSON { message: ... },
    // otherwise fall back to extractServerMessage which strips HTML or returns HTTP code.
    private fun extractMessageFromResponse(status: HttpStatusCode, bodyText: String?, headers: io.ktor.http.Headers): String {
        try {
            // Prefer explicit header (some fronting proxies preserve headers even if they replace body)
            val headerMsg = headers["X-Error-Message"] ?: headers["x-error-message"]
            if (!headerMsg.isNullOrBlank()) return headerMsg
        } catch (_: Exception) { /* ignore header read errors */ }

        // Try JSON { message: '...' } in body
        try {
            val raw = bodyText ?: ""
            if (raw.trim().startsWith("{") || raw.trim().startsWith("[")) {
                val elem = Json.parseToJsonElement(raw)
                if (elem is JsonObject) {
                    val msg = elem["message"]?.jsonPrimitive?.contentOrNull
                        ?: elem["error"]?.jsonPrimitive?.contentOrNull
                    if (!msg.isNullOrBlank()) return msg
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return extractServerMessage(status, bodyText)
    }

    // --- Helpers: defensive response handling ---
    private suspend inline fun <reified T> safeGetList(url: String): List<T> {
        val response = client.get(url)
        val status = response.status
        val contentType = response.headers[HttpHeaders.ContentType]?.toString() ?: ""

        val text = try {
            response.bodyAsText()
        } catch (e: Exception) {
            throw Exception("El servidor devolvió un cuerpo no textual (Content-Type=$contentType).", e)
        }

        if (!status.isSuccess()) {
            val msg = try {
                Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            } catch (_: Exception) { null }
            throw Exception(msg ?: "Respuesta de error del servidor: ${status.value}")
        }

        if (contentType.contains("application/json", ignoreCase = true) || text.trimStart().startsWith("[") || text.trimStart().startsWith("{")) {
            try {
                return Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(text)
            } catch (e: Exception) {
                // Try a more defensive manual parse for lists/objects to avoid bubbling a deserialization
                // exception to the UI. We'll attempt to parse into a JsonElement and map values.
                try {
                    val elem = Json.parseToJsonElement(text)
                    // If T is a list-like type and elem is array, try to map elements to T using a forgiving approach
                    if (elem is JsonArray) {
                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                        val list = mutableListOf<T>()
                        elem.forEach { je ->
                            try {
                                val item = json.decodeFromJsonElement<T>(je)
                                list.add(item)
                            } catch (_: Exception) {
                                // Skip problematic element
                            }
                        }
                        return list
                    }
                } catch (_: Exception) {
                    // fallthrough
                }

                throw Exception("Error al deserializar JSON recibido.", e)
            }
        } else {
            throw Exception("Respuesta inesperada: Content-Type=$contentType; no es JSON.")
        }
    }

    private suspend inline fun <reified T> safePostList(url: String, bodyObj: Any): List<T> {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(bodyObj)
        }
        val status = response.status
        val contentType = response.headers[HttpHeaders.ContentType]?.toString() ?: ""

        val text = try {
            response.bodyAsText()
        } catch (e: Exception) {
            throw Exception("El servidor devolvió un cuerpo no textual (Content-Type=$contentType).", e)
        }

        if (!status.isSuccess()) {
            val msg = try {
                Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            } catch (_: Exception) { null }
            throw Exception(msg ?: "Respuesta de error del servidor: ${status.value}")
        }

        if (contentType.contains("application/json", ignoreCase = true) || text.trimStart().startsWith("[") || text.trimStart().startsWith("{")) {
            try {
                return Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(text)
            } catch (e: Exception) {
                try {
                    val elem = Json.parseToJsonElement(text)
                    if (elem is JsonArray) {
                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                        val list = mutableListOf<T>()
                        elem.forEach { je ->
                            try {
                                val item = json.decodeFromJsonElement<T>(je)
                                list.add(item)
                            } catch (_: Exception) {
                                // Skip problematic element
                            }
                        }
                        return list
                    }
                } catch (_: Exception) {
                    // fallthrough
                }

                throw Exception("Error al deserializar JSON recibido.", e)
            }
        } else {
            throw Exception("Respuesta inesperada: Content-Type=$contentType; no es JSON.")
        }
    }

    // Authentication
    suspend fun login(email: String, password: String): Result<Usuario> {
        return try {
            println("Attempting login with: $email")

            // Perform the POST and inspect the raw response first to avoid trying to
            // deserialize an error-shaped payload (e.g. { message: "..." }) into Usuario
            val call = client.post("$baseUrl/usuarios/login") {
                setBody(LoginRequest(email, password))
            }

            val status = call.status
            // Read body as text so we can decide how to handle it
            val textBody: String = try {
                call.body()
            } catch (e: Exception) {
                // If we can't read the body as text, return a generic failure
                println("Login error reading body: ${e.message}")
                return Result.failure(Exception("Error de inicio de sesión"))
            }

            if (!status.isSuccess()) {
                // Try to extract a user-friendly message from the JSON payload if present
                try {
                    val parsed = Json.parseToJsonElement(textBody)
                    val msg = parsed.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    return Result.failure(Exception(msg ?: "Credenciales inválidas"))
                } catch (_: Exception) {
                    return Result.failure(Exception("Credenciales inválidas"))
                }
            }

            // Try to decode a successful response into Usuario
            try {
                val usuario: Usuario = Json { ignoreUnknownKeys = true }.decodeFromString(textBody)
                println("Login successful: ${usuario.UsuariosNombre}")
                Result.success(usuario)
            } catch (e: Exception) {
                // If decoding fails, maybe the server returned { message: '...' } with 200 OK.
                try {
                    val parsed = Json.parseToJsonElement(textBody)
                    val msg = parsed.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    return Result.failure(Exception(msg ?: "Respuesta inesperada del servidor"))
                } catch (_: Exception) {
                    println("Login decode error: ${e.message}")
                    return Result.failure(e)
                }
            }
        } catch (e: Exception) {
            println("Login error: ${e.message}")
            Result.failure(e)
        }
    }

    // Flotas
    suspend fun getAllFlotas(): Result<List<Flota>> {
        return try {
            val response: List<Flota> = safeGetList("$baseUrl/flotas")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFlota(flota: FlotaCreateRequest): Result<Flota> {
        return try {
            val httpResponse: HttpResponse = client.post("$baseUrl/flotas") {
                setBody(flota)
            }

            if (httpResponse.status.isSuccess()) {
                val response: Flota = httpResponse.body()
                Result.success(response)
            } else {
                val text = try { httpResponse.bodyAsText() } catch (_: Exception) { null }
                val msg = extractMessageFromResponse(httpResponse.status, text, httpResponse.headers)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFlotasByUsuarioId(userId: Int): Result<List<Flota>> {
        return try {
            val response: List<Flota> = safeGetList("$baseUrl/usuarios/$userId/flotas")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Usuarios
    suspend fun getAllUsuarios(): Result<List<Usuario>> {
        return try {
            val response: List<Usuario> = safeGetList("$baseUrl/usuarios")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDistribuidores(): Result<List<Distribuidor>> {
        return try {
            val response: List<Distribuidor> = safeGetList("$baseUrl/incentivos/distribuidores")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUsuario(usuario: UsuarioCreateRequest): Result<Usuario> {
        return try {
            val response: Usuario = client.post("$baseUrl/usuarios") {
                setBody(usuario)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUsuario(usuarioId: Int, request: UsuarioUpdateRequest): Result<Usuario> {
        return try {
            val response: Usuario = client.put("$baseUrl/usuarios/$usuarioId") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // PerfilesUsuario
    suspend fun getPerfilesUsuario(): Result<List<PerfilesUsuario>> {
        return try {
            val response: List<PerfilesUsuario> = safeGetList("$baseUrl/perfiles-usuario")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPerfilUsuario(request: PerfilesUsuarioCreateRequest): Result<PerfilesUsuario> {
        return try {
            val response: PerfilesUsuario = client.post("$baseUrl/perfiles-usuario") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePerfilUsuario(perfilId: Int, request: PerfilesUsuarioUpdateRequest): Result<PerfilesUsuario> {
        return try {
            val response: PerfilesUsuario = client.put("$baseUrl/perfiles-usuario/$perfilId") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Permisos
    suspend fun getPermisos(): Result<List<Permisos>> {
        return try {
            val response: List<Permisos> = safeGetList("$baseUrl/permisos")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPermisosByPerfilId(perfilId: Int): Result<List<Permisos>> {
        return try {
            val response: List<Permisos> = safeGetList("$baseUrl/permisos/perfil/$perfilId")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPermiso(request: PermisosCreateRequest): Result<Permisos> {
        return try {
            val response: Permisos = client.post("$baseUrl/permisos") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermiso(permisoId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/permisos/$permisoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // FlotasUsuarios
    suspend fun createFlotaUsuario(request: FlotasUsuariosCreateRequest): Result<FlotasUsuarios> {
        return try {
            val call = client.post("$baseUrl/flotas-usuarios") {
                setBody(request)
            }

            val status = call.status
            val textBody = try { call.bodyAsText() } catch (e: Exception) { "" }

            if (!status.isSuccess()) {
                return Result.failure(Exception(extractMessageFromResponse(status, textBody, call.headers)))
            }

            // Try to decode the successful response into FlotasUsuarios
            try {
                val parsed: FlotasUsuarios = Json { ignoreUnknownKeys = true }.decodeFromString(textBody)
                return Result.success(parsed)
            } catch (_: Exception) {
                // Fallback: server may return only an id/insertId. Try to extract id and GET the created resource.
                return try {
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["idFlotasUsuarios"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createFlotaUsuario; body=$textBody")
                    val created: FlotasUsuarios = client.get("$baseUrl/flotas-usuarios/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFlotaUsuario(flotaUsuarioId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/flotas-usuarios/$flotaUsuarioId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFlotasUsuariosByUsuarioId(usuarioId: Int): Result<List<FlotasUsuarios>> {
        return try {
            // Some backend deployments don't expose a /usuario/:id route; fetch all and filter client-side
            val all: List<FlotasUsuarios> = safeGetList("$baseUrl/flotas-usuarios")
            val filtered = all.filter { it.Usuarios_idUsuarios == usuarioId }
            Result.success(filtered)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching flotas-usuarios for usuario $usuarioId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUsuariosByFlotaId(flotaId: Int): Result<List<UsuarioWithFlotaUsuarioId>> {
        return try {
            val usuarios: List<UsuarioWithFlotaUsuarioId> = safeGetList("$baseUrl/flotas-usuarios/flota/$flotaId/usuarios")
            Result.success(usuarios)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching usuarios for flota $flotaId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUsuariosNoAsociadosByFlotaId(flotaId: Int): Result<List<Usuario>> {
        return try {
            val usuarios: List<Usuario> = safeGetList("$baseUrl/flotas-usuarios/flota/$flotaId/usuarios-no-asociados")
            Result.success(usuarios)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching usuarios no asociados for flota $flotaId: ${e.message}")
            Result.failure(e)
        }
    }

    // Vehiculos
    suspend fun getVehiculosByFlotaId(flotaId: Int): Result<List<Vehiculo>> {
        return try {
            val call = client.get("$baseUrl/vehiculos") {
                parameter("Flotas_idFlotas", flotaId)
            }

            // Read raw text for debugging and then decode to objects
            val text: String = try {
                call.body()
            } catch (e: Exception) {
                println("[ApiClient] Error reading vehiculos raw body as text: ${e.message}")
                throw e
            }

            println("[ApiClient] Raw /vehiculos response (first 2000 chars): ${text.take(2000)}")

            // Decode using kotlinx.serialization to the data class list
            val parsed: List<Vehiculo> = try {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(text)
            } catch (e: Exception) {
                println("[ApiClient] Error decoding vehiculos JSON: ${e.message}")
                throw e
            }

            println("[ApiClient] Parsed vehiculos count: ${parsed.size}")
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obtener un vehículo por id
    suspend fun getVehiculoById(id: Int): Result<Vehiculo> {
        return try {
            val response: Vehiculo = client.get("$baseUrl/vehiculos/$id").body()
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching vehiculo id=$id: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getKmRecorridoPorVehiculoByFlota(flotaId: Int): Map<Int, Float> {
        val response: List<VehiculoKmRecorridoDto> = safeGetList("$baseUrl/prueba-rendimiento/flota/$flotaId/km-recorrido")
        return response.associate { it.vehiculoId to it.kmRecorrido }
    }

    // Reports - download PDF for a vehicle rendimiento
    suspend fun downloadVehiculoRendimientoPdf(vehiculoId: Int): Result<ByteArray> {
        return try {
            val url = "$baseUrl/reports/vehiculo/$vehiculoId/rendimiento.pdf"
            println("[ApiClient] Downloading PDF from: $url")
            val response = client.get(url)
            val status = response.status
            println("[ApiClient] Response status: $status")
            val contentType = response.headers["Content-Type"] ?: response.headers["content-type"]
            println("[ApiClient] Content-Type: $contentType")
            // read raw bytes
            val bytes: ByteArray = try {
                response.body()
            } catch (e: Exception) {
                println("[ApiClient] Error reading response body as bytes: ${e.message}")
                throw e
            }
            println("[ApiClient] Downloaded bytes: ${bytes.size}")
            // Basic validation: check for PDF header
            if (bytes.size < 4 || !(bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())) {
                println("[ApiClient] Warning: downloaded file does not start with %PDF header. First bytes: ${bytes.take(8).joinToString(" ") { it.toString(16).uppercase().padStart(2, '0') }}")
            }
            Result.success(bytes)
        } catch (e: Exception) {
            println("Error downloading PDF: ${e.message}")
            Result.failure(e)
        }
    }

    // POST arbitrary payload to generate a semáforo PDF on the server
    suspend fun downloadSemaforoReportPdf(payload: Any): Result<ByteArray> {
        return try {
            val url = "$baseUrl/reports/semaforo/report.pdf"
            println("[ApiClient] Posting to: $url")
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                if (payload is String) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                } else {
                    setBody(payload)
                }
            }
            val status = response.status
            if (!status.isSuccess()) {
                val text = try { response.bodyAsText() } catch (_: Exception) { "" }
                return Result.failure(Exception(extractMessageFromResponse(status, text, response.headers)))
            }

            val bytes: ByteArray = try { response.body() } catch (e: Exception) { throw e }
            // Basic PDF header check
            if (bytes.size < 4 || !(bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())) {
                println("[ApiClient] Warning: response does not start with %PDF header")
            }
            Result.success(bytes)
        } catch (e: Exception) {
            println("Error downloading semaforo PDF: ${e.message}")
            Result.failure(e)
        }
    }

    // POST arbitrary payload to generate an inspección PDF on the server
    suspend fun downloadPruebaInspeccionReportPdf(payload: Any): Result<ByteArray> {
        return try {
            val url = "$baseUrl/reports/inspeccion/report.pdf"
            println("[ApiClient] Posting to: $url")
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                if (payload is String) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                } else {
                    setBody(payload)
                }
            }
            val status = response.status
            if (!status.isSuccess()) {
                val text = try { response.bodyAsText() } catch (_: Exception) { "" }
                return Result.failure(Exception(extractMessageFromResponse(status, text, response.headers)))
            }

            val bytes: ByteArray = try { response.body() } catch (e: Exception) { throw e }
            if (bytes.size < 4 || !(bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())) {
                println("[ApiClient] Warning: response does not start with %PDF header for inspeccion report")
            }
            Result.success(bytes)
        } catch (e: Exception) {
            println("Error downloading inspeccion PDF: ${e.message}")
            Result.failure(e)
        }
    }

    // POST arbitrary payload to generate a desecho PDF on the server
    suspend fun downloadDesechoReportPdf(payload: Any): Result<ByteArray> {
        return try {
            val url = "$baseUrl/reports/desecho/report.pdf"
            println("[ApiClient] Posting to: $url")
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                if (payload is String) {
                    setBody(TextContent(payload, ContentType.Application.Json))
                } else {
                    setBody(payload)
                }
            }
            val status = response.status
            if (!status.isSuccess()) {
                val text = try { response.bodyAsText() } catch (_: Exception) { "" }
                return Result.failure(Exception(extractMessageFromResponse(status, text, response.headers)))
            }

            val bytes: ByteArray = try { response.body() } catch (e: Exception) { throw e }
            if (bytes.size < 4 || !(bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())) {
                println("[ApiClient] Warning: response does not start with %PDF header for desecho report")
            }
            Result.success(bytes)
        } catch (e: Exception) {
            println("Error downloading desecho PDF: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createVehiculo(vehiculo: VehiculoCreateRequest): Result<Vehiculo> {
        return try {
            // Debug: report if an image is present and its size
            try {
                val imgInfo = vehiculo.VehiculosImagen?.let { "present, base64 length=${it.length}" } ?: "no image"
                println("[ApiClient] createVehiculo - sending vehiculo with image: $imgInfo")
            } catch (_: Exception) {}
            val responseCall = client.post("$baseUrl/vehiculos") {
                setBody(vehiculo)
            }

            // If status not success, try to include response body in error
            if (!responseCall.status.isSuccess()) {
                val text = try { responseCall.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(responseCall.status, text, responseCall.headers)))
            }

            // Try to decode directly as Vehiculo
            try {
                val response: Vehiculo = responseCall.body()
                return Result.success(response)
            } catch (_: Exception) {
                // Backend may return only { id } or { insertId } or a message -> try to parse id and fetch the created resource
                return try {
                    val textBody: String = responseCall.body()
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createVehiculo; body=$textBody")
                    val created: Vehiculo = client.get("$baseUrl/vehiculos/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            println("[ApiClient] createVehiculo error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun updateVehiculoOdometro(vehiculoId: Int, request: VehiculoUpdateRequest): Result<Vehiculo> {
        return try {
            val call = client.put("$baseUrl/vehiculos/$vehiculoId") {
                setBody(request)
            }

            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            try {
                val response: Vehiculo = call.body()
                Result.success(response)
            } catch (_: Exception) {
                // Backend may return only a message -> fetch the updated entity
                val updated: Vehiculo = client.get("$baseUrl/vehiculos/$vehiculoId").body()
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateVehiculoTerminada(vehiculoId: Int, terminada: Boolean): Result<Vehiculo> {
        return try {
            val body = mapOf("terminada" to terminada)
            val call = client.put("$baseUrl/vehiculos/$vehiculoId/terminada") {
                setBody(body)
            }

            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            try {
                val response: Vehiculo = call.body()
                Result.success(response)
            } catch (_: Exception) {
                val updated: Vehiculo = client.get("$baseUrl/vehiculos/$vehiculoId").body()
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVehiculo(vehiculoId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/vehiculos/$vehiculoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // TiposVehiculos
    suspend fun getTiposVehiculos(): Result<List<TipoVehiculo>> {
        return try {
            val response: List<TipoVehiculo> = safeGetList("$baseUrl/tipo-vehiculos")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createTipoVehiculo(request: TipoVehiculoCreateRequest): Result<TipoVehiculo> {
        return try {
            val call = client.post("$baseUrl/tipo-vehiculos") { setBody(request) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            // Try to decode directly as TipoVehiculo
            try {
                val response: TipoVehiculo = call.body()
                return Result.success(response)
            } catch (_: Exception) {
                // If the backend returned only an { id } or { insertId } object (or a message), try to parse the raw body and fetch by id
                return try {
                    val textBody: String = call.body()
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createTipoVehiculo; body=$textBody")
                    val created: TipoVehiculo = client.get("$baseUrl/tipo-vehiculos/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTipoVehiculo(tipoId: Int, request: TipoVehiculoUpdateRequest): Result<TipoVehiculo> {
        return try {
            val call = client.put("$baseUrl/tipo-vehiculos/$tipoId") { setBody(request) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            try {
                val response: TipoVehiculo = call.body()
                Result.success(response)
            } catch (_: Exception) {
                // Backend returned only a message -> fetch the updated entity
                val updated: TipoVehiculo = client.get("$baseUrl/tipo-vehiculos/$tipoId").body()
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTipoVehiculo(tipoId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/tipo-vehiculos/$tipoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // LlantasVehiculos
    suspend fun getLlantasVehiculosByVehiculoId(vehiculoId: Int): Result<List<LlantaVehiculo>> {
        return try {
            val response: List<LlantaVehiculo> = safeGetList("$baseUrl/llantas-vehiculos/vehiculo/$vehiculoId")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createLlantaVehiculo(llantaVehiculo: LlantaVehiculoCreateRequest): Result<LlantaVehiculo> {
        return try {
            val response: LlantaVehiculo = client.post("$baseUrl/llantas-vehiculos") {
                setBody(llantaVehiculo)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLlantaVehiculo(llantaVehiculoId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/llantas-vehiculos/$llantaVehiculoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retireLlantaVehiculo(idLlantasVehiculos: Int, causa: String?, usuarioId: Int?, replacedBy: Int?): Result<Int> {
        return try {
            val call = client.post("$baseUrl/llantas-vehiculos/retirar") {
                setBody(mapToJsonObject(mapOf(
                    "idLlantasVehiculos" to idLlantasVehiculos,
                    "causa" to causa,
                    "usuarioId" to usuarioId,
                    "replacedBy" to replacedBy
                )))
            }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            } else {
                val bodyText: String = call.body()
                try {
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(bodyText).jsonObject
                    val terminadaId = json["terminadaId"]?.jsonPrimitive?.intOrNull
                    Result.success(terminadaId ?: -1)
                } catch (e: Exception) {
                    Result.success(-1)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Llantas
    suspend fun getAllLlantas(): Result<List<Llanta>> {
        return try {
            val response: List<Llanta> = safeGetList("$baseUrl/llantas")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createLlanta(request: LlantaCreateRequest): Result<Llanta> {
        return try {
            val call = client.post("$baseUrl/llantas") { setBody(request) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            try {
                val response: Llanta = call.body()
                Result.success(response)
            } catch (_: Exception) {
                // Try to read returned id and GET the created resource
                return try {
                    val textBody: String = call.body()
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createLlanta; body=$textBody")
                    val created: Llanta = client.get("$baseUrl/llantas/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLlanta(llantaId: Int, request: LlantaUpdateRequest): Result<Llanta> {
        return try {
            val call = client.put("$baseUrl/llantas/$llantaId") { setBody(request) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            try {
                val response: Llanta = call.body()
                Result.success(response)
            } catch (_: Exception) {
                // Backend returned only a message -> fetch the updated resource
                val updated: Llanta = client.get("$baseUrl/llantas/$llantaId").body()
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLlanta(llantaId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/llantas/$llantaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Delete a LlantasDesecho
    suspend fun deleteLlantaDesecho(id: Int): Result<Unit> {
        return try {
            val call = client.delete("$baseUrl/llantas-desecho/$id")
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // LlantasFlota - obtener llantas con marca/modelo/medida y si están asociadas a la flota
    suspend fun getLlantasByFlota(flotaId: Int): Result<List<Llanta>> {
        return try {
            val response: List<Llanta> = safeGetList("$baseUrl/llantas-flota/flota/$flotaId")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun associateLlantaToFlota(llantaId: Int, flotaId: Int): Result<Int> {
        return try {
            val response = client.post("$baseUrl/llantas-flota") { setBody(mapToJsonObject(mapOf("Llantas_idLlantas" to llantaId, "Flotas_idFlotas" to flotaId))) }
            // return created id -- parse safely to avoid polymorphic Map deserialization issues
            val text = try { response.bodyAsText() } catch (e: Exception) { "" }
            try {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
                val id = json["idLlantasFlota"]?.jsonPrimitive?.intOrNull
                    ?: json["id"]?.jsonPrimitive?.intOrNull
                    ?: json["insertId"]?.jsonPrimitive?.intOrNull
                Result.success(id ?: -1)
            } catch (e: Exception) {
                Result.success(-1)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disassociateLlantaFromFlota(idLlantasFlota: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/llantas-flota/$idLlantasFlota")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disassociateLlantaFromFlotaByLlantaAndFlota(llantaId: Int, flotaId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/llantas-flota/by-llanta-flota") {
                setBody(mapOf("Llantas_idLlantas" to llantaId, "Flotas_idFlotas" to flotaId))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun createMultipleLlantasVehiculo(requests: List<LlantaVehiculoCreateRequest>): Result<Unit> {
    return try {
        val response: String = client.post("$baseUrl/llantas-vehiculos/batch") {
            contentType(ContentType.Application.Json)
            setBody(requests)
        }.body()
        
        // Si necesitas procesar la respuesta, puedes hacerlo aquí
        println("Batch response: $response")
        Result.success(Unit)
        } catch (e: Exception) {
            println("Batch error: ${e.message}")
            Result.failure(e)
        }
    }

    // Sucursales
    suspend fun getSucursalesByFlotaId(flotaId: Int): Result<List<Sucursal>> {
        return try {
            val response: List<Sucursal> = safeGetList("$baseUrl/sucursales?Flotas_idFlotas=$flotaId")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // PruebaRendimiento
    suspend fun createPruebaRendimiento(request: PruebaRendimientoCreateRequest): Result<PruebaRendimiento> {
        return try {
            val call = client.post("$baseUrl/prueba-rendimiento") { setBody(request) }

            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            try {
                val response: PruebaRendimiento = call.body()
                Result.success(response)
            } catch (_: Exception) {
                // Backend returned only an id/object different from expected. Try to extract id and GET created resource.
                return try {
                    val textBody: String = call.body()
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["idPruebaRendimiento"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createPruebaRendimiento; body=$textBody")
                    val created: PruebaRendimiento = client.get("$baseUrl/prueba-rendimiento/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUltimoPruebaRendimientoByVehiculo(vehiculoId: Int): Result<PruebaRendimiento?> {
        return try {
            val url = "$baseUrl/prueba-rendimiento/vehiculo/$vehiculoId/ultimo"
            val call = client.get(url)
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }

            val raw: String = try { call.body() } catch (e: Exception) { "" }
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "null") {
                Result.success(null)
            } else {
                try {
                    val parsed: PruebaRendimiento = Json { ignoreUnknownKeys = true }.decodeFromString(raw)
                    Result.success(parsed)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obtener mapa de últimos odómetros por vehículo para una flota
    suspend fun getUltimosPruebaRendimientoByFlota(flotaId: Int): Result<Map<Int, Float>> {
        return try {
            // Obtener vehículos de la flota
            val vehiculosRes = getVehiculosByFlotaId(flotaId)
            if (vehiculosRes.isFailure) return Result.failure(vehiculosRes.exceptionOrNull() ?: Exception("Error fetching vehiculos"))
            val vehiculos = vehiculosRes.getOrNull() ?: emptyList()

            val map = mutableMapOf<Int, Float>()
            // Para cada vehículo, solicitar su último registro y extraer odómetro
            for (v in vehiculos) {
                try {
                    val ultimoRes = getUltimoPruebaRendimientoByVehiculo(v.idVehiculos)
                    if (ultimoRes.isSuccess) {
                        val prueba = ultimoRes.getOrNull()
                        if (prueba != null) {
                            map[v.idVehiculos] = prueba.PruebaRendimientoOdometro
                        }
                    }
                } catch (_: Exception) {
                    // ignore individual vehicle failures
                }
            }

            Result.success(map)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // LlantaRendimiento
    suspend fun createLlantaRendimiento(request: LlantaRendimientoCreateRequest): Result<LlantaRendimiento> {
        return try {
            val response: LlantaRendimiento = client.post("$baseUrl/llantas-rendimiento") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createMultipleLlantasRendimiento(requests: List<LlantaRendimientoCreateRequest>): Result<Unit> {
        return try {
            val url = "$baseUrl/llantas-rendimiento/batch"
            println("=== SIMPLE DEBUG ===")
            println("URL: $url")
            println("Requests count: ${requests.size}")

            // First, test basic connectivity (optional) and log status
            try {
                println("Testing basic connectivity...")
                val testResponse = client.get("$baseUrl/flotas")
                println("Basic connectivity test: ${testResponse.status}")
            } catch (e: Exception) {
                println("Connectivity test failed: ${e.message}")
            }

            // Now try the batch endpoint
            println("Attempting batch request...")
            // Compute a simple Host header value from baseUrl to ensure fronting proxies
            // (IIS/Plesk) receive a normalized Host header. This helps when proxies
            // reject requests due to unexpected Host formatting.
            val hostHeader = try {
                baseUrl.replace(Regex("^https?://"), "").substringBefore("/").substringBefore(":" )
            } catch (_: Exception) {
                null
            }

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                hostHeader?.let { header(HttpHeaders.Host, it) }
                setBody(requests)
            }

            val status = response.status
            val contentType = response.headers[HttpHeaders.ContentType]?.toString() ?: "(no content-type)"
            val bodyText = try {
                response.body<String>()
            } catch (e: Exception) {
                "(could not read response body: ${e.message})"
            }

            println("Batch response status: $status")
            println("Batch response Content-Type: $contentType")
            println("Batch response body (first 4000 chars): ${bodyText.take(4000)}")

            if (!status.isSuccess()) {
                // Return a failure containing a cleaned server message
                return Result.failure(Exception(extractMessageFromResponse(status, bodyText, response.headers)))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            println("ERROR creating multiple llantas rendimiento: ${e.message}")
            println("Error type: ${e::class.simpleName}")
            Result.failure(e)
        }
    }

    // Llantas Rendimiento - obtener todos los registros (usado por la UI para calcular últimos valores)
    suspend fun getAllLlantasRendimiento(): Result<List<LlantaRendimiento>> {
        return try {
            val response: List<LlantaRendimiento> = safeGetList("$baseUrl/llantas-rendimiento")
            println("[ApiClient] Fetched llantas-rendimiento count=${response.size}")
            // print a sample of ids for debugging
            val sample = response.take(10).map { "id=${it.idLlantasRendimiento}, llantaVehiculoId=${it.LlantasVehiculos_idLlantasVehiculos}" }
            println("[ApiClient] Sample entries: ${sample.joinToString(" | ")}")
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching all llantas rendimiento: ${e.message}")
            Result.failure(e)
        }
    }

    // Obtener rendimientos para una llanta instalada (por id de LlantasVehiculos)
    suspend fun getLlantasRendimientoByLlantaVehiculoId(llantaVehiculoId: Int): Result<List<LlantaRendimiento>> {
        return try {
            val url = "$baseUrl/llantas-rendimiento/debug/by-llanta/$llantaVehiculoId"
            println("[ApiClient] GET $url")
            val call = client.get(url)
            if (!call.status.isSuccess()) {
                // Fallback: obtener todos y filtrar en cliente
                println("[ApiClient] Fallback: debug endpoint not available, using GET /llantas-rendimiento")
                val all: List<LlantaRendimiento> = safeGetList("$baseUrl/llantas-rendimiento")
                val filtered = all.filter { it.LlantasVehiculos_idLlantasVehiculos == llantaVehiculoId }
                return Result.success(filtered)
            }
            val raw: String = try { call.body() } catch (_: Exception) { "[]" }
            println("[ApiClient] Raw response (llantas-rendimiento debug): $raw")
            val parsed: List<LlantaRendimiento> = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(raw)
            println("[ApiClient] Parsed ${parsed.size} llantas; sampleIds=${parsed.take(5).map { it.idLlantasRendimiento }}, sampleKm=${parsed.take(5).map { it.kmRecorrido }}")
            Result.success(parsed)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching llantas rendimiento by llantaVehiculoId=$llantaVehiculoId: ${e.message}")
            // Último fallback: obtener todos y filtrar
            return try {
                val all: List<LlantaRendimiento> = safeGetList("$baseUrl/llantas-rendimiento")
                val filtered = all.filter { it.LlantasVehiculos_idLlantasVehiculos == llantaVehiculoId }
                Result.success(filtered)
            } catch (e2: Exception) {
                Result.failure(e)
            }
        }
    }

    // Check if a llanta (by LlantasVehiculos id) is marked as terminada (reads last record's flag)
    suspend fun isLlantaTerminada(llantaVehiculoId: Int): Result<Boolean> {
        return try {
            val url = "$baseUrl/llantas-rendimiento/by-llanta/$llantaVehiculoId/terminada"
            val call = client.get(url)
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            val raw: String = try { call.body() } catch (e: Exception) { "" }
            val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
            val terminada = parsed["terminada"]?.jsonPrimitive?.booleanOrNull ?: false
            Result.success(terminada)
        } catch (e: Exception) {
            println("[ApiClient] Error checking terminada for llanta $llantaVehiculoId: ${e.message}")
            Result.failure(e)
        }
    }

    // Update the last rendimiento's PTerminada flag for a llanta
    suspend fun updateUltimaLlantaRendimiento(llantaVehiculoId: Int, pTerminada: Boolean): Result<Unit> {
        return try {
            val url = "$baseUrl/llantas-rendimiento/by-llanta/$llantaVehiculoId/terminada"
            val call = client.put(url) {
                setBody(mapToJsonObject(mapOf("pTerminada" to if (pTerminada) 1 else 0)))
            }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            println("[ApiClient] Error updating terminada for llanta $llantaVehiculoId: ${e.message}")
            Result.failure(e)
        }
    }

    // Obtener una prueba de rendimiento por id (para recuperar odómetro/fecha de la prueba)
    suspend fun getPruebaRendimientoById(id: Int): Result<PruebaRendimiento> {
        return try {
            val url = "$baseUrl/prueba-rendimiento/$id"
            println("[ApiClient] GET $url")
            val call = client.get(url)
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            val raw: String = try { call.body() } catch (e: Exception) { "" }
            val parsed: PruebaRendimiento = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(raw)
            Result.success(parsed)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching prueba rendimiento id=$id: ${e.message}")
            Result.failure(e)
        }
    }

    // Obtener los últimos registros de llantas rendimiento por llanta vehiculo
    suspend fun getUltimosLlantasRendimiento(): Result<List<LlantaRendimiento>> {
        return try {
            val response: List<LlantaRendimiento> = client.get("$baseUrl/llantas-rendimiento/ultimos").body()
            println("[ApiClient] Fetched ultimos llantas-rendimiento count=${response.size}")
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching ultimos llantas rendimiento: ${e.message}")
            Result.failure(e)
        }
    }

    // Request a map of last records by providing a list of llantaVehiculoIds
    suspend fun getUltimosLlantasRendimientoMap(ids: List<Int>): Result<Map<Int, LlantaRendimiento>> {
        val url = "$baseUrl/llantas-rendimiento/ultimos/map"
        // First, try to deserialize directly to Map<String, LlantaRendimiento>
        try {
            val rawMap: Map<String, LlantaRendimiento> = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(ids)
            }.body()

            val result = rawMap.mapNotNull { (k, v) ->
                val key = k.toIntOrNull()
                if (key == null) null else key to v
            }.toMap()

            println("[ApiClient] Fetched ultimos map (direct) size=${result.size}")
            return Result.success(result)
        } catch (e: Exception) {
            println("[ApiClient] Direct map deserialization failed: ${e.message}")
        }

        // Fallback: read raw string and parse manually (more verbose for debugging)
        return try {
            val raw: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(ids)
            }.body()
            println("[ApiClient] Raw ultimos map response: ${raw.take(2000)}")
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val elem = json.parseToJsonElement(raw).jsonObject
            val resultMap = elem.mapNotNull { (k, v) ->
                val key = k.toIntOrNull()
                if (key == null) return@mapNotNull null
                try {
                    val value = json.decodeFromJsonElement<LlantaRendimiento>(v)
                    key to value
                } catch (e: Exception) {
                    println("[ApiClient] Error decoding LlantaRendimiento for key=$k : ${e.message}")
                    null
                }
            }.toMap()

            println("[ApiClient] Fetched ultimos map (parsed) size=${resultMap.size}")
            Result.success(resultMap)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching ultimos map: ${e.message}")
            Result.failure(e)
        }
    }

    // Request a LIST of last records for given llantaVehiculoIds
    suspend fun getUltimosLlantasRendimientoList(ids: List<Int>): Result<List<LlantaRendimiento>> {
        return try {
            val url = "$baseUrl/llantas-rendimiento/ultimos/list"
            // Read raw response and attempt a lenient decode. If decoding fails, try to parse elements individually
            val call = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(ids)
            }

            val raw: String = try {
                call.body()
            } catch (e: Exception) {
                println("[ApiClient] Error reading ultimos list raw body: ${e.message}")
                return Result.failure(e)
            }

            println("[ApiClient] Raw ultimos list response (first 2000 chars): ${raw.take(2000)}")

            try {
                val parsed: List<LlantaRendimiento> = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(raw)
                println("[ApiClient] Fetched ultimos list size=${parsed.size}")
                return Result.success(parsed)
            } catch (e: Exception) {
                println("[ApiClient] Lenient decode failed for ultimos list: ${e.message}")
                // Try to parse as JSON array and decode elements one by one
                try {
                    val elem = Json.parseToJsonElement(raw)
                    if (elem is JsonArray) {
                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                        val list = mutableListOf<LlantaRendimiento>()
                        elem.forEach { je ->
                            try {
                                val item = json.decodeFromJsonElement<LlantaRendimiento>(je)
                                list.add(item)
                            } catch (ie: Exception) {
                                println("[ApiClient] Skipping item in ultimos list due to decode error: ${ie.message}")
                            }
                        }
                        println("[ApiClient] Fetched ultimos list partial size=${list.size}")
                        return Result.success(list)
                    }
                } catch (pe: Exception) {
                    println("[ApiClient] Error parsing ultimos list JSON: ${pe.message}")
                }

                // As a last resort, return an empty list instead of failing with a raw deserialization error
                println("[ApiClient] Returning empty ultimos list due to deserialization failures")
                return Result.success(emptyList())
            }
        } catch (e: Exception) {
            println("[ApiClient] Error fetching ultimos list: ${e.message}")
            Result.failure(e)
        }
    }

    // PruebasSemaforo
    suspend fun createPruebaSemaforo(request: PruebasSemaforoCreateRequest): Result<PruebasSemaforo> {
        return try {
            val call = client.post("$baseUrl/pruebas-semaforo") { setBody(request) }

            val status = call.status
            val textBody = try { call.bodyAsText() } catch (e: Exception) { "" }

            if (!status.isSuccess()) {
                val msg = try { kotlinx.serialization.json.Json.parseToJsonElement(textBody).jsonObject["message"]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
                return Result.failure(Exception(msg ?: extractMessageFromResponse(status, textBody, call.headers)))
            }

            // Try to decode the successful response body
            try {
                val parsed: PruebasSemaforo = Json { ignoreUnknownKeys = true }.decodeFromString(textBody)
                return Result.success(parsed)
            } catch (_: Exception) {
                // If decoding fails, try to extract an id and GET the created resource
                return try {
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["idPruebasSemaforo"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createPruebaSemaforo; body=$textBody")
                    val created: PruebasSemaforo = client.get("$baseUrl/pruebas-semaforo/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPruebasSemaforoByFlotaId(flotaId: Int): Result<List<PruebasSemaforo>> {
        return try {
            val response: List<PruebasSemaforo> = safeGetList("$baseUrl/pruebas-semaforo/flota/$flotaId")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // VehiculoSemaforo
    suspend fun createVehiculoSemaforo(request: VehiculoSemaforoCreateRequest): Result<VehiculoSemaforo> {
        return try {
            val call = client.post("$baseUrl/vehiculo-semaforo") { setBody(request) }
            val status = call.status
            val textBody = try { call.bodyAsText() } catch (e: Exception) { "" }

            if (!status.isSuccess()) {
                val msg = try { kotlinx.serialization.json.Json.parseToJsonElement(textBody).jsonObject["message"]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
                return Result.failure(Exception(msg ?: extractMessageFromResponse(status, textBody, call.headers)))
            }

            try {
                val parsed: VehiculoSemaforo = Json { ignoreUnknownKeys = true }.decodeFromString(textBody)
                return Result.success(parsed)
            } catch (_: Exception) {
                return try {
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["idVehiculoSemaforo"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createVehiculoSemaforo; body=$textBody")
                    val created: VehiculoSemaforo = client.get("$baseUrl/vehiculo-semaforo/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVehiculoSemaforoById(id: Int): Result<VehiculoSemaforo> {
        return try {
            val response: VehiculoSemaforo = client.get("$baseUrl/vehiculo-semaforo/$id").body()
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching vehiculo semaforo by id $id: ${e.message}")
            Result.failure(e)
        }
    }

    // Delete a Vehiculo Semaforo
    suspend fun deleteVehiculoSemaforo(id: Int): Result<Unit> {
        return try {
            val call = client.delete("$baseUrl/vehiculo-semaforo/$id")
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVehiculosSemaforoByPruebaId(pruebaId: Int): Result<List<VehiculoSemaforo>> {
        return try {
            // Backend may not expose a prueba-specific endpoint in all deployments; fetch all and filter client-side
            val all: List<VehiculoSemaforo> = safeGetList("$baseUrl/vehiculo-semaforo")
            val filtered = all.filter { it.PruebasSemaforo_idPruebasSemaforo == pruebaId }
            Result.success(filtered)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching vehiculos semaforo for prueba $pruebaId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getLlantasSemaforoByVehiculoId(vehiculoId: Int): Result<List<LlantasSemaforo>> {
        return try {
            val all: List<LlantasSemaforo> = safeGetList("$baseUrl/llantas-semaforo")
            val filtered = all.filter { it.VehiculoSemaforo_idVehiculoSemaforo == vehiculoId }
            Result.success(filtered)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching llantas semaforo for vehiculo $vehiculoId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateVehiculoSemaforo(id: Int, request: VehiculoSemaforoCreateRequest): Result<Unit> {
        return try {
            val call = client.put("$baseUrl/vehiculo-semaforo/$id") { setBody(request) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLlantaSemaforo(id: Int, request: LlantasSemaforoCreateRequest): Result<Unit> {
        return try {
            // Normalize defaults client-side to avoid sending null/blank Piso/Observación
            val normalizedPiso = request.LlantasSemaforoPiso?.ifBlank { "Original" } ?: "Original"
            val normalizedObserv = request.LlantasSemaforoObserv?.ifBlank { "LLANTA OK" } ?: "LLANTA OK"

            // Build a JsonObject explicitly so null photo fields are sent as explicit nulls
            val jsonObj = kotlinx.serialization.json.buildJsonObject {
                put("VehiculoSemaforo_idVehiculoSemaforo", kotlinx.serialization.json.JsonPrimitive(request.VehiculoSemaforo_idVehiculoSemaforo))
                put("Llantas_idLlantas", kotlinx.serialization.json.JsonPrimitive(request.Llantas_idLlantas))
                // Always include a numeric presion in the payload. If the request is null, send 0
                put("LlantasSemaforoPresion", kotlinx.serialization.json.JsonPrimitive(request.LlantasSemaforoPresion ?: 0))
                put("LlantasSemaforoColor", kotlinx.serialization.json.JsonPrimitive(request.LlantasSemaforoColor))
                // Treat null as false (Normal) to avoid clearing the flag unintentionally
                put("LlantasSemaforoCondPel", kotlinx.serialization.json.JsonPrimitive(request.LlantasSemaforoCondPel ?: false))
                    // Include vigía flag (0/1) when present on the request
                    put("LlantasSemaforoVigia", kotlinx.serialization.json.JsonPrimitive(request.LlantasSemaforoVigia))
                put("LlantasSemaforoObserv", kotlinx.serialization.json.JsonPrimitive(normalizedObserv))
                put("LlantasSemaforoPiso", kotlinx.serialization.json.JsonPrimitive(normalizedPiso))
                request.LlantasSemaforoComent?.let { put("LlantasSemaforoComent", kotlinx.serialization.json.JsonPrimitive(it)) } ?: put("LlantasSemaforoComent", kotlinx.serialization.json.JsonNull)
                // Photos: include explicit null when absent so server can perform deletion
                if (request.LlantasSemaforoFoto1 == null) put("LlantasSemaforoFoto1", kotlinx.serialization.json.JsonNull) else put("LlantasSemaforoFoto1", kotlinx.serialization.json.JsonPrimitive(request.LlantasSemaforoFoto1))
                if (request.LlantasSemaforoFoto2 == null) put("LlantasSemaforoFoto2", kotlinx.serialization.json.JsonNull) else put("LlantasSemaforoFoto2", kotlinx.serialization.json.JsonPrimitive(request.LlantasSemaforoFoto2))
            }

            // Debug: log outgoing payload to diagnose nulls for Piso/Observación
            try {
                val dbg = jsonObj.toString()
                val pisoVal = normalizedPiso
                val observVal = normalizedObserv
                println("[ApiClient] PUT /llantas-semaforo/$id payload (first 500 chars): ${dbg.take(500)}")
                println("[ApiClient]   -> Piso='$pisoVal' Observacion='$observVal'")
            } catch (_: Exception) { /* ignore logging errors */ }

            val call = client.put("$baseUrl/llantas-semaforo/$id") { setBody(jsonObj) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // LlantasSemaforo
    suspend fun createLlantaSemaforo(request: LlantasSemaforoCreateRequest): Result<LlantasSemaforo> {
        return try {
            val call = client.post("$baseUrl/llantas-semaforo") { setBody(request) }
            val status = call.status
            val textBody = try { call.bodyAsText() } catch (e: Exception) { "" }

            if (!status.isSuccess()) {
                val msg = try { kotlinx.serialization.json.Json.parseToJsonElement(textBody).jsonObject["message"]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
                return Result.failure(Exception(msg ?: extractMessageFromResponse(status, textBody, call.headers)))
            }

            try {
                val parsed: LlantasSemaforo = Json { ignoreUnknownKeys = true }.decodeFromString(textBody)
                return Result.success(parsed)
            } catch (_: Exception) {
                return try {
                    val jsonElem = kotlinx.serialization.json.Json.parseToJsonElement(textBody)
                    val id = jsonElem.jsonObject["idLlantasSemaforo"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["id"]?.jsonPrimitive?.intOrNull
                        ?: jsonElem.jsonObject["insertId"]?.jsonPrimitive?.intOrNull
                        ?: -1
                    if (id <= 0) throw Exception("No id returned from createLlantaSemaforo; body=$textBody")
                    val created: LlantasSemaforo = client.get("$baseUrl/llantas-semaforo/$id").body()
                    Result.success(created)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // PruebasDesecho
    suspend fun getAllPruebasDesecho(flotasId: Int? = null): Result<List<PruebasDesecho>> {
        return try {
            val url = if (flotasId != null) "$baseUrl/pruebas-desecho?Flotas_idFlotas=$flotasId" else "$baseUrl/pruebas-desecho"
            val response: List<PruebasDesecho> = safeGetList(url)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPruebaDesecho(request: PruebasDesechoCreateRequest): Result<PruebasDesecho> {
        return try {
            val response: PruebasDesecho = client.post("$baseUrl/pruebas-desecho") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // LlantasDesecho
    suspend fun getLlantasDesechoByPruebaId(pruebaId: Int): Result<List<LlantasDesecho>> {
        return try {
            // The backend does not currently expose a /prueba/:id route for llantas-desecho in some deployments.
            // Fetch all llantas-desecho and filter client-side by PruebasDesecho_idPruebasDesecho to avoid 404s.
            val all: List<LlantasDesecho> = safeGetList("$baseUrl/llantas-desecho")
            val filtered = all.filter { it.PruebasDesecho_idPruebasDesecho == pruebaId }
            Result.success(filtered)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching llantas desecho for prueba $pruebaId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createLlantaDesecho(request: LlantasDesechoCreateRequest): Result<LlantasDesecho> {
        return try {
            val jsonObj = kotlinx.serialization.json.buildJsonObject {
                request.LlantasDesechocol?.let { if (it.isNotBlank()) put("LlantasDesechocol", kotlinx.serialization.json.JsonPrimitive(it)) }
                put("PruebasDesecho_idPruebasDesecho", kotlinx.serialization.json.JsonPrimitive(request.PruebasDesecho_idPruebasDesecho))
                put("Llantas_idLlantas", kotlinx.serialization.json.JsonPrimitive(request.Llantas_idLlantas))
                request.LlantasDesechoNoLlanta?.let { if (it.isNotBlank()) put("LlantasDesechoNoLlanta", kotlinx.serialization.json.JsonPrimitive(it)) }
                put("LlantasDesechoPiso", kotlinx.serialization.json.JsonPrimitive(request.LlantasDesechoPiso))
                put("LlantasDesechoCausaDes", kotlinx.serialization.json.JsonPrimitive(request.LlantasDesechoCausaDes.trim()))
                request.LlantasDesechoUbi?.let { if (it.isNotBlank()) put("LlantasDesechoUbi", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.Latitud?.let { put("Latitud", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.Longitud?.let { put("Longitud", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.LlantasDesechoRemanente?.let { put("LlantasDesechoRemanente", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.Usuarios_idUsuarios?.let { put("Usuarios_idUsuarios", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.LlantasDesechoFecha?.let { if (it.isNotBlank()) put("LlantasDesechoFecha", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.LlantasDesechoComentarios?.let { if (it.isNotBlank()) put("LlantasDesechoComentarios", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.LlantasDesechoFoto1?.let { if (it.isNotBlank()) put("LlantasDesechoFoto1", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.LlantasDesechoFoto2?.let { if (it.isNotBlank()) put("LlantasDesechoFoto2", kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            println("[ApiClient] POST $baseUrl/llantas-desecho -> body=${jsonObj}")
            val httpResponse = client.post("$baseUrl/llantas-desecho") {
                setBody(jsonObj)
            }
            if (httpResponse.status.value >= 400) {
                println("[ApiClient] POST error: status=${httpResponse.status}, body=${httpResponse.bodyAsText()}")
                return Result.failure(Exception("HTTP ${httpResponse.status}: ${httpResponse.bodyAsText()}"))
            }
            val response: LlantasDesecho = httpResponse.body()
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] POST exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateLlantaDesecho(id: Int, request: LlantasDesechoUpdateRequest): Result<Unit> {
        return try {
            // Build JsonObject: only include non-null/non-empty keys for text/numeric fields.
            // Keep explicit JsonNull only for photo fields so server can delete images when requested.
            val jsonObj = kotlinx.serialization.json.buildJsonObject {
                // Optional textual color
                request.LlantasDesechocol?.let { if (it.isNotBlank()) put("LlantasDesechocol", kotlinx.serialization.json.JsonPrimitive(it)) }

                // Required references/strings (always include)
                put("PruebasDesecho_idPruebasDesecho", kotlinx.serialization.json.JsonPrimitive(request.PruebasDesecho_idPruebasDesecho))
                put("Llantas_idLlantas", kotlinx.serialization.json.JsonPrimitive(request.Llantas_idLlantas))

                // Optional numeric
                request.LlantasDesechoNoLlanta?.let { put("LlantasDesechoNoLlanta", kotlinx.serialization.json.JsonPrimitive(it)) }

                put("LlantasDesechoPiso", kotlinx.serialization.json.JsonPrimitive(request.LlantasDesechoPiso))
                put("LlantasDesechoCausaDes", kotlinx.serialization.json.JsonPrimitive(request.LlantasDesechoCausaDes.trim()))

                // Ubicación (zona) and GPS coordinates
                request.LlantasDesechoUbi?.let { if (it.isNotBlank()) put("LlantasDesechoUbi", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.Latitud?.let { put("Latitud", kotlinx.serialization.json.JsonPrimitive(it)) }
                request.Longitud?.let { put("Longitud", kotlinx.serialization.json.JsonPrimitive(it)) }

                request.LlantasDesechoRemanente?.let { put("LlantasDesechoRemanente", kotlinx.serialization.json.JsonPrimitive(it)) }

                // Optional comments: include only if non-null (the form requires non-blank to enable save)
                request.LlantasDesechoComentarios?.let { put("LlantasDesechoComentarios", kotlinx.serialization.json.JsonPrimitive(it)) }

                // Photos: include explicit null when absent so server can perform deletion
                if (request.LlantasDesechoFoto1 == null) put("LlantasDesechoFoto1", kotlinx.serialization.json.JsonNull) else put("LlantasDesechoFoto1", kotlinx.serialization.json.JsonPrimitive(request.LlantasDesechoFoto1))
                if (request.LlantasDesechoFoto2 == null) put("LlantasDesechoFoto2", kotlinx.serialization.json.JsonNull) else put("LlantasDesechoFoto2", kotlinx.serialization.json.JsonPrimitive(request.LlantasDesechoFoto2))
            }

            // Debug: log outgoing PUT body so we can verify the exact payload sent from the client
            try {
                println("[ApiClient] PUT $baseUrl/llantas-desecho/$id -> body=${jsonObj}")
            } catch (_: Exception) {}

            val call = client.put("$baseUrl/llantas-desecho/$id") { setBody(jsonObj) }
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /*suspend fun createMultipleLlantasVehiculo(requests: List<LlantaVehiculoCreateRequest>): Result<Unit> {
    return try {
        client.post("$baseUrl/llantasvehiculos/batch") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(requests)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    }*/
    //inspecciones
    // PruebasInspeccion
    suspend fun getPruebasInspeccionByFlotaId(flotaId: Int): Result<List<PruebaInspeccion>> {
        return try {
            val response: List<PruebaInspeccion> = safeGetList("$baseUrl/pruebas-inspeccion/flota/$flotaId")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPruebaInspeccion(request: PruebaInspeccionCreateRequest): Result<PruebaInspeccion> {
        return try {
            val url = "$baseUrl/pruebas-inspeccion"
            println("[ApiClient] POST -> $url")
            println("[ApiClient] Body: $request")
            val response: PruebaInspeccion = client.post(url) {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createVehiculoInspeccion(request: VehiculoInspeccionCreateRequest): Result<VehiculoInspeccion> {
        return try {
            val response: VehiculoInspeccion = client.post("$baseUrl/vehiculos-inspeccion") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVehiculosInspeccionByPruebaId(pruebaId: Int): Result<List<VehiculoInspeccion>> {
        return try {
            val url = "$baseUrl/vehiculos-inspeccion/prueba/$pruebaId"
            println("[ApiClient] GET $url")
            val response: List<VehiculoInspeccion> = client.get(url).body()
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching vehiculos inspeccion for prueba $pruebaId: ${e.message}")
            Result.failure(e)
        }
    }

    // Obtener un vehículo de inspección por id
    suspend fun getVehiculoInspeccionById(id: Int): Result<VehiculoInspeccion> {
        return try {
            val url = "$baseUrl/vehiculos-inspeccion/$id"
            println("[ApiClient] GET $url")
            val response: VehiculoInspeccion = client.get(url).body()
            Result.success(response)
        } catch (e: Exception) {
            println("[ApiClient] Error fetching vehiculo inspeccion id=$id: ${e.message}")
            Result.failure(e)
        }
    }

    // Delete a Vehiculo Inspeccion
    suspend fun deleteVehiculoInspeccion(id: Int): Result<Unit> {
        return try {
            val call = client.delete("$baseUrl/vehiculos-inspeccion/$id")
            if (!call.status.isSuccess()) {
                val text = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, text, call.headers)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obtener llantas de inspección por vehiculoInspeccion id
    suspend fun getLlantasInspeccionByVehiculoId(vehiculoId: Int): Result<List<LlantaInspeccion>> {
        return try {
            val url = "$baseUrl/llantas-inspeccion/vehiculo/$vehiculoId"
            println("[ApiClient] GET $url")
            val call = client.get(url)
            val status = call.status
            val contentType = call.headers[HttpHeaders.ContentType] ?: "(no content-type)"
            val raw = try { call.body<String>() } catch (e: Exception) { "(could not read body: ${e.message})" }
            println("[ApiClient] Response status=$status content-type=$contentType")
            println("[ApiClient] raw llantas-inspeccion response (first 2000 chars): ${raw.take(2000)}")

            if (!status.isSuccess()) {
                // try extract message
                val msg = try { Json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
                return Result.failure(Exception(msg ?: extractMessageFromResponse(status, raw, call.headers)))
            }

            // Try to decode. Backend may return null for some string fields (DOT)
            // which would make kotlinx.serialization fail for non-nullable properties. Normalize
            // those nulls to empty strings in the raw JSON before decoding so we don't skip
            // valid items.
            try {
                val normalized = raw
                    .replace(Regex("\"LlantasInspeccionDOT\"\\s*:\\s*null"), "\"LlantasInspeccionDOT\":\"\"")

                val parsed: List<LlantaInspeccion> = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(normalized)
                println("[ApiClient] Parsed llantas-inspeccion count=${parsed.size}")
                Result.success(parsed)
            } catch (e: Exception) {
                println("[ApiClient] Error decoding llantas-inspeccion JSON: ${e.message}")
                // As a fallback, attempt manual parse of array and decode elements (after normalizing)
                try {
                    val normalized2 = raw
                        .replace(Regex("\"LlantasInspeccionDOT\"\\s*:\\s*null"), "\"LlantasInspeccionDOT\":\"\"")
                    val elem = Json.parseToJsonElement(normalized2)
                    if (elem is JsonArray) {
                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                        val list = mutableListOf<LlantaInspeccion>()
                        elem.forEach { je ->
                            try {
                                // Some database drivers return Buffer objects like { "type":"Buffer","data":[...]} for blob columns.
                                // Convert those into base64 strings here so the deserializer can decode into String properties.
                                val transformed = if (je is JsonObject) {
                                    val m = je.toMutableMap()
                                    fun convertBufferField(fieldName: String) {
                                        val v = je[fieldName]
                                        if (v is JsonObject) {
                                            val dataElem = v["data"]
                                            if (dataElem is JsonArray) {
                                                try {
                                                    val bytes = ByteArray(dataElem.size) { i ->
                                                        dataElem[i].jsonPrimitive.int.toByte()
                                                    }
                                                    val base64 = encodeToBase64(bytes)
                                                    m[fieldName] = JsonPrimitive(base64)
                                                } catch (_: Exception) {
                                                    // ignore conversion errors
                                                }
                                            }
                                        }
                                    }

                                    convertBufferField("LlantasInspeccionFoto")
                                    convertBufferField("LlantasInspeccionFoto2")
                                    JsonObject(m)
                                } else je

                                val item = json.decodeFromJsonElement<LlantaInspeccion>(transformed)
                                list.add(item)
                            } catch (ie: Exception) {
                                // skip individual invalid items
                            }
                        }
                        println("[ApiClient] Parsed llantas-inspeccion partial count=${list.size}")
                        return Result.success(list)
                    }
                } catch (_: Exception) {
                    // fallthrough
                }

                Result.failure(Exception("Error al parsear llantas-inspeccion: ${e.message}"))
            }
        } catch (e: Exception) {
            println("[ApiClient] Error fetching llantas inspeccion for vehiculo $vehiculoId: ${e.message}")
            Result.failure(e)
        }
    }

    // Actualizar vehículo de inspección
    // Helper: convert a Map<String, Any?> into a JsonObject for safe serialization
    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (k, v) ->
                when (v) {
                    null -> put(k, JsonNull)
                    is String -> put(k, JsonPrimitive(v))
                    is Int -> put(k, JsonPrimitive(v))
                    is Long -> put(k, JsonPrimitive(v))
                    is Float -> put(k, JsonPrimitive(v))
                    is Double -> put(k, JsonPrimitive(v))
                    is Boolean -> put(k, JsonPrimitive(v))
                    is Number -> put(k, JsonPrimitive(v.toDouble()))
                    is Map<*, *> -> {
                        // Unsafe cast but useful for nested objects with String keys
                        @Suppress("UNCHECKED_CAST")
                        put(k, mapToJsonObject(v as Map<String, Any?>))
                    }
                    is List<*> -> {
                        val arr = buildJsonArray {
                            v.forEach { item ->
                                when (item) {
                                    null -> add(JsonNull)
                                    is String -> add(JsonPrimitive(item))
                                    is Int -> add(JsonPrimitive(item))
                                    is Long -> add(JsonPrimitive(item))
                                    is Float -> add(JsonPrimitive(item))
                                    is Double -> add(JsonPrimitive(item))
                                    is Boolean -> add(JsonPrimitive(item))
                                    is Number -> add(JsonPrimitive(item.toDouble()))
                                    is Map<*, *> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        add(mapToJsonObject(item as Map<String, Any?>))
                                    }
                                    else -> add(JsonPrimitive(item.toString()))
                                }
                            }
                        }
                        put(k, arr)
                    }
                    else -> put(k, JsonPrimitive(v.toString()))
                }
            }
        }
    }

    // Simple base64 encoder (pure Kotlin) used to convert byte arrays returned by some DB drivers
    // which expose blob columns as "Buffer" objects. Kept local to avoid cross-file dependencies.
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

    suspend fun updateVehiculoInspeccion(id: Int, body: Map<String, Any?>): Result<VehiculoInspeccion> {
        return try {
            val url = "$baseUrl/vehiculos-inspeccion/$id"
            println("[ApiClient] PUT $url")
            val jsonObj = mapToJsonObject(body)
            try { println("[ApiClient] PUT body: ${jsonObj.toString().take(4000)}") } catch (_: Exception) {}
            val call = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(jsonObj)
            }
            if (!call.status.isSuccess()) {
                val txt = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, txt, call.headers)))
            }
            val parsed: VehiculoInspeccion = try { call.body() } catch (_: Exception) {
                // Fallback: fetch updated
                client.get(url).body()
            }
            Result.success(parsed)
        } catch (e: Exception) {
            println("[ApiClient] Error updating vehiculo inspeccion id=$id: ${e.message}")
            Result.failure(e)
        }
    }

    // Actualizar una llanta de inspección
    suspend fun updateLlantaInspeccion(id: Int, body: Map<String, Any?>): Result<LlantaInspeccion> {
        return try {
            val url = "$baseUrl/llantas-inspeccion/$id"
            println("[ApiClient] PUT $url")
            val jsonObj = mapToJsonObject(body)
            try { println("[ApiClient] PUT body: ${jsonObj.toString().take(4000)}") } catch (_: Exception) {}
            val call = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(jsonObj)
            }
            if (!call.status.isSuccess()) {
                val txt = try { call.body<String>() } catch (_: Exception) { "(no body)" }
                return Result.failure(Exception(extractMessageFromResponse(call.status, txt, call.headers)))
            }
            val parsed: LlantaInspeccion = try { call.body() } catch (_: Exception) {
                client.get(url).body()
            }
            Result.success(parsed)
        } catch (e: Exception) {
            println("[ApiClient] Error updating llanta inspeccion id=$id: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createMultipleLlantasInspeccion(requests: List<LlantaInspeccionCreateRequest>): Result<List<LlantaInspeccion>> {
        return try {
            val call = client.post("$baseUrl/llantas-inspeccion/batch") {
                contentType(ContentType.Application.Json)
                setBody(requests)
            }

            val status = call.status
            val raw: String = try { call.body() } catch (e: Exception) { "" }

            if (!status.isSuccess()) {
                val msg = try { Json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
                return Result.failure(Exception(msg ?: extractMessageFromResponse(status, raw, call.headers)))
            }

            // Handle two possible backend shapes:
            // 1) an array of created LlantaInspeccion objects
            // 2) an object like { message: "..", results: [{ id: 1 }, ...] }
            try {
                val trimmed = raw.trimStart()
                if (trimmed.startsWith("[")) {
                    val parsed: List<LlantaInspeccion> = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(raw)
                    return Result.success(parsed)
                } else if (trimmed.startsWith("{")) {
                    val elem = Json.parseToJsonElement(raw).jsonObject
                    val resultsElem = elem["results"]
                    if (resultsElem != null && resultsElem is JsonArray) {
                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                        val list = mutableListOf<LlantaInspeccion>()
                        resultsElem.forEach { je ->
                            try {
                                val item = json.decodeFromJsonElement<LlantaInspeccion>(je)
                                list.add(item)
                            } catch (_: Exception) {
                                // skip if cannot decode
                            }
                        }
                        return Result.success(list)
                    }

                    // If no results array, but the server returned success (e.g., only a message), treat as success with empty list
                    return Result.success(emptyList())
                }
            } catch (e: Exception) {
                println("[ApiClient] Warning: could not parse batch llantas response: ${e.message}")
            }

            // Fallback: return empty list on success
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Parametros
    suspend fun getParametrosByFlotaId(flotaId: Int): Result<List<Parametro>> {
        return try {
            println("🔍 Fetching parametros for flota: $flotaId")
            println("🌐 URL: $baseUrl/parametros/flota/$flotaId")
            val response: List<Parametro> = client.get("$baseUrl/parametros/flota/$flotaId").body()
            println("✅ Parametros fetched successfully: ${response.size} items")
            Result.success(response)
        } catch (e: Exception) {
            println("❌ Error fetching parametros: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getParametroById(parametroId: Int): Result<Parametro> {
        return try {
            val response: Parametro = client.get("$baseUrl/parametros/$parametroId").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchLlantasByMedida(medida: String): Result<List<Llanta>> {
        return try {
            println("🔍 Searching llantas by medida: $medida")
            println("🌐 URL: $baseUrl/llantas/search/medida?q=$medida")
            val response: List<Llanta> = client.get("$baseUrl/llantas/search/medida") {
                parameter("q", medida)
            }.body()
            println("✅ Found llantas: ${response.size}")
            Result.success(response)
        } catch (e: Exception) {
            println("❌ Error searching llantas: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun createParametro(request: ParametroCreateRequest): Result<Parametro> {
        return try {
            val response: Parametro = client.post("$baseUrl/parametros") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateParametro(parametroId: Int, request: ParametroUpdateRequest): Result<Parametro> {
        return try {
            val response: Parametro = client.put("$baseUrl/parametros/$parametroId") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteParametro(parametroId: Int): Result<Unit> {
        return try {
            client.delete("$baseUrl/parametros/$parametroId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}