package com.megatransportes.yokoh.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class LoginRequest(
    val UsuariosCorreo: String,
    val UsuariosPassword: String
)

@Serializable
data class Usuario(
    val idUsuarios: Int,
    val UsuariosNombre: String,
    val UsuariosTelefono: String,
    val UsuariosCorreo: String,
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val PerfilesUsuarioNombre: String? = null,
    val Distribuidor_idDistribuidor: Int? = null,
    val DistribuidorNombre: String? = null
)

@Serializable
data class UsuarioCreateRequest(
    val UsuariosNombre: String,
    val UsuariosTelefono: String,
    val UsuariosCorreo: String,
    val UsuariosPassword: String,
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val Distribuidor_idDistribuidor: Int? = null
)

@Serializable
data class Distribuidor(
    val idDistribuidor: Int,
    val DistribuidorNombre: String
)

@Serializable
data class Flota(
    val idFlotas: Int,
    val FlotasNombre: String,
    val FlotasClasificacion: String,
    val FlotasZona: String,
    val FlotasEstado: String,
    val FlotasCiudad: String
)

@Serializable
data class FlotaCreateRequest(
    val FlotasNombre: String,
    val FlotasClasificacion: String,
    val FlotasZona: String,
    val FlotasEstado: String,
    val FlotasCiudad: String,
    // Optional: id of the usuario who created this flota. If provided, backend may auto-associate.
    val Usuarios_idUsuarios: Int? = null
)

@Serializable
data class Vehiculo(
    val idVehiculos: Int,
    val Flotas_idFlotas: Int,
    val VehiculosNumero: String,
    val TipoVehiculos_idTipoVehiculos: Int,
    val VehiculosOdometro: Float,
    val VehiculosOdometroRegistro: Float? = null,
    val VehiculosImagen: String? = null,
    val FlotasNombre: String? = null,
    val TipoVehiculosNombre: String? = null,
    val VehiculosPTerminada: Int = 0,
    val kmRecorridoLatest: Float? = null
)

@Serializable
data class VehiculoCreateRequest(
    val Flotas_idFlotas: Int,
    val VehiculosNumero: String,
    val TipoVehiculos_idTipoVehiculos: Int,
    val VehiculosOdometro: Float,
    val VehiculosImagen: String? = null
)

@Serializable
data class VehiculoUpdateRequest(
    val VehiculosOdometro: Float
)

@Serializable
data class TipoVehiculo(
    val idTipoVehiculos: Int,
    val TipoVehiculosNombre: String,
    val TipoVehiculosCantLlantas: Int,
    val TipoVehiculosLlantasEmp: String? = null
)

@Serializable
data class TipoVehiculoCreateRequest(
    val TipoVehiculosNombre: String,
    val TipoVehiculosCantLlantas: Int,
    val TipoVehiculosLlantasEmp: String? = null
)

@Serializable
data class TipoVehiculoUpdateRequest(
    val TipoVehiculosNombre: String,
    val TipoVehiculosCantLlantas: Int,
    val TipoVehiculosLlantasEmp: String? = null
)

@Serializable
data class Llanta(
    val idLlantas: Int,
    val LlantasMarca: String,
    val LlantasModelo: String,
    val LlantasPrecio: Float? = null,
    val LlantasMedida: String,
    val LlantasMm: Float
    ,
    val asociada: Int? = 0
)

@Serializable
data class LlantaCreateRequest(
    val LlantasMarca: String,
    val LlantasModelo: String,
    val LlantasPrecio: Float? = null,
    val LlantasMedida: String,
    val LlantasMm: Float
)

@Serializable
data class LlantaUpdateRequest(
    val LlantasMarca: String,
    val LlantasModelo: String,
    val LlantasPrecio: Float? = null,
    val LlantasMedida: String,
    val LlantasMm: Float
)

@Serializable
data class LlantaVehiculo(
    val idLlantasVehiculos: Int,
    val Llantas_idLlantas: Int,
    val Vehiculos_idVehiculos: Int,
    val LlantasVehiculosNoQuemado: String,
    val LlantasVehiculosPresion: Int,
    val LlantasVehiculosPrecio: Float? = null,
    val LlantasVehiculosPiso: String,
    val LlantasVehiculosMM1: Float,
    val LlantasVehiculosMM2: Float,
    val LlantasVehiculosMM3: Float,
    val LlantasVehiculosMM4: Float,
    val LlantasMarca: String? = null,
    val LlantasModelo: String? = null,
    val LlantasMedida: String? = null,
    val LlantasMm: Int? = null,
    val LlantasPrecio: Float? = null
    ,
    val LlantasVehiculosFechaInicio: String? = null
)

@Serializable
data class LlantaVehiculoCreateRequest(
    val Llantas_idLlantas: Int,
    val Vehiculos_idVehiculos: Int,
    val LlantasVehiculosNoQuemado: String,
    val LlantasVehiculosPresion: Int,
    val LlantasVehiculosPrecio: Float,
    val LlantasVehiculosPiso: String,
    val LlantasVehiculosFechaInicio: String? = null,
    val LlantasVehiculosMM1: Float,
    val LlantasVehiculosMM2: Float,
    val LlantasVehiculosMM3: Float,
    val LlantasVehiculosMM4: Float
    ,
    @kotlinx.serialization.SerialName("replaceId")
    val replaceId: Int? = null
)

@Serializable
data class Sucursal(
    val idSucursales: Int,
    val Flotas_idFlotas: Int,
    val SucursalesNombre: String,
    val FlotasNombre: String? = null
)

@Serializable
data class PruebaRendimiento(
    val idPruebaRendimiento: Int,
    val Vehiculos_idVehiculos: Int,
    val PruebaRendimientoFecha: String,
    val PruebaRendimientoOdometro: Float,
    val latitude: Double? = null,   
    val longitude: Double? = null   
)

@Serializable
data class PruebaRendimientoCreateRequest(
    val Vehiculos_idVehiculos: Int,
    val PruebaRendimientoFecha: String,
    val PruebaRendimientoOdometro: Float,
    val Usuarios_idUsuarios: Int? = null,
    val latitude: Double? = null,   
    val longitude: Double? = null   
)

@Serializable
data class LlantaRendimiento(
    val idLlantasRendimiento: Int,
    val PruebaRendimiento_idPruebaRendimiento: Int,
    val LlantasVehiculos_idLlantasVehiculos: Int,
    val LlantasRendimientoMm1: Float,
    val LlantasRendimientoMm2: Float,
    val LlantasRendimientoMm3: Float,
    val LlantasRendimientoMm4: Float,
    val LlantasRendimientoPresion: Int,
    val LlantasRendimientoCondPel: Boolean,
    val LlantasRendimientoFoto: String? = null,
    // New DB column added: tinyint flag (0/1) indicating this wheel was marked "Terminada"
    val LlantasRendimientoPTerminada: Int = 0
    ,
    // Include the odometer from the associated PruebaRendimiento (if the backend provides it joined)
    val PruebaRendimientoOdometro: Float? = null
    ,
    // Vehicle's odometer reading (to calculate km = prueba.odo - veh.odo)
    val VehiculosOdometro: Float? = null
    ,
    // Server-provided precomputed kilometers for this rendimiento (preferred)
    val kmRecorrido: Float? = null
    ,
    // Optional comment for rendimiento (new DB column)
    val LlantasRendimientoComent: String? = null
    ,
    // Optional desgaste classification for rendimiento (new DB column)
    val LlantasRendimientoDesgaste: String? = null
    ,
    // Causa de retiro cuando la llanta es terminada (nueva columna DB)
    val LlantasRendimientoCausaRetiro: String? = null
)

@Serializable
data class LlantaRendimientoCreateRequest(
    val PruebaRendimiento_idPruebaRendimiento: Int,
    val LlantasVehiculos_idLlantasVehiculos: Int,
    val LlantasRendimientoMm1: Float,
    val LlantasRendimientoMm2: Float,
    val LlantasRendimientoMm3: Float,
    val LlantasRendimientoMm4: Float,
    val LlantasRendimientoPresion: Int,
    val LlantasRendimientoCondPel: Boolean,
    val LlantasRendimientoVigia: Int = 0,
    val LlantasRendimientoFoto: String? = null,
    // pass 0 or 1 depending on whether the wheel was marked as "Terminada"
    val LlantasRendimientoPTerminada: Int = 0
    ,
    // Optional comment for rendimiento
    val LlantasRendimientoComent: String? = null
    ,
    // Optional desgaste classification for rendimiento
    val LlantasRendimientoDesgaste: String? = null
    ,
    // Causa de retiro cuando la llanta es terminada
    val LlantasRendimientoCausaRetiro: String? = null
)

@Serializable
data class PruebasSemaforo(
    val idPruebasSemaforo: Int,
    val PruebasSemaforoFecha: String,
    val PruebasSemaforoTitulo: String,
    val Flotas_idFlotas: Int? = null,
    // Optional coordinates saved when the prueba was created
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class PruebasSemaforoCreateRequest(
    val PruebasSemaforoTitulo: String,
    val Flotas_idFlotas: Int,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class VehiculoSemaforo(
    val idVehiculoSemaforo: Int,
    val PruebasSemaforo_idPruebasSemaforo: Int,
    val TipoVehiculos_idTipoVehiculos: Int,
    val VehiculoSemaforoNo: String
    ,
    // Optional coordinates saved when the vehicle was created/updated
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Alternate possible JSON keys used in different backend deployments
    @kotlinx.serialization.SerialName("VehiculoPruebaSemaforoLat")
    val VehiculoPruebaSemaforoLat: Double? = null,
    @kotlinx.serialization.SerialName("VehiculoPruebaSemaforoLon")
    val VehiculoPruebaSemaforoLon: Double? = null,
    @kotlinx.serialization.SerialName("VehiculosLatitude")
    val VehiculosLatitude: Double? = null,
    @kotlinx.serialization.SerialName("VehiculosLongitude")
    val VehiculosLongitude: Double? = null
)


@Serializable
data class VehiculoSemaforoCreateRequest(
    val PruebasSemaforo_idPruebasSemaforo: Int,
    val TipoVehiculos_idTipoVehiculos: Int,
    val VehiculoSemaforoNo: String,
    val Usuarios_idUsuarios: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class LlantasSemaforo(
    val idLlantasSemaforo: Int,
    val VehiculoSemaforo_idVehiculoSemaforo: Int,
    val Llantas_idLlantas: Int,
    val LlantasSemaforoPresion: Int? = null,
    // Backend may return 0/1 OR true/false depending on route normalization.
    @Serializable(with = FlexibleIntFlagSerializer::class)
    val LlantasSemaforoVigia: Int = 0,
    val LlantasSemaforoColor: String,
    val LlantasSemaforoCondPel: Boolean? = null,
    val LlantasSemaforoObserv: String? = null,
    val LlantasSemaforoPiso: String? = null,
    val LlantasSemaforoComent: String? = null,
    val LlantasSemaforoFoto1: String? = null,
    val LlantasSemaforoFoto2: String? = null
)

@Serializable
data class LlantasSemaforoCreateRequest(
    val VehiculoSemaforo_idVehiculoSemaforo: Int,
    val Llantas_idLlantas: Int,
    val LlantasSemaforoPresion: Int? = null,
    // Send 0 or 1 for vigía to match backend tinyint
    val LlantasSemaforoVigia: Int = 0,
    val LlantasSemaforoColor: String,
    val LlantasSemaforoCondPel: Boolean? = null,
    val LlantasSemaforoObserv: String? = null,
    val LlantasSemaforoPiso: String? = null,
    val LlantasSemaforoComent: String? = null,
    val LlantasSemaforoFoto1: String? = null,
    val LlantasSemaforoFoto2: String? = null
)

// Accepts flag values as: 1/0, true/false, "1"/"0", "true"/"false".
object FlexibleIntFlagSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleIntFlag", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0

        primitive.intOrNull?.let { return if (it != 0) 1 else 0 }
        primitive.booleanOrNull?.let { return if (it) 1 else 0 }

        val content = primitive.content.trim().lowercase()
        return when (content) {
            "1", "true", "t", "yes", "si", "sí" -> 1
            "0", "false", "f", "no" -> 0
            else -> 0
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(if (value != 0) 1 else 0)
    }
}

@Serializable
data class PruebasDesecho(
    val idPruebasDesecho: Int,
    val PruebasDesechoNombre: String,
    val PruebasDesechoFecha: String? = null
)

@Serializable
data class LlantasDesecho(
    val idLlantasDesecho: Int,
    val LlantasDesechocol: String? = null,
    val PruebasDesecho_idPruebasDesecho: Int,
    val Llantas_idLlantas: Int,
    val LlantasDesechoNoLlanta: String? = null,
    val LlantasDesechoPiso: String,
    // Some backend deployments may omit this field or return null for older records.
    // Keep it nullable so deserialization does not fail during the save/read flow.
    val LlantasDesechoCausaDes: String? = null,
    val LlantasDesechoUbi: String? = null,
    val Latitud: Double? = null,
    val Longitud: Double? = null,
    val LlantasDesechoRemanente: Float? = null,
    val LlantasDesechoFecha: String? = null,
    val LlantasDesechoFoto1: String? = null,
    val LlantasDesechoFoto2: String? = null,
    val LlantasDesechoComentarios: String? = null
)

@Serializable
data class PerfilesUsuario(
    val idPerfilesUsuario: Int,
    val PerfilesUsuarioNombre: String
)

@Serializable
data class Permisos(
    val idPermisos: Int,
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val PermisosNombre: String
)

@Serializable
data class FlotasUsuarios(
    val idFlotasUsuarios: Int,
    val Usuarios_idUsuarios: Int,
    val Flotas_idFlotas: Int
)

@Serializable
data class ApiResponse<T>(
    val message: String,
    val data: T? = null,
    val error: String? = null
)

// Request models for desecho
@Serializable
data class PruebasDesechoCreateRequest(
    val PruebasDesechoNombre: String,
    val PruebasDesechoFecha: String,
    val Flotas_idFlotas: Int? = null
)

@Serializable
data class LlantasDesechoCreateRequest(
    val LlantasDesechocol: String? = null,
    val PruebasDesecho_idPruebasDesecho: Int,
    val Llantas_idLlantas: Int,
    val LlantasDesechoNoLlanta: String? = null,
    val LlantasDesechoPiso: String,
    val LlantasDesechoCausaDes: String,
    val LlantasDesechoUbi: String? = null,
    val Latitud: Double? = null,
    val Longitud: Double? = null,
    val LlantasDesechoRemanente: Float? = null,
    val Usuarios_idUsuarios: Int? = null,
    val LlantasDesechoFecha: String? = null,
    val LlantasDesechoFoto1: String? = null,
    val LlantasDesechoFoto2: String? = null,
    val LlantasDesechoComentarios: String? = null
)

@Serializable
data class LlantasDesechoUpdateRequest(
    val LlantasDesechocol: String? = null,
    val PruebasDesecho_idPruebasDesecho: Int,
    val Llantas_idLlantas: Int,
    val LlantasDesechoNoLlanta: String? = null,
    val LlantasDesechoPiso: String,
    val LlantasDesechoCausaDes: String,
    val LlantasDesechoUbi: String? = null,
    val Latitud: Double? = null,
    val Longitud: Double? = null,
    val LlantasDesechoRemanente: Float? = null,
    val LlantasDesechoFecha: String? = null,
    val LlantasDesechoFoto1: String? = null,
    val LlantasDesechoFoto2: String? = null,
    val LlantasDesechoComentarios: String? = null
)

@Serializable
data class UsuarioUpdateRequest(
    val UsuariosNombre: String,
    val UsuariosTelefono: String,
    val UsuariosCorreo: String,
    val UsuariosPassword: String? = null,
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val Distribuidor_idDistribuidor: Int? = null
)

@Serializable
data class PerfilesUsuarioCreateRequest(
    val PerfilesUsuarioNombre: String
)

@Serializable
data class PerfilesUsuarioUpdateRequest(
    val PerfilesUsuarioNombre: String
)

@Serializable
data class PermisosCreateRequest(
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val PermisosNombre: String
)

@Serializable
data class FlotasUsuariosCreateRequest(
    val Usuarios_idUsuarios: Int,
    val Flotas_idFlotas: Int
)

@Serializable
data class FlotasUsuariosUpdateRequest(
    val Usuarios_idUsuarios: Int,
    val Flotas_idFlotas: Int
)

@Serializable
data class PermisosUpdateRequest(
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val PermisosNombre: String
)

@Serializable
data class FlotasUsuariosWithDetails(
    val idFlotasUsuarios: Int,
    val Usuarios_idUsuarios: Int,
    val Flotas_idFlotas: Int,
    val FlotasNombre: String? = null
)
// prueba inspeccion

@Serializable
data class PruebaInspeccion(
    val idPruebaInspeccion: Int,
    val PruebaInspeccionTitulo: String,
    val PruebaInspeccionFecha: String,
    val Flotas_idFlotas: Int,
    // Optional coordinates captured by backend (if client sent them when creating)
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class PruebaInspeccionCreateRequest(
    val PruebaInspeccionTitulo: String,
    val Flotas_idFlotas: Int
    ,
    // Optional coordinates captured by client when creating the prueba
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class VehiculoInspeccion(
    val idVehiculoInspeccion: Int,
    val pruebasinspeccion_idPruebaInspeccion: Int,
    val TipoVehiculos_idTipoVehiculos: Int,
    val VehiculoInspeccionNo: String
    ,
    // Optional name for the vehicle type (joined in backend query)
    val TipoVehiculosNombre: String? = null,
    // Optional coordinates saved when the vehicle was created/updated
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Alternate possible JSON keys used in different backend deployments
    @kotlinx.serialization.SerialName("VehiculosInspeccionLat")
    val VehiculosInspeccionLat: Double? = null,
    @kotlinx.serialization.SerialName("VehiculosInspeccionLon")
    val VehiculosInspeccionLon: Double? = null,
    @kotlinx.serialization.SerialName("VehiculosLatitude")
    val VehiculosLatitude: Double? = null,
    @kotlinx.serialization.SerialName("VehiculosLongitude")
    val VehiculosLongitude: Double? = null
)



@Serializable
data class VehiculoInspeccionCreateRequest(
    val pruebasinspeccion_idPruebaInspeccion: Int,
    val TipoVehiculos_idTipoVehiculos: Int,
    val VehiculoInspeccionNo: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Optional creator id - backend will use this or infer from authentication
    val Usuarios_idUsuarios: Int? = null
)

@Serializable
data class LlantaInspeccion(
    val idLlantasInspeccion: Int,
    val vehiculosinspeccion_idVehiculoInspeccion: Int,
    val Llantas_idLlantas: Int,
    val LlantasInspeccionMm1: Float,
    val LlantasInspeccionMm2: Float,
    val LlantasInspeccionMm3: Float,
    val LlantasInspeccionMm4: Float,
    val LlantasInspeccionPresion: Int,
    // New flag: vigia (0/1) stored as tinyint in DB — client uses Int 0/1
    val LlantasInspeccionVigia: Int = 0,
    // Backend sends 0/1 (tinyint) for this field; keep it as Int and interpret in UI
    val LlantasInspeccionCondPel: Int,
    val LlantasInspeccionObservacion: String? = null,
    val LlantasInspeccionComentario: String? = null,
    val LlantasInspeccionDOT: String? = null,
    val LlantasInspeccionPiso: String? = null,
    val LlantasInspeccionDesgaste: String? = null,
        // Photo fields are optional in backend responses
        val LlantasInspeccionFoto: String? = null,
        val LlantasInspeccionFoto2: String? = null
)

@Serializable
data class LlantaInspeccionCreateRequest(
    val vehiculosinspeccion_idVehiculoInspeccion: Int,
    val Llantas_idLlantas: Int,
    val LlantasInspeccionMm1: Float,
    val LlantasInspeccionMm2: Float,
    val LlantasInspeccionMm3: Float,
    val LlantasInspeccionMm4: Float,
    val LlantasInspeccionPresion: Int,
    // Send 0 or 1 to match backend tinyint convention for the new vigia flag
    val LlantasInspeccionVigia: Int = 0,
    // Send 0 or 1 to match backend tinyint convention
    val LlantasInspeccionCondPel: Int,
    val LlantasInspeccionObservacion: String? = null,
    val LlantasInspeccionComentario: String? = null,
    val LlantasInspeccionDOT: String? = null,
    val LlantasInspeccionPiso: String? = null,
    val LlantasInspeccionDesgaste: String? = null,
        // Photos optional when creating (may be omitted)
        val LlantasInspeccionFoto: String? = null,
        val LlantasInspeccionFoto2: String? = null
)

// Parametros
@Serializable
data class Parametro(
    val idParametros: Int,
    val Flotas_idFlotas: Int,
    val Llantas_idLlantas: Int,
    val ParametrosRC: String,
    val ParametrosPMin: Float,
    val ParametrosPSug: Float,
    val ParametrosPMax: Float,
    val ParametrosProfMin: Int,
    val ParametrosProfMax: Int,
    val LlantasMarca: String? = null,
    val LlantasModelo: String? = null,
    val LlantasMedida: String? = null,
    val LlantasMm: Int? = null
)

@Serializable
data class ParametroCreateRequest(
    val Flotas_idFlotas: Int,
    val Llantas_idLlantas: Int,
    val ParametrosRC: String,
    val ParametrosPMin: Float,
    val ParametrosPSug: Float,
    val ParametrosPMax: Float,
    val ParametrosProfMin: Int,
    val ParametrosProfMax: Int
)

@Serializable
data class ParametroUpdateRequest(
    val ParametrosRC: String,
    val ParametrosPMin: Float,
    val ParametrosPSug: Float,
    val ParametrosPMax: Float,
    val ParametrosProfMin: Int,
    val ParametrosProfMax: Int
)

// UI form model: values are strings for direct binding with text fields.
@Serializable
data class LlantaRendimientoFormData(
    val llantaVehiculoId: Int = 0,
    val selectedLlanta: Llanta? = null,
    val noQuemado: String = "",
    val piso: String = "Original",
    val mm1: String = "",
    val mm2: String = "",
    val mm3: String = "",
    val mm4: String = "",
    val presion: String = "",
    val condPel: Boolean = false,
    val vigia: Boolean = false,
    val pTerminada: Boolean = false,
    val foto: String? = null,
    val fotoNombre: String? = null,
    val fotoTamano: Long? = null
    ,
    // New UI field: comentarios for rendimiento. Default to "Ninguno" as requested.
    val comentarios: String = "Ninguno"
    ,
    // New UI field: desgaste classification. Default to SIN DESGASTE
    val desgaste: String = "A. SIN DESGASTE IRREGULAR"
    ,
    // Causa de retiro cuando la llanta es terminada
    val causaRetiro: String? = null
    ,
    val replacedLlantaVehiculoId: Int? = null
)