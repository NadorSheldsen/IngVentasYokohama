package com.megatransportes.yokoh.ui.screens.vehiculos



import androidx.compose.foundation.clickable

import androidx.compose.foundation.Image

import androidx.compose.foundation.layout.*

import com.megatransportes.yokoh.ui.components.PlatformLazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Clear

import androidx.compose.material.icons.filled.Search

import androidx.compose.material.icons.filled.FilterList

import androidx.compose.material.icons.filled.Check

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.outlined.PhotoCamera

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import androidx.compose.ui.layout.ContentScale

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.Vehiculo

import com.megatransportes.yokoh.data.models.LlantaVehiculo // ← Agregar esta importación

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.utils.FileConverter

import com.megatransportes.yokoh.utils.FileSaveUtils

import com.megatransportes.yokoh.utils.OpenFileUtils

import com.megatransportes.yokoh.utils.byteArrayToImageBitmap

import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import com.megatransportes.yokoh.utils.getPlatformContext

import androidx.compose.ui.draw.alpha

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.ImageBitmap

import androidx.compose.material.icons.automirrored.filled.InsertDriveFile

import androidx.compose.material.icons.filled.Delete

import com.megatransportes.yokoh.utils.ErrorUtils

import com.megatransportes.yokoh.utils.NumberFormatter

import com.megatransportes.yokoh.utils.TimeProvider



// Helper util disponible a nivel de archivo para formatear odómetro con separador de miles

private fun formatWithComma(value: Float): String {

    return try {

        NumberFormatter.formatWithComma(value, 0)

    } catch (e: Exception) {

        value.toString()

    }

}



@Composable

private fun VehicleThumbnail(

    base64Image: String?,

    hasTerminada: Boolean

) {

    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = base64Image) {

        value = if (base64Image.isNullOrBlank()) {

            null

        } else {

            withContext(Dispatchers.Default) {

                try {

                    // Guardrails to avoid expensive decode for oversized payloads in list rows.

                    if (base64Image.length > 1_200_000) return@withContext null

                    val bytes = FileConverter.safeStringToByteArray(base64Image)

                    if (bytes.isEmpty() || bytes.size > 900_000) return@withContext null

                    byteArrayToImageBitmap(bytes)

                } catch (_: Throwable) {

                    null

                }

            }

        }

    }



    Card(modifier = Modifier.size(64.dp).alpha(if (hasTerminada) 0.5f else 1f)) {

        Box(

            modifier = Modifier.fillMaxSize(),

            contentAlignment = Alignment.Center

        ) {

            if (imageBitmap != null) {

                Image(

                    bitmap = imageBitmap!!,

                    contentDescription = "Foto del vehículo",

                    modifier = Modifier.fillMaxSize(),

                    contentScale = ContentScale.Crop

                )

            } else {

                Icon(

                    imageVector = Icons.Outlined.PhotoCamera,

                    contentDescription = "Sin miniatura",

                    tint = MaterialTheme.colorScheme.onSurfaceVariant,

                    modifier = Modifier.size(22.dp)

                )

            }

        }

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun VehiculosScreen(

    repository: YokohamaRepository,

    flota: Flota,

    onAddVehiculoClick: () -> Unit,

    onVehiculoClick: (Vehiculo, Int) -> Unit,

    onPruebaRendimientoClick: (Vehiculo, List<LlantaVehiculo>) -> Unit,

    onBack: () -> Unit,

    onHome: () -> Unit = {}

) {

    

    var vehiculos by remember { mutableStateOf<List<Vehiculo>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Use the persisted VehiculosPTerminada flag on the vehicle entity (returned by the backend)

    // Filter: by default do not show vehicles with Terminada=true

    var showTerminadas by remember { mutableStateOf(false) }

    var sortByRecent by remember { mutableStateOf(false) }

    var showSortMenu by remember { mutableStateOf(false) }

    // Keep a map of the original registered odometer per vehicle so we can compute

    // "kilómetros recorridos" = lastPruebaOdometro - registeredOdometer

    var registeredOdometers by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()

    val platformContext = getPlatformContext()

    val snackbarHostState = remember { SnackbarHostState() }



    // --- Permission check: hide FAB if perfil lacks "Gestionar Vehiculos" ---

    val currentUser by repository.currentUser.collectAsState()

    var hasManageVehiculosPermission by remember { mutableStateOf(false) }

    var hasDeleteVehiculoPermission by remember { mutableStateOf(false) }



    // Observe odometer updates published by the repository so we can merge them into the displayed list

    val vehiculoOdometerUpdates by repository.vehiculoOdometerUpdates.collectAsState()



    LaunchedEffect(currentUser) {

        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario

        if (perfilId != null) {

            try {

                val permisosResult = repository.getPermisosByPerfilId(perfilId)

                val permisosList = permisosResult.getOrNull().orEmpty()

                hasManageVehiculosPermission = permisosList.any { it.PermisosNombre == "Gestionar Vehículos" }

                // Only allow deletion UI if profile has the explicit "Eliminar vehículo" permission

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



    // Filter vehicles based on search query, sort order, and 'showTerminadas' toggle

    val filteredVehiculos = remember(vehiculos, searchQuery, sortByRecent, showTerminadas) {

        val base = if (searchQuery.isBlank()) {

            vehiculos

        } else {

            vehiculos.filter { vehiculo ->

                vehiculo.VehiculosNumero.toString().contains(searchQuery, ignoreCase = true) ||

                vehiculo.TipoVehiculosNombre?.contains(searchQuery, ignoreCase = true) == true ||

                vehiculo.FlotasNombre?.contains(searchQuery, ignoreCase = true) == true

            }

        }

        val filtered = if (showTerminadas) base else base.filter { v -> !(v.VehiculosPTerminada == 1) }

        if (sortByRecent) filtered.sortedByDescending { it.idVehiculos } else filtered.sortedBy { it.VehiculosNumero }

    }



    suspend fun fetchVehiculos() {

        isLoading = true

        errorMessage = null



        repository.getVehiculosByFlotaId(flota.idFlotas)

            .onSuccess { result ->

                // Ensure we only display vehicles that belong to the current flota.

                val filtered = result.filter { it.Flotas_idFlotas == flota.idFlotas }

                vehiculos = filtered

                // Capture the original registered odometer for each vehicle so we can

                // compute distance travelled even if VehiculosOdometro gets updated later.

                registeredOdometers = filtered.associate { it.idVehiculos to (it.VehiculosOdometroRegistro ?: it.VehiculosOdometro) }



                // Fetch latest odometers for all vehicles in the flota using one request

                // and publish them in bulk to avoid N network calls (ANR risk).

                repository.getUltimosPruebaRendimientoByFlota(flota.idFlotas)

                    .onSuccess { latestMap ->

                        repository.publishVehiculoOdometersLocal(latestMap)

                    }

            }

            .onFailure { error ->

                errorMessage = ErrorUtils.userMessage(error, "Error cargando vehículos")

            }



        isLoading = false

    }



    LaunchedEffect(key1 = flota.idFlotas) {

        coroutineScope.launch { fetchVehiculos() }

    }



    // We rely on the VehiculosPTerminada column returned by the backend now.



    // Merge any odometer updates into our local vehiculos list when they arrive

    LaunchedEffect(vehiculoOdometerUpdates, vehiculos) {

        if (vehiculos.isNotEmpty() && vehiculoOdometerUpdates.isNotEmpty()) {

            val updated = vehiculos.map { v ->

                val newOdo = vehiculoOdometerUpdates[v.idVehiculos]

                if (newOdo != null && newOdo != v.VehiculosOdometro) {

                    v.copy(VehiculosOdometro = newOdo)

                } else v

            }

            // Only set if something changed to avoid recomposition loops

            if (updated != vehiculos) {

                vehiculos = updated

            }

        }

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("Vehículos de ${flota.FlotasNombre}") },

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

                        Icon(imageVector = Icons.Default.Home, contentDescription = "Flota")

                    }

                }

            )

        },

    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },

        floatingActionButton = {

            if (hasManageVehiculosPermission) {

                FloatingActionButton(

                    onClick = onAddVehiculoClick,

                    containerColor = MaterialTheme.colorScheme.primary

                ) {

                    Icon(

                        imageVector = Icons.Default.Add,

                        contentDescription = "Agregar vehículo"

                    )

                }

            }

        }

    ) { paddingValues ->

        Box(

            modifier = Modifier

                .fillMaxSize()

                .padding(paddingValues)

        ) {

            Column(

                modifier = Modifier.fillMaxSize()

            ) {

                // Barra de búsqueda con filtros

                Card(

                    modifier = Modifier.fillMaxWidth().padding(16.dp),

                    colors = CardDefaults.cardColors(

                        containerColor = MaterialTheme.colorScheme.surfaceVariant

                    )

                ) {

                    OutlinedTextField(

                        value = searchQuery,

                        onValueChange = { searchQuery = it },

                        placeholder = { Text("Buscar vehículos...") },

                        leadingIcon = {

                            Icon(

                                imageVector = Icons.Default.Search,

                                contentDescription = "Buscar"

                            )

                        },

                        trailingIcon = {

                            Row {

                                Box {

                                    IconButton(onClick = { showSortMenu = true }) {

                                        Icon(

                                            imageVector = Icons.Default.FilterList,

                                            contentDescription = if (sortByRecent) "Orden: Más reciente" else "Orden: A-Z"

                                        )

                                    }

                                    DropdownMenu(

                                        expanded = showSortMenu,

                                        onDismissRequest = { showSortMenu = false }

                                    ) {

                                        DropdownMenuItem(

                                            text = { Text("A-Z") },

                                            onClick = { sortByRecent = false; showSortMenu = false },

                                            leadingIcon = if (!sortByRecent) {{ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }} else null

                                        )

                                        DropdownMenuItem(

                                            text = { Text("Más reciente") },

                                            onClick = { sortByRecent = true; showSortMenu = false },

                                            leadingIcon = if (sortByRecent) {{ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }} else null

                                        )

                                    }

                                }

                                if (searchQuery.isNotEmpty()) {

                                    IconButton(onClick = { searchQuery = "" }) {

                                        Icon(

                                            imageVector = Icons.Default.Clear,

                                            contentDescription = "Limpiar búsqueda"

                                        )

                                    }

                                }

                            }

                        },

                        modifier = Modifier.fillMaxWidth().padding(16.dp),

                        singleLine = true

                    )

                }



                Box(

                    modifier = Modifier.weight(1f).fillMaxWidth()

                ) {

                    when {

                        isLoading && vehiculos.isEmpty() -> {

                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                        }

                        errorMessage != null -> {

                            ErrorScreen(

                                errorMessage = errorMessage!!,

                                onRetry = { coroutineScope.launch { fetchVehiculos() } },

                                modifier = Modifier.align(Alignment.TopCenter)

                            )

                        }

                        filteredVehiculos.isEmpty() && searchQuery.isNotEmpty() -> {

                            Text(

                                text = "No se encontraron vehículos que coincidan con '$searchQuery'",

                                modifier = Modifier.align(Alignment.Center).padding(16.dp)

                            )

                        }

                        vehiculos.isEmpty() -> {

                            Text(

                                text = "No hay vehículos registrados para esta flota",

                                modifier = Modifier.align(Alignment.Center).padding(16.dp)

                            )

                        }

                        else -> {

                            PlatformLazyColumn(

                                modifier = Modifier.fillMaxSize(),

                                contentPadding = PaddingValues(16.dp),

                                verticalArrangement = Arrangement.spacedBy(8.dp)

                            ) {

                                items(filteredVehiculos, key = { it.idVehiculos }) { vehiculo ->

                                    // Prefer the latest PruebaRendimiento odometer if published by the repository

                                    val displayedOdo = vehiculoOdometerUpdates[vehiculo.idVehiculos] ?: vehiculo.VehiculosOdometro



                                    VehiculoItem(

                                        vehiculo = vehiculo,

                                        displayedOdometer = displayedOdo,

                                        registeredOdometer = registeredOdometers[vehiculo.idVehiculos] ?: (vehiculo.VehiculosOdometroRegistro ?: vehiculo.VehiculosOdometro),

                                        kmRecorrido = vehiculo.kmRecorridoLatest,

                                        hasTerminada = (vehiculo.VehiculosPTerminada == 1),

                                        onClick = {

                                            coroutineScope.launch {

                                                // Verificar si el vehículo tiene llantas registradas

                                                repository.getLlantasVehiculosByVehiculoId(vehiculo.idVehiculos)

                                                    .onSuccess { llantas ->

                                                        repository.getTiposVehiculos()

                                                            .onSuccess { tipos ->

                                                                val tipoVehiculo = tipos.find { it.idTipoVehiculos == vehiculo.TipoVehiculos_idTipoVehiculos }

                                                                val cantidadLlantas = tipoVehiculo?.TipoVehiculosCantLlantas ?: 4



                                                                if (llantas.isEmpty()) {

                                                                    // No tiene llantas, ir a registro de llantas

                                                                    onVehiculoClick(vehiculo, cantidadLlantas)

                                                                } else {

                                                                    // Tiene llantas, ir a prueba de rendimiento

                                                                    onPruebaRendimientoClick(vehiculo, llantas)

                                                                }

                                                            }

                                                    }

                                                    .onFailure { error ->

                                                        errorMessage = ErrorUtils.userMessage(error, "Error verificando llantas")

                                                    }

                                            }

                                        },

                                        onDownloadPdf = {

                                            // Download PDF and save to cache

                                            coroutineScope.launch {

                                                repository.downloadVehiculoRendimientoPdf(vehiculo.idVehiculos)

                                                    .onSuccess { bytes ->

                                                                try {

                                                            // Basic header check for PDF: starts with "%PDF"

                                                            val isPdfHeader = bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

                                                            if (!isPdfHeader) {

                                                                // Keep the list visible; show a concise snackbar message

                                                                snackbarHostState.showSnackbar("El archivo descargado no parece un PDF")

                                                                return@onSuccess

                                                            }



                                                            val savedPath = FileSaveUtils.saveBytesToCache("vehiculo_${vehiculo.idVehiculos}_rendimiento.pdf", bytes, platformContext)

                                                            // Keep the list visible; show the saved path in a snackbar

                                                            snackbarHostState.showSnackbar("PDF guardado: $savedPath")

                                                            // Try to open the saved PDF (may switch to external viewer)

                                                            try {

                                                                OpenFileUtils.openFile(savedPath, platformContext = platformContext)

                                                            } catch (openEx: Exception) {

                                                                snackbarHostState.showSnackbar(ErrorUtils.userMessage(openEx, "PDF guardado pero no se pudo abrir automáticamente"))

                                                            }

                                                        } catch (e: Exception) {

                                                            snackbarHostState.showSnackbar(ErrorUtils.userMessage(e, "Error guardando PDF"))

                                                        }

                                                    }

                                                    .onFailure { err ->

                                                        snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error descargando PDF"))

                                                    }

                                            }

                                        }

                                        ,

                                        showDelete = hasDeleteVehiculoPermission,

                                        onDelete = {

                                            coroutineScope.launch {

                                                repository.deleteVehiculo(vehiculo.idVehiculos)

                                                    .onSuccess {

                                                        vehiculos = vehiculos.filter { it.idVehiculos != vehiculo.idVehiculos }

                                                        snackbarHostState.showSnackbar("Vehículo eliminado")

                                                    }

                                                    .onFailure { err ->

                                                        snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error eliminando vehículo"))

                                                    }

                                            }

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



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun VehiculoItem(

    vehiculo: Vehiculo,

    displayedOdometer: Float,

    registeredOdometer: Float,

    kmRecorrido: Float? = null,

    hasTerminada: Boolean = false,

    onClick: () -> Unit,

    onDownloadPdf: () -> Unit,

    showDelete: Boolean = false,

    onDelete: () -> Unit = {}

) {

    // Rendering large base64 images in list rows can cause ANR on low/mid devices.

    // Keep list lightweight; open/detail screens can render full images if needed.

    val hasImage = !vehiculo.VehiculosImagen.isNullOrBlank()



    var showConfirm by remember { mutableStateOf(false) }



    Card(

        modifier = Modifier

            .fillMaxWidth()

            .clickable(onClick = onClick)

            .alpha(1f),

        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

    ) {

        Column(

            modifier = Modifier.fillMaxWidth().padding(16.dp)

        ) {

            // Título en su propia fila que ocupa todo el ancho; delete en esquina superior derecha

            val titleColor = if (hasTerminada) MaterialTheme.colorScheme.primary.copy(alpha = 0.98f) else MaterialTheme.colorScheme.primary

            Box(modifier = Modifier.fillMaxWidth()) {

                Text(

                    text = "Vehículo #${vehiculo.VehiculosNumero}",

                    style = MaterialTheme.typography.titleLarge,

                    color = titleColor,

                    modifier = Modifier.align(Alignment.CenterStart)

                )



                if (showDelete) {

                    IconButton(onClick = { showConfirm = true }, modifier = Modifier.align(Alignment.TopEnd)) {

                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar vehículo")

                    }

                }

            }



            if (showConfirm) {

                AlertDialog(

                    onDismissRequest = { showConfirm = false },

                    title = { Text("Eliminar vehículo") },

                    text = { Text("¿Seguro que desea eliminar el vehículo #${vehiculo.VehiculosNumero}? Esta acción no se puede deshacer.") },

                    confirmButton = {

                        TextButton(onClick = {

                            showConfirm = false

                            onDelete()

                        }) { Text("Eliminar") }

                    },

                    dismissButton = {

                        TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }

                    }

                )

            }

            

            Spacer(modifier = Modifier.height(8.dp))

            

            // Fila con la información y la foto

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically

            ) {

                Column(

                    modifier = Modifier.weight(1f)

                ) {

                    if (hasImage) {

                        Text(

                            text = "Imagen disponible",

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                        Spacer(modifier = Modifier.height(8.dp))

                    }

                    // Make body text clearer for terminated vehicles as requested

                    val bodyColor = if (hasTerminada) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f) else MaterialTheme.colorScheme.onSurface

                    Text(

                        text = "Tipo: ${vehiculo.TipoVehiculosNombre ?: "No especificado"}",

                        style = MaterialTheme.typography.bodyMedium,

                        color = bodyColor

                    )

                    Text(

                        text = "Odómetro: ${formatWithComma(displayedOdometer)} km",

                        style = MaterialTheme.typography.bodyMedium,

                        color = bodyColor

                    )



                    // Prefer the server-computed km recorridos. Fall back to the local

                    // delta only when the server hasn't provided the value yet.

                    val kmsRecorridos = kmRecorrido ?: (displayedOdometer - registeredOdometer).coerceAtLeast(0f)

                    Text(

                        text = "Kilómetros recorridos: ${formatWithComma(kmsRecorridos)} km",

                        style = MaterialTheme.typography.bodyMedium,

                        color = bodyColor

                    )

                    Text(

                        text = "Flota: ${vehiculo.FlotasNombre ?: "No especificada"}",

                        style = MaterialTheme.typography.bodyMedium,

                        color = bodyColor

                    )

                }



                // Thumbnail area: always reserve the thumbnail column on the right so we can

                // show the 'Terminada' label above the photo even when there is no image.

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {

                    // Show the 'Terminada' label above the thumbnail (as requested), even if there is no photo

                    if (hasTerminada) {

                        Text(

                            text = "Terminada",

                            style = MaterialTheme.typography.bodySmall,

                            color = Color.Red,

                            maxLines = 1

                        )

                        Spacer(modifier = Modifier.height(4.dp))

                    }



                    // Show thumbnail with async decode and safety guards for large payloads.

                    VehicleThumbnail(

                        base64Image = vehiculo.VehiculosImagen,

                        hasTerminada = hasTerminada

                    )

                    Spacer(modifier = Modifier.width(8.dp))

                }



                IconButton(onClick = onDownloadPdf) {

                    Icon(

                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,

                        contentDescription = "Descargar reporte PDF"

                    )

                }

            }

        }

    }

}

