package com.megatransportes.yokoh.ui.screens.inspecciones

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.ui.components.PhotoPickerDialog
import com.megatransportes.yokoh.ui.components.PhotoSlot
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.megatransportes.yokoh.utils.FilePickerUtils
import com.megatransportes.yokoh.utils.formatDateOnly
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.createFilePickerUtils
import com.megatransportes.yokoh.utils.InitializeFilePickerIfNeeded
import com.megatransportes.yokoh.utils.ErrorUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import com.megatransportes.yokoh.utils.NumberFormatter
import com.megatransportes.yokoh.ui.screens.parametros.LlantasAdminScreen
import com.megatransportes.yokoh.ui.screens.parametros.TipoVehiculosAdminScreen
import com.megatransportes.yokoh.ui.screens.parametros.ParametrosListScreen
import com.megatransportes.yokoh.ui.screens.parametros.EditParametroScreen
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import com.megatransportes.yokoh.ui.components.MicButton
import com.megatransportes.yokoh.ui.components.FieldDescriptor
import com.megatransportes.yokoh.ui.components.FieldType
import com.megatransportes.yokoh.utils.getPlatformContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspeccionVehicularScreen(
    repository: YokohamaRepository,
    pruebaInspeccion: PruebaInspeccion,
    flota: Flota,
    vehiculoInspeccionExisting: VehiculoInspeccion? = null,
    onInspeccionRegistrada: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onOpenLlantasAdmin: (() -> Unit)? = null,
    onOpenAddParametro: (llantaId: Int) -> Unit = {},
    onOpenParametrosList: (llantaIds: List<Int>) -> Unit = {}
) {
    var tipoVehiculos by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }
    var llantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var parametros by remember { mutableStateOf<List<Parametro>>(emptyList()) }
    var selectedTipoVehiculo by remember { mutableStateOf<TipoVehiculo?>(null) }
    var vehiculoInspeccionNo by remember { mutableStateOf("") }
    var showTipoVehiculoDropdown by remember { mutableStateOf(false) }
    var tipoVehiculoSearch by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationAttempted by remember { mutableStateOf(false) }
    var debugLog by remember { mutableStateOf<String?>(null) }
    var showDebug by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isEditing = vehiculoInspeccionExisting != null
    var showLlantasAdmin by remember { mutableStateOf(false) }
    var showTipoVehiculosAdmin by remember { mutableStateOf(false) }
    var showParametrosDialog by remember { mutableStateOf(false) }
    var suggestedLlantaIdsForParametros by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showEditParametroDialog by remember { mutableStateOf(false) }
    var selectedLlantaIdForParametro by remember { mutableStateOf<Int?>(null) }
    val currentUser by repository.currentUser.collectAsState()

    // Inicializar FilePicker
    val filePicker = remember { createFilePickerUtils() }
    InitializeFilePickerIfNeeded()

    // Estado para las llantas de inspección
    var llantasInspeccionData by remember {
        mutableStateOf<List<LlantaInspeccionFormData>>(emptyList())
    }
    var loadingLlantasInspeccion by remember { mutableStateOf(false) }
    
    // Estado para la última llanta seleccionada (para auto-llenar)
    var lastSelectedLlanta by remember { mutableStateOf<Llanta?>(null) }

    // Cargar tipos de vehículos y llantas (usar directamente el scope de LaunchedEffect)
    LaunchedEffect(key1 = Unit) {
        repository.getTiposVehiculos()
            .onSuccess { result -> tipoVehiculos = result }
            .onFailure { _ -> errorMessage = "Error cargando tipos de vehículos" }

        repository.getLlantasByFlota(flota.idFlotas)
            .onSuccess { result -> llantas = result }
            .onFailure { _ -> errorMessage = "Error cargando llantas" }

        // Cargar parámetros de la flota para validar selección de llantas
        repository.getParametrosByFlotaId(flota.idFlotas)
            .onSuccess { result -> parametros = result }
            .onFailure { _ -> /* silencioso, se mostrará advertencia en envío/selección */ }

        // If opened for editing an existing VehiculoInspeccion, prefill fields and load llantasInspeccion
        vehiculoInspeccionExisting?.let { existing ->
            // Prefill basic fields
            vehiculoInspeccionNo = existing.VehiculoInspeccionNo
            // Try to find the tipo object by id or name
            val foundTipo = tipoVehiculos.firstOrNull { it.idTipoVehiculos == existing.TipoVehiculos_idTipoVehiculos }
                ?: tipoVehiculos.firstOrNull { it.TipoVehiculosNombre.equals(existing.TipoVehiculosNombre ?: "", ignoreCase = true) }
            if (foundTipo != null) selectedTipoVehiculo = foundTipo

            // Start a background prefetch so the request can complete even if the composable is cancelled
            repository.prefetchLlantasInspeccionByVehiculoId(existing.idVehiculoInspeccion)

            // Load llantas inspeccion for this vehiculoInspeccion (guarded to avoid overlapping requests)
            if (!loadingLlantasInspeccion) {
                loadingLlantasInspeccion = true
                try {
                    println("[UI] Loading llantasInspeccion for vehiculoInspeccionId=${existing.idVehiculoInspeccion}")
                    val llRes = repository.getLlantasInspeccionByVehiculoId(existing.idVehiculoInspeccion)
                    println("[UI] llRes received: $llRes")
                    llRes.onSuccess { list ->
                        println("[UI] llRes success list.size=${list.size}")
                        // Map to form data including id so we can update
                        llantasInspeccionData = list.map { li ->
                                val foto1Data = li.LlantasInspeccionFoto ?: ""
                                val foto2Data = li.LlantasInspeccionFoto2 ?: ""
                                LlantaInspeccionFormData(
                                id = li.idLlantasInspeccion,
                                selectedLlanta = llantas.firstOrNull { it.idLlantas == li.Llantas_idLlantas },
                                piso = li.LlantasInspeccionPiso ?: "",
                                dot = li.LlantasInspeccionDOT ?: "",
                                    // Default presion to "0" for compatibility with semaforo behavior
                                    presion = li.LlantasInspeccionPresion.toString(),
                                    previousPresion = li.LlantasInspeccionPresion.toString(),
                                        // Prefer explicit vigia flag from backend; fallback to presion==0 for older records
                                    vigia = (li.LlantasInspeccionVigia == 1) || (li.LlantasInspeccionPresion == 0),
                                mm1 = li.LlantasInspeccionMm1.toString(),
                                mm2 = li.LlantasInspeccionMm2.toString(),
                                mm3 = li.LlantasInspeccionMm3.toString(),
                                mm4 = li.LlantasInspeccionMm4.toString(),
                                desgaste = li.LlantasInspeccionDesgaste ?: "",
                                condicionPeligrosa = (li.LlantasInspeccionCondPel == 1),
                                observacion = li.LlantasInspeccionObservacion ?: "",
                                foto1 = foto1Data,
                                foto1Nombre = if (foto1Data.isNotBlank()) "foto_${li.idLlantasInspeccion}_1.jpg" else null,
                                foto1Tamano = if (foto1Data.isNotBlank()) ((foto1Data.length * 3L) / 4L) else null,
                                foto2 = foto2Data,
                                foto2Nombre = if (foto2Data.isNotBlank()) "foto_${li.idLlantasInspeccion}_2.jpg" else null,
                                foto2Tamano = if (foto2Data.isNotBlank()) ((foto2Data.length * 3L) / 4L) else null,
                                comentarios = li.LlantasInspeccionComentario ?: ""
                            )
                        }
                    }.onFailure { err ->
                        val msg = err.message ?: err.toString()
                        if (err is CancellationException || err.cause is CancellationException || msg.contains("coroutine scope left the composition", ignoreCase = true)) {
                            // probable cancellation due to composition disposal — ignore silently
                        } else {
                            // capture debug info for inspection
                            debugLog = runCatching { err.stackTraceToString() }.getOrNull() ?: msg
                            errorMessage = "Error cargando llantas de inspección: ${err.message}"
                        }
                    }
                } finally {
                    loadingLlantasInspeccion = false
                }
            }
        }
    }

    // Cuando se selecciona un tipo de vehículo (en creación), recrear formularios con la cantidad correcta
    LaunchedEffect(selectedTipoVehiculo) {
        if (!isEditing) {
            val tipo = selectedTipoVehiculo
            llantasInspeccionData = if (tipo != null) {
                List(tipo.TipoVehiculosCantLlantas) { LlantaInspeccionFormData() }
            } else {
                emptyList()
            }
        }
    }

    // Si estamos editando y `tipoVehiculos` o `llantas` se cargaron después, asegurarnos de prellenar
    LaunchedEffect(key1 = tipoVehiculos, key2 = llantas, key3 = vehiculoInspeccionExisting) {
        val existing = vehiculoInspeccionExisting ?: return@LaunchedEffect

        // Resolver y asignar el tipo si aún no está asignado
        if (selectedTipoVehiculo == null && tipoVehiculos.isNotEmpty()) {
            val foundTipo = tipoVehiculos.firstOrNull { it.idTipoVehiculos == existing.TipoVehiculos_idTipoVehiculos }
                ?: tipoVehiculos.firstOrNull { it.TipoVehiculosNombre.equals(existing.TipoVehiculosNombre ?: "", ignoreCase = true) }
            if (foundTipo != null) selectedTipoVehiculo = foundTipo
        }

        // Si aún no cargamos los formularios de llanta, solicitarlos al repositorio y mapearlos
        // Ensure background prefetch (if not already started) then read cached or fresh data
        repository.prefetchLlantasInspeccionByVehiculoId(existing.idVehiculoInspeccion)

        if (llantasInspeccionData.isEmpty() && !loadingLlantasInspeccion) {
            loadingLlantasInspeccion = true
            try {
                val llRes = repository.getLlantasInspeccionByVehiculoId(existing.idVehiculoInspeccion)
                llRes.onSuccess { list ->
                    llantasInspeccionData = list.map { li ->
                        val foto1Data = li.LlantasInspeccionFoto ?: ""
                        val foto2Data = li.LlantasInspeccionFoto2 ?: ""
                        LlantaInspeccionFormData(
                            id = li.idLlantasInspeccion,
                            selectedLlanta = llantas.firstOrNull { it.idLlantas == li.Llantas_idLlantas },
                            piso = li.LlantasInspeccionPiso ?: "",
                            dot = li.LlantasInspeccionDOT ?: "",
                            presion = li.LlantasInspeccionPresion.toString(),
                            previousPresion = li.LlantasInspeccionPresion.toString(),
                            vigia = (li.LlantasInspeccionVigia == 1) || (li.LlantasInspeccionPresion == 0),
                            mm1 = li.LlantasInspeccionMm1.toString(),
                            mm2 = li.LlantasInspeccionMm2.toString(),
                            mm3 = li.LlantasInspeccionMm3.toString(),
                            mm4 = li.LlantasInspeccionMm4.toString(),
                            desgaste = li.LlantasInspeccionDesgaste ?: "",
                            condicionPeligrosa = (li.LlantasInspeccionCondPel == 1),
                            observacion = li.LlantasInspeccionObservacion ?: "",
                            foto1 = foto1Data,
                            foto1Nombre = if (foto1Data.isNotBlank()) "foto_${li.idLlantasInspeccion}_1.jpg" else null,
                            foto1Tamano = if (foto1Data.isNotBlank()) ((foto1Data.length * 3L) / 4L) else null,
                            foto2 = foto2Data,
                            foto2Nombre = if (foto2Data.isNotBlank()) "foto_${li.idLlantasInspeccion}_2.jpg" else null,
                            foto2Tamano = if (foto2Data.isNotBlank()) ((foto2Data.length * 3L) / 4L) else null,
                            comentarios = li.LlantasInspeccionComentario ?: ""
                        )
                    }
                }.onFailure { err ->
                    val msg = err.message ?: err.toString()
                    if (err is CancellationException || err.cause is CancellationException || msg.contains("coroutine scope left the composition", ignoreCase = true)) {
                        // ignore silently
                    } else {
                        debugLog = runCatching { err.stackTraceToString() }.getOrNull() ?: msg
                        errorMessage = "Error cargando llantas de inspección: ${err.message}"
                    }
                }
            } finally {
                loadingLlantasInspeccion = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Inspección - ${pruebaInspeccion.PruebaInspeccionTitulo}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Flota") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            OutlinedButton(
                onClick = { /* no-op header */ },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, Color.Red),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${flota.FlotasNombre}".uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Inspección: ${pruebaInspeccion.PruebaInspeccionTitulo}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatDateOnly(pruebaInspeccion.PruebaInspeccionFecha)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Información del vehículo
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Información del Vehículo",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Búsqueda de tipo de vehículo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = showTipoVehiculoDropdown,
                            onExpandedChange = { if (!isEditing) showTipoVehiculoDropdown = !showTipoVehiculoDropdown },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedTipoVehiculo?.TipoVehiculosNombre ?: tipoVehiculoSearch,
                                onValueChange = {
                                    tipoVehiculoSearch = it
                                    if (selectedTipoVehiculo != null && !isEditing) {
                                        selectedTipoVehiculo = null
                                    }
                                },
                                label = { Text("Tipo de Vehículo") },
                                trailingIcon = {
                                    if (selectedTipoVehiculo != null && !isEditing) {
                                        IconButton(onClick = { selectedTipoVehiculo = null }) {
                                            Icon(Icons.Default.Clear, "Limpiar")
                                        }
                                    } else if (!isEditing) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTipoVehiculoDropdown)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                readOnly = isEditing || selectedTipoVehiculo != null
                            )

                            if (showTipoVehiculoDropdown) {
                                ExposedDropdownMenu(
                                    expanded = showTipoVehiculoDropdown,
                                    onDismissRequest = { showTipoVehiculoDropdown = false }
                                ) {
                                    tipoVehiculos.filter { tipo ->
                                        tipo.TipoVehiculosNombre.contains(tipoVehiculoSearch, ignoreCase = true)
                                    }.forEach { tipo ->
                                        DropdownMenuItem(
                                            text = { Text(tipo.TipoVehiculosNombre) },
                                            onClick = {
                                                if (!isEditing) {
                                                    selectedTipoVehiculo = tipo
                                                    showTipoVehiculoDropdown = false
                                                    tipoVehiculoSearch = ""
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        IconButton(
                            onClick = { showTipoVehiculosAdmin = true },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Administrar Tipos de Vehículos",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Número del vehículo
                    OutlinedTextField(
                        value = vehiculoInspeccionNo,
                        onValueChange = { if (!isEditing) vehiculoInspeccionNo = it },
                        label = { Text("Número del Vehículo / Placas") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isEditing
                    )
                }
            }

            // Formularios para las llantas
            if (selectedTipoVehiculo != null) {
                val tipo = selectedTipoVehiculo!!
                Text(
                    text = "Llantas del Vehículo (${tipo.TipoVehiculosCantLlantas} llantas)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                Column {
                    llantasInspeccionData.forEachIndexed { index, data ->
                        LlantaInspeccionForm(
                            index = index + 1,
                            data = data,
                            llantas = llantas,
                            medidasConParametro = parametros.mapNotNull { it.LlantasMedida }.toSet(),
                            filePicker = filePicker,
                            onOpenLlantasAdmin = { showLlantasAdmin = true },
                            showValidationErrors = validationAttempted,
                            onDataChange = { newData ->
                                llantasInspeccionData = llantasInspeccionData.toMutableList().apply {
                                    this[index] = newData
                                }

                                // Si se seleccionó una nueva llanta, actualizar la última seleccionada
                                if (newData.selectedLlanta != null && newData.selectedLlanta != data.selectedLlanta) {
                                    lastSelectedLlanta = newData.selectedLlanta

                                    // Auto-llenar las formas siguientes que no tengan llanta seleccionada
                                    val updatedList = llantasInspeccionData.toMutableList()
                                    for (i in (index + 1) until updatedList.size) {
                                        updatedList[i] = updatedList[i].copy(
                                            selectedLlanta = newData.selectedLlanta,
                                            mm1 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm1,
                                            mm2 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm2,
                                            mm3 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm3,
                                            mm4 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm4
                                        )
                                    }
                                    llantasInspeccionData = updatedList
                                }
                            }
                        )

                        if (index != llantasInspeccionData.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                thickness = 2.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            } else if (isEditing && llantasInspeccionData.isNotEmpty()) {
                // If editing but tipo hasn't been resolved yet, still show the saved llantas
                Text(
                    text = "Llantas del Vehículo (${llantasInspeccionData.size} llantas)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                Column {
                    llantasInspeccionData.forEachIndexed { index, data ->
                        LlantaInspeccionForm(
                            index = index + 1,
                            data = data,
                            llantas = llantas,
                            medidasConParametro = parametros.mapNotNull { it.LlantasMedida }.toSet(),
                            filePicker = filePicker,
                            onOpenLlantasAdmin = { showLlantasAdmin = true },
                            showValidationErrors = validationAttempted,
                            onDataChange = { newData ->
                                llantasInspeccionData = llantasInspeccionData.toMutableList().apply {
                                    this[index] = newData
                                }

                                if (newData.selectedLlanta != null && newData.selectedLlanta != data.selectedLlanta) {
                                    lastSelectedLlanta = newData.selectedLlanta
                                }
                            }
                        )

                        if (index != llantasInspeccionData.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                thickness = 2.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Mostrar botón para ver depuración si hay debugLog
            debugLog?.let { dbg ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showDebug = !showDebug }) {
                        Text(if (showDebug) "Ocultar depuración" else "Ver depuración")
                    }
                }

                if (showDebug) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(text = dbg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                        validationAttempted = true
                    // Validar formulario
                    if (selectedTipoVehiculo == null || vehiculoInspeccionNo.isBlank()) {
                        errorMessage = "Complete la información del vehículo"
                        return@Button
                    }

                    val invalidLlanta = llantasInspeccionData.indexOfFirst { !it.isValid() }
                    if (invalidLlanta != -1) {
                        errorMessage = "Complete todos los campos de la llanta ${invalidLlanta + 1}"
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null

                        try {
                            try { println("[UI][Inspeccion] Guardar pulsado: isEditing=$isEditing llantasCount=${llantasInspeccionData.size} ids=${llantasInspeccionData.map { it.id }}") } catch (_: Exception) {}
                            // Validar que todas las llantas tengan parámetros
                            val parametrosResult = repository.getParametrosByFlotaId(flota.idFlotas)
                            if (parametrosResult.isFailure) {
                                isLoading = false
                                errorMessage = "No se pudieron cargar los parámetros de la flota"
                                return@launch
                            }
                            
                            val parametrosList = parametrosResult.getOrNull() ?: emptyList()
                            val medidasConParametro = parametrosList.mapNotNull { it.LlantasMedida }.toSet()
                            val missingLlantaIds = llantasInspeccionData.mapNotNull { data ->
                                val llanta = data.selectedLlanta
                                val medida = llanta?.LlantasMedida
                                if (llanta != null && medida != null && !medidasConParametro.contains(medida)) llanta.idLlantas to medida else null
                            }.distinctBy { it.second }.map { it.first }
                            if (missingLlantaIds.isNotEmpty()) {
                                isLoading = false
                                errorMessage = null
                                suggestedLlantaIdsForParametros = missingLlantaIds
                                showParametrosDialog = true
                                return@launch
                            }
                            if (vehiculoInspeccionExisting == null) {
                                // CREATE flow (existing is null)
                                val loc = com.megatransportes.yokoh.platform.getLastKnownLocation()
                                val vehiculoInspeccionRequest = VehiculoInspeccionCreateRequest(
                                    pruebasinspeccion_idPruebaInspeccion = pruebaInspeccion.idPruebaInspeccion,
                                    TipoVehiculos_idTipoVehiculos = selectedTipoVehiculo!!.idTipoVehiculos,
                                    VehiculoInspeccionNo = vehiculoInspeccionNo,
                                    latitude = loc?.latitude,
                                    longitude = loc?.longitude,
                                    Usuarios_idUsuarios = currentUser?.idUsuarios
                                )

                                repository.createVehiculoInspeccion(vehiculoInspeccionRequest)
                                    .onSuccess { vehiculoInspeccion ->
                                        // Create all llantas for the new vehiculoInspeccion
                                        val llantasInspeccionRequests = llantasInspeccionData.map { data ->
                                            LlantaInspeccionCreateRequest(
                                                vehiculosinspeccion_idVehiculoInspeccion = vehiculoInspeccion.idVehiculoInspeccion,
                                                Llantas_idLlantas = data.selectedLlanta!!.idLlantas,
                                                LlantasInspeccionMm1 = data.mm1.toFloatOrNull() ?: 0f,
                                                LlantasInspeccionMm2 = data.mm2.toFloatOrNull() ?: 0f,
                                                LlantasInspeccionMm3 = data.mm3.toFloatOrNull() ?: 0f,
                                                LlantasInspeccionMm4 = data.mm4.toFloatOrNull() ?: 0f,
                                                LlantasInspeccionPresion = if (data.vigia) 0 else data.presion.toIntOrNull() ?: 0,
                                                // send vigia numeric flag (0/1)
                                                LlantasInspeccionVigia = if (data.vigia) 1 else 0,
                                                // convert boolean to 0/1 for backend
                                                LlantasInspeccionCondPel = if (data.condicionPeligrosa) 1 else 0,
                                                LlantasInspeccionObservacion = data.observacion.takeIf { it.isNotBlank() },
                                                LlantasInspeccionComentario = data.comentarios.takeIf { it.isNotBlank() },
                                                LlantasInspeccionDOT = data.dot.takeIf { it.isNotBlank() },
                                                LlantasInspeccionPiso = data.piso.takeIf { it.isNotBlank() },
                                                LlantasInspeccionDesgaste = data.desgaste.takeIf { it.isNotBlank() },
                                                LlantasInspeccionFoto = data.foto1.takeIf { it.isNotBlank() },
                                                LlantasInspeccionFoto2 = data.foto2.takeIf { it.isNotBlank() }
                                            )
                                        }

                                        // Debug: log outgoing batch create payload
                                        try {
                                            println("[UI][Inspeccion] createMultipleLlantasInspeccion items=${llantasInspeccionRequests.size} sample=${llantasInspeccionRequests.firstOrNull()}")
                                        } catch (_: Exception) {}

                                        repository.createMultipleLlantasInspeccion(llantasInspeccionRequests)
                                            .onSuccess {
                                                isLoading = false
                                                onInspeccionRegistrada()
                                            }
                                            .onFailure { error ->
                                                isLoading = false
                                                errorMessage = ErrorUtils.userMessage(error, "No se pudo guardar las llantas")
                                            }
                                    }
                                    .onFailure { error ->
                                        isLoading = false
                                        errorMessage = ErrorUtils.userMessage(error, "No se pudo guardar el vehículo")
                                    }
                            } else {
                                // UPDATE flow
                                val vehId = vehiculoInspeccionExisting.idVehiculoInspeccion
                                val body = mapOf(
                                    "TipoVehiculos_idTipoVehiculos" to selectedTipoVehiculo!!.idTipoVehiculos,
                                    "VehiculoInspeccionNo" to vehiculoInspeccionNo
                                )

                                repository.updateVehiculoInspeccion(vehId, body)
                                    .onSuccess {
                                        // For llantas: update ones with id, create batch for ones without id
                                        val toCreate = mutableListOf<LlantaInspeccionCreateRequest>()
                                        val updateJobs = mutableListOf<Result<LlantaInspeccion>>()

                                        for (data in llantasInspeccionData) {
                                            if (data.id != null && data.id > 0) {
                                                val ubody: Map<String, Any?> = mapOf(
                                                    "LlantasInspeccionMm1" to (data.mm1.toFloatOrNull() ?: 0f),
                                                    "LlantasInspeccionMm2" to (data.mm2.toFloatOrNull() ?: 0f),
                                                    "LlantasInspeccionMm3" to (data.mm3.toFloatOrNull() ?: 0f),
                                                    "LlantasInspeccionMm4" to (data.mm4.toFloatOrNull() ?: 0f),
                                                    "LlantasInspeccionPresion" to (if (data.vigia) 0 else data.presion.toIntOrNull() ?: 0),
                                                    "LlantasInspeccionVigia" to (if (data.vigia) 1 else 0),
                                                    // convert boolean to 0/1
                                                    "LlantasInspeccionCondPel" to (if (data.condicionPeligrosa) 1 else 0),
                                                    "LlantasInspeccionObservacion" to data.observacion.takeIf { it.isNotBlank() },
                                                    "LlantasInspeccionComentario" to data.comentarios.takeIf { it.isNotBlank() },
                                                    "LlantasInspeccionDOT" to (data.dot.takeIf { it.isNotBlank() }),
                                                    "LlantasInspeccionPiso" to data.piso.takeIf { it.isNotBlank() },
                                                    "LlantasInspeccionDesgaste" to data.desgaste.takeIf { it.isNotBlank() },
                                                    "LlantasInspeccionFoto" to data.foto1,
                                                        "LlantasInspeccionFoto2" to data.foto2
                                                )
                                                // Debug: log update payload for this llanta
                                                try { println("[UI][Inspeccion] updateLlantaInspeccion id=${data.id} body=${ubody}") } catch (_: Exception) {}
                                                val res = repository.updateLlantaInspeccion(data.id, ubody)
                                                // If update succeeded, reflect server values in UI state so changes appear without reload
                                                if (res.isSuccess) {
                                                    val updated = res.getOrNull()
                                                    if (updated != null) {
                                                        try {
                                                            llantasInspeccionData = llantasInspeccionData.map { existing ->
                                                                if (existing.id == updated.idLlantasInspeccion) {
                                                                    existing.copy(
                                                                        presion = updated.LlantasInspeccionPresion.toString(),
                                                                        previousPresion = updated.LlantasInspeccionPresion.toString(),
                                                                        vigia = (updated.LlantasInspeccionVigia == 1) || (updated.LlantasInspeccionPresion == 0),
                                                                        foto1 = updated.LlantasInspeccionFoto ?: existing.foto1,
                                                                        foto1Nombre = existing.foto1Nombre ?: updated.LlantasInspeccionFoto?.let { "foto_${updated.idLlantasInspeccion}_1.jpg" },
                                                                        foto1Tamano = existing.foto1Tamano ?: updated.LlantasInspeccionFoto?.let { (it.length * 3L) / 4L },
                                                                        foto2 = updated.LlantasInspeccionFoto2 ?: existing.foto2,
                                                                        foto2Nombre = existing.foto2Nombre ?: updated.LlantasInspeccionFoto2?.let { "foto_${updated.idLlantasInspeccion}_2.jpg" },
                                                                        foto2Tamano = existing.foto2Tamano ?: updated.LlantasInspeccionFoto2?.let { (it.length * 3L) / 4L },
                                                                        observacion = updated.LlantasInspeccionObservacion ?: existing.observacion,
                                                                        comentarios = updated.LlantasInspeccionComentario ?: existing.comentarios,
                                                                        dot = updated.LlantasInspeccionDOT ?: existing.dot,
                                                                        piso = updated.LlantasInspeccionPiso ?: existing.piso,
                                                                        desgaste = updated.LlantasInspeccionDesgaste ?: existing.desgaste
                                                                    )
                                                                } else existing
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                                updateJobs.add(res)
                                            } else {
                                                // create
                                                toCreate.add(
                                                    LlantaInspeccionCreateRequest(
                                                        vehiculosinspeccion_idVehiculoInspeccion = vehId,
                                                        Llantas_idLlantas = data.selectedLlanta!!.idLlantas,
                                                        LlantasInspeccionMm1 = data.mm1.toFloatOrNull() ?: 0f,
                                                        LlantasInspeccionMm2 = data.mm2.toFloatOrNull() ?: 0f,
                                                        LlantasInspeccionMm3 = data.mm3.toFloatOrNull() ?: 0f,
                                                        LlantasInspeccionMm4 = data.mm4.toFloatOrNull() ?: 0f,
                                                        LlantasInspeccionPresion = if (data.vigia) 0 else data.presion.toIntOrNull() ?: 0,
                                                        // send vigia numeric flag (0/1)
                                                        LlantasInspeccionVigia = if (data.vigia) 1 else 0,
                                                        // convert boolean to 0/1 when creating new record
                                                        LlantasInspeccionCondPel = if (data.condicionPeligrosa) 1 else 0,
                                                        LlantasInspeccionObservacion = data.observacion.takeIf { it.isNotBlank() },
                                                        LlantasInspeccionComentario = data.comentarios.takeIf { it.isNotBlank() },
                                                        LlantasInspeccionDOT = data.dot.takeIf { it.isNotBlank() },
                                                        LlantasInspeccionPiso = data.piso.takeIf { it.isNotBlank() },
                                                        LlantasInspeccionDesgaste = data.desgaste.takeIf { it.isNotBlank() },
                                                        LlantasInspeccionFoto = data.foto1,
                                                            LlantasInspeccionFoto2 = data.foto2.takeIf { it.isNotBlank() }
                                                    )
                                                )
                                            }
                                        }

                                        // Wait for updates
                                        var anyFail = false
                                        updateJobs.forEach { res -> if (res.isFailure) anyFail = true }

                                        if (toCreate.isNotEmpty()) {
                                            try {
                                                println("[UI][Inspeccion] createMultipleLlantasInspeccion (toCreate) items=${toCreate.size} sample=${toCreate.firstOrNull()}")
                                            } catch (_: Exception) {}
                                            repository.createMultipleLlantasInspeccion(toCreate)
                                                .onSuccess {
                                                    isLoading = false
                                                    onInspeccionRegistrada()
                                                }
                                                .onFailure { err ->
                                                    isLoading = false
                                                    errorMessage = ErrorUtils.userMessage(err, "Error creando nuevas llantas")
                                                }
                                        } else {
                                            isLoading = false
                                            if (anyFail) {
                                                errorMessage = "Algunas actualizaciones fallaron"
                                            } else {
                                                onInspeccionRegistrada()
                                            }
                                        }
                                    }
                                    .onFailure { err ->
                                        isLoading = false
                                        errorMessage = ErrorUtils.userMessage(err, "Error actualizando vehículo")
                                    }
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = ErrorUtils.userMessage(e, "Error inesperado")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = selectedTipoVehiculo != null && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registrando inspección...")
                    }
                } else {
                    Text("Registrar Inspección", style = MaterialTheme.typography.titleMedium)
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Cancelar", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // Dialog para LlantasAdmin
    if (showLlantasAdmin) {
        Dialog(
            onDismissRequest = { showLlantasAdmin = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                LlantasAdminScreen(
                    repository = repository,
                    onBack = { showLlantasAdmin = false }
                )
            }
        }
    }
    
    // Dialog para TipoVehiculosAdmin
    if (showTipoVehiculosAdmin) {
        Dialog(
            onDismissRequest = { showTipoVehiculosAdmin = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                TipoVehiculosAdminScreen(
                    repository = repository,
                    onBack = {
                        showTipoVehiculosAdmin = false
                        // Reload tipos after closing the admin screen
                        coroutineScope.launch {
                            repository.getTiposVehiculos()
                                .onSuccess { result -> tipoVehiculos = result }
                        }
                    }
                )
            }
        }
    }
    
    // Dialog para EditParametroScreen
    if (showEditParametroDialog && selectedLlantaIdForParametro != null) {
        Dialog(
            onDismissRequest = { showEditParametroDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                EditParametroScreen(
                    repository = repository,
                    flota = flota,
                    llantaId = selectedLlantaIdForParametro!!,
                    parametro = null,
                    onParametroSaved = {
                        val savedId = selectedLlantaIdForParametro
                        showEditParametroDialog = false
                        selectedLlantaIdForParametro = null

                        if (savedId != null) {
                            suggestedLlantaIdsForParametros = suggestedLlantaIdsForParametros.filterNot { it == savedId }
                        }

                        // Recargar parámetros para actualizar validaciones
                        coroutineScope.launch {
                            repository.getParametrosByFlotaId(flota.idFlotas)
                                .onSuccess { result -> parametros = result }
                                .onFailure { _ -> }
                        }

                        if (suggestedLlantaIdsForParametros.isEmpty()) {
                            showParametrosDialog = false
                        } else {
                            showParametrosDialog = true
                        }
                    },
                    onBack = { showEditParametroDialog = false },
                    onHome = {}
                )
            }
        }
    }
    if (showParametrosDialog) {
        Dialog(
            onDismissRequest = { showParametrosDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                ParametrosListScreen(
                    repository = repository,
                    flota = flota,
                    onParametroClick = { /* no-op - abrir en Dialog */ },
                    onAddParametroClick = { llantaId ->
                        selectedLlantaIdForParametro = llantaId
                        showEditParametroDialog = true
                    },
                    onBack = { showParametrosDialog = false },
                    suggestedLlantaIds = suggestedLlantaIdsForParametros
                )
            }
        }
    }
}

// Función para formatear el tamaño del archivo
private fun formatFileSize(bytes: Long): String {
    val kilobyte = 1024
    val megabyte = kilobyte * 1024
    return when {
        bytes >= megabyte -> NumberFormatter.formatWithComma(bytes.toDouble() / megabyte, 1) + " MB"
        bytes >= kilobyte -> NumberFormatter.formatWithComma(bytes.toDouble() / kilobyte, 1) + " KB"
        else -> "$bytes bytes"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlantaInspeccionForm(
    index: Int,
    data: LlantaInspeccionFormData,
    llantas: List<Llanta>,
    medidasConParametro: Set<String> = emptySet(),
    filePicker: FilePickerUtils,
    showValidationErrors: Boolean,
    onDataChange: (LlantaInspeccionFormData) -> Unit,
    onOpenLlantasAdmin: (() -> Unit)? = null
) {
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var filteredLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var showExtraFields by remember { mutableStateOf(false) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    val formBorderColor = when {
        data.isValid() -> Color(0xFF2E7D32)
        showValidationErrors -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(2.dp, formBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fila 1: Selector de llanta con número
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "$index", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00A040), fontWeight = FontWeight.Bold)
                }

                // Search input with suggestions (same UX as LlantasVehiculoScreen)
                Column(modifier = Modifier.weight(1f)) {
                    

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = data.selectedLlanta?.let { "${it.LlantasMarca} ${it.LlantasModelo} - ${it.LlantasMedida}\"" } ?: searchText,
                            onValueChange = { newValue ->
                                if (data.selectedLlanta == null) {
                                    searchText = newValue
                                } else {
                                    onDataChange(data.copy(selectedLlanta = null))
                                    searchText = newValue
                                }
                            },
                            label = { Text("Buscar Llanta") },
                            placeholder = { Text("Escribe marca, modelo o medida...") },
                            isError = showValidationErrors && data.selectedLlanta == null,
                            trailingIcon = {
                                if (data.selectedLlanta != null) {
                                    IconButton(onClick = {
                                        onDataChange(data.copy(selectedLlanta = null))
                                        searchText = ""
                                    }) {
                                        Icon(Icons.Default.Clear, "Limpiar")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            readOnly = data.selectedLlanta != null
                        )

                        IconButton(onClick = { onOpenLlantasAdmin?.invoke() }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Administrar llantas",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Filter suggestions
                    LaunchedEffect(searchText) {
                        if (searchText.isNotEmpty() && data.selectedLlanta == null) {
                            val loaded = llantas.filter { llanta ->
                                llanta.LlantasMarca.contains(searchText, ignoreCase = true) ||
                                        llanta.LlantasModelo.contains(searchText, ignoreCase = true) ||
                                        llanta.LlantasMedida.toString().contains(searchText)
                            }.take(10)
                            filteredLlantas = loaded
                            showSuggestions = loaded.isNotEmpty()
                        } else {
                            filteredLlantas = emptyList()
                            showSuggestions = false
                        }
                    }

                    if (showSuggestions && data.selectedLlanta == null) {
                        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                filteredLlantas.forEach { llanta ->
                                    TextButton(onClick = {
                                        onDataChange(data.copy(
                                            selectedLlanta = llanta,
                                            mm1 = llanta.LlantasMm.toString(),
                                            mm2 = llanta.LlantasMm.toString(),
                                            mm3 = llanta.LlantasMm.toString(),
                                            mm4 = llanta.LlantasMm.toString()
                                        ))
                                        searchText = ""
                                        showSuggestions = false
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "${llanta.LlantasMarca} ${llanta.LlantasModelo}", style = MaterialTheme.typography.bodyMedium)
                                                if (!medidasConParametro.contains(llanta.LlantasMedida)) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(text = "(Sin parámetros)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            Text(text = "Medida: ${llanta.LlantasMedida}\" - Precio: $${llanta.LlantasPrecio}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (llanta != filteredLlantas.last()) HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                
            }

            // Advertencia si la llanta seleccionada no tiene parámetros
            if (data.selectedLlanta != null && !medidasConParametro.contains(data.selectedLlanta.LlantasMedida)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sin parámetros",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "La llanta seleccionada no tiene parámetros configurados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Campos MM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Focus requesters for MM fields
                val focusRequester1 = remember { FocusRequester() }
                val focusRequester2 = remember { FocusRequester() }
                val focusRequester3 = remember { FocusRequester() }
                val focusRequester4 = remember { FocusRequester() }
                val fm = LocalFocusManager.current

                var mm1State by remember { mutableStateOf(TextFieldValue(data.mm1)) }
                var mm1Focused by remember { mutableStateOf(false) }
                LaunchedEffect(data.mm1) { if (mm1State.text != data.mm1) mm1State = TextFieldValue(data.mm1) }
                LaunchedEffect(mm1Focused) { if (mm1Focused) mm1State = mm1State.copy(selection = TextRange(0, mm1State.text.length)) }
                OutlinedTextField(
                    value = mm1State,
                    onValueChange = {
                        val t = it.text
                        mm1State = TextFieldValue(t, selection = TextRange(t.length))
                        val newData = data.copy(mm1 = t)
                        
                        // Verificar si la diferencia entre MM es mayor a 1 y actualizar desgaste
                        val mm1Val = t.toFloatOrNull() ?: 0f
                        val mm2Val = newData.mm2.toFloatOrNull() ?: 0f
                        val mm3Val = newData.mm3.toFloatOrNull() ?: 0f
                        val mm4Val = newData.mm4.toFloatOrNull() ?: 0f
                        val valores = listOf(mm1Val, mm2Val, mm3Val, mm4Val).filter { it > 0f }
                        if (valores.isNotEmpty()) {
                            val max = valores.maxOrNull() ?: 0f
                            val min = valores.minOrNull() ?: 0f
                            val diferencia = max - min
                            if (diferencia > 1f) {
                                onDataChange(newData.copy(desgaste = "B. CON DESGASTE IRREGULAR"))
                            } else if (newData.desgaste == "B. CON DESGASTE IRREGULAR") {
                                onDataChange(newData.copy(desgaste = "A. SIN DESGASTE IRREGULAR"))
                            } else {
                                onDataChange(newData)
                            }
                        } else {
                            onDataChange(newData)
                        }
                    },
                    label = { Text("MM1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusRequester2.requestFocus() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester1).onFocusChanged { mm1Focused = it.isFocused },
                    isError = showValidationErrors && (data.mm1.isBlank() || data.mm1.toFloatOrNull() == null)
                )

                var mm2State by remember { mutableStateOf(TextFieldValue(data.mm2)) }
                var mm2Focused by remember { mutableStateOf(false) }
                LaunchedEffect(data.mm2) { if (mm2State.text != data.mm2) mm2State = TextFieldValue(data.mm2) }
                LaunchedEffect(mm2Focused) { if (mm2Focused) mm2State = mm2State.copy(selection = TextRange(0, mm2State.text.length)) }
                OutlinedTextField(
                    value = mm2State,
                    onValueChange = {
                        val t = it.text
                        mm2State = TextFieldValue(t, selection = TextRange(t.length))
                        val newData = data.copy(mm2 = t)
                        
                        // Verificar si la diferencia entre MM es mayor a 1 y actualizar desgaste
                        val mm1Val = newData.mm1.toFloatOrNull() ?: 0f
                        val mm2Val = t.toFloatOrNull() ?: 0f
                        val mm3Val = newData.mm3.toFloatOrNull() ?: 0f
                        val mm4Val = newData.mm4.toFloatOrNull() ?: 0f
                        val valores = listOf(mm1Val, mm2Val, mm3Val, mm4Val).filter { it > 0f }
                        if (valores.isNotEmpty()) {
                            val max = valores.maxOrNull() ?: 0f
                            val min = valores.minOrNull() ?: 0f
                            val diferencia = max - min
                            if (diferencia > 1f) {
                                onDataChange(newData.copy(desgaste = "B. CON DESGASTE IRREGULAR"))
                            } else if (newData.desgaste == "B. CON DESGASTE IRREGULAR") {
                                onDataChange(newData.copy(desgaste = "A. SIN DESGASTE IRREGULAR"))
                            } else {
                                onDataChange(newData)
                            }
                        } else {
                            onDataChange(newData)
                        }
                    },
                    label = { Text("MM2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusRequester3.requestFocus() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester2).onFocusChanged { mm2Focused = it.isFocused },
                    isError = showValidationErrors && (data.mm2.isBlank() || data.mm2.toFloatOrNull() == null)
                )

                var mm3State by remember { mutableStateOf(TextFieldValue(data.mm3)) }
                var mm3Focused by remember { mutableStateOf(false) }
                LaunchedEffect(data.mm3) { if (mm3State.text != data.mm3) mm3State = TextFieldValue(data.mm3) }
                LaunchedEffect(mm3Focused) { if (mm3Focused) mm3State = mm3State.copy(selection = TextRange(0, mm3State.text.length)) }
                OutlinedTextField(
                    value = mm3State,
                    onValueChange = {
                        val t = it.text
                        mm3State = TextFieldValue(t, selection = TextRange(t.length))
                        val newData = data.copy(mm3 = t)
                        
                        // Verificar si la diferencia entre MM es mayor a 1 y actualizar desgaste
                        val mm1Val = newData.mm1.toFloatOrNull() ?: 0f
                        val mm2Val = newData.mm2.toFloatOrNull() ?: 0f
                        val mm3Val = t.toFloatOrNull() ?: 0f
                        val mm4Val = newData.mm4.toFloatOrNull() ?: 0f
                        val valores = listOf(mm1Val, mm2Val, mm3Val, mm4Val).filter { it > 0f }
                        if (valores.isNotEmpty()) {
                            val max = valores.maxOrNull() ?: 0f
                            val min = valores.minOrNull() ?: 0f
                            val diferencia = max - min
                            if (diferencia > 1f) {
                                onDataChange(newData.copy(desgaste = "B. CON DESGASTE IRREGULAR"))
                            } else if (newData.desgaste == "B. CON DESGASTE IRREGULAR") {
                                onDataChange(newData.copy(desgaste = "A. SIN DESGASTE IRREGULAR"))
                            } else {
                                onDataChange(newData)
                            }
                        } else {
                            onDataChange(newData)
                        }
                    },
                    label = { Text("MM3") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusRequester4.requestFocus() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester3).onFocusChanged { mm3Focused = it.isFocused },
                    isError = showValidationErrors && (data.mm3.isBlank() || data.mm3.toFloatOrNull() == null)
                )

                var mm4State by remember { mutableStateOf(TextFieldValue(data.mm4)) }
                var mm4Focused by remember { mutableStateOf(false) }
                LaunchedEffect(data.mm4) { if (mm4State.text != data.mm4) mm4State = TextFieldValue(data.mm4) }
                LaunchedEffect(mm4Focused) { if (mm4Focused) mm4State = mm4State.copy(selection = TextRange(0, mm4State.text.length)) }
                OutlinedTextField(
                    value = mm4State,
                    onValueChange = {
                        val t = it.text
                        mm4State = TextFieldValue(t, selection = TextRange(t.length))
                        val newData = data.copy(mm4 = t)
                        
                        // Verificar si la diferencia entre MM es mayor a 1 y actualizar desgaste
                        val mm1Val = newData.mm1.toFloatOrNull() ?: 0f
                        val mm2Val = newData.mm2.toFloatOrNull() ?: 0f
                        val mm3Val = newData.mm3.toFloatOrNull() ?: 0f
                        val mm4Val = t.toFloatOrNull() ?: 0f
                        val valores = listOf(mm1Val, mm2Val, mm3Val, mm4Val).filter { it > 0f }
                        if (valores.isNotEmpty()) {
                            val max = valores.maxOrNull() ?: 0f
                            val min = valores.minOrNull() ?: 0f
                            val diferencia = max - min
                            if (diferencia > 1f) {
                                onDataChange(newData.copy(desgaste = "B. CON DESGASTE IRREGULAR"))
                            } else if (newData.desgaste == "B. CON DESGASTE IRREGULAR") {
                                onDataChange(newData.copy(desgaste = "A. SIN DESGASTE IRREGULAR"))
                            } else {
                                onDataChange(newData)
                            }
                        } else {
                            onDataChange(newData)
                        }
                    },
                    label = { Text("MM4") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { fm.clearFocus() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester4).onFocusChanged { mm4Focused = it.isFocused },
                    isError = showValidationErrors && (data.mm4.isBlank() || data.mm4.toFloatOrNull() == null)
                )
            }

            // Fila 2: Presión, Condición peligrosa, Foto, Vigía, Desplegar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Presión (sin etiqueta, el usuario sabrá por contexto)
                val _ctx = getPlatformContext()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MicButton(
                        fields = listOf(
                            FieldDescriptor(title = "Presión", type = FieldType.NUMBER, onFill = { v -> onDataChange(data.copy(presion = v)) }),
                            FieldDescriptor(title = "MM1", type = FieldType.NUMBER, onFill = { v -> onDataChange(data.copy(mm1 = v)) }),
                            FieldDescriptor(title = "MM2", type = FieldType.NUMBER, onFill = { v -> onDataChange(data.copy(mm2 = v)) }),
                            FieldDescriptor(title = "MM3", type = FieldType.NUMBER, onFill = { v -> onDataChange(data.copy(mm3 = v)) }),
                            FieldDescriptor(title = "MM4", type = FieldType.NUMBER, onFill = { v -> onDataChange(data.copy(mm4 = v)) }),
                            FieldDescriptor(title = "Vigia", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(vigia = v.trim().lowercase().startsWith("s"))) }),
                            FieldDescriptor(title = "Condición peligrosa", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(condicionPeligrosa = v.trim().lowercase().startsWith("s"))) }),
                            FieldDescriptor(title = "Comentarios", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(comentarios = v)) })
                        ),
                        modifier = Modifier.size(40.dp),
                        startListeningAction = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.start(_ctx) },
                        stopListening = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.stopAndGet() }
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Presión", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = if (data.vigia) "0" else data.presion,
                            onValueChange = {
                                if (!data.vigia) {
                                    val raw = it
                                    val num = raw.replace(',','.') .toFloatOrNull()
                                    val clamped = if (num != null && num > 160f) "160" else raw
                                    onDataChange(data.copy(presion = clamped))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(64.dp).height(48.dp),
                            enabled = !data.vigia,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            isError = showValidationErrors && (
                                if (data.vigia) {
                                    data.presion.isNotBlank() && (data.presion.toIntOrNull() == null || (data.presion.toIntOrNull() ?: 0) > 160)
                                } else {
                                    data.presion.isBlank() || data.presion.toIntOrNull() == null || (data.presion.toIntOrNull() ?: 0) > 160
                                }
                            )
                        )
                    }
                }

                // Condición peligrosa (ícono de advertencia)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Peligro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                        onClick = { onDataChange(data.copy(condicionPeligrosa = !data.condicionPeligrosa)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Condición peligrosa",
                            tint = if (data.condicionPeligrosa) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Foto (solo icono)
                IconButton(
                    onClick = { showPhotoPickerDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.PhotoCamera,
                        contentDescription = "Foto",
                        tint = if (data.foto1.isNotBlank() || data.foto2.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Vigía (solo checkbox)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Vigía", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Checkbox(
                        checked = data.vigia,
                        onCheckedChange = { checked ->
                            // Match Semaforo behaviour: when checked set presion to "0", when unchecked keep existing presion
                            onDataChange(data.copy(vigia = checked, presion = if (checked) "0" else data.presion))
                        }
                    )
                }

                // Botón para plegar/desplegar campos extra
                IconButton(onClick = { showExtraFields = !showExtraFields }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (showExtraFields) "Ocultar" else "Mostrar",
                        modifier = Modifier.graphicsLayer(rotationZ = if (showExtraFields) 45f else 0f),
                        tint = Color(0xFF00A040)
                    )
                }
            }

            // Campos extra (desplegables)
            AnimatedVisibility(visible = showExtraFields) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Piso
                    var showPisoDropdown by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showPisoDropdown,
                        onExpandedChange = { showPisoDropdown = !showPisoDropdown }
                    ) {
                        OutlinedTextField(
                            value = data.piso,
                            onValueChange = {},
                            label = { Text("Piso") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPisoDropdown) },
                            isError = showValidationErrors && data.piso.isBlank(),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = true
                        )
                        ExposedDropdownMenu(
                            expanded = showPisoDropdown,
                            onDismissRequest = { showPisoDropdown = false }
                        ) {
                            listOf("ORIGINAL", "VITALIZADO", "VIPAL", "MICHELIN", "HULES BANDA", "GALGO", "CONTINENTAL", "BANDAG").forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        onDataChange(data.copy(piso = opcion))
                                        showPisoDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // DOT
                    OutlinedTextField(
                        value = data.dot,
                        onValueChange = { onDataChange(data.copy(dot = it)) },
                        label = { Text("DOT/No. Eco") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Desgaste
                    var showDesgasteDropdown by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showDesgasteDropdown,
                        onExpandedChange = { showDesgasteDropdown = !showDesgasteDropdown }
                    ) {
                        OutlinedTextField(
                            value = data.desgaste,
                            onValueChange = {},
                            label = { Text("Desgaste") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDesgasteDropdown) },
                            isError = showValidationErrors && data.desgaste.isBlank(),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = true
                        )
                        ExposedDropdownMenu(
                            expanded = showDesgasteDropdown,
                            onDismissRequest = { showDesgasteDropdown = false }
                        ) {
                            listOf(
                                "A. SIN DESGASTE IRREGULAR",
                                "B. CON DESGASTE IRREGULAR",
                                "C. DESGASTE ESCALONADO EN EL HOMBRO",
                                "D. DESGASTE COMPLETO DE HOMBRO",
                                "E. DESGASTE UNILATERAL (CAMBER)",
                                "F. DESGASTE TIPO CONTRAPELO (CONVERGENCI",
                                "G. DESGASTE TIPO RIO/EROSION",
                                "H. DESGASTE DE COSTILLAS (DEPRESIONES)",
                                "I. DESGASTE PUNTA-TALÓN",
                                "J. DESGASTE PREMATURO",
                                "K. DESGASTE POR FRENADO DE PÁNICO",
                                "L. DESGASTE DIAGONAL",
                                "M. DESGASTE POR PRESIÓN INSUFICIENTE",
                                "N. DESGASTE POR SOBREINFLADO",
                                "O. DESGASTE ONDULADO EN HOMBRO",
                                "P. DESGASTE ALTERNADO DE BLOQUES",
                                "Q. DESGASTE EN COSTILLAS (DEPRESIÓN ALTE",
                                "R. DESGASTE EXCÉNTRICO"
                            ).forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        onDataChange(data.copy(desgaste = opcion))
                                        showDesgasteDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Observación
                    var showObservacionDropdown by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showObservacionDropdown,
                        onExpandedChange = { showObservacionDropdown = !showObservacionDropdown }
                    ) {
                        OutlinedTextField(
                            value = data.observacion,
                            onValueChange = {},
                            label = { Text("Observación") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showObservacionDropdown) },
                            isError = showValidationErrors && data.observacion.isBlank(),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = true
                        )
                        ExposedDropdownMenu(
                            expanded = showObservacionDropdown,
                            onDismissRequest = { showObservacionDropdown = false }
                        ) {
                            listOf(
                                "LLANTA OK",
                                "CONDICION PELIGROSA",
                                "CORTES PISO / ARRANCAMIENTOS",
                                "CUSHION RIM CRACK",
                                "DAÑO EN COSTADO",
                                "DESGASTE IRREGULAR",
                                "OBJETO INCRUSTADO",
                                "PRESIÓN EXCESIVA",
                                "PRESIÓN INSUFICIENTE",
                                "RIN DAÑADO/FALTA TUERCA Y/O BIRLO",
                                "VALV INACCESIBLE/DAÑADA"
                            ).forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        onDataChange(data.copy(observacion = opcion))
                                        showObservacionDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Comentarios
                    OutlinedTextField(
                        value = data.comentarios,
                        onValueChange = { onDataChange(data.copy(comentarios = it)) },
                        label = { Text("Comentarios") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }

            // Calcular diferencia entre el mayor y el menor MM para mostrar advertencia de desgaste irregular
            val diferencia = remember(data.mm1, data.mm2, data.mm3, data.mm4) {
                val mm1Val = data.mm1.toFloatOrNull() ?: 0f
                val mm2Val = data.mm2.toFloatOrNull() ?: 0f
                val mm3Val = data.mm3.toFloatOrNull() ?: 0f
                val mm4Val = data.mm4.toFloatOrNull() ?: 0f
                val valores = listOf(mm1Val, mm2Val, mm3Val, mm4Val)
                val max = valores.maxOrNull() ?: 0f
                val min = valores.minOrNull() ?: 0f
                max - min
            }

            if (diferencia > 1f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Desgaste irregular detectado",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Desgaste irregular, factor delta ${diferencia} mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Dialog para seleccionar fotos
        if (showPhotoPickerDialog) {
            val photoSlots = listOf(
                PhotoSlot(
                    index = 0,
                    base64Data = data.foto1.takeIf { it.isNotBlank() },
                    fileName = data.foto1Nombre,
                    fileSize = data.foto1Tamano
                ),
                PhotoSlot(
                    index = 1,
                    base64Data = data.foto2.takeIf { it.isNotBlank() },
                    fileName = data.foto2Nombre,
                    fileSize = data.foto2Tamano
                )
            )
            
            PhotoPickerDialog(
                photoSlots = photoSlots,
                onPhotosChanged = { updatedSlots ->
                    onDataChange(data.copy(
                        foto1 = updatedSlots[0].base64Data ?: "",
                        foto1Nombre = updatedSlots[0].fileName,
                        foto1Tamano = updatedSlots[0].fileSize,
                        foto2 = updatedSlots[1].base64Data ?: "",
                        foto2Nombre = updatedSlots[1].fileName,
                        foto2Tamano = updatedSlots[1].fileSize
                    ))
                },
                onDismiss = { showPhotoPickerDialog = false },
                filePickerUtils = filePicker
            )
        }

    }
}

data class LlantaInspeccionFormData(
    val id: Int? = null,
    val selectedLlanta: Llanta? = null,
    val piso: String = "Original",
    val dot: String = "",
    val presion: String = "",
    val previousPresion: String? = null,
    val vigia: Boolean = false,
    val mm1: String = "",
    val mm2: String = "",
    val mm3: String = "",
    val mm4: String = "",
    val desgaste: String = "A. SIN DESGASTE IRREGULAR",
    val condicionPeligrosa: Boolean = false,
    val observacion: String = "LLANTA OK",
    val foto1: String = "",
    val foto1Nombre: String? = null,
    val foto1Tamano: Long? = null,
    val foto2: String = "",
    val foto2Nombre: String? = null,
    val foto2Tamano: Long? = null,
    val comentarios: String = "Ninguno"
) {
    fun isValid(): Boolean {
     // DOT no es obligatorio
     val p = presion.replace(',','.') .toFloatOrNull()
     // When vigia is false, pressure is required and must be numeric <= 160
     val presionOk = if (!vigia) {
         p != null && p <= 160f
     } else {
         // When vigia is true, pressure may be blank or 0; allow blank or numeric 0-160
         presion.isBlank() || (p != null && p <= 160f)
     }
     return selectedLlanta != null && piso.isNotBlank() && 
         presionOk && mm1.isNotBlank() && 
               mm2.isNotBlank() && mm3.isNotBlank() && mm4.isNotBlank() && 
               desgaste.isNotBlank() && observacion.isNotBlank()
    }
}
