package com.megatransportes.yokoh.ui.screens.desecho

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import com.megatransportes.yokoh.ui.components.PlatformLazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.DateFormatter
import com.megatransportes.yokoh.utils.NumberFormatter
import com.megatransportes.yokoh.utils.TimeProvider
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Clear
import com.megatransportes.yokoh.utils.FilePickerUtils
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.createFilePickerUtils
import com.megatransportes.yokoh.utils.InitializeFilePickerIfNeeded
import com.megatransportes.yokoh.ui.components.PhotoPickerDialog
import com.megatransportes.yokoh.ui.components.PhotoSlot
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material.icons.outlined.PhotoCamera
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.ui.components.MicButton
import com.megatransportes.yokoh.ui.components.FieldDescriptor
import com.megatransportes.yokoh.ui.components.FieldType
import com.megatransportes.yokoh.ui.components.BluetoothCaliperAutoListener
import com.megatransportes.yokoh.utils.getPlatformContext
import com.megatransportes.yokoh.platform.getLastKnownLocation
import com.megatransportes.yokoh.platform.Location as PlatformLocation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

// Función para formatear el tamaño del archivo
private fun formatFileSize(bytes: Long): String {
    val kilobyte = 1024
    val megabyte = kilobyte * 1024
    return when {
        bytes >= megabyte -> "${NumberFormatter.formatWithComma(bytes.toDouble() / megabyte, 1)} MB"
        bytes >= kilobyte -> "${NumberFormatter.formatWithComma(bytes.toDouble() / kilobyte, 1)} KB"
        else -> "$bytes bytes"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaLlantaDesechoScreen(
    repository: YokohamaRepository,
    pruebaDesecho: PruebasDesecho,
    existingLlanta: LlantasDesecho? = null,
    onLlantaCreada: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    val currentUser by repository.currentUser.collectAsState()
    var llantaSearch by remember { mutableStateOf("") }
    var llantasEncontradas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var llantaSeleccionada by remember { mutableStateOf<Llanta?>(null) }
    var noLlanta by remember { mutableStateOf("") }
    var pisoSeleccionado by remember { mutableStateOf("Original") }
    var causaSeleccionada by remember { mutableStateOf("") }
    var ubicacionSeleccionada by remember { mutableStateOf("") }
    var remanente by remember { mutableStateOf("") }
    var comentarios by remember { mutableStateOf("Ninguno") }
    var foto1 by remember { mutableStateOf<String?>(null) }
    var foto1Nombre by remember { mutableStateOf<String?>(null) }
    var foto1Tamano by remember { mutableStateOf<Long?>(null) }
    var foto2 by remember { mutableStateOf<String?>(null) }
    var foto2Nombre by remember { mutableStateOf<String?>(null) }
    var foto2Tamano by remember { mutableStateOf<Long?>(null) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationAttempted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Inicializar FilePicker
    val filePicker = remember { createFilePickerUtils() }
    InitializeFilePickerIfNeeded()

    // If editing, prefill the fields once (including selecting the llanta by id)
    LaunchedEffect(existingLlanta?.idLlantasDesecho) {
        existingLlanta?.let { ex ->
            // prefill simple fields
            noLlanta = ex.LlantasDesechoNoLlanta?.toString() ?: ""
            pisoSeleccionado = ex.LlantasDesechoPiso
            causaSeleccionada = ex.LlantasDesechoCausaDes ?: ""
            ubicacionSeleccionada = ex.LlantasDesechoUbi ?: ""
            remanente = ex.LlantasDesechoRemanente?.toString() ?: ""
            comentarios = ex.LlantasDesechoComentarios ?: ""
            foto1 = ex.LlantasDesechoFoto1
            foto2 = ex.LlantasDesechoFoto2
            // Prefill llanta selection: set display text and attempt to load full llanta details
            try {
                // Set a human-readable search text immediately so the user sees the selected llanta
                llantaSearch = "${ex.Llantas_idLlantas}" // temporary ID display; repository lookup will replace with full name
                if (ex.Llantas_idLlantas > 0) {
                    repository.getLlantaWithDetails(ex.Llantas_idLlantas).onSuccess { ll ->
                        llantaSeleccionada = ll
                        llantaSearch = "${ll.LlantasMarca} ${ll.LlantasModelo}"
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    val pisoOptions = listOf("Original", "Vitalizado 1")
    
    // Mapa de ubicación a causas correspondientes
    val ubicacionToCausas = mapOf(
        "Banda de rodamiento" to listOf(
            "CORTE EN PISO",
            "DAÑO DEBIDO A ATRAPAMIENTO DE PIEDRAS EN PISO",
            "DESGARRE EN PISO",
            "DESGASTE IRREGULAR EXCESIVO",
            "FALLA DE RENOVADO",
            "IMPACTO EN PISO",
            "PENETRACION EN PISO",
            "SEPARACION DE BANDA DE RODAMIENTO DEBIDO A PENETRACION",
            "SEPARACION EN PISO DEBIDO A IMPACTO"
        ),
        "Hombro" to listOf(
            "CORTE EN HOMBRO",
            "PENETRACION EN HOMBRO"
        ),
        "Costado" to listOf(
            "CASCO FATIGADO",
            "CORTE EN COSTADO",
            "DAÑO DEBIDO A OBJETO ENTRE DUAL",
            "DAÑO DEBIDO A SOBRECARGA",
            "DAÑO EN COSTADO POR ABRASION (FROTAMIENTO)",
            "DAÑO POR INTERFERENCIA MECANICA",
            "DAÑO POR MARCAJE",
            "DAÑO POR OZONO Y/O PRODUCTOS QUIMICOS",
            "DESGARRE EN COSTADO",
            "IMPACTO EN COSTADO",
            "PENETRACION EN COSTADO",
            "RODADA BAJA",
            "RUPTURA EN CUERDAS DEBIDO A RODADO BAJO",
            "RUPTURA EN CUERDAS DEL COSTADO",
            "SEPARACION DE COSTADO DEBIDO A CEJA DAÑADA",
            "SEPARACION DE COSTADO DEBIDO A PENETRACION",
            "SEPARACION EN COSTADO DEBIDO A IMPACTO"
        ),
        "Ceja/ Pestaña" to listOf(
            "CEJA BAQUELIZADA",
            "CEJA DEFORMADA",
            "CEJA DISTORSIONADA",
            "CORTE EN CEJA",
            "DAÑO POR INCRUSTACION DE RIN EN CEJA",
            "DESGARRE EN CEJA",
            "SEPARACION DE CHAFER (TUS)"
        ),
        "Liner" to listOf(
            "DAÑO EN LINER",
            "EXCESO DE REPARACIONES",
            "REPARACION INADECUADA"
        ),
        "Otros" to listOf(
            "SIN FALLA DEP. SIST.",
            "TERMINACION VIDA UTIL",
            "POSIBLE CONDICION DE MANUFACTURA",
            "RECHAZO PLANTA RENOVADO"
        )
    )
    
    val ubicacionOptions = listOf("Banda de rodamiento", "Hombro", "Costado", "Ceja/ Pestaña", "Liner", "Otros")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Llanta de Desecho") },
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
        }
    ) { paddingValues ->
        // Overlay state for mic recording indicator (host-level Popup to avoid clipping)
        var overlayRecording by remember { mutableStateOf(false) }
        var overlayCenterWindow by remember { mutableStateOf(Offset.Zero) }
        var overlaySizeDp by remember { mutableStateOf(40.dp) }

        // Draw screen content and host-level Popup overlay
        Box(modifier = Modifier.fillMaxSize()) {
            PlatformLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .border(2.dp, if (llantaSeleccionada != null && noLlanta.isNotBlank() && pisoSeleccionado.isNotBlank() && ubicacionSeleccionada.isNotBlank() && causaSeleccionada.isNotBlank() && remanente.toFloatOrNull() != null && comentarios.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Search field with dropdown suggestions (select-like)
            item {
                var suggestionsExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = suggestionsExpanded,
                    onExpandedChange = {
                        // Toggle expanded and if opening while the search is empty, fetch all llantas
                        suggestionsExpanded = !suggestionsExpanded
                        if (suggestionsExpanded && llantaSearch.isBlank()) {
                            coroutineScope.launch {
                                repository.getAllLlantas()
                                    .onSuccess { llantas ->
                                        llantasEncontradas = llantas
                                        suggestionsExpanded = llantas.isNotEmpty()
                                    }
                                    .onFailure { /* Manejar error */ }
                            }
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = llantaSearch,
                        onValueChange = { raw ->
                            llantaSearch = raw
                            if (raw.isBlank()) {
                                // Si no hay texto, mostrar todas las llantas (como un select con todas las opciones)
                                coroutineScope.launch {
                                    repository.getAllLlantas()
                                        .onSuccess { llantas ->
                                            llantasEncontradas = llantas
                                            suggestionsExpanded = llantas.isNotEmpty()
                                        }
                                        .onFailure { /* Manejar error */ }
                                }
                            } else if (raw.length > 2) {
                                // Búsqueda por término (mantener comportamiento previo)
                                coroutineScope.launch {
                                    repository.searchLlantas(raw)
                                        .onSuccess { llantas ->
                                            llantasEncontradas = llantas
                                            suggestionsExpanded = llantas.isNotEmpty()
                                        }
                                        .onFailure { /* Manejar error */ }
                                }
                            } else {
                                // Texto corto: no consultar y ocultar
                                llantasEncontradas = emptyList()
                                suggestionsExpanded = false
                            }
                        },
                        label = { Text("Buscar llanta por marca o modelo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = suggestionsExpanded) },
                        isError = validationAttempted && llantaSeleccionada == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = suggestionsExpanded,
                        onDismissRequest = { suggestionsExpanded = false }
                    ) {
                        if (llantasEncontradas.isEmpty()) {
                            DropdownMenuItem(text = { Text("No hay resultados") }, onClick = { suggestionsExpanded = false })
                        } else {
                            llantasEncontradas.forEach { llanta ->
                                DropdownMenuItem(
                                    text = { Text("${llanta.LlantasMarca} ${llanta.LlantasModelo} — ${llanta.LlantasMedida}") },
                                    onClick = {
                                        llantaSeleccionada = llanta
                                        llantasEncontradas = emptyList()
                                        llantaSearch = "${llanta.LlantasMarca} ${llanta.LlantasModelo}"
                                        suggestionsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Micrófono: cubre búsqueda, número, piso, causa, remanente y comentarios
            item {
                val _ctx = getPlatformContext()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicButton(
                        fields = listOf(
                            FieldDescriptor(title = "Buscar llanta", type = FieldType.TEXT, onFill = { v -> llantaSearch = v }),
                            FieldDescriptor(title = "DOT / NO económico", type = FieldType.TEXT, onFill = { v -> noLlanta = v }),
                            FieldDescriptor(title = "Piso", type = FieldType.TEXT, onFill = { v -> pisoSeleccionado = v }),
                            FieldDescriptor(title = "Causa de desecho", type = FieldType.TEXT, onFill = { v -> causaSeleccionada = v }),
                            FieldDescriptor(title = "Remanente", type = FieldType.NUMBER, onFill = { v -> remanente = v }),
                            FieldDescriptor(title = "Comentarios", type = FieldType.TEXT, onFill = { v -> comentarios = v })
                        ),
                        modifier = Modifier.size(44.dp),
                        startListeningAction = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.start(_ctx) },
                        stopListening = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.stopAndGet() },
                        onOverlayRequested = { recording, centerWindow, circleDp ->
                            overlayRecording = recording
                            overlayCenterWindow = centerWindow
                            overlaySizeDp = circleDp
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Usar voz para completar campos", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }

            item {
                OutlinedTextField(
                    value = noLlanta,
                    onValueChange = { noLlanta = it },
                    label = { Text("Número de llanta *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = validationAttempted && noLlanta.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = pisoSeleccionado,
                        onValueChange = {},
                        label = { Text("Piso *") },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        isError = validationAttempted && pisoSeleccionado.isBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        pisoOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    pisoSeleccionado = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Ubicación select (antes de causa de desecho)
            item {
                var expandedU by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedU,
                    onExpandedChange = { expandedU = !expandedU }
                ) {
                    OutlinedTextField(
                        value = ubicacionSeleccionada,
                        onValueChange = { ubicacionSeleccionada = it },
                        label = { Text("Ubicación (zona) *") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedU) },
                        isError = validationAttempted && ubicacionSeleccionada.isBlank(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedU, onDismissRequest = { expandedU = false }) {
                        ubicacionOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { 
                                ubicacionSeleccionada = option
                                // Limpiar causa seleccionada si no es válida para la nueva ubicación
                                val causasNuevas = ubicacionToCausas[option] ?: emptyList()
                                if (causaSeleccionada !in causasNuevas) {
                                    causaSeleccionada = ""
                                }
                                expandedU = false 
                            })
                        }
                    }
                }
            }

            // Causa de desecho
            item {
                var expanded by remember { mutableStateOf(false) }
                // Filtrar causas según la ubicación seleccionada
                val causasFiltradas = remember(ubicacionSeleccionada) {
                    if (ubicacionSeleccionada.isBlank()) {
                        emptyList()
                    } else {
                        ubicacionToCausas[ubicacionSeleccionada] ?: emptyList()
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = causaSeleccionada,
                        onValueChange = { causaSeleccionada = it },
                        label = { Text("Causa de desecho *") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        isError = validationAttempted && causaSeleccionada.isBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (causasFiltradas.isEmpty()) {
                            DropdownMenuItem(text = { Text("Seleccione una ubicación primero") }, onClick = { expanded = false })
                        } else {
                            causasFiltradas.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        causaSeleccionada = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                BluetoothCaliperAutoListener(
                    onMeasurementReceived = { value ->
                        remanente = value.toString()
                    }
                )
                OutlinedTextField(
                    value = remanente,
                    onValueChange = { remanente = it },
                    label = { Text("Remanente (mm) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validationAttempted && remanente.toFloatOrNull() == null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = comentarios,
                    onValueChange = { comentarios = it },
                    label = { Text("Comentarios *") },
                    maxLines = 3,
                    isError = validationAttempted && comentarios.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sección de fotos
            item {
                Text(
                    "Fotos:", 
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // Fotos: icon-only picker + preview (imagen encima)
            item {
                var isLoadingFile1 by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Foto 1 column
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showPhotoPickerDialog = true },
                                    enabled = !isLoadingFile1,
                                    modifier = Modifier.size(84.dp)
                                ) {
                                    if (isLoadingFile1) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.PhotoCamera, 
                                            contentDescription = "Seleccionar imágenes", 
                                            modifier = Modifier.size(52.dp), 
                                            tint = if (foto1?.isNotBlank() == true || foto2?.isNotBlank() == true) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Foto 2 column
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                // Second photo button removed, using shared dialog
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        validationAttempted = true
                        // Verificar que todos los campos obligatorios estén llenos
                        // Los campos de ID son autoincrementales por el backend, pero los mandamos seguros
                        val pisoSeguro = if (pisoSeleccionado.isNotBlank()) pisoSeleccionado else "Original"
                        val causaSegura = if (causaSeleccionada.isNotBlank()) causaSeleccionada else "CORTE EN PISO"
                        val noLlantaSeguro = if (noLlanta.isNotBlank()) noLlanta else "1"
                        val remanenteSeguro = if (remanente.toFloatOrNull() != null) remanente.toFloatOrNull()!! else 1.0f
                        val comentariosSeguros = if (comentarios.isNotBlank()) comentarios else "Ninguno"
                        
                        if (llantaSeleccionada != null && noLlanta.isNotBlank() && pisoSeleccionado.isNotBlank() && ubicacionSeleccionada.isNotBlank() && causaSeleccionada.isNotBlank() && remanente.toFloatOrNull() != null && comentarios.isNotBlank()) {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val loc = getLastKnownLocation()
                                    val causaFinal = causaSeleccionada.trim()
                                    println("[NuevaLlantaDesechoScreen] Guardando llanta: causaFinal='$causaFinal', ubicacion='${ubicacionSeleccionada.trim()}'")
                                    if (existingLlanta == null) {
                                        repository.createLlantaDesecho(
                                            LlantasDesechoCreateRequest(
                                                PruebasDesecho_idPruebasDesecho = pruebaDesecho.idPruebasDesecho,
                                                Llantas_idLlantas = llantaSeleccionada!!.idLlantas,
                                                LlantasDesechoNoLlanta = noLlantaSeguro,
                                                LlantasDesechoPiso = pisoSeguro,
                                                LlantasDesechoCausaDes = causaFinal,
                                                LlantasDesechoUbi = ubicacionSeleccionada.takeIf { it.isNotBlank() },
                                                Latitud = loc?.latitude,
                                                Longitud = loc?.longitude,
                                                LlantasDesechoRemanente = remanenteSeguro,
                                                Usuarios_idUsuarios = currentUser?.idUsuarios,
                                                LlantasDesechoFecha = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd"),
                                                LlantasDesechoComentarios = comentariosSeguros,
                                                LlantasDesechoFoto1 = foto1,
                                                LlantasDesechoFoto2 = foto2
                                            )
                                        ).onSuccess {
                                            onLlantaCreada()
                                        }.onFailure {
                                            // Manejar error
                                            isLoading = false
                                            errorMessage = "Error guardando la llanta: ${it.message ?: "Error desconocido"}"
                                        }
                                    } else {
                                        // Update existing
                                        repository.updateLlantaDesecho(
                                            existingLlanta.idLlantasDesecho,
                                            LlantasDesechoUpdateRequest(
                                                PruebasDesecho_idPruebasDesecho = pruebaDesecho.idPruebasDesecho,
                                                Llantas_idLlantas = llantaSeleccionada!!.idLlantas,
                                                LlantasDesechoNoLlanta = noLlantaSeguro,
                                                LlantasDesechoPiso = pisoSeguro,
                                                LlantasDesechoCausaDes = causaFinal,
                                                LlantasDesechoUbi = ubicacionSeleccionada.takeIf { it.isNotBlank() },
                                                Latitud = loc?.latitude,
                                                Longitud = loc?.longitude,
                                                LlantasDesechoRemanente = remanenteSeguro,
                                                LlantasDesechoFecha = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd"),
                                                LlantasDesechoComentarios = comentariosSeguros,
                                                LlantasDesechoFoto1 = foto1,
                                                LlantasDesechoFoto2 = foto2
                                            )
                                        ).onSuccess {
                                            onLlantaCreada()
                                        }.onFailure {
                                            isLoading = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    isLoading = false
                                    errorMessage = e.message ?: "Error inesperado"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    enabled = llantaSeleccionada != null && noLlanta.isNotBlank() && pisoSeleccionado.isNotBlank() && ubicacionSeleccionada.isNotBlank() && causaSeleccionada.isNotBlank() && remanente.toFloatOrNull() != null && comentarios.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text(if (existingLlanta == null) "Agregar Llanta de Desecho" else "Guardar Cambios", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            // Host overlay removed temporarily (was causing compile-time parse issues).
        }

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = errorMessage ?: "Error inesperado",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (showPhotoPickerDialog) {
            PhotoPickerDialog(
                photoSlots = listOf(
                    PhotoSlot(
                        index = 0,
                        base64Data = foto1 ?: "",
                        fileName = foto1Nombre ?: "",
                        fileSize = foto1Tamano ?: 0L
                    ),
                    PhotoSlot(
                        index = 1,
                        base64Data = foto2 ?: "",
                        fileName = foto2Nombre ?: "",
                        fileSize = foto2Tamano ?: 0L
                    )
                ),
                onPhotosChanged = { updatedSlots ->
                    val slot1 = updatedSlots.getOrNull(0)
                    val slot2 = updatedSlots.getOrNull(1)
                    foto1 = slot1?.base64Data?.takeIf { it.isNotBlank() }
                    foto1Nombre = slot1?.fileName?.takeIf { it.isNotBlank() }
                    foto1Tamano = slot1?.fileSize?.takeIf { it > 0 }
                    foto2 = slot2?.base64Data?.takeIf { it.isNotBlank() }
                    foto2Nombre = slot2?.fileName?.takeIf { it.isNotBlank() }
                    foto2Tamano = slot2?.fileSize?.takeIf { it > 0 }
                },
                onDismiss = { showPhotoPickerDialog = false },
                filePickerUtils = filePicker
            )
        }
    }
}
}

