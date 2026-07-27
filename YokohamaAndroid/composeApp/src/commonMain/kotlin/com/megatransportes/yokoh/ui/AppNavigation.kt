package com.megatransportes.yokoh.ui



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.material.ExperimentalMaterialApi

import androidx.compose.material.pullrefresh.pullRefresh

import androidx.compose.material.pullrefresh.rememberPullRefreshState

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.Vehiculo

import com.megatransportes.yokoh.data.models.LlantaVehiculo

import com.megatransportes.yokoh.data.models.PruebasDesecho

import com.megatransportes.yokoh.data.models.PruebasSemaforo

import com.megatransportes.yokoh.data.models.Usuario

import com.megatransportes.yokoh.data.models.PerfilesUsuario

import com.megatransportes.yokoh.data.models.PruebaInspeccion

import com.megatransportes.yokoh.data.models.Parametro

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.screens.login.LoginScreen

import com.megatransportes.yokoh.ui.screens.flotas.FlotasScreen

import com.megatransportes.yokoh.ui.screens.flotas.AddFlotaScreen

import com.megatransportes.yokoh.ui.screens.flotas.FlotaMenuScreen

import com.megatransportes.yokoh.ui.screens.flotas.AgregarScreen

import com.megatransportes.yokoh.ui.screens.flotas.FlotaUsuariosScreen

import com.megatransportes.yokoh.ui.screens.usuarios.UsuariosScreen

import com.megatransportes.yokoh.ui.screens.usuarios.AddUsuarioScreen

import com.megatransportes.yokoh.ui.screens.usuarios.EditUsuarioScreen

import com.megatransportes.yokoh.ui.screens.usuarios.PerfilesUsuarioListScreen

import com.megatransportes.yokoh.ui.screens.usuarios.EditPerfilUsuarioScreen

import com.megatransportes.yokoh.ui.screens.vehiculos.VehiculosScreen

import com.megatransportes.yokoh.ui.screens.vehiculos.AddVehiculoScreen

import com.megatransportes.yokoh.ui.screens.vehiculos.LlantasVehiculoScreen

import com.megatransportes.yokoh.ui.screens.vehiculos.PruebaRendimientoScreen

import com.megatransportes.yokoh.ui.screens.semaforo.SemaforoScreen

import com.megatransportes.yokoh.ui.screens.semaforo.PruebasSemaforoListScreen

import com.megatransportes.yokoh.ui.screens.semaforo.PruebaSemaforoReportScreen

import com.megatransportes.yokoh.ui.screens.desecho.PilasDesechoScreen

import com.megatransportes.yokoh.ui.screens.desecho.PruebaDesechoReportScreen

import com.megatransportes.yokoh.ui.screens.desecho.LlantasDesechoScreen

import com.megatransportes.yokoh.ui.screens.desecho.NuevaLlantaDesechoScreen



import com.megatransportes.yokoh.ui.screens.inspecciones.PruebasInspeccionListScreen

import com.megatransportes.yokoh.ui.screens.inspecciones.InspeccionVehicularScreen

import com.megatransportes.yokoh.ui.screens.inspecciones.PruebaInspeccionReportScreen

import com.megatransportes.yokoh.ui.screens.parametros.ParametrosListScreen

import com.megatransportes.yokoh.ui.screens.parametros.EditParametroScreen





@OptIn(ExperimentalMaterialApi::class)
@Composable

fun AppNavigation(

    repository: YokohamaRepository,

    navigator: Navigator,

    isDarkTheme: Boolean,

    onToggleTheme: () -> Unit

) {

    val screen by remember { derivedStateOf { navigator.currentScreen } }

    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }

    var refreshTick by remember { mutableStateOf(0) }

    val pullState = rememberPullRefreshState(

        refreshing = isRefreshing,

        onRefresh = {

            scope.launch {

                isRefreshing = true

                refreshTick += 1

                delay(1000)

                isRefreshing = false

            }

        }

    )

    Box(

        modifier = Modifier

            .fillMaxSize()

            .navigationBarsPadding()

            .imePadding()

    ) {
        key(screen to refreshTick) {
            key(isRefreshing) {
            Box(

                modifier = Modifier

                    .fillMaxSize()

                    .pullRefresh(pullState)

            ) {
                key(screen) {
                val s = screen
                when (s) {

        is Screen.Login -> {

            LoginScreen(

                repository = repository,

                onLoginSuccess = { navigator.setRoot(Screen.Flotas) }

            )

        }

        is Screen.Flotas -> {

            FlotasScreen(

                repository = repository,

                onFlotaSelected = { flota -> navigator.navigate(Screen.FlotaMenu(flota)) },

                onAddFlotaClick = { navigator.navigate(Screen.AddFlota) },

                onFlotaUsuariosClick = { flota -> navigator.navigate(Screen.FlotaUsuarios(flota)) },

                onNavigateToUsuarios = { navigator.navigate(Screen.Usuarios) },

                isDarkTheme = isDarkTheme,

                onToggleTheme = onToggleTheme,

                onLogout = { 

                    repository.logout()

                    navigator.setRoot(Screen.Login)

                }

            )

        }

        is Screen.AddFlota -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            AddFlotaScreen(

                repository = repository,

                onFlotaCreated = { navigator.pop() },

                onBack = back

            )

        }

        is Screen.Usuarios -> {

            UsuariosScreen(

                repository = repository,

                onUsuarioSelected = { usuario -> 

                    navigator.navigate(Screen.EditUsuario(usuario))

                },

                onAddUsuarioClick = { navigator.navigate(Screen.AddUsuario) },

                onNavigateToFlotas = { navigator.navigate(Screen.Flotas) },

                isDarkTheme = isDarkTheme,

                onToggleTheme = onToggleTheme,

                onLogout = { 

                    repository.logout()

                    navigator.setRoot(Screen.Login)

                }

            )

        }

        is Screen.AddUsuario -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            AddUsuarioScreen(

                repository = repository,

                onUsuarioCreated = { navigator.pop() },

                onBack = back

            )

        }

        is Screen.EditUsuario -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            EditUsuarioScreen(

                repository = repository,

                usuario = s.usuario,

                onUsuarioUpdated = { navigator.pop() },

                onPerfilesUsuarioClick = { navigator.navigate(Screen.PerfilesUsuarioList) },

                onBack = back

            )

        }

        is Screen.PerfilesUsuarioList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PerfilesUsuarioListScreen(

                repository = repository,

                onPerfilClick = { perfil -> 

                    navigator.navigate(Screen.EditPerfilUsuario(perfil))

                },

                onAddPerfilClick = { 

                    navigator.navigate(Screen.EditPerfilUsuario(null)) 

                },

                onBack = back

            )

        }

        is Screen.EditPerfilUsuario -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            EditPerfilUsuarioScreen(

                repository = repository,

                perfil = s.perfil,

                onPerfilSaved = { navigator.pop() },

                onBack = back

            )

        }

        is Screen.FlotaMenu -> {

            // Ensure that pressing back from the Flota menu always returns to the Flotas list

            val back: () -> Unit = { navigator.setRoot(Screen.Flotas); Unit }

            // Register the back handler so hardware/system back will also follow this behavior

            navigator.setBackHandler(back)

            FlotaMenuScreen(

                repository = repository,

                flota = s.flota,

                onPruebasRendimientoClick = { navigator.navigate(Screen.Vehiculos(s.flota)) },

                onSemaforosClick = { navigator.navigate(Screen.PruebasSemaforoList(s.flota)) },

                onInspeccionesClick = { navigator.navigate(Screen.PruebasInspeccionList(s.flota)) },

                onPilasDesechoClick = { navigator.navigate(Screen.PilasDesecho(s.flota)) },

                onParametrosClick = { navigator.navigate(Screen.ParametrosList(s.flota)) },

                onParametrosGoToLlantas = { navigator.navigate(Screen.ParametrosList(s.flota, initialTab = 1)) },

                onBack = back,

                // Add navigation to AsignarLlantas

                onAsignarLlantasClick = { navigator.navigate(Screen.Agregar(s.flota)) }

            )

        }



        is Screen.FlotaUsuarios -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            FlotaUsuariosScreen(

                repository = repository,

                flota = s.flota,

                onBack = back

            )

        }



        is Screen.Agregar -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            AgregarScreen(

                repository = repository,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }



        is Screen.AsignarLlantas -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            com.megatransportes.yokoh.ui.screens.flotas.AsignarLlantasScreen(

                repository = repository,

                flotaId = s.flota.idFlotas,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) },

                onLlantaAssigned = { llantaId ->

                    // Open AddParametro directly so user can fill the parameters for the assigned llanta

                    navigator.navigate(Screen.AddParametro(s.flota, llantaId))

                }

            )

        }

        is Screen.Vehiculos -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            VehiculosScreen(

                repository = repository,

                flota = s.flota,

                onAddVehiculoClick = { navigator.navigate(Screen.AddVehiculo(s.flota)) },

                onVehiculoClick = { vehiculo, cantidadLlantas -> 

                    navigator.navigate(Screen.LlantasVehiculo(s.flota, vehiculo, cantidadLlantas)) 

                },

                onPruebaRendimientoClick = { vehiculo, llantas -> 

                    navigator.navigate(Screen.PruebaRendimiento(s.flota, vehiculo, llantas)) 

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.AddVehiculo -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            AddVehiculoScreen(

                repository = repository,

                flota = s.flota,

                onVehiculoCreated = { navigator.pop() },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.LlantasVehiculo -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            LlantasVehiculoScreen(

                repository = repository,

                flota = s.flota,

                vehiculo = s.vehiculo,

                cantidadLlantas = s.cantidadLlantas,

                onLlantasRegistradas = { 

                    // Go back to the existing Vehiculos screen instead of pushing a new one

                    navigator.pop()

                },

                onBack = back,

                onOpenParametrosList = { llantaIds ->

                    navigator.navigate(Screen.ParametrosList(s.flota, suggestedLlantaIds = llantaIds))

                }

            )

        }

        is Screen.PruebaRendimiento -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            val flota = s.flota

            PruebaRendimientoScreen(

                repository = repository,

                flota = flota,

                vehiculo = s.vehiculo,

                llantasVehiculo = s.llantasVehiculo,

                onPruebaRegistrada = {

                    // Return to the existing Vehiculos screen instead of pushing a new one

                    navigator.pop()

                },

                onBack = back,

                onOpenBitacora = { llantaVeh ->

                    navigator.navigate(Screen.LlantaBitacora(llantaVeh))

                },

                onOpenLlantasAdmin = { navigator.navigate(Screen.LlantasAdmin) },

                onHome = { navigator.navigate(Screen.FlotaMenu(flota)) }

            )

        }

        is Screen.PruebasSemaforoList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

                PruebasSemaforoListScreen(

                repository = repository,

                flota = s.flota,

                onPruebaClick = { pruebaSemaforo ->

                    navigator.navigate(Screen.VehiculosSemaforoList(s.flota, pruebaSemaforo))

                },

                onReportClick = { pruebaSemaforo ->

                    navigator.navigate(Screen.PruebaSemaforoReport(s.flota, pruebaSemaforo))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }



        is Screen.PruebaSemaforoReport -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebaSemaforoReportScreen(

                repository = repository,

                flota = s.flota,

                prueba = s.prueba,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }



        is Screen.VehiculosSemaforoList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

                com.megatransportes.yokoh.ui.screens.semaforo.VehiculosSemaforoListScreen(

                repository = repository,

                pruebaSemaforo = s.pruebaSemaforo,

                onVehiculoClick = { veh ->

                    navigator.navigate(Screen.Semaforo(s.flota, s.pruebaSemaforo, veh.idVehiculoSemaforo))

                },

                onAddVehiculo = {

                    navigator.navigate(Screen.Semaforo(s.flota, s.pruebaSemaforo, null))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }



        is Screen.Semaforo -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            SemaforoScreen(

                repository = repository,

                flota = s.flota,

                pruebaSemaforo = s.pruebaSemaforo,

                initialEditingVehiculoId = s.initialEditingVehiculoId,

                onSemaforoRegistrado = { navigator.pop() },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) },

                onOpenLlantasAdmin = { navigator.navigate(Screen.LlantasAdmin) },

                onOpenAddParametro = { llantaId -> navigator.navigate(Screen.AddParametro(s.flota, llantaId)) },

                onOpenParametrosList = { _ -> navigator.navigate(Screen.ParametrosList(s.flota)) }

            )

        }

        is Screen.PilasDesecho -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PilasDesechoScreen(

                repository = repository,

                flota = s.flota,

                onPilaSelected = { prueba ->

                    navigator.navigate(Screen.LlantasDesechoScreen(prueba, s.flota))

                },

                onReportClick = { prueba -> navigator.navigate(Screen.PruebaDesechoReport(s.flota, prueba)) },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }



        is Screen.PruebaDesechoReport -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebaDesechoReportScreen(

                repository = repository,

                flota = s.flota,

                prueba = s.prueba,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        

        is Screen.LlantasDesechoScreen -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            LlantasDesechoScreen(

                repository = repository,

                pruebaDesecho = s.pruebaDesecho,

                onNuevaLlantaClick = { 

                    navigator.navigate(Screen.NuevaLlantaDesechoScreen(s.pruebaDesecho, s.flota, null)) 

                },

                onLlantaClick = { llanta ->

                    navigator.navigate(Screen.NuevaLlantaDesechoScreen(s.pruebaDesecho, s.flota, llanta))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.NuevaLlantaDesechoScreen -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            NuevaLlantaDesechoScreen(

                repository = repository,

                pruebaDesecho = s.pruebaDesecho,

                existingLlanta = s.existing,

                onLlantaCreada = {

                    // Replace the current screen with a fresh LlantasDesechoScreen so the list reloads

                    navigator.replace(Screen.LlantasDesechoScreen(s.pruebaDesecho, s.flota))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.PruebasInspeccionList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebasInspeccionListScreen(

                repository = repository,

                flota = s.flota,

                onPruebaClick = { pruebaInspeccion ->

                    // Navigate to intermediate list of vehicles for the selected inspección

                    navigator.navigate(Screen.VehiculosInspeccionList(pruebaInspeccion, s.flota))

                },

                onReportClick = { pruebaInspeccion ->

                    navigator.navigate(Screen.PruebaInspeccionReport(s.flota, pruebaInspeccion))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }



        is Screen.PruebaInspeccionReport -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebaInspeccionReportScreen(

                repository = repository,

                flota = s.flota,

                prueba = s.prueba,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.VehiculosInspeccionList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

                com.megatransportes.yokoh.ui.screens.inspecciones.VehiculosInspeccionListScreen(

                repository = repository,

                pruebaInspeccion = s.pruebaInspeccion,

                onVehiculoClick = { veh ->

                    // Open the InspeccionVehicularScreen to edit the selected vehiculoInspeccion

                    navigator.navigate(Screen.InspeccionVehicularScreen(s.pruebaInspeccion, s.flota, veh))

                },

                onAddVehiculo = { prueba ->

                    // Open InspeccionVehicularScreen to create a new vehicle for this prueba (no existing veh)

                    navigator.navigate(Screen.InspeccionVehicularScreen(prueba, s.flota, null))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.InspeccionVehicularScreen -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            InspeccionVehicularScreen(

                repository = repository,

                pruebaInspeccion = s.pruebaInspeccion,

                flota = s.flota,

                vehiculoInspeccionExisting = s.vehiculoInspeccion,

                onInspeccionRegistrada = {

                    // After creating/updating a vehicle inspection, go back to the

                    // list of vehicles for this prueba so the user can see the newly added item.

                    // Use replace so the list reloads fresh instead of stacking screens.

                    navigator.replace(Screen.VehiculosInspeccionList(s.pruebaInspeccion, s.flota))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) },

                onOpenLlantasAdmin = { navigator.navigate(Screen.LlantasAdmin) },

                onOpenAddParametro = { llantaId -> navigator.navigate(Screen.AddParametro(s.flota, llantaId)) },

                onOpenParametrosList = { _ -> navigator.navigate(Screen.ParametrosList(s.flota)) }

            )

        }

        is Screen.ParametrosList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            ParametrosListScreen(

                repository = repository,

                flota = s.flota,

                onParametroClick = { parametro ->

                    navigator.navigate(Screen.EditParametro(s.flota, parametro))

                },

                onAddParametroClick = { llantaId ->

                    navigator.navigate(Screen.AddParametro(s.flota, llantaId))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) },

                suggestedLlantaIds = s.suggestedLlantaIds

            )

        }

        is Screen.AddParametro -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            EditParametroScreen(

                repository = repository,

                flota = s.flota,

                llantaId = s.llantaId,

                parametro = null,

                onParametroSaved = { 

                    navigator.navigate(Screen.ParametrosList(s.flota))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.EditParametro -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            EditParametroScreen(

                repository = repository,

                flota = s.flota,

                llantaId = s.parametro.Llantas_idLlantas,

                parametro = s.parametro,

                onParametroSaved = { 

                    navigator.navigate(Screen.ParametrosList(s.flota)) 

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(s.flota)) }

            )

        }

        is Screen.LlantasAdmin -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            com.megatransportes.yokoh.ui.screens.parametros.LlantasAdminScreen(

                repository = repository,

                onBack = back

            )

        }

        is Screen.LlantaBitacora -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            com.megatransportes.yokoh.ui.screens.vehiculos.LlantaBitacoraScreen(

                repository = repository,

                llantaVehiculo = s.llantaVehiculo,

                onBack = back

            )

        }

        }

        }

        }

        val progress = pullState.progress
        if (progress > 0f || isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

    }

}