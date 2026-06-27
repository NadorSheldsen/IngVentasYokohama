package com.megatransportes.yokoh.ui.screens.desecho



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material3.SnackbarHostState

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.foundation.clickable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.LlantasDesecho

import com.megatransportes.yokoh.data.models.PruebasDesecho

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.utils.ErrorUtils

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun LlantasDesechoScreen(

    repository: YokohamaRepository,

    pruebaDesecho: PruebasDesecho,

    onNuevaLlantaClick: () -> Unit,

    onLlantaClick: (LlantasDesecho) -> Unit = {},

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    var llantasDesecho by remember { mutableStateOf<List<LlantasDesecho>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }



    // Permission check for delete action

    val currentUser by repository.currentUser.collectAsState()

    var hasDeleteVehiculoPermission by remember { mutableStateOf(false) }



    LaunchedEffect(currentUser) {

        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario

        if (perfilId != null) {

            try {

                val permisosResult = repository.getPermisosByPerfilId(perfilId)

                val permisosList = permisosResult.getOrNull().orEmpty()

                hasDeleteVehiculoPermission = permisosList.any { it.PermisosNombre == "Eliminar vehículo" }

            } catch (_: Throwable) {

                hasDeleteVehiculoPermission = false

            }

        } else {

            hasDeleteVehiculoPermission = false

        }

    }



    LaunchedEffect(pruebaDesecho.idPruebasDesecho) {

        coroutineScope.launch {

            isLoading = true

            errorMessage = null

            repository.getLlantasDesechoByPruebaId(pruebaDesecho.idPruebasDesecho)

                .onSuccess { llantasDesecho = it }

                .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando llantas de desecho") }

            isLoading = false

        }

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("Llantas de Desecho - ${pruebaDesecho.PruebasDesechoNombre}") },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")

                    }

                },

                actions = {

                    IconButton(onClick = onHome) {

                        Icon(Icons.Default.Home, "Home")

                    }

                }

            )

        },

        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },

        floatingActionButton = {

            FloatingActionButton(

                onClick = onNuevaLlantaClick,

                containerColor = MaterialTheme.colorScheme.primary

            ) {

                Icon(Icons.Default.Add, "Nueva llanta de desecho")

            }

        }

    ) { paddingValues ->

        Box(modifier = Modifier

            .fillMaxSize()

            .padding(paddingValues)) {



            when {

                errorMessage != null -> {

                    ErrorScreen(

                        errorMessage = errorMessage!!,

                        onRetry = {

                            coroutineScope.launch {

                                isLoading = true

                                errorMessage = null

                                repository.getLlantasDesechoByPruebaId(pruebaDesecho.idPruebasDesecho)

                                    .onSuccess { llantasDesecho = it }

                                    .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando llantas de desecho") }

                                isLoading = false

                            }

                        }

                    )

                }

                isLoading -> {

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        CircularProgressIndicator()

                    }

                }

                llantasDesecho.isEmpty() -> {

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        Text(text = "No hay llantas en esta pila de desecho")

                    }

                }

                else -> {

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                        items(llantasDesecho, key = { it.idLlantasDesecho }) { llanta ->

                            var showConfirm by remember { mutableStateOf(false) }

                            Card(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .padding(vertical = 8.dp)

                                    .clickable { onLlantaClick(llanta) },

                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

                            ) {

                                Column(modifier = Modifier.padding(16.dp)) {

                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                                        Column(modifier = Modifier.weight(1f)) {

                                            Text(

                                                text = "Llanta #${llanta.LlantasDesechoNoLlanta ?: "N/A"}",

                                                style = MaterialTheme.typography.titleMedium

                                            )

                                            Text(

                                                text = "Causa: ${llanta.LlantasDesechoCausaDes}",

                                                style = MaterialTheme.typography.bodySmall

                                            )

                                            Text(

                                                text = "Piso: ${llanta.LlantasDesechoPiso}",

                                                style = MaterialTheme.typography.bodySmall

                                            )

                                            if (llanta.LlantasDesechoRemanente != null) {

                                                Text(

                                                    text = "Remanente: ${llanta.LlantasDesechoRemanente} mm",

                                                    style = MaterialTheme.typography.bodySmall

                                                )

                                            }

                                        }



                                        if (hasDeleteVehiculoPermission) {

                                            IconButton(onClick = { showConfirm = true }) {

                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar llanta desecha")

                                            }

                                        }

                                    }



                                    if (showConfirm) {

                                        AlertDialog(

                                            onDismissRequest = { showConfirm = false },

                                            title = { Text("Eliminar llanta") },

                                            text = { Text("¿Seguro que desea eliminar la llanta ${llanta.LlantasDesechoNoLlanta ?: ""}? Esta acción no se puede deshacer.") },

                                            confirmButton = {

                                                TextButton(onClick = {

                                                    showConfirm = false

                                                    coroutineScope.launch {

                                                        repository.deleteLlantaDesecho(llanta.idLlantasDesecho)

                                                            .onSuccess {

                                                                llantasDesecho = llantasDesecho.filter { it.idLlantasDesecho != llanta.idLlantasDesecho }

                                                                snackbarHostState.showSnackbar("Llanta eliminada")

                                                            }

                                                            .onFailure {

                                                                snackbarHostState.showSnackbar("Error eliminando la llanta")

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