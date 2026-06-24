package com.megatransportes.yokoh.ui.screens.inspecciones



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.PruebaInspeccion

import com.megatransportes.yokoh.data.models.VehiculoInspeccion

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.utils.ErrorUtils

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun VehiculosInspeccionListScreen(

    repository: YokohamaRepository,

    pruebaInspeccion: PruebaInspeccion,

    flota: Flota,

    onVehiculoClick: (VehiculoInspeccion) -> Unit,

    onAddVehiculo: (PruebaInspeccion) -> Unit,

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    var vehiculos by remember { mutableStateOf<List<VehiculoInspeccion>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }



    // Permission check: manage vs delete permissions

    val currentUser by repository.currentUser.collectAsState()

    var hasManageVehiculosPermission by remember { mutableStateOf(false) }

    var hasDeleteVehiculoPermission by remember { mutableStateOf(false) }



    LaunchedEffect(currentUser) {

        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario

        if (perfilId != null) {

            try {

                val permisosResult = repository.getPermisosByPerfilId(perfilId)

                val permisosList = permisosResult.getOrNull().orEmpty()

                hasManageVehiculosPermission = permisosList.any { it.PermisosNombre == "Gestionar Vehículos" }

                // Deletion requires explicit "Eliminar vehículo" permission

                hasDeleteVehiculoPermission = permisosList.any { it.PermisosNombre == "Eliminar vehículo" }

            } catch (t: Throwable) {

                hasManageVehiculosPermission = false

                hasDeleteVehiculoPermission = false

            }

        } else {

            hasManageVehiculosPermission = false

            hasDeleteVehiculoPermission = false

        }

    }



    LaunchedEffect(pruebaInspeccion.idPruebaInspeccion) {

        coroutineScope.launch {

            isLoading = true

            errorMessage = null

            val res = repository.getVehiculosInspeccionByPruebaId(pruebaInspeccion.idPruebaInspeccion)

            res.onSuccess { list ->

                vehiculos = list

                isLoading = false

            }

            res.onFailure { err ->

                errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos de inspección")

                isLoading = false

            }

        }

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("Vehículos - ${pruebaInspeccion.PruebaInspeccionTitulo}") },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Regresar")

                    }

                },

                actions = {

                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Flota") }

                }

            )

        },

        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },

        floatingActionButton = {

            FloatingActionButton(

                onClick = { onAddVehiculo(pruebaInspeccion) },

                containerColor = MaterialTheme.colorScheme.primary,

                contentColor = MaterialTheme.colorScheme.onPrimary

            ) {

                Icon(Icons.Default.Add, contentDescription = "Agregar vehículo")

            }

        }

    ) { paddingValues ->

        Box(modifier = Modifier

            .fillMaxSize()

            .padding(paddingValues)) {

            when {

                isLoading -> {

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        CircularProgressIndicator()

                    }

                }

                errorMessage != null -> {

                    ErrorScreen(

                        errorMessage = errorMessage!!,

                        onRetry = {

                            coroutineScope.launch {

                                isLoading = true

                                errorMessage = null

                                val r = repository.getVehiculosInspeccionByPruebaId(pruebaInspeccion.idPruebaInspeccion)

                                r.onSuccess { vehiculos = it; isLoading = false }

                                r.onFailure { e -> errorMessage = ErrorUtils.userMessage(e, "Error cargando vehículos"); isLoading = false }

                            }

                        }

                    )

                }

                vehiculos.isEmpty() -> {

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            Text("No hay vehículos para esta prueba")

                            Text("Usa + para agregar uno")

                        }

                    }

                }

                else -> {

                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        items(vehiculos) { veh ->

                            var showConfirm by remember { mutableStateOf(false) }

                            Card(onClick = { onVehiculoClick(veh) }, modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {

                                        Column(modifier = Modifier.padding(16.dp)) {

                                            // Title row with optional delete button

                                            Box(modifier = Modifier.fillMaxWidth()) {

                                                Text(

                                                    text = veh.VehiculoInspeccionNo,

                                                    style = MaterialTheme.typography.headlineSmall,

                                                    modifier = Modifier.align(Alignment.CenterStart)

                                                )



                                                if (hasDeleteVehiculoPermission) {

                                                    IconButton(onClick = { showConfirm = true }, modifier = Modifier.align(Alignment.TopEnd)) {

                                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar vehiculo inspeccion")

                                                    }

                                                }

                                            }



                                            if (!veh.TipoVehiculosNombre.isNullOrBlank()) {

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Text(

                                                    text = veh.TipoVehiculosNombre ?: "",

                                                    style = MaterialTheme.typography.bodyMedium,

                                                    color = MaterialTheme.colorScheme.onSurfaceVariant

                                                )

                                            }



                                            if (showConfirm) {

                                                AlertDialog(

                                                    onDismissRequest = { showConfirm = false },

                                                    title = { Text("Eliminar vehículo") },

                                                    text = { Text("¿Seguro que desea eliminar el vehículo ${veh.VehiculoInspeccionNo}? Esta acción no se puede deshacer.") },

                                                    confirmButton = {

                                                        TextButton(onClick = {

                                                            showConfirm = false

                                                            coroutineScope.launch {

                                                                repository.deleteVehiculoInspeccion(veh.idVehiculoInspeccion)

                                                                    .onSuccess {

                                                                        vehiculos = vehiculos.filter { it.idVehiculoInspeccion != veh.idVehiculoInspeccion }

                                                                        snackbarHostState.showSnackbar("Vehículo eliminado")

                                                                    }

                                                                    .onFailure { err ->

                                                                        snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error eliminando vehículo"))

                                                                    }

                                                            }

                                                        }) { Text("Eliminar") }

                                                    },

                                                    dismissButton = {

                                                        TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }

                                                    }

                                                )

                                            }

                                        }

                            }

                        }

                    }

                }

            }

        }

    }

}

