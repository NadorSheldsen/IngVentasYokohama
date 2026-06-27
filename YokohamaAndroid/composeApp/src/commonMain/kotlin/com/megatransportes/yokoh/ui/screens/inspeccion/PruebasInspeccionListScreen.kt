package com.megatransportes.yokoh.ui.screens.inspecciones



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Description

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

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.PruebaInspeccion

import com.megatransportes.yokoh.data.models.PruebaInspeccionCreateRequest

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import kotlinx.coroutines.launch

import com.megatransportes.yokoh.utils.formatDateOnly

import com.megatransportes.yokoh.utils.ErrorUtils

import com.megatransportes.yokoh.platform.getLastKnownLocation



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun PruebasInspeccionListScreen(

    repository: YokohamaRepository,

    flota: Flota,

    onPruebaClick: (PruebaInspeccion) -> Unit,

    onReportClick: (PruebaInspeccion) -> Unit = {},

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    var pruebasInspeccion by remember { mutableStateOf<List<PruebaInspeccion>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }

    var newPruebaTitulo by remember { mutableStateOf("") }

    var isCreating by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()



    suspend fun fetchPruebasInspeccion() {

        isLoading = true

        errorMessage = null

        val res = repository.getPruebasInspeccionByFlotaId(flota.idFlotas)

        res.onSuccess { result ->

            pruebasInspeccion = result

        }

        res.onFailure { error ->

            errorMessage = ErrorUtils.userMessage(error, "Error cargando pruebas de inspección")

        }

        isLoading = false

    }



    // Cargar pruebas de inspección

    LaunchedEffect(flota.idFlotas) {

        coroutineScope.launch { fetchPruebasInspeccion() }

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

                        text = "Crear Prueba de Inspección",

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

                                            val request = PruebaInspeccionCreateRequest(

                                                PruebaInspeccionTitulo = newPruebaTitulo,

                                                Flotas_idFlotas = flota.idFlotas,

                                                latitude = loc?.latitude,

                                                longitude = loc?.longitude

                                            )

                                            println("[UI] Creating PruebaInspeccion with: $request")

                                        repository.createPruebaInspeccion(request)

                                            .onSuccess { newPrueba ->

                                                pruebasInspeccion = pruebasInspeccion + newPrueba

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

                title = { Text("Inspecciones - ${flota.FlotasNombre}") },

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

                isLoading && pruebasInspeccion.isEmpty() -> {

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

                        onRetry = { coroutineScope.launch { fetchPruebasInspeccion() } }

                    )

                }



                pruebasInspeccion.isEmpty() -> {

                    Box(

                        modifier = Modifier.fillMaxSize(),

                        contentAlignment = Alignment.Center

                    ) {

                        Column(

                            horizontalAlignment = Alignment.CenterHorizontally,

                            verticalArrangement = Arrangement.spacedBy(16.dp)

                        ) {

                            Text(

                                text = "No hay pruebas de inspección",

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

                    LazyColumn(

                        modifier = Modifier.fillMaxSize(),

                        contentPadding = PaddingValues(16.dp),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        items(pruebasInspeccion, key = { it.idPruebaInspeccion }) { prueba ->

                            Card(

                                onClick = { onPruebaClick(prueba) },

                                modifier = Modifier.fillMaxWidth(),

                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

                            ) {

                                BoxWithConstraints(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(16.dp)

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

                                                println("Ubicación no disponible para prueba id=${prueba.idPruebaInspeccion}")

                                            }

                                        } catch (e: Exception) {

                                            println("Error abriendo mapa: ${e.message}")

                                        }

                                    }



                                    if (isCompact) {

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                                Text(

                                                    text = prueba.PruebaInspeccionTitulo,

                                                    style = MaterialTheme.typography.headlineSmall,

                                                    fontWeight = FontWeight.Bold

                                                )

                                                Text(

                                                    text = "Fecha: ${formatDateOnly(prueba.PruebaInspeccionFecha)}",

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

                                                IconButton(onClick = { onReportClick(prueba) }) {

                                                    Icon(imageVector = Icons.Default.Description, contentDescription = "Reporte")

                                                }

                                            }

                                        }

                                    } else {

                                        Row(

                                            modifier = Modifier.fillMaxWidth(),

                                            horizontalArrangement = Arrangement.SpaceBetween,

                                            verticalAlignment = Alignment.CenterVertically

                                        ) {

                                            Column(

                                                modifier = Modifier.weight(1f),

                                                verticalArrangement = Arrangement.spacedBy(8.dp)

                                            ) {

                                                Text(

                                                    text = prueba.PruebaInspeccionTitulo,

                                                    style = MaterialTheme.typography.headlineSmall,

                                                    fontWeight = FontWeight.Bold

                                                )

                                                Text(

                                                    text = "Fecha: ${formatDateOnly(prueba.PruebaInspeccionFecha)}",

                                                    style = MaterialTheme.typography.bodyLarge,

                                                    color = MaterialTheme.colorScheme.onSurfaceVariant

                                                )

                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {

                                                IconButton(onClick = openLocation) {

                                                    Icon(imageVector = Icons.Default.Place, contentDescription = "Ubicación")

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

                    }

                }

            }

        }

    }

}