package com.megatransportes.yokoh.ui

import com.megatransportes.yokoh.data.models.*

sealed class Screen {
    object Login : Screen()
    object Flotas : Screen()
    object AddFlota : Screen()
    object Usuarios : Screen()
    object AddUsuario : Screen()
    data class EditUsuario(val usuario: Usuario) : Screen()
    object PerfilesUsuarioList : Screen()
    data class EditPerfilUsuario(val perfil: PerfilesUsuario?) : Screen()
    data class FlotaMenu(val flota: Flota) : Screen()
    data class FlotaUsuarios(val flota: Flota) : Screen()
    data class Vehiculos(val flota: Flota) : Screen()
    data class AddVehiculo(val flota: Flota) : Screen()
    data class LlantasVehiculo(val flota: Flota, val vehiculo: Vehiculo, val cantidadLlantas: Int) : Screen()
    data class PruebaRendimiento(val flota: Flota, val vehiculo: Vehiculo, val llantasVehiculo: List<LlantaVehiculo>) : Screen()
    data class PruebasSemaforoList(val flota: Flota) : Screen()
    data class PruebaSemaforoReport(val flota: Flota, val prueba: PruebasSemaforo) : Screen()
    data class VehiculosSemaforoList(val flota: Flota, val pruebaSemaforo: PruebasSemaforo) : Screen()
    data class Semaforo(val flota: Flota, val pruebaSemaforo: PruebasSemaforo, val initialEditingVehiculoId: Int? = null) : Screen()
    data class PilasDesecho(val flota: Flota) : Screen()
    data class LlantasDesechoScreen(val pruebaDesecho: PruebasDesecho, val flota: Flota) : Screen()
    // nueva llanta screen can accept an optional existing LlantasDesecho for editing
    data class NuevaLlantaDesechoScreen(val pruebaDesecho: PruebasDesecho, val flota: Flota, val existing: LlantasDesecho? = null) : Screen()
    data class PruebaDesechoReport(val flota: Flota, val prueba: PruebasDesecho) : Screen()
    data class PruebasInspeccionList(val flota: Flota) : Screen()
    data class PruebaInspeccionReport(val flota: Flota, val prueba: PruebaInspeccion) : Screen()
    data class InspeccionVehicularScreen(
        val pruebaInspeccion: PruebaInspeccion,
        val flota: Flota,
        val vehiculoInspeccion: com.megatransportes.yokoh.data.models.VehiculoInspeccion? = null
    ) : Screen()
    // Intermediate screen: list vehicles that belong to a given PruebaInspeccion
    data class VehiculosInspeccionList(val pruebaInspeccion: PruebaInspeccion, val flota: Flota) : Screen()
    data class ParametrosList(val flota: Flota, val initialTab: Int = 0, val suggestedLlantaIds: List<Int> = emptyList()) : Screen()
    data class AddParametro(val flota: Flota, val llantaId: Int) : Screen()
    data class EditParametro(val flota: Flota, val parametro: Parametro) : Screen()
    data class AsignarLlantas(val flota: Flota) : Screen()
    data class Agregar(val flota: Flota) : Screen()
    object LlantasAdmin : Screen()
    data class LlantaBitacora(val llantaVehiculo: com.megatransportes.yokoh.data.models.LlantaVehiculo) : Screen()
}
