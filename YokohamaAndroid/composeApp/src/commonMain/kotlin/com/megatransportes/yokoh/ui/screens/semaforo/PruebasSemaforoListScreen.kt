package com.megatransportes.yokoh.ui.screens.semaforo



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.clickable

import com.megatransportes.yokoh.ui.components.PlatformLazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Description

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Place

import com.megatransportes.yokoh.utils.getPlatformContext

import com.megatransportes.yokoh.utils.OpenFileUtils

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.window.Dialog

import com.megatransportes.yokoh.data.models.*

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import kotlinx.coroutines.launch

import com.megatransportes.yokoh.utils.ErrorUtils

import com.megatransportes.yokoh.platform.getLastKnownLocation



// Helper para mostrar solo la fecha (sin hora)

private fun formatDateOnly(dateTime: String?): String {

    if (dateTime == null) return ""

    return try {

        if (dateTime.contains('T')) dateTime.substringBefore('T')

        else if (dateTime.length >= 10) dateTime.substring(0, 10)

        else dateTime

    } catch (e: Exception) {

        dateTime

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun PruebasSemaforoListScreen(

    repository: YokohamaRepository,

    flota: Flota,

    onPruebaClick: (PruebasSemaforo) -> Unit,

    onReportClick: (PruebasSemaforo) -> Unit,

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    var pruebasSemaforo by remember { mutableStateOf<List<PruebasSemaforo>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }

    var newPruebaTitulo by remember { mutableStateOf("") }

    var isCreating by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()



    suspend fun fetchPruebasSemaforo() {

        isLoading = true

        errorMessage = null

        repository.getPruebasSemaforoByFlotaId(flota.idFlotas)

            .onSuccess { result ->

                pruebasSemaforo = result

            }

            .onFailure { error ->

                errorMessage = ErrorUtils.userMessage(error, "Error cargando pruebas semáforo")

            }

        isLoading = false

    }



    // Cargar pruebas semáforo

    LaunchedEffect(flota.idFlotas) {

        coroutineScope.launch { fetchPruebasSemaforo() }

    }



    // Diálogo para crear nueva prueba

    if (showCreateDialog) {

        Dialog(onDismissRequest = { 

            showCreateDialog = false

            newPruebaTitulo = ""

        }) {

            Card(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(16.dp)

            ) {

                Column(

                    modifier = Modifier.padding(16.dp),

                    verticalArrangement = Arrangement.spacedBy(16.dp)

                ) {

                    Text(

                        text = "Crear Prueba Semáforo",

                        style = MaterialTheme.typography.titleLarge

                    )

                    

                    OutlinedTextField(

                        value = newPruebaTitulo,

                        onValueChange = { newPruebaTitulo = it },

                        label = { Text("Título de la prueba") },

                        modifier = Modifier.fillMaxWidth(),

                        enabled = !isCreating

                    )

                    

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        OutlinedButton(

                            onClick = { 

                                showCreateDialog = false

                                newPruebaTitulo = ""

                            },

                            modifier = Modifier.weight(1f),

                            enabled = !isCreating

                        ) {

                            Text("Cancelar")

                        }

                        

                        Button(

                            onClick = {

                                if (newPruebaTitulo.isNotBlank()) {

                                    coroutineScope.launch {

                                        isCreating = true

                                            val loc = getLastKnownLocation()

                                            val request = PruebasSemaforoCreateRequest(

                                                PruebasSemaforoTitulo = newPruebaTitulo,

                                                Flotas_idFlotas = flota.idFlotas,

                                                latitude = loc?.latitude,

                                                longitude = loc?.longitude

                                            )

                                        repository.createPruebasSemaforo(request)

                                            .onSuccess { newPrueba ->

                                                pruebasSemaforo = pruebasSemaforo + newPrueba

                                                showCreateDialog = false

                                                newPruebaTitulo = ""

                                                isCreating = false

                                                onPruebaClick(newPrueba)

                                            }

                                            .onFailure { error ->

                                                errorMessage = ErrorUtils.userMessage(error, "No se pudo crear la prueba")

                                                isCreating = false

                                            }

                                    }

                                }

                            },

                            modifier = Modifier.weight(1f),

                            enabled = newPruebaTitulo.isNotBlank() && !isCreating

                        ) {

                            if (isCreating) {

                                CircularProgressIndicator(

                                    modifier = Modifier.size(16.dp),

                                    strokeWidth = 2.dp

                                )

                            } else {

                                Text("Crear")

                            }

                        }

                    }

                }

            }

        }

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("Semáforos - ${flota.FlotasNombre}") },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(

                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription = "Regresar"

                        )

                    }

                },

                actions = {

                    IconButton(onClick = onHome) {

                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")

                    }

                }

            )

        },

        floatingActionButton = {

            FloatingActionButton(

                onClick = { showCreateDialog = true },

                containerColor = MaterialTheme.colorScheme.primary,

                contentColor = MaterialTheme.colorScheme.onPrimary

            ) {

                Icon(Icons.Default.Add, contentDescription = "Agregar prueba")

            }

        }

    ) { paddingValues ->

        Box(

            modifier = Modifier

                .fillMaxSize()

                .padding(paddingValues)

        ) {

            when {

                isLoading && pruebasSemaforo.isEmpty() -> {

                    Box(

                        modifier = Modifier.fillMaxSize(),

                        contentAlignment = Alignment.Center

                    ) {

                        CircularProgressIndicator()

                    }

                }

                

                errorMessage != null -> {

                    ErrorScreen(

                        errorMessage = errorMessage!!,

                        onRetry = { coroutineScope.launch { fetchPruebasSemaforo() } }

                    )

                }

                

                pruebasSemaforo.isEmpty() -> {

                    Box(

                        modifier = Modifier.fillMaxSize(),

                        contentAlignment = Alignment.Center

                    ) {

                        Column(

                            horizontalAlignment = Alignment.CenterHorizontally,

                            verticalArrangement = Arrangement.spacedBy(16.dp)

                        ) {

                            Text(

                                text = "No hay pruebas semáforo",

                                style = MaterialTheme.typography.titleMedium

                            )

                            Text(

                                text = "Presiona el botón + para crear una nueva prueba",

                                style = MaterialTheme.typography.bodyMedium

                            )

                        }

                    }

                }

                

                else -> {

                    PlatformLazyColumn(

                        modifier = Modifier.fillMaxSize(),

                        contentPadding = PaddingValues(16.dp),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        items(pruebasSemaforo, key = { it.idPruebasSemaforo }) { prueba ->

                            PruebaSemaforoCard(

                                prueba = prueba,

                                onClick = { onPruebaClick(prueba) },

                                onReportClick = { onReportClick(prueba) }

                            )

                        }

                    }

                }

            }

        }

    }

}



@Composable

private fun PruebaSemaforoCard(

    prueba: PruebasSemaforo,

    onClick: () -> Unit,

    onReportClick: () -> Unit

) {

    Card(

        modifier = Modifier

            .fillMaxWidth()

            .clickable { onClick() },

        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

    ) {

        BoxWithConstraints(

            modifier = Modifier

                .fillMaxWidth()

                .padding(12.dp)

        ) {

            val isCompact = maxWidth < 420.dp

            val platformContext = getPlatformContext()

            val openLocation: () -> Unit = {

                try {

                    val lat = prueba.latitude

                    val lng = prueba.longitude

                    if (lat != null && lng != null && kotlin.math.abs(lat) > 1e-6 && kotlin.math.abs(lng) > 1e-6) {

                        val url = "https://www.google.com/maps/search/?api=1&query=${lat},${lng}"

                        OpenFileUtils.openUrl(url, platformContext = platformContext)

                    } else {

                        println("Ubicación no disponible para prueba id=${prueba.idPruebasSemaforo}")

                    }

                } catch (e: Exception) {

                    println("Error abriendo mapa: ${e.message}")

                }

            }



            if (isCompact) {

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        Text(

                            text = prueba.PruebasSemaforoTitulo,

                            style = MaterialTheme.typography.headlineSmall,

                            fontWeight = FontWeight.Bold

                        )

                        Text(

                            text = "Fecha: ${formatDateOnly(prueba.PruebasSemaforoFecha)}",

                            style = MaterialTheme.typography.bodyLarge,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.End,

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        IconButton(onClick = openLocation) {

                            Icon(imageVector = Icons.Default.Place, contentDescription = "Ubicación")

                        }

                        IconButton(onClick = onReportClick) {

                            Icon(imageVector = Icons.Default.Description, contentDescription = "Reporte")

                        }

                    }

                }

            } else {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Column(

                        modifier = Modifier.weight(1f),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        Text(

                            text = prueba.PruebasSemaforoTitulo,

                            style = MaterialTheme.typography.headlineSmall,

                            fontWeight = FontWeight.Bold

                        )

                        Text(

                            text = "Fecha: ${formatDateOnly(prueba.PruebasSemaforoFecha)}",

                            style = MaterialTheme.typography.bodyLarge,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }



                    IconButton(onClick = openLocation) {

                        Icon(imageVector = Icons.Default.Place, contentDescription = "Ubicación")

                    }



                    IconButton(onClick = onReportClick) {

                        Icon(imageVector = Icons.Default.Description, contentDescription = "Reporte")

                    }

                }

            }

        }

    }

}

