package com.megatransportes.yokoh.ui.screens.desecho



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.clickable

import androidx.compose.ui.window.Dialog

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Description

import androidx.compose.material.icons.filled.Home

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.PruebasDesecho

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.utils.ErrorUtils

import com.megatransportes.yokoh.utils.DateFormatter

import com.megatransportes.yokoh.utils.TimeProvider

import kotlinx.coroutines.launch

import androidx.compose.material3.SnackbarHost

import androidx.compose.material3.SnackbarHostState



// Helper local: extrae sólo la parte de fecha de un datetime (ej. "2025-10-21T12:34:56" -> "2025-10-21")

private fun formatDateOnly(dateTime: String?): String {

    if (dateTime.isNullOrBlank()) return "Sin fecha"

    // Manejar formatos comunes: ISO (T), espacio separado, o timestamp simple

    return dateTime.split('T', ' ').firstOrNull() ?: dateTime

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun PilasDesechoScreen(

    repository: YokohamaRepository,

    flota: Flota,

    onPilaSelected: (PruebasDesecho) -> Unit,

    onReportClick: (PruebasDesecho) -> Unit = {},

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    var pruebasDesecho by remember { mutableStateOf<List<PruebasDesecho>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showNewDialog by remember { mutableStateOf(false) }

    var nuevaPilaValor by remember { mutableStateOf("") }

    var isCreating by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val coroutineScope = rememberCoroutineScope()



    LaunchedEffect(flota.idFlotas) {

        coroutineScope.launch {

            isLoading = true

            errorMessage = null

            repository.getAllPruebasDesechoByFlota(flota.idFlotas)

                .onSuccess { pruebasDesecho = it }

                .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando pilas de desecho") }

            isLoading = false

        }

    }



    Scaffold(

        snackbarHost = { SnackbarHost(snackbarHostState) },

        topBar = {

            TopAppBar(

                title = { Text("Pilas de Desecho - ${flota.FlotasNombre}") },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.Default.ArrowBack, "Regresar")

                    }

                },

                actions = {

                    IconButton(onClick = onHome) {

                        Icon(Icons.Default.Home, "Home")

                    }

                }

            )

        },

        floatingActionButton = {

            FloatingActionButton(

                onClick = { showNewDialog = true },

                containerColor = MaterialTheme.colorScheme.primary

            ) {

                Icon(Icons.Default.Add, "Nueva pila de desecho")

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

                                repository.getAllPruebasDesechoByFlota(flota.idFlotas)

                                    .onSuccess { pruebasDesecho = it }

                                    .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando pilas de desecho") }

                                isLoading = false

                            }

                        }

                    )

                }

                isLoading -> {

                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                }

                pruebasDesecho.isEmpty() -> {

                    Text(

                        text = "No hay pilas de desecho",

                        modifier = Modifier.align(Alignment.Center)

                    )

                }

                else -> {

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                        items(pruebasDesecho, key = { it.idPruebasDesecho }) { prueba ->

                            Card(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .padding(vertical = 8.dp),

                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

                            ) {

                                Row(modifier = Modifier

                                    .fillMaxWidth()

                                    .clickable { onPilaSelected(prueba) }

                                    .padding(16.dp),

                                    verticalAlignment = Alignment.CenterVertically

                                ) {

                                    Column(modifier = Modifier.weight(1f)) {

                                        Text(

                                            text = prueba.PruebasDesechoNombre,

                                            style = MaterialTheme.typography.headlineSmall

                                        )

                                        Text(

                                            text = "Fecha: ${formatDateOnly(prueba.PruebasDesechoFecha)}",

                                            style = MaterialTheme.typography.bodyLarge

                                        )

                                    }

                                    IconButton(onClick = { onReportClick(prueba) }) {

                                        Icon(imageVector = Icons.Default.Description, contentDescription = "Reporte")

                                    }

                                }

                            }

                        }

                    }

                }

            }



            // Dialog para crear nueva pila en línea

            if (showNewDialog) {

                Dialog(onDismissRequest = { if (!isCreating) { showNewDialog = false; nuevaPilaValor = "" } }) {

                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                            Text(text = "Nueva Pila de Desecho", style = MaterialTheme.typography.titleLarge)



                            OutlinedTextField(

                                value = nuevaPilaValor,

                                onValueChange = { nuevaPilaValor = it },

                                label = { Text("Título Pila") },

                                keyboardOptions = KeyboardOptions.Default,

                                modifier = Modifier.fillMaxWidth()

                            )



                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {

                                OutlinedButton(onClick = { if (!isCreating) { showNewDialog = false; nuevaPilaValor = "" } }, modifier = Modifier.weight(1f)) {

                                    Text("Cancelar")

                                }



                                Button(onClick = {

                                    if (nuevaPilaValor.isNotBlank()) {

                                        isCreating = true

                                        coroutineScope.launch {

                                            // Attempt to create; always close dialog and then refresh list. Show snackbar on error.

                                            val result = repository.createPruebaDesecho(

                                                com.megatransportes.yokoh.data.models.PruebasDesechoCreateRequest(

                                                    PruebasDesechoNombre = nuevaPilaValor,

                                                    PruebasDesechoFecha = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd"),

                                                    Flotas_idFlotas = flota.idFlotas

                                                )

                                            )



                                            // Close dialog immediately after attempt

                                            isCreating = false

                                            showNewDialog = false

                                            val createdOk = result.isSuccess

                                            if (!createdOk) {

                                                // Show error but continue to refresh list

                                                val message = result.exceptionOrNull()?.message ?: "No se pudo crear la pila"

                                                snackbarHostState.showSnackbar(message)

                                            }



                                            // Refresh list regardless; this makes the UI reflect server state

                                            isLoading = true

                                            repository.getAllPruebasDesechoByFlota(flota.idFlotas).onSuccess { pruebasDesecho = it }

                                                .onFailure { snackbarHostState.showSnackbar("No se pudo actualizar la lista") }

                                            isLoading = false



                                            // Clear input

                                            nuevaPilaValor = ""

                                        }

                                    }

                                }, modifier = Modifier.weight(1f), enabled = nuevaPilaValor.isNotBlank() && !isCreating) {

                                    if (isCreating) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Crear")

                                }

                            }

                        }

                    }

                }

            }

        }

    }

}