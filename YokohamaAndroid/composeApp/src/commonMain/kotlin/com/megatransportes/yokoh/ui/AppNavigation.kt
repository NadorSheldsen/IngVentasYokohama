package com.megatransportes.yokoh.ui



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.material.ExperimentalMaterialApi

import androidx.compose.material.pullrefresh.PullRefreshIndicator

import androidx.compose.material.pullrefresh.pullRefresh

import androidx.compose.material.pullrefresh.rememberPullRefreshState

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

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

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterialApi::class)

@Composable

fun AppNavigation(

    repository: YokohamaRepository,

    navigator: Navigator,

    isDarkTheme: Boolean,

    onToggleTheme: () -> Unit

) {

    // Observe navigator.currentScreen (state) and render accordingly

    val screen by remember { derivedStateOf { navigator.currentScreen } }

    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }

    var refreshTick by remember { mutableStateOf(0) }

    val pullRefreshState = rememberPullRefreshState(

        refreshing = isRefreshing,

        onRefresh = {

            isRefreshing = true

            refreshTick += 1

            scope.launch {

                // Keep indicator visible briefly so user perceives refresh action.

                delay(600)

                isRefreshing = false

            }

        }

    )



    Box(

        modifier = Modifier

            .fillMaxSize()

            .navigationBarsPadding()

            .imePadding()

            .pullRefresh(pullRefreshState)

    ) {

        key(screen, refreshTick) {

            when (val screen = screen) {

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

                usuario = screen.usuario,

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

                perfil = screen.perfil,

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

                flota = screen.flota,

                onPruebasRendimientoClick = { navigator.navigate(Screen.Vehiculos(screen.flota)) },

                onSemaforosClick = { navigator.navigate(Screen.PruebasSemaforoList(screen.flota)) },

                onInspeccionesClick = { navigator.navigate(Screen.PruebasInspeccionList(screen.flota)) },

                onPilasDesechoClick = { navigator.navigate(Screen.PilasDesecho(screen.flota)) },

                onParametrosClick = { navigator.navigate(Screen.ParametrosList(screen.flota)) },

                onParametrosGoToLlantas = { navigator.navigate(Screen.ParametrosList(screen.flota, initialTab = 1)) },

                onBack = back,

                // Add navigation to AsignarLlantas

                onAsignarLlantasClick = { navigator.navigate(Screen.Agregar(screen.flota)) }

            )

        }



        is Screen.FlotaUsuarios -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            FlotaUsuariosScreen(

                repository = repository,

                flota = screen.flota,

                onBack = back

            )

        }



        is Screen.Agregar -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            AgregarScreen(

                repository = repository,

                flota = screen.flota,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }



        is Screen.AsignarLlantas -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            com.megatransportes.yokoh.ui.screens.flotas.AsignarLlantasScreen(

                repository = repository,

                flotaId = screen.flota.idFlotas,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) },

                onLlantaAssigned = { llantaId ->

                    // Open AddParametro directly so user can fill the parameters for the assigned llanta

                    navigator.navigate(Screen.AddParametro(screen.flota, llantaId))

                }

            )

        }

        is Screen.Vehiculos -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            VehiculosScreen(

                repository = repository,

                flota = screen.flota,

                onAddVehiculoClick = { navigator.navigate(Screen.AddVehiculo(screen.flota)) },

                onVehiculoClick = { vehiculo, cantidadLlantas -> 

                    navigator.navigate(Screen.LlantasVehiculo(screen.flota, vehiculo, cantidadLlantas)) 

                },

                onPruebaRendimientoClick = { vehiculo, llantas -> 

                    navigator.navigate(Screen.PruebaRendimiento(screen.flota, vehiculo, llantas)) 

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.AddVehiculo -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            AddVehiculoScreen(

                repository = repository,

                flota = screen.flota,

                onVehiculoCreated = { navigator.pop() },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.LlantasVehiculo -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            LlantasVehiculoScreen(

                repository = repository,

                flota = screen.flota,

                vehiculo = screen.vehiculo,

                cantidadLlantas = screen.cantidadLlantas,

                onLlantasRegistradas = { 

                    // Go back to the existing Vehiculos screen instead of pushing a new one

                    navigator.pop()

                },

                onBack = back,

                onOpenParametrosList = { llantaIds ->

                    navigator.navigate(Screen.ParametrosList(screen.flota, suggestedLlantaIds = llantaIds))

                }

            )

        }

        is Screen.PruebaRendimiento -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            val flota = screen.flota

            PruebaRendimientoScreen(

                repository = repository,

                flota = flota,

                vehiculo = screen.vehiculo,

                llantasVehiculo = screen.llantasVehiculo,

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

                flota = screen.flota,

                onPruebaClick = { pruebaSemaforo ->

                    navigator.navigate(Screen.VehiculosSemaforoList(screen.flota, pruebaSemaforo))

                },

                onReportClick = { pruebaSemaforo ->

                    navigator.navigate(Screen.PruebaSemaforoReport(screen.flota, pruebaSemaforo))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }



        is Screen.PruebaSemaforoReport -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebaSemaforoReportScreen(

                repository = repository,

                flota = screen.flota,

                prueba = screen.prueba,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }



        is Screen.VehiculosSemaforoList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

                com.megatransportes.yokoh.ui.screens.semaforo.VehiculosSemaforoListScreen(

                repository = repository,

                flota = screen.flota,

                pruebaSemaforo = screen.pruebaSemaforo,

                onVehiculoClick = { veh ->

                    navigator.navigate(Screen.Semaforo(screen.flota, screen.pruebaSemaforo, veh.idVehiculoSemaforo))

                },

                onAddVehiculo = {

                    navigator.navigate(Screen.Semaforo(screen.flota, screen.pruebaSemaforo, null))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }



        is Screen.Semaforo -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            SemaforoScreen(

                repository = repository,

                flota = screen.flota,

                pruebaSemaforo = screen.pruebaSemaforo,

                initialEditingVehiculoId = screen.initialEditingVehiculoId,

                onSemaforoRegistrado = { navigator.pop() },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) },

                onOpenLlantasAdmin = { navigator.navigate(Screen.LlantasAdmin) },

                onOpenAddParametro = { llantaId -> navigator.navigate(Screen.AddParametro(screen.flota, llantaId)) },

                onOpenParametrosList = { _ -> navigator.navigate(Screen.ParametrosList(screen.flota)) }

            )

        }

        is Screen.PilasDesecho -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PilasDesechoScreen(

                repository = repository,

                flota = screen.flota,

                onPilaSelected = { prueba ->

                    navigator.navigate(Screen.LlantasDesechoScreen(prueba, screen.flota))

                },

                onReportClick = { prueba -> navigator.navigate(Screen.PruebaDesechoReport(screen.flota, prueba)) },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }



        is Screen.PruebaDesechoReport -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebaDesechoReportScreen(

                repository = repository,

                flota = screen.flota,

                prueba = screen.prueba,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        

        is Screen.LlantasDesechoScreen -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            LlantasDesechoScreen(

                repository = repository,

                pruebaDesecho = screen.pruebaDesecho,

                flota = screen.flota,

                onNuevaLlantaClick = { 

                    navigator.navigate(Screen.NuevaLlantaDesechoScreen(screen.pruebaDesecho, screen.flota, null)) 

                },

                onLlantaClick = { llanta ->

                    navigator.navigate(Screen.NuevaLlantaDesechoScreen(screen.pruebaDesecho, screen.flota, llanta))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.NuevaLlantaDesechoScreen -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            NuevaLlantaDesechoScreen(

                repository = repository,

                pruebaDesecho = screen.pruebaDesecho,

                flota = screen.flota,

                existingLlanta = screen.existing,

                onLlantaCreada = {

                    // Replace the current screen with a fresh LlantasDesechoScreen so the list reloads

                    navigator.replace(Screen.LlantasDesechoScreen(screen.pruebaDesecho, screen.flota))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.PruebasInspeccionList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebasInspeccionListScreen(

                repository = repository,

                flota = screen.flota,

                onPruebaClick = { pruebaInspeccion ->

                    // Navigate to intermediate list of vehicles for the selected inspección

                    navigator.navigate(Screen.VehiculosInspeccionList(pruebaInspeccion, screen.flota))

                },

                onReportClick = { pruebaInspeccion ->

                    navigator.navigate(Screen.PruebaInspeccionReport(screen.flota, pruebaInspeccion))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }



        is Screen.PruebaInspeccionReport -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            PruebaInspeccionReportScreen(

                repository = repository,

                flota = screen.flota,

                prueba = screen.prueba,

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.VehiculosInspeccionList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

                com.megatransportes.yokoh.ui.screens.inspecciones.VehiculosInspeccionListScreen(

                repository = repository,

                pruebaInspeccion = screen.pruebaInspeccion,

                flota = screen.flota,

                onVehiculoClick = { veh ->

                    // Open the InspeccionVehicularScreen to edit the selected vehiculoInspeccion

                    navigator.navigate(Screen.InspeccionVehicularScreen(screen.pruebaInspeccion, screen.flota, veh))

                },

                onAddVehiculo = { prueba ->

                    // Open InspeccionVehicularScreen to create a new vehicle for this prueba (no existing veh)

                    navigator.navigate(Screen.InspeccionVehicularScreen(prueba, screen.flota, null))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.InspeccionVehicularScreen -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            InspeccionVehicularScreen(

                repository = repository,

                pruebaInspeccion = screen.pruebaInspeccion,

                flota = screen.flota,

                vehiculoInspeccionExisting = screen.vehiculoInspeccion,

                onInspeccionRegistrada = {

                    // After creating/updating a vehicle inspection, go back to the

                    // list of vehicles for this prueba so the user can see the newly added item.

                    // Use replace so the list reloads fresh instead of stacking screens.

                    navigator.replace(Screen.VehiculosInspeccionList(screen.pruebaInspeccion, screen.flota))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) },

                onOpenLlantasAdmin = { navigator.navigate(Screen.LlantasAdmin) },

                onOpenAddParametro = { llantaId -> navigator.navigate(Screen.AddParametro(screen.flota, llantaId)) },

                onOpenParametrosList = { _ -> navigator.navigate(Screen.ParametrosList(screen.flota)) }

            )

        }

        is Screen.ParametrosList -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            ParametrosListScreen(

                repository = repository,

                flota = screen.flota,

                onParametroClick = { parametro ->

                    navigator.navigate(Screen.EditParametro(screen.flota, parametro))

                },

                onAddParametroClick = { llantaId ->

                    navigator.navigate(Screen.AddParametro(screen.flota, llantaId))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) },

                suggestedLlantaIds = screen.suggestedLlantaIds

            )

        }

        is Screen.AddParametro -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            EditParametroScreen(

                repository = repository,

                flota = screen.flota,

                llantaId = screen.llantaId,

                parametro = null,

                onParametroSaved = { 

                    navigator.navigate(Screen.ParametrosList(screen.flota))

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

            )

        }

        is Screen.EditParametro -> {

            val back: () -> Unit = { navigator.pop(); Unit }

            navigator.setBackHandler(back)

            EditParametroScreen(

                repository = repository,

                flota = screen.flota,

                llantaId = screen.parametro.Llantas_idLlantas,

                parametro = screen.parametro,

                onParametroSaved = { 

                    navigator.navigate(Screen.ParametrosList(screen.flota)) 

                },

                onBack = back,

                onHome = { navigator.navigate(Screen.FlotaMenu(screen.flota)) }

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

                llantaVehiculo = screen.llantaVehiculo,

                onBack = back

            )

        }

            }

        }



        PullRefreshIndicator(

            refreshing = isRefreshing,

            state = pullRefreshState,

            modifier = Modifier.align(Alignment.TopCenter)

        )

    }

}