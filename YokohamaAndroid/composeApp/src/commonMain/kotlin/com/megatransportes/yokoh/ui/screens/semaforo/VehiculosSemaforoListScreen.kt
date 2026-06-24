package com.megatransportes.yokoh.ui.screens.semaforo



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp

import com.megatransportes.yokoh.data.models.*

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.utils.ErrorUtils

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun VehiculosSemaforoListScreen(

    repository: YokohamaRepository,

    flota: Flota,

    pruebaSemaforo: PruebasSemaforo,

    onVehiculoClick: (VehiculoSemaforo) -> Unit,

    onAddVehiculo: () -> Unit,

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    var vehiculos by remember { mutableStateOf<List<VehiculoSemaforo>>(emptyList()) }

    var tipoVehiculos by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }

    // mapa vehiculoId -> resumenColor ("Verde"/"Amarillo"/"Rojo")

    var vehiculoColor by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }



    // Permission check for managing vehicles (including deletion)

    val currentUser by repository.currentUser.collectAsState()

    var hasManageVehiculosPermission by remember { mutableStateOf(false) }



    LaunchedEffect(currentUser) {

        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario

        if (perfilId != null) {

            try {

                val permisosResult = repository.getPermisosByPerfilId(perfilId)

                val permisosList = permisosResult.getOrNull().orEmpty()

                hasManageVehiculosPermission = permisosList.any { it.PermisosNombre == "Gestionar Vehículos" }

            } catch (t: Throwable) {

                hasManageVehiculosPermission = false

            }

        } else {

            hasManageVehiculosPermission = false

        }

    }



    LaunchedEffect(pruebaSemaforo.idPruebasSemaforo) {

        coroutineScope.launch {

            // Cargar tipos para mostrar el nombre del tipo de vehículo

            repository.getTiposVehiculos()

                .onSuccess { list -> tipoVehiculos = list }



            repository.getVehiculosSemaforoByPruebaId(pruebaSemaforo.idPruebasSemaforo)

                .onSuccess { list ->

                    vehiculos = list

                    isLoading = false



                    // Prefetch llantas semáforo por vehículo para calcular color resumen

                    val colorMap = mutableMapOf<Int, String>()

                    for (v in list) {

                        try {

                            val res = repository.getLlantasSemaforoByVehiculoId(v.idVehiculoSemaforo)

                            res.onSuccess { llList ->

                                // Determinar color: Rojo > Amarillo > Verde

                                val colors = llList.mapNotNull { it.LlantasSemaforoColor }

                                val summary = when {

                                    colors.any { it.equals("Rojo", true) } -> "Rojo"

                                    colors.any { it.equals("Amarillo", true) } -> "Amarillo"

                                    colors.any { it.equals("Verde", true) } -> "Verde"

                                    else -> "-"

                                }

                                colorMap[v.idVehiculoSemaforo] = summary

                            }

                        } catch (e: Exception) {

                            // ignore per-vehicle errors

                        }

                    }

                    vehiculoColor = colorMap.toMap()

                }

                .onFailure { err -> errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos"); isLoading = false }

        }

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("Vehículos - ${pruebaSemaforo.PruebasSemaforoTitulo}") },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Regresar")

                    }

                },

                actions = {

                    IconButton(onClick = onHome) {

                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")

                    }

                }

            )

        },

        floatingActionButtonPosition = FabPosition.End,

        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },

        floatingActionButton = {

            FloatingActionButton(

                onClick = onAddVehiculo,

                containerColor = MaterialTheme.colorScheme.primary

            ) {

                Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar vehículo", tint = MaterialTheme.colorScheme.onPrimary)

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

                                repository.getVehiculosSemaforoByPruebaId(pruebaSemaforo.idPruebasSemaforo)

                                    .onSuccess { list -> vehiculos = list; isLoading = false }

                                    .onFailure { err -> errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos"); isLoading = false }

                            }

                        }

                    )

                }

                vehiculos.isEmpty() -> {

                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

                        Text("No hay vehículos para esta prueba")

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Pulsa + para agregar uno")

                    }

                }

                else -> {

                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                items(vehiculos, key = { it.idVehiculoSemaforo }) { veh ->

                                    val typeName = tipoVehiculos.firstOrNull { it.idTipoVehiculos == veh.TipoVehiculos_idTipoVehiculos }?.TipoVehiculosNombre

                                    val summaryColor = vehiculoColor[veh.idVehiculoSemaforo] ?: "-"

                                    Card(modifier = Modifier.fillMaxWidth(), onClick = { onVehiculoClick(veh) }, elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {

                                        Column(modifier = Modifier.padding(12.dp)) {

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {

                                                Column {

                                                    Text(text = veh.VehiculoSemaforoNo ?: "-", style = MaterialTheme.typography.titleMedium)

                                                    if (typeName != null) {

                                                        Text(text = typeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                    }

                                                }



                                                Row(verticalAlignment = Alignment.CenterVertically) {

                                                    // Color summary chip

                                                    Box(modifier = Modifier

                                                        .size(28.dp)

                                                        .padding(start = 8.dp), contentAlignment = Alignment.Center) {

                                                        val color = when (summaryColor) {

                                                            "Rojo" -> Color.Red

                                                            "Amarillo" -> Color.Yellow

                                                            "Verde" -> Color.Green

                                                            else -> Color.LightGray

                                                        }

                                                        Surface(shape = MaterialTheme.shapes.small, color = color) {

                                                            Box(modifier = Modifier.size(28.dp)) {}

                                                        }

                                                    }



                                                    if (hasManageVehiculosPermission) {

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        var showConfirm by remember { mutableStateOf(false) }

                                                        IconButton(onClick = { showConfirm = true }) {

                                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar vehículo semáforo")

                                                        }

                                                        if (showConfirm) {

                                                            AlertDialog(

                                                                onDismissRequest = { showConfirm = false },

                                                                title = { Text("Eliminar vehículo") },

                                                                text = { Text("¿Seguro que desea eliminar el vehículo ${veh.VehiculoSemaforoNo ?: ""}? Esta acción no se puede deshacer.") },

                                                                confirmButton = {

                                                                    TextButton(onClick = {

                                                                        showConfirm = false

                                                                        coroutineScope.launch {

                                                                            repository.deleteVehiculoSemaforo(veh.idVehiculoSemaforo)

                                                                                .onSuccess {

                                                                                    vehiculos = vehiculos.filter { it.idVehiculoSemaforo != veh.idVehiculoSemaforo }

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

        }

    }

}

