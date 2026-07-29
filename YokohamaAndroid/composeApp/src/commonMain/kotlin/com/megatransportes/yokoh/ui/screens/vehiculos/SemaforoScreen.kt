package com.megatransportes.yokoh.ui.screens.semaforo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.platform.getLastKnownLocation
import com.megatransportes.yokoh.utils.ErrorUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.megatransportes.yokoh.utils.FilePickerUtils
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.createFilePickerUtils
import com.megatransportes.yokoh.utils.InitializeFilePickerIfNeeded
import com.megatransportes.yokoh.ui.components.PhotoPickerDialog
import com.megatransportes.yokoh.ui.components.PhotoSlot
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import com.megatransportes.yokoh.utils.formatDateOnly
import com.megatransportes.yokoh.utils.NumberFormatter
import com.megatransportes.yokoh.ui.screens.parametros.LlantasAdminScreen
import com.megatransportes.yokoh.ui.screens.parametros.TipoVehiculosAdminScreen
import com.megatransportes.yokoh.ui.screens.parametros.ParametrosListScreen
import com.megatransportes.yokoh.ui.screens.parametros.EditParametroScreen
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.ui.components.MicButton
import com.megatransportes.yokoh.ui.components.FieldDescriptor
import com.megatransportes.yokoh.ui.components.FieldType
import com.megatransportes.yokoh.ui.components.TireImage
import com.megatransportes.yokoh.utils.getPlatformContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemaforoScreen(
    repository: YokohamaRepository,
    flota: Flota,
    pruebaSemaforo: PruebasSemaforo,
    initialEditingVehiculoId: Int? = null,
    onSemaforoRegistrado: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onOpenLlantasAdmin: (() -> Unit)? = null,
    onOpenAddParametro: (llantaId: Int) -> Unit = {},
    onOpenParametrosList: (llantaIds: List<Int>) -> Unit = {}
) {
    val currentUser by repository.currentUser.collectAsState()
    var tipoVehiculos by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }
    var llantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var parametros by remember { mutableStateOf<List<Parametro>>(emptyList()) }
    var editingVehiculoId by remember { mutableStateOf<Int?>(initialEditingVehiculoId) }
    var selectedTipoVehiculo by remember { mutableStateOf<TipoVehiculo?>(null) }
    var vehiculoSemaforoNo by remember { mutableStateOf("") }
    var showTipoVehiculoDropdown by remember { mutableStateOf(false) }
    var tipoVehiculoSearch by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationAttempted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isEditing = editingVehiculoId != null
    var showLlantasAdmin by remember { mutableStateOf(false) }
    var showTipoVehiculosAdmin by remember { mutableStateOf(false) }
    var showParametrosDialog by remember { mutableStateOf(false) }
    var suggestedLlantaIdsForParametros by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showEditParametroDialog by remember { mutableStateOf(false) }
    var selectedLlantaIdForParametro by remember { mutableStateOf<Int?>(null) }

    // Inicializar FilePicker
    val filePicker = remember { createFilePickerUtils() }
    InitializeFilePickerIfNeeded()

    // Estado para las llantas del semáforo
    var llantasSemaforoData by remember {
        mutableStateOf<List<LlantaSemaforoFormData>>(emptyList())
    }

    // Cargar tipos de vehículos y llantas
    LaunchedEffect(key1 = Unit) {
        coroutineScope.launch {
            repository.getTiposVehiculos()
                .onSuccess { result -> tipoVehiculos = result }
                .onFailure { _ -> errorMessage = "Error cargando tipos de vehículos" }
            
            repository.getLlantasByFlota(flota.idFlotas)
                .onSuccess { result -> llantas = result }
                .onFailure { _ -> errorMessage = "Error cargando llantas" }
            repository.getParametrosByFlotaId(flota.idFlotas)
                .onSuccess { result -> parametros = result }
                .onFailure { _ -> }
            
            // Esta pantalla es exclusivamente un editor/creador de un único vehículo.
            // Las listas de vehículos se muestran en la pantalla dedicada (VehiculosSemaforoListScreen).
        }
    }

    // Si la pantalla se abre pidiendo edición de un vehículo (navegación desde lista), cargar sus datos
    LaunchedEffect(key1 = editingVehiculoId) {
        editingVehiculoId?.let { id ->
            coroutineScope.launch {
                // Asegurarnos de tener los tipos cargados para poder resolver el tipo del vehículo
                if (tipoVehiculos.isEmpty()) {
                    repository.getTiposVehiculos().onSuccess { list -> tipoVehiculos = list }
                }

                // Cargar el vehículo individualmente usando el endpoint específico
                repository.getVehiculoSemaforoById(id)
                    .onSuccess { v ->
                        vehiculoSemaforoNo = v.VehiculoSemaforoNo
                        selectedTipoVehiculo = tipoVehiculos.firstOrNull { it.idTipoVehiculos == v.TipoVehiculos_idTipoVehiculos }

                        repository.getLlantasSemaforoByVehiculoId(v.idVehiculoSemaforo)
                            .onSuccess { llList ->
                                // Debug: log incoming presion/vigia to help diagnose missing psi on reopen
                                println("[SemaforoScreen] Received ${llList.size} llantas for vehiculo ${v.idVehiculoSemaforo}")
                                llList.forEach { l -> println("[SemaforoScreen] llanta id=${l.idLlantasSemaforo} presion=${l.LlantasSemaforoPresion} vigia=${l.LlantasSemaforoVigia}") }
                                llantasSemaforoData = llList.map { ll ->
                                    val resolvedLlanta = llantas.firstOrNull { it.idLlantas == ll.Llantas_idLlantas }
                                        ?: Llanta(
                                            idLlantas = ll.Llantas_idLlantas,
                                            LlantasMarca = "",
                                            LlantasModelo = "",
                                            LlantasPrecio = null,
                                            LlantasMedida = "",
                                            LlantasMm = 0f,
                                            asociada = 1
                                        )

                                        LlantaSemaforoFormData(
                                        idLlantasSemaforo = ll.idLlantasSemaforo,
                                        selectedLlanta = resolvedLlanta,
                                        // If backend returns null for presion, default to "0" so the input is prefilled
                                        presion = ll.LlantasSemaforoPresion?.toString() ?: "0",
                                        // Prefer explicit vigia flag from backend; fallback to presion==0 for older records
                                        vigia = (ll.LlantasSemaforoVigia == 1) || (ll.LlantasSemaforoPresion == 0),
                                        piso = ll.LlantasSemaforoPiso ?: "Original",
                                        color = ll.LlantasSemaforoColor,
                                        condicionPeligrosa = ll.LlantasSemaforoCondPel ?: false,
                                        observacion = ll.LlantasSemaforoObserv ?: "LLANTA OK",
                                        comentarios = ll.LlantasSemaforoComent ?: "Ninguno",
                                        foto1 = ll.LlantasSemaforoFoto1,
                                        foto2 = ll.LlantasSemaforoFoto2
                                    )
                                }
                            }
                            .onFailure { err -> errorMessage = ErrorUtils.userMessage(err, "No se pudieron cargar las llantas del vehículo") }
                    }
                    .onFailure { err -> errorMessage = ErrorUtils.userMessage(err, "No se pudo cargar el vehículo para edición") }
            }
        }
    }

    // Cuando se selecciona (o limpia) el tipo de vehículo, sincronizar formularios con la cantidad de llantas
    LaunchedEffect(selectedTipoVehiculo) {
        if (!isEditing) {
            val tipo = selectedTipoVehiculo
            llantasSemaforoData = if (tipo != null) {
                List(tipo.TipoVehiculosCantLlantas) { LlantaSemaforoFormData() }
            } else {
                emptyList()
            }
        }
    }

    // Si la lista maestra de llantas se carga después de haber creado formularios con placeholders,
    // reemplazar los placeholders por las llantas reales (resolución por id).
    LaunchedEffect(key1 = llantas) {
        if (llantas.isNotEmpty() && llantasSemaforoData.isNotEmpty()) {
            llantasSemaforoData = llantasSemaforoData.map { data ->
                val sel = data.selectedLlanta
                if (sel != null && sel.LlantasMarca.isBlank()) {
                    val resolved = llantas.firstOrNull { it.idLlantas == sel.idLlantas }
                    if (resolved != null) data.copy(selectedLlanta = resolved) else data
                } else data
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Semáforo - ${pruebaSemaforo.PruebasSemaforoTitulo}") },
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { /* no-op header */ },
                shape = RoundedCornerShape(12.dp),
                // Use theme surface color for border and content to respect dark mode
                border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
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
                        text = "Prueba: ${pruebaSemaforo.PruebasSemaforoTitulo}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                    Text(
                        text = "${formatDateOnly(pruebaSemaforo.PruebasSemaforoFecha)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            // (Vehículos relacionados removidos: esta pantalla es sólo editor/creador)

            // Selección de tipo de vehículo
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface)
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        value = vehiculoSemaforoNo,
                        onValueChange = { if (!isEditing) vehiculoSemaforoNo = it },
                        label = { Text("Número del Vehículo") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isEditing
                    )
                }
            }

            // Formularios para las llantas
            selectedTipoVehiculo?.let { tipo ->
                Text(
                    text = "Llantas del Vehículo (${tipo.TipoVehiculosCantLlantas} llantas)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                val medidasConParametro = parametros.mapNotNull { it.LlantasMedida }.toSet()
                llantasSemaforoData.forEachIndexed { index, data ->
                    LlantaSemaforoForm(
                        index = index + 1,
                        data = data,
                        llantas = llantas,
                        medidasConParametro = medidasConParametro,
                        filePicker = filePicker,
                        onOpenLlantasAdmin = { showLlantasAdmin = true },
                        showValidationErrors = validationAttempted,
                        onDataChange = { newData ->
                            val oldData = llantasSemaforoData[index]
                            try { println("[SemaforoScreen] onDataChange index=$index oldCond=${oldData.condicionPeligrosa} newCond=${newData.condicionPeligrosa}") } catch (_: Exception) {}
                            llantasSemaforoData = llantasSemaforoData.toMutableList().apply {
                                this[index] = newData
                            }
                            try { println("[SemaforoScreen] after update llantasSemaforoData size=${llantasSemaforoData.size} condStates=${llantasSemaforoData.map { it.condicionPeligrosa }}") } catch (_: Exception) {}

                            // Si se seleccionó una nueva llanta, auto-llenar las formas siguientes (sobrescribir)
                            if (newData.selectedLlanta != null && newData.selectedLlanta != oldData.selectedLlanta) {
                                val updatedList = llantasSemaforoData.toMutableList()
                                for (i in (index + 1) until updatedList.size) {
                                    updatedList[i] = updatedList[i].copy(
                                        selectedLlanta = newData.selectedLlanta
                                    )
                                }
                                llantasSemaforoData = updatedList
                            }
                        }
                    )
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                        validationAttempted = true
                    // Validar formulario
                    if (selectedTipoVehiculo == null || vehiculoSemaforoNo.isBlank()) {
                        errorMessage = "Complete la información del vehículo"
                        return@Button
                    }

                    val invalidLlanta = llantasSemaforoData.indexOfFirst { !it.isValid() }
                    if (invalidLlanta != -1) {
                        errorMessage = "Complete todos los campos de la llanta ${invalidLlanta + 1}"
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null

                        try {
                            // Validar que todas las llantas tengan parámetros
                            val parametrosResult = repository.getParametrosByFlotaId(flota.idFlotas)
                            if (parametrosResult.isFailure) {
                                isLoading = false
                                errorMessage = "No se pudieron cargar los parámetros de la flota"
                                return@launch
                            }
                            
                            val parametrosList = parametrosResult.getOrNull() ?: emptyList()
                            val medidasConParametro = parametrosList.mapNotNull { it.LlantasMedida }.toSet()
                            val missingLlantaIds = llantasSemaforoData.mapNotNull { data ->
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
                            val loc = getLastKnownLocation()
                            val vehiculoSemaforoRequest = VehiculoSemaforoCreateRequest(
                                PruebasSemaforo_idPruebasSemaforo = pruebaSemaforo.idPruebasSemaforo,
                                TipoVehiculos_idTipoVehiculos = selectedTipoVehiculo!!.idTipoVehiculos,
                                VehiculoSemaforoNo = vehiculoSemaforoNo,
                                Usuarios_idUsuarios = currentUser?.idUsuarios,
                                latitude = loc?.latitude,
                                longitude = loc?.longitude
                            )

                            if (editingVehiculoId == null) {
                                // Crear nuevo vehículo y sus llantas
                                repository.createVehiculoSemaforo(vehiculoSemaforoRequest)
                                    .onSuccess { vehiculoSemaforo ->
                                        val llantasSemaforoRequests = llantasSemaforoData.map { data ->
                                            val pisoVal = data.piso.ifBlank { "Original" }
                                            val observVal = data.observacion.ifBlank { "LLANTA OK" }
                                            LlantasSemaforoCreateRequest(
                                                VehiculoSemaforo_idVehiculoSemaforo = vehiculoSemaforo.idVehiculoSemaforo,
                                                Llantas_idLlantas = data.selectedLlanta!!.idLlantas,
                                                LlantasSemaforoPresion = if (data.vigia) 0 else data.presion.replace(',','.')
                                                    .toIntOrNull() ?: 0,
                                                LlantasSemaforoVigia = if (data.vigia) 1 else 0,
                                                LlantasSemaforoColor = data.color,
                                                LlantasSemaforoCondPel = data.condicionPeligrosa,
                                                LlantasSemaforoPiso = pisoVal,
                                                LlantasSemaforoObserv = observVal,
                                                LlantasSemaforoComent = data.comentarios,
                                                LlantasSemaforoFoto1 = data.foto1,
                                                LlantasSemaforoFoto2 = data.foto2
                                            )
                                        }

                                        repository.createMultipleLlantasSemaforo(llantasSemaforoRequests)
                                            .onSuccess {
                                                isLoading = false
                                                // Señalar que se registró el semáforo (la lista se maneja en la pantalla de lista)
                                                onSemaforoRegistrado()
                                            }
                                            .onFailure { error ->
                                                isLoading = false
                                                errorMessage = ErrorUtils.userMessage(error, "No se pudieron guardar las llantas")
                                            }
                                    }
                                    .onFailure { error ->
                                        isLoading = false
                                        errorMessage = ErrorUtils.userMessage(error, "No se pudo guardar el vehículo")
                                    }
                            } else {
                                // Actualizar vehículo y sus llantas individualmente
                                val id = editingVehiculoId!!
                                repository.updateVehiculoSemaforo(id, vehiculoSemaforoRequest)
                                    .onSuccess {
                                        var failed: Throwable? = null
                                        // Procesar cada llanta: actualizar si tiene id, crear si no
                                        for (data in llantasSemaforoData) {
                                            val pisoVal = data.piso.ifBlank { "Original" }
                                            val observVal = data.observacion.ifBlank { "LLANTA OK" }
                                            val request = LlantasSemaforoCreateRequest(
                                                VehiculoSemaforo_idVehiculoSemaforo = id,
                                                Llantas_idLlantas = data.selectedLlanta!!.idLlantas,
                                                LlantasSemaforoPresion = if (data.vigia) 0 else data.presion.replace(',','.')
                                                    .toIntOrNull() ?: 0,
                                                LlantasSemaforoVigia = if (data.vigia) 1 else 0,
                                                LlantasSemaforoColor = data.color,
                                                LlantasSemaforoCondPel = data.condicionPeligrosa,
                                                LlantasSemaforoPiso = pisoVal,
                                                LlantasSemaforoObserv = observVal,
                                                LlantasSemaforoComent = data.comentarios,
                                                LlantasSemaforoFoto1 = data.foto1,
                                                LlantasSemaforoFoto2 = data.foto2
                                            )

                                            // Debug: log per-llanta request fields to confirm values reaching the client API
                                            try {
                                                println("[SemaforoScreen] Updating llanta id=${data.idLlantasSemaforo} piso='${pisoVal}' observ='${observVal}' color='${data.color}' presion='${data.presion}' vigia=${data.vigia}")
                                                println("[SemaforoScreen] -> request presion=${request.LlantasSemaforoPresion} vigia=${request.LlantasSemaforoVigia} condPel=${request.LlantasSemaforoCondPel}")
                                            } catch (_: Exception) { /* ignore */ }

                                            if (data.idLlantasSemaforo != null) {
                                                val res = repository.updateLlantaSemaforo(data.idLlantasSemaforo, request)
                                                if (res.isFailure) {
                                                    failed = res.exceptionOrNull()
                                                    break
                                                }
                                            } else {
                                                val res = repository.createLlantasSemaforo(request)
                                                if (res.isFailure) {
                                                    failed = res.exceptionOrNull()
                                                    break
                                                }
                                            }
                                        }

                                        if (failed == null) {
                                            isLoading = false
                                            // Señalar que se registró/actualizó (la lista se maneja en la pantalla de lista)
                                            onSemaforoRegistrado()
                                        } else {
                                            isLoading = false
                                            errorMessage = ErrorUtils.userMessage(failed, "Error guardando llantas")
                                        }
                                    }
                                    .onFailure { error ->
                                        isLoading = false
                                        errorMessage = ErrorUtils.userMessage(error, "No se pudo actualizar el vehículo")
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
                        Text("Registrando semáforo...")
                    }
                } else {
                    Text("Registrar Semáforo", style = MaterialTheme.typography.titleMedium)
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
                    onBack = { showLlantasAdmin = false },
                    flotaId = flota.idFlotas
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
    
    // Dialog para ParametrosListScreen
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

                        // Quitar de sugerencias la llanta recién parametrizada
                        if (savedId != null) {
                            suggestedLlantaIdsForParametros = suggestedLlantaIdsForParametros.filterNot { it == savedId }
                        }

                        // Recargar parámetros para actualizar validaciones
                        coroutineScope.launch {
                            repository.getParametrosByFlotaId(flota.idFlotas)
                                .onSuccess { result -> parametros = result }
                                .onFailure { _ -> }
                        }

                        // Si ya no quedan sugerencias, cerrar ambos diálogos
                        if (suggestedLlantaIdsForParametros.isEmpty()) {
                            showParametrosDialog = false
                        } else {
                            // Volver a la lista para continuar con las restantes
                            showParametrosDialog = true
                        }
                    },
                    onBack = { showEditParametroDialog = false },
                    onHome = {}
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
private fun LlantaSemaforoForm(
    index: Int,
    data: LlantaSemaforoFormData,
    llantas: List<Llanta>,
    medidasConParametro: Set<String> = emptySet(),
    filePicker: FilePickerUtils,
    showValidationErrors: Boolean,
    onDataChange: (LlantaSemaforoFormData) -> Unit,
    onOpenLlantasAdmin: (() -> Unit)? = null
) {
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var filteredLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var isLoadingFile1 by remember { mutableStateOf(false) }
    var fileError1 by remember { mutableStateOf<String?>(null) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var showExtraFields by remember { mutableStateOf(false) }

    val formBorderColor = when {
        data.isValid() -> Color(0xFF2E7D32)
        showValidationErrors -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, formBorderColor, MaterialTheme.shapes.large)
            .padding(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // Compact custom outlined-like field with reduced inner padding
            @Composable
            fun CompactOutlinedTextField(
                value: String,
                onValueChange: (String) -> Unit,
                modifier: Modifier = Modifier,
                minWidth: Dp = 48.dp,
                height: Dp = 44.dp,
                singleLine: Boolean = true,
                keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
                textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
                enabled: Boolean = true,
                borderColor: Color = MaterialTheme.colorScheme.outline
            ) {
                Box(
                    modifier = modifier
                        .width(minWidth)
                        .height(height)
                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = singleLine,
                        textStyle = textStyle.copy(textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = keyboardOptions,
                        enabled = enabled,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Selector de llanta con número a la izquierda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Número de llanta a la izquierda
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
                                            selectedLlanta = llanta
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

                // Color selector moved here to its own row (below the main controls)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(28.dp))
                    listOf(
                        "Verde" to Color(0xFF00C853),
                        "Amarillo" to Color(0xFFFFD600),
                        "Rojo" to Color.Red
                    ).forEach { (colorName, color) ->
                        val selected = data.color == colorName
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onDataChange(data.copy(color = colorName)) },
                            contentAlignment = Alignment.Center
                        ) {
                            TireImage(
                                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = if (selected) 1f else 0.35f),
                                tintColor = color
                            )
                        }
                    }
                }

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

            // Fila principal: colores, foto (solo 1), presion, condicion peligrosa, vigia y botón desplegar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // color selector moved below to its own row

                // Foto principal (solo una) - botón; columna centrada para alinear el icono con el renglón
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { showPhotoPickerDialog = true },
                            enabled = !isLoadingFile1,
                            modifier = Modifier.size(64.dp)
                        ) {
                            if (isLoadingFile1) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.PhotoCamera,
                                    contentDescription = "Seleccionar imagen",
                                    modifier = Modifier.size(40.dp),
                                    tint = if (data.foto1?.isNotBlank() == true || data.foto2?.isNotBlank() == true)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (fileError1 != null) {
                        Text(
                            text = fileError1!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Presión
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Presión", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val _ctx = getPlatformContext()
                        MicButton(
                            fields = listOf(
                                FieldDescriptor(title = "Presión", type = FieldType.NUMBER, onFill = { v -> onDataChange(data.copy(presion = v)) }),
                                FieldDescriptor(title = "Vigia", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(vigia = v.trim().lowercase().startsWith("s"))) }),
                                FieldDescriptor(title = "Piso", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(piso = v)) }),
                                FieldDescriptor(title = "Condición peligrosa", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(condicionPeligrosa = v.trim().lowercase().startsWith("s"))) }),
                                FieldDescriptor(title = "Observación", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(observacion = v)) }),
                                FieldDescriptor(title = "Comentarios", type = FieldType.TEXT, onFill = { v -> onDataChange(data.copy(comentarios = v)) })
                            ),
                            modifier = Modifier.size(56.dp),
                            startListeningAction = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.start(_ctx) },
                            stopListening = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.stopAndGet() }
                        )

                        CompactOutlinedTextField(
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
                        minWidth = 48.dp,
                        height = 44.dp,
                        enabled = !data.vigia,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier,
                        borderColor = when {
                            !showValidationErrors -> MaterialTheme.colorScheme.outline
                            data.vigia -> if (data.presion.isNotBlank() && (data.presion.toIntOrNull() == null || (data.presion.toIntOrNull() ?: 0) > 160)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            else -> if (data.presion.isBlank() || data.presion.toIntOrNull() == null || (data.presion.toIntOrNull() ?: 0) > 160) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        }
                    )
                }

                }

                // Condición peligrosa
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Peligro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                    onClick = {
                        val newVal = !data.condicionPeligrosa
                        try { println("[SemaforoScreen] Toggle CondPel id=${data.idLlantasSemaforo} -> $newVal") } catch (_: Exception) {}
                        onDataChange(data.copy(condicionPeligrosa = newVal))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    val alpha = if (data.condicionPeligrosa) 1f else 0.35f
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Condición peligrosa",
                        modifier = Modifier.size(32.dp).graphicsLayer(alpha = alpha),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                }

                // Vigía
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Vigía/Inac", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Checkbox(
                        checked = data.vigia,
                        onCheckedChange = { checked -> onDataChange(data.copy(vigia = checked, presion = if (checked) "0" else data.presion)) }
                    )
                }

                // Botón para plegar/desplegar campos extra
                IconButton(onClick = { showExtraFields = !showExtraFields }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (showExtraFields) "Ocultar" else "Mostrar",
                        modifier = Modifier.graphicsLayer(rotationZ = if (showExtraFields) 45f else 0f),
                        tint = Color(0xFF00A040)
                    )
                }
            }

            // Campos extra (piso, observación, comentarios y segunda foto)
            AnimatedVisibility(visible = showExtraFields) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Piso (Vitalizado/Original)
                    var showPisoDropdown by remember { mutableStateOf(false) }
                    var pisoText by remember { mutableStateOf(data.piso) }
                    LaunchedEffect(data.piso) { pisoText = data.piso }
                    ExposedDropdownMenuBox(
                        expanded = showPisoDropdown,
                        onExpandedChange = { showPisoDropdown = !showPisoDropdown }
                    ) {
                        OutlinedTextField(
                            value = pisoText,
                            onValueChange = {
                                pisoText = it
                                onDataChange(data.copy(piso = it))
                            },
                            label = { Text("Piso") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPisoDropdown) },
                            isError = showValidationErrors && data.piso.isBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clickable { showPisoDropdown = true },
                            readOnly = false
                        )

                        ExposedDropdownMenu(
                            expanded = showPisoDropdown,
                            onDismissRequest = { showPisoDropdown = false }
                        ) {
                            listOf("Vitalizado", "Original").forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        pisoText = opcion
                                        onDataChange(data.copy(piso = opcion))
                                        showPisoDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Observación
                    var showObservacionDropdown by remember { mutableStateOf(false) }
                    var observacionText by remember { mutableStateOf(data.observacion) }
                    LaunchedEffect(data.observacion) { observacionText = data.observacion }
                    ExposedDropdownMenuBox(
                        expanded = showObservacionDropdown,
                        onExpandedChange = { showObservacionDropdown = !showObservacionDropdown }
                    ) {
                        OutlinedTextField(
                            value = observacionText,
                            onValueChange = {
                                observacionText = it
                                onDataChange(data.copy(observacion = it))
                            },
                            label = { Text("Observación") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showObservacionDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clickable { showObservacionDropdown = true },
                            readOnly = false
                        )

                        ExposedDropdownMenu(
                            expanded = showObservacionDropdown,
                            onDismissRequest = { showObservacionDropdown = false }
                        ) {
                            listOf("LLANTA OK", "DAÑO EN EL COSTADO").forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        observacionText = opcion
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

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                thickness = 2.5.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        if (showPhotoPickerDialog) {
            PhotoPickerDialog(
                photoSlots = listOf(
                    PhotoSlot(
                        index = 0,
                        base64Data = data.foto1 ?: "",
                        fileName = data.foto1Nombre ?: "",
                        fileSize = data.foto1Tamano ?: 0L
                    ),
                    PhotoSlot(
                        index = 1,
                        base64Data = data.foto2 ?: "",
                        fileName = data.foto2Nombre ?: "",
                        fileSize = data.foto2Tamano ?: 0L
                    )
                ),
                onPhotosChanged = { updatedSlots ->
                    val foto1 = updatedSlots.getOrNull(0)
                    val foto2 = updatedSlots.getOrNull(1)
                    onDataChange(
                        data.copy(
                            foto1 = foto1?.base64Data?.takeIf { it.isNotBlank() },
                            foto1Nombre = foto1?.fileName?.takeIf { it.isNotBlank() },
                            foto1Tamano = foto1?.fileSize?.takeIf { it > 0 },
                            foto2 = foto2?.base64Data?.takeIf { it.isNotBlank() },
                            foto2Nombre = foto2?.fileName?.takeIf { it.isNotBlank() },
                            foto2Tamano = foto2?.fileSize?.takeIf { it > 0 }
                        )
                    )
                },
                onDismiss = { showPhotoPickerDialog = false },
                filePickerUtils = filePicker
            )
        }
}

data class LlantaSemaforoFormData(
    val idLlantasSemaforo: Int? = null,
    val selectedLlanta: Llanta? = null,
    val presion: String = "",
    val vigia: Boolean = false,
    val piso: String = "Original",
    val color: String = "Verde",
    val condicionPeligrosa: Boolean = false,
    val observacion: String = "LLANTA OK",
    val comentarios: String = "Ninguno",
    val foto1: String? = null,
    val foto1Nombre: String? = null,
    val foto1Tamano: Long? = null,
    val foto2: String? = null,
    val foto2Nombre: String? = null,
    val foto2Tamano: Long? = null
) {
        fun isValid(): Boolean {
            val p = presion.replace(',','.') .toFloatOrNull()
         // When vigia is false, pressure is required and must be numeric <= 160
         val presionValida = if (!vigia) {
             p != null && p <= 160f
         } else {
             // When vigia is true, pressure may be blank or 0; allow blank or numeric 0-160
             presion.isBlank() || (p != null && p <= 160f)
         }

         return selectedLlanta != null &&
             presionValida &&
             piso.isNotBlank() &&
             color.isNotBlank() &&
             observacion.isNotBlank()
    }
}