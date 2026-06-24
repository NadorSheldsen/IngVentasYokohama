package com.megatransportes.yokoh.navigation

import androidx.compose.runtime.*
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.ui.screens.flotas.FlotaMenuScreen
import com.megatransportes.yokoh.ui.screens.semaforo.PruebasSemaforoListScreen
import com.megatransportes.yokoh.ui.screens.semaforo.SemaforoScreen
import com.megatransportes.yokoh.ui.screens.semaforo.PruebaSemaforoReportScreen

sealed class Screen {
    object FlotaMenu : Screen()
    object PruebasSemaforoList : Screen()
    object PruebaSemaforoReport : Screen()
    object Semaforo : Screen()
    object VehiculosSemaforoList : Screen()
    // Add other screens as needed
}

@Composable
fun FlotaNavigation(
    repository: YokohamaRepository,
    flota: Flota,
    onBack: () -> Unit
) {
    // Simple navigation stack so back pops to the immediate previous screen
    data class NavState(val screen: Screen, val prueba: PruebasSemaforo? = null, val vehiculo: VehiculoSemaforo? = null)

    val navStack = remember { mutableStateListOf(NavState(Screen.FlotaMenu)) }

    fun pushState(screen: Screen, prueba: PruebasSemaforo? = null, vehiculo: VehiculoSemaforo? = null) {
        navStack.add(NavState(screen, prueba, vehiculo))
    }

    fun popState() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.size - 1)
        } else {
            // if stack only has root, call external onBack
            onBack()
        }
    }

    val current = navStack.last()

    when (val screen = current.screen) {
        Screen.FlotaMenu -> {
            FlotaMenuScreen(
                repository = repository,
                flota = flota,
                onPruebasRendimientoClick = { /* TODO: Navigate to pruebas rendimiento */ },
                onSemaforosClick = { pushState(Screen.PruebasSemaforoList) },
                onInspeccionesClick = { /* TODO: Navigate to inspecciones */ },
                onPilasDesechoClick = { /* TODO: Navigate to pilas desecho */ },
                onParametrosClick = { /* TODO: Navigate to parametros */ },
                onParametrosGoToLlantas = { /* TODO: Navigate to parametros -> llantas tab */ },
                onAsignarLlantasClick = { /* TODO: Navigate to asignar llantas */ },
                onBack = onBack
            )
        }

        Screen.PruebasSemaforoList -> {
            PruebasSemaforoListScreen(
                repository = repository,
                flota = flota,
                onPruebaClick = { prueba ->
                    pushState(Screen.VehiculosSemaforoList, prueba = prueba)
                },
                onReportClick = { prueba ->
                    pushState(Screen.PruebaSemaforoReport, prueba = prueba)
                },
                onBack = { popState() }
            )
        }

        Screen.VehiculosSemaforoList -> {
            val prueba = current.prueba
            prueba?.let { pruebaNonNull ->
                com.megatransportes.yokoh.ui.screens.semaforo.VehiculosSemaforoListScreen(
                    repository = repository,
                    flota = flota,
                    pruebaSemaforo = pruebaNonNull,
                    onVehiculoClick = { veh ->
                        // Open editor for selected vehicle
                        pushState(Screen.Semaforo, prueba = pruebaNonNull, vehiculo = veh)
                    },
                    onAddVehiculo = {
                        // Open SemaforoScreen for creating a new vehicle
                        pushState(Screen.Semaforo, prueba = pruebaNonNull, vehiculo = null)
                    },
                    onBack = { popState() }
                )
            }
        }

        Screen.Semaforo -> {
            val prueba = current.prueba
            val veh = current.vehiculo
            prueba?.let { pruebaNonNull ->
                SemaforoScreen(
                    repository = repository,
                    flota = flota,
                    pruebaSemaforo = pruebaNonNull,
                    initialEditingVehiculoId = veh?.idVehiculoSemaforo,
                    onSemaforoRegistrado = { popState() },
                    onBack = { popState() }
                )
            }
        }
        Screen.PruebaSemaforoReport -> {
            val prueba = current.prueba
            prueba?.let { pruebaNonNull ->
                PruebaSemaforoReportScreen(
                    repository = repository,
                    flota = flota,
                    prueba = pruebaNonNull,
                    onBack = { popState() },
                    onHome = { /* go to root FlotaMenu */ while (navStack.size > 1) popState() }
                )
            }
        }
    }
}
