package com.megatransportes.yokoh.data.repository

import com.megatransportes.yokoh.data.api.ApiClient
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.session.SessionManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

class YokohamaRepository(
    private val apiClient: ApiClient,
    private val sessionManager: SessionManager
) {
    // Simple in-memory cache for llantasInspeccion to avoid repeated network calls
    private val llantasInspeccionCache: MutableMap<Int, List<LlantaInspeccion>> = mutableMapOf()
    private val llantasInspeccionMutex = Mutex()
    private val backgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start a background fetch for llantas inspeccion; results are cached and survive
     * composable cancellation so subsequent calls to [getLlantasInspeccionByVehiculoId]
     * can return cached data without being cancelled by the UI scope.
     */
    fun prefetchLlantasInspeccionByVehiculoId(vehiculoId: Int) {
        backgroundScope.launch {
            // If already cached, nothing to do
            llantasInspeccionMutex.withLock {
                if (llantasInspeccionCache.containsKey(vehiculoId)) return@launch
            }

            val res = apiClient.getLlantasInspeccionByVehiculoId(vehiculoId)
            res.onSuccess { list ->
                llantasInspeccionMutex.withLock {
                    llantasInspeccionCache[vehiculoId] = list
                }
            }
        }
    }
    private val _currentUser = MutableStateFlow<Usuario?>(sessionManager.getCurrentUser())
    val currentUser: StateFlow<Usuario?> = _currentUser.asStateFlow()
    // Map of vehiculoId -> latest odometer known to the client. Updated after successful writes so UI can merge changes.
    private val _vehiculoOdometerUpdates = kotlinx.coroutines.flow.MutableStateFlow<Map<Int, Float>>(emptyMap())
    val vehiculoOdometerUpdates: StateFlow<Map<Int, Float>> = _vehiculoOdometerUpdates.asStateFlow()

    // Allow publishing an odometer update locally so UI can react immediately (optimistic update)
    fun publishVehiculoOdometerLocal(vehiculoId: Int, odometro: Float) {
        val current = _vehiculoOdometerUpdates.value.toMutableMap()
        current[vehiculoId] = odometro
        _vehiculoOdometerUpdates.value = current.toMap()
    }

    // Publish multiple odometer updates in one state update to reduce recompositions.
    fun publishVehiculoOdometersLocal(odometerByVehiculoId: Map<Int, Float>) {
        if (odometerByVehiculoId.isEmpty()) return
        val current = _vehiculoOdometerUpdates.value.toMutableMap()
        current.putAll(odometerByVehiculoId)
        _vehiculoOdometerUpdates.value = current.toMap()
    }
    
    // Authentication
    suspend fun login(email: String, password: String): Result<Usuario> {
        return apiClient.login(email, password).also { result ->
            result.onSuccess { user ->
                sessionManager.saveUserSession(user)
                _currentUser.value = user
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _currentUser.value = null
    }

    fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    // Flotas
    suspend fun getAllFlotas(): Result<List<Flota>> {
        return apiClient.getAllFlotas()
    }

    suspend fun getFlotasForCurrentUser(): Result<List<Flota>> {
        val currentUserId = sessionManager.getCurrentUser()?.idUsuarios 
            ?: return Result.failure(Exception("No user logged in"))
        
        return apiClient.getFlotasByUsuarioId(currentUserId)
    }

    suspend fun createFlota(flota: FlotaCreateRequest): Result<Flota> {
        return apiClient.createFlota(flota)
    }

    // Usuarios
    suspend fun getAllUsuarios(): Result<List<Usuario>> {
        return apiClient.getAllUsuarios()
    }

    suspend fun getDistribuidores(): Result<List<Distribuidor>> {
        return apiClient.getDistribuidores()
    }

    suspend fun createUsuario(usuario: UsuarioCreateRequest): Result<Usuario> {
        return apiClient.createUsuario(usuario)
    }

    suspend fun updateUsuario(usuarioId: Int, request: UsuarioUpdateRequest): Result<Usuario> {
        val result = apiClient.updateUsuario(usuarioId, request)
        result.onSuccess { updatedUser ->
            val currentId = sessionManager.getCurrentUser()?.idUsuarios
            if (currentId != null && currentId == updatedUser.idUsuarios) {
                // Persist updated session and notify observers so UI reacts to permission changes
                sessionManager.saveUserSession(updatedUser)
                _currentUser.value = updatedUser
            }
        }
        return result
    }

    suspend fun getFlotasByUsuarioId(usuarioId: Int): Result<List<Flota>> {
        return apiClient.getFlotasByUsuarioId(usuarioId)
    }

    // Vehiculos
    suspend fun getVehiculosByFlotaId(flotaId: Int): Result<List<Vehiculo>> {
        return apiClient.getVehiculosByFlotaId(flotaId)
    }
    
    suspend fun createVehiculo(vehiculo: VehiculoCreateRequest): Result<Vehiculo> {
        return apiClient.createVehiculo(vehiculo)
    }
    
    suspend fun updateVehiculoOdometro(vehiculoId: Int, request: VehiculoUpdateRequest): Result<Vehiculo> {
        val result = apiClient.updateVehiculoOdometro(vehiculoId, request)
        result.onSuccess { vehiculo ->
            // Publish odometer update so any UI observing this repository can merge the new value.
            val current = _vehiculoOdometerUpdates.value.toMutableMap()
            current[vehiculoId] = vehiculo.VehiculosOdometro
            _vehiculoOdometerUpdates.value = current.toMap()
        }
        return result
    }

    suspend fun updateVehiculoTerminada(vehiculoId: Int, terminada: Boolean): Result<Vehiculo> {
        return try {
            val res = apiClient.updateVehiculoTerminada(vehiculoId, terminada)
            // Optionally we could update local caches here if needed
            res
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVehiculo(vehiculoId: Int): Result<Unit> {
        return try {
            apiClient.deleteVehiculo(vehiculoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // TiposVehiculos
    suspend fun getTiposVehiculos(): Result<List<TipoVehiculo>> {
        return apiClient.getTiposVehiculos()
    }
    
    suspend fun createTipoVehiculo(request: TipoVehiculoCreateRequest): Result<TipoVehiculo> {
        return apiClient.createTipoVehiculo(request)
    }

    suspend fun updateTipoVehiculo(tipoId: Int, request: TipoVehiculoUpdateRequest): Result<TipoVehiculo> {
        return apiClient.updateTipoVehiculo(tipoId, request)
    }

    suspend fun deleteTipoVehiculo(tipoId: Int): Result<Unit> {
        return apiClient.deleteTipoVehiculo(tipoId)
    }

    // LlantasVehiculos
    suspend fun getLlantasVehiculosByVehiculoId(vehiculoId: Int): Result<List<LlantaVehiculo>> {
        return apiClient.getLlantasVehiculosByVehiculoId(vehiculoId)
    }

    suspend fun createLlantaVehiculo(llantaVehiculo: LlantaVehiculoCreateRequest): Result<LlantaVehiculo> {
        return apiClient.createLlantaVehiculo(llantaVehiculo)
    }

    suspend fun deleteLlantaVehiculo(llantaVehiculoId: Int): Result<Unit> {
        return try {
            apiClient.deleteLlantaVehiculo(llantaVehiculoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retirarLlantaVehiculo(llantaVehiculoId: Int, causa: String?, usuarioId: Int?, replacedBy: Int?): Result<Int> {
        return try {
            apiClient.retireLlantaVehiculo(llantaVehiculoId, causa, usuarioId, replacedBy)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Llantas
    suspend fun getAllLlantas(): Result<List<Llanta>> {
        return apiClient.getAllLlantas()
    }
    
    suspend fun createLlanta(request: LlantaCreateRequest): Result<Llanta> {
        return apiClient.createLlanta(request)
    }

    suspend fun updateLlanta(llantaId: Int, request: LlantaUpdateRequest): Result<Llanta> {
        return apiClient.updateLlanta(llantaId, request)
    }

    suspend fun deleteLlanta(llantaId: Int): Result<Unit> {
        return apiClient.deleteLlanta(llantaId)
    }
    
    suspend fun getLlantasByFlota(flotaId: Int): Result<List<Llanta>> {
        return apiClient.getLlantasByFlota(flotaId)
    }

    suspend fun associateLlantaToFlota(llantaId: Int, flotaId: Int): Result<Int> {
        return apiClient.associateLlantaToFlota(llantaId, flotaId)
    }

    suspend fun disassociateLlantaFromFlota(idLlantasFlota: Int): Result<Unit> {
        return apiClient.disassociateLlantaFromFlota(idLlantasFlota)
    }

    suspend fun disassociateLlantaFromFlotaByLlantaAndFlota(llantaId: Int, flotaId: Int): Result<Unit> {
        return apiClient.disassociateLlantaFromFlotaByLlantaAndFlota(llantaId, flotaId)
    }

    // PruebaRendimiento
    suspend fun createPruebaRendimiento(request: PruebaRendimientoCreateRequest): Result<PruebaRendimiento> {
        return apiClient.createPruebaRendimiento(request)
    }

    suspend fun getUltimoPruebaRendimientoByVehiculo(vehiculoId: Int): Result<PruebaRendimiento?> {
        return apiClient.getUltimoPruebaRendimientoByVehiculo(vehiculoId)
    }

    suspend fun getUltimosPruebaRendimientoByFlota(flotaId: Int): Result<Map<Int, Float>> {
        return apiClient.getUltimosPruebaRendimientoByFlota(flotaId)
    }

    // LlantaRendimiento
    suspend fun createLlantaRendimiento(request: LlantaRendimientoCreateRequest): Result<LlantaRendimiento> {
        return apiClient.createLlantaRendimiento(request)
    }

    // Obtener todas las entradas de llantas rendimiento (para calcular últimos valores por llanta)
    suspend fun getAllLlantasRendimiento(): Result<List<LlantaRendimiento>> {
        return apiClient.getAllLlantasRendimiento()
    }

    // Obtener directamente los últimos registros por llanta vehiculo desde el backend
    suspend fun getUltimosLlantasRendimiento(): Result<List<LlantaRendimiento>> {
        return apiClient.getUltimosLlantasRendimiento()
    }

    // Obtener mapa de últimos registros por una lista de llantaVehiculoIds
    suspend fun getUltimosLlantasRendimientoMap(ids: List<Int>): Result<Map<Int, LlantaRendimiento>> {
        return apiClient.getUltimosLlantasRendimientoMap(ids)
    }

    suspend fun getUltimosLlantasRendimientoList(ids: List<Int>): Result<List<LlantaRendimiento>> {
        return apiClient.getUltimosLlantasRendimientoList(ids)
    }

    /**
     * Carga inicial estricta para todas las llantas de un vehículo.
     * Regla ABSOLUTA: si existe al menos un registro de LlantaRendimiento para la llanta,
     *                usar EXCLUSIVAMENTE valores de LlantaRendimiento.
     *                Solo cuando NO exista ningún registro, usar valores de LlantaVehiculo.
     * Devuelve lista ordenada siguiendo el orden recibido de `getLlantasVehiculosByVehiculoId`.
     */
    suspend fun getInitialFormDataForAllLlantas(vehiculoId: Int): List<LlantaRendimientoFormData> {
        try {
            // 1) Obtener llantas instaladas en el vehículo (preserva orden backend)
            val llantasRes = apiClient.getLlantasVehiculosByVehiculoId(vehiculoId)
            val llantas = llantasRes.getOrNull() ?: emptyList()
            if (llantas.isEmpty()) return emptyList()

            // 2) Preparar ids y solicitar últimos registros en batch
            val ids = llantas.map { it.idLlantasVehiculos }

            // Preferir el endpoint map; si falla, intentar list y convertir a mapa
            val ultimosMap: Map<Int, LlantaRendimiento> = apiClient.getUltimosLlantasRendimientoMap(ids).getOrNull()
                ?: run {
                    val listFallback = apiClient.getUltimosLlantasRendimientoList(ids).getOrNull() ?: emptyList()
                    listFallback.associateBy { it.LlantasVehiculos_idLlantasVehiculos }
                }

            // 3) Mapear manteniendo orden y reglas estrictas
            return llantas.map { lv ->
                val last = ultimosMap[lv.idLlantasVehiculos]
                if (last != null) {
                    // Usar EXCLUSIVAMENTE valores de llantasrendimiento
                    LlantaRendimientoFormData(
                        llantaVehiculoId = lv.idLlantasVehiculos,
                        mm1 = last.LlantasRendimientoMm1.toString(),
                        mm2 = last.LlantasRendimientoMm2.toString(),
                        mm3 = last.LlantasRendimientoMm3.toString(),
                        mm4 = last.LlantasRendimientoMm4.toString(),
                        presion = last.LlantasRendimientoPresion.toString(),
                        condPel = last.LlantasRendimientoCondPel,
                        pTerminada = (last.LlantasRendimientoPTerminada == 1),
                        foto = last.LlantasRendimientoFoto,
                        fotoNombre = null,
                        fotoTamano = null,
                        comentarios = last.LlantasRendimientoComent ?: "Ninguno",
                        desgaste = last.LlantasRendimientoDesgaste ?: "A. SIN DESGASTE IRREGULAR"
                    )
                } else {
                    // SOLO si NO existe ningún LlantaRendimiento: usar valores de LlantaVehiculo
                    LlantaRendimientoFormData(
                        llantaVehiculoId = lv.idLlantasVehiculos,
                        mm1 = lv.LlantasVehiculosMM1.toString(),
                        mm2 = lv.LlantasVehiculosMM2.toString(),
                        mm3 = lv.LlantasVehiculosMM3.toString(),
                        mm4 = lv.LlantasVehiculosMM4.toString(),
                        presion = lv.LlantasVehiculosPresion.toString(),
                        condPel = false,
                        pTerminada = false,
                        foto = null,
                        fotoNombre = null,
                        fotoTamano = null,
                        comentarios = "Ninguno",
                        desgaste = "A. SIN DESGASTE IRREGULAR"
                    )
                }
            }
        } catch (e: Exception) {
            println("[Repository] getInitialFormDataForAllLlantas error: ${e.message}")
            return emptyList()
        }
    }

    // Obtener historial de rendimientos para una llanta instalada
    suspend fun getLlantasRendimientoByLlantaVehiculoId(llantaVehiculoId: Int): Result<List<LlantaRendimiento>> {
        return apiClient.getLlantasRendimientoByLlantaVehiculoId(llantaVehiculoId)
    }

    // Update the last rendimiento PTerminada flag for a given llanta
    suspend fun updateUltimaLlantaRendimiento(llantaVehiculoId: Int, pTerminada: Boolean): Result<Unit> {
        return try {
            apiClient.updateUltimaLlantaRendimiento(llantaVehiculoId, pTerminada)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Check whether a llanta is currently marked as terminada (reads last record flag)
    suspend fun isLlantaTerminada(llantaVehiculoId: Int): Result<Boolean> {
        return try {
            apiClient.isLlantaTerminada(llantaVehiculoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obtener una prueba de rendimiento por su id
    suspend fun getPruebaRendimientoById(id: Int): Result<PruebaRendimiento> {
        return apiClient.getPruebaRendimientoById(id)
    }

    // Sucursales
    suspend fun getSucursalesByFlotaId(flotaId: Int): Result<List<Sucursal>> {
        return apiClient.getSucursalesByFlotaId(flotaId)
    }

    // PruebasSemaforo
    suspend fun createPruebasSemaforo(request: PruebasSemaforoCreateRequest): Result<PruebasSemaforo> {
        return apiClient.createPruebaSemaforo(request)
    }

    suspend fun getPruebasSemaforoByFlotaId(flotaId: Int): Result<List<PruebasSemaforo>> {
        return apiClient.getPruebasSemaforoByFlotaId(flotaId)
    }

    // VehiculoSemaforo
    suspend fun createVehiculoSemaforo(request: VehiculoSemaforoCreateRequest): Result<VehiculoSemaforo> {
        return apiClient.createVehiculoSemaforo(request)
    }

    suspend fun getVehiculosSemaforoByPruebaId(pruebaId: Int): Result<List<VehiculoSemaforo>> {
        return apiClient.getVehiculosSemaforoByPruebaId(pruebaId)
    }

    suspend fun deleteVehiculoSemaforo(id: Int): Result<Unit> {
        return apiClient.deleteVehiculoSemaforo(id)
    }

    suspend fun getVehiculoSemaforoById(id: Int): Result<VehiculoSemaforo> {
        return apiClient.getVehiculoSemaforoById(id)
    }

    suspend fun getLlantasSemaforoByVehiculoId(vehiculoSemaforoId: Int): Result<List<LlantasSemaforo>> {
        return apiClient.getLlantasSemaforoByVehiculoId(vehiculoSemaforoId)
    }

    suspend fun updateVehiculoSemaforo(id: Int, request: VehiculoSemaforoCreateRequest): Result<Unit> {
        return apiClient.updateVehiculoSemaforo(id, request)
    }

    suspend fun updateLlantaSemaforo(id: Int, request: LlantasSemaforoCreateRequest): Result<Unit> {
        return apiClient.updateLlantaSemaforo(id, request)
    }

    // LlantasSemaforo
    suspend fun createLlantasSemaforo(request: LlantasSemaforoCreateRequest): Result<LlantasSemaforo> {
        return apiClient.createLlantaSemaforo(request)
    }

    // PruebasDesecho
    suspend fun getAllPruebasDesecho(): Result<List<PruebasDesecho>> {
        return apiClient.getAllPruebasDesecho()
    }

    suspend fun getAllPruebasDesechoByFlota(flotasId: Int?): Result<List<PruebasDesecho>> {
        return apiClient.getAllPruebasDesecho(flotasId)
    }

    suspend fun createPruebaDesecho(request: PruebasDesechoCreateRequest): Result<PruebasDesecho> {
        return apiClient.createPruebaDesecho(request)
    }

    // LlantasDesecho
    suspend fun getLlantasDesechoByPruebaId(pruebaId: Int): Result<List<LlantasDesecho>> {
        return apiClient.getLlantasDesechoByPruebaId(pruebaId)
    }

    suspend fun createLlantaDesecho(request: LlantasDesechoCreateRequest): Result<LlantasDesecho> {
        return apiClient.createLlantaDesecho(request)
    }

    suspend fun updateLlantaDesecho(id: Int, request: LlantasDesechoUpdateRequest): Result<Unit> {
        return apiClient.updateLlantaDesecho(id, request)
    }

    suspend fun deleteLlantaDesecho(id: Int): Result<Unit> {
        return apiClient.deleteLlantaDesecho(id)
    }

    // PerfilesUsuario
    suspend fun getPerfilesUsuario(): Result<List<PerfilesUsuario>> {
        return apiClient.getPerfilesUsuario()
    }

    suspend fun createPerfilUsuario(request: PerfilesUsuarioCreateRequest): Result<PerfilesUsuario> {
        return apiClient.createPerfilUsuario(request)
    }

    suspend fun updatePerfilUsuario(perfilId: Int, request: PerfilesUsuarioUpdateRequest): Result<PerfilesUsuario> {
        return apiClient.updatePerfilUsuario(perfilId, request)
    }

    // Permisos
    suspend fun getPermisos(): Result<List<Permisos>> {
        return apiClient.getPermisos()
    }

    suspend fun getPermisosByPerfilId(perfilId: Int): Result<List<Permisos>> {
        return apiClient.getPermisosByPerfilId(perfilId)
    }

    suspend fun createPermiso(request: PermisosCreateRequest): Result<Permisos> {
        return apiClient.createPermiso(request)
    }

    suspend fun deletePermiso(permisoId: Int): Result<Unit> {
        return apiClient.deletePermiso(permisoId)
    }

    // FlotasUsuarios
    suspend fun createFlotaUsuario(request: FlotasUsuariosCreateRequest): Result<FlotasUsuarios> {
        return apiClient.createFlotaUsuario(request)
    }

    suspend fun deleteFlotaUsuario(flotaUsuarioId: Int): Result<Unit> {
        return apiClient.deleteFlotaUsuario(flotaUsuarioId)
    }

    suspend fun getFlotasUsuariosByUsuarioId(usuarioId: Int): Result<List<FlotasUsuarios>> {
        return apiClient.getFlotasUsuariosByUsuarioId(usuarioId)
    }

    suspend fun getUsuariosByFlotaId(flotaId: Int): Result<List<UsuarioWithFlotaUsuarioId>> {
        return apiClient.getUsuariosByFlotaId(flotaId)
    }

    suspend fun getUsuariosNoAsociadosByFlotaId(flotaId: Int): Result<List<Usuario>> {
        return apiClient.getUsuariosNoAsociadosByFlotaId(flotaId)
    }

    // Métodos utilitarios
    suspend fun getVehiculoWithDetails(vehiculoId: Int): Result<Vehiculo> {
        return try {
            // TODO: Implementar lógica para obtener vehículo con detalles completos
            val vehiculos = apiClient.getVehiculosByFlotaId(0) // Placeholder
            val vehiculo = vehiculos.getOrNull()?.firstOrNull { it.idVehiculos == vehiculoId }
            if (vehiculo != null) {
                Result.success(vehiculo)
            } else {
                Result.failure(Exception("Vehículo no encontrado"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Obtener vehículo por id directamente (delegar al ApiClient)
    suspend fun getVehiculoById(vehiculoId: Int): Result<Vehiculo> {
        return try {
            apiClient.getVehiculoById(vehiculoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLlantaWithDetails(llantaId: Int): Result<Llanta> {
        return try {
            val llantas = apiClient.getAllLlantas()
            val llanta = llantas.getOrNull()?.firstOrNull { it.idLlantas == llantaId }
            if (llanta != null) {
                Result.success(llanta)
            } else {
                Result.failure(Exception("Llanta no encontrada"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTipoVehiculoWithDetails(tipoVehiculoId: Int): Result<TipoVehiculo> {
        return try {
            val tipos = apiClient.getTiposVehiculos()
            val tipo = tipos.getOrNull()?.firstOrNull { it.idTipoVehiculos == tipoVehiculoId }
            if (tipo != null) {
                Result.success(tipo)
            } else {
                Result.failure(Exception("Tipo de vehículo no encontrado"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calcula los kilómetros recorridos por una llanta instalada usando su historial de rendimiento.
     * Emite km = ultimoOdometro - primerOdometro (nunca negativo).
     */
    suspend fun getKmRecorridoPorLlanta(llantaVehiculoId: Int): Flow<Result<Float>> {
        return flow {
            try {
                // Obtener historial de rendimientos para la llanta
                val llantasRes = apiClient.getLlantasRendimientoByLlantaVehiculoId(llantaVehiculoId)
                if (llantasRes.isFailure) {
                    emit(Result.failure(Exception("Error fetching llantas rendimiento: ${llantasRes.exceptionOrNull()?.message}")))
                    return@flow
                }

                val list = llantasRes.getOrNull() ?: emptyList()
                if (list.isEmpty()) {
                    // No hay pruebas para esta llanta: emitir 0
                    emit(Result.success(0f))
                    return@flow
                }

                // Resolver odómetros por prueba para tomar el primero y último del historial
                val sorted = list.sortedBy { it.idLlantasRendimiento }
                val odoByPrueba = mutableMapOf<Int, Float>()

                sorted.forEach { item ->
                    val pruebaId = item.PruebaRendimiento_idPruebaRendimiento
                    if (!odoByPrueba.containsKey(pruebaId)) {
                        val pruebaRes = apiClient.getPruebaRendimientoById(pruebaId)
                        val odo = pruebaRes.getOrNull()?.PruebaRendimientoOdometro
                            ?: item.PruebaRendimientoOdometro
                        if (odo != null) {
                            odoByPrueba[pruebaId] = odo
                        }
                    }
                }

                if (odoByPrueba.isEmpty()) {
                    emit(Result.success(0f))
                    return@flow
                }

                val firstOdo = sorted.mapNotNull { odoByPrueba[it.PruebaRendimiento_idPruebaRendimiento] }.firstOrNull()
                val lastOdo = sorted.mapNotNull { odoByPrueba[it.PruebaRendimiento_idPruebaRendimiento] }.lastOrNull()

                val km = ((lastOdo ?: 0f) - (firstOdo ?: 0f)).coerceAtLeast(0f)
                emit(Result.success(km))
            } catch (e: Exception) {
                emit(Result.failure(e))
            }
        }
    }

    /**
     * Calcula los kilómetros recorridos por vehículo dentro de una flota.
     * Emite km = última prueba - primera prueba del vehículo (nunca negativo).
     */
    suspend fun getKmRecorridoPorVehiculoByFlota(flotaId: Int): Result<Map<Int, Float>> {
        return try {
            val response = apiClient.getKmRecorridoPorVehiculoByFlota(flotaId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Métodos para operaciones batch - CORREGIDO
    suspend fun createMultipleLlantasVehiculo(requests: List<LlantaVehiculoCreateRequest>): Result<Unit> {
    return try {
        apiClient.createMultipleLlantasVehiculo(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }   

    suspend fun createMultipleLlantasRendimiento(requests: List<LlantaRendimientoCreateRequest>): Result<Unit> {
        return try {
            apiClient.createMultipleLlantasRendimiento(requests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Reports
    suspend fun downloadVehiculoRendimientoPdf(vehiculoId: Int): Result<ByteArray> {
        return try {
            apiClient.downloadVehiculoRendimientoPdf(vehiculoId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadSemaforoReportPdf(payload: Any): Result<ByteArray> {
        return try {
            apiClient.downloadSemaforoReportPdf(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadPruebaInspeccionReportPdf(payload: Any): Result<ByteArray> {
        return try {
            apiClient.downloadPruebaInspeccionReportPdf(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadDesechoReportPdf(payload: Any): Result<ByteArray> {
        return try {
            apiClient.downloadDesechoReportPdf(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createMultipleLlantasSemaforo(requests: List<LlantasSemaforoCreateRequest>): Result<List<LlantasSemaforo>> {
        return try {
            val results = mutableListOf<LlantasSemaforo>()
            for (request in requests) {
                val result = apiClient.createLlantaSemaforo(request)
                if (result.isSuccess) {
                    results.add(result.getOrThrow())
                } else {
                    return Result.failure(result.exceptionOrNull() ?: Exception("Error creating llanta semaforo"))
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    

    // Métodos de validación
    suspend fun validateEmail(email: String): Result<Boolean> {
        return try {
            // Simular validación de email
            val isValid = email.contains("@") && email.contains(".")
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validatePlaca(placa: String): Result<Boolean> {
        return try {
            // Simular validación de placa (puedes implementar lógica más compleja)
            val isValid = placa.length in 6..8
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Métodos de búsqueda
    suspend fun searchLlantas(query: String): Result<List<Llanta>> {
        return try {
            val llantas = apiClient.getAllLlantas()
            llantas.onSuccess { list ->
                val filtered = list.filter { llanta ->
                    llanta.LlantasMarca.contains(query, ignoreCase = true) ||
                    llanta.LlantasModelo.contains(query, ignoreCase = true) ||
                    llanta.LlantasMedida.toString().contains(query)
                }
                return Result.success(filtered)
            }
            llantas
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchVehiculos(query: String, flotaId: Int? = null): Result<List<Vehiculo>> {
        return try {
            val vehiculos = if (flotaId != null) {
                apiClient.getVehiculosByFlotaId(flotaId)
            } else {
                // Obtener todos los vehículos de todas las flotas (puede ser ineficiente)
                val allFlotas = apiClient.getAllFlotas()
                val allVehiculos = mutableListOf<Vehiculo>()
                allFlotas.onSuccess { flotas ->
                    flotas.forEach { flota ->
                        apiClient.getVehiculosByFlotaId(flota.idFlotas).onSuccess { vehs ->
                            allVehiculos.addAll(vehs)
                        }
                    }
                }
                Result.success(allVehiculos)
            }
            
            vehiculos.onSuccess { list ->
                val filtered = list.filter { vehiculo ->
                    vehiculo.VehiculosNumero.contains(query, ignoreCase = true) ||
                    (vehiculo.FlotasNombre?.contains(query, ignoreCase = true) ?: false) ||
                    (vehiculo.TipoVehiculosNombre?.contains(query, ignoreCase = true) ?: false)
                }
                return Result.success(filtered)
            }
            vehiculos
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // PruebasInspeccion
    suspend fun getPruebasInspeccionByFlotaId(flotaId: Int): Result<List<PruebaInspeccion>> {
        val result = apiClient.getPruebasInspeccionByFlotaId(flotaId)
        return result
    }

    suspend fun createPruebaInspeccion(request: PruebaInspeccionCreateRequest): Result<PruebaInspeccion> {
        return try {
            apiClient.createPruebaInspeccion(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createVehiculoInspeccion(request: VehiculoInspeccionCreateRequest): Result<VehiculoInspeccion> {
        return apiClient.createVehiculoInspeccion(request)
    }

    suspend fun getVehiculosInspeccionByPruebaId(pruebaId: Int): Result<List<VehiculoInspeccion>> {
        return apiClient.getVehiculosInspeccionByPruebaId(pruebaId)
    }

    suspend fun getVehiculoInspeccionById(id: Int): Result<VehiculoInspeccion> {
        return apiClient.getVehiculoInspeccionById(id)
    }

    suspend fun getLlantasInspeccionByVehiculoId(vehiculoId: Int): Result<List<LlantaInspeccion>> {
        // Check cache first to avoid duplicate in-flight requests caused by recomposition
        // Use mutex to ensure only one fetch updates the cache at a time
        llantasInspeccionMutex.withLock {
            val cached = llantasInspeccionCache[vehiculoId]
            if (cached != null) {
                println("[Repo] Returning cached llantasInspeccion for vehiculoId=$vehiculoId count=${cached.size}")
                return Result.success(cached)
            }
        }

        val res = apiClient.getLlantasInspeccionByVehiculoId(vehiculoId)
        res.onSuccess { list ->
            println("[Repo] Fetched llantasInspeccion for vehiculoId=$vehiculoId count=${list.size}")
            // store in cache
            llantasInspeccionMutex.withLock {
                llantasInspeccionCache[vehiculoId] = list
            }
        }.onFailure { err ->
            println("[Repo] Failed fetch llantasInspeccion vehiculoId=$vehiculoId: ${err.message}")
        }
        return res
    }

    suspend fun updateVehiculoInspeccion(id: Int, body: Map<String, Any?>): Result<VehiculoInspeccion> {
        return apiClient.updateVehiculoInspeccion(id, body)
    }

    suspend fun updateLlantaInspeccion(id: Int, body: Map<String, Any?>): Result<LlantaInspeccion> {
        val res = apiClient.updateLlantaInspeccion(id, body)
        res.onSuccess { updated ->
            try {
                // If we have a cached list for the vehiculo, replace the updated item in cache
                val vehId = updated.vehiculosinspeccion_idVehiculoInspeccion
                llantasInspeccionMutex.withLock {
                    val current = llantasInspeccionCache[vehId]
                    if (current != null) {
                        llantasInspeccionCache[vehId] = current.map { if (it.idLlantasInspeccion == updated.idLlantasInspeccion) updated else it }
                    }
                }
            } catch (_: Exception) {}
        }
        return res
    }

    suspend fun deleteVehiculoInspeccion(id: Int): Result<Unit> {
        return apiClient.deleteVehiculoInspeccion(id)
    }

    suspend fun createMultipleLlantasInspeccion(requests: List<LlantaInspeccionCreateRequest>): Result<List<LlantaInspeccion>> {
        return apiClient.createMultipleLlantasInspeccion(requests)
    }

    // Parametros
    suspend fun getParametrosByFlotaId(flotaId: Int): Result<List<Parametro>> {
        return apiClient.getParametrosByFlotaId(flotaId)
    }

    suspend fun getParametroById(parametroId: Int): Result<Parametro> {
        return apiClient.getParametroById(parametroId)
    }

    suspend fun searchLlantasByMedida(medida: String): Result<List<Llanta>> {
        return apiClient.searchLlantasByMedida(medida)
    }

    suspend fun createParametro(request: ParametroCreateRequest): Result<Parametro> {
        return apiClient.createParametro(request)
    }

    suspend fun updateParametro(parametroId: Int, request: ParametroUpdateRequest): Result<Parametro> {
        return apiClient.updateParametro(parametroId, request)
    }

    suspend fun deleteParametro(parametroId: Int): Result<Unit> {
        return apiClient.deleteParametro(parametroId)
    }
}