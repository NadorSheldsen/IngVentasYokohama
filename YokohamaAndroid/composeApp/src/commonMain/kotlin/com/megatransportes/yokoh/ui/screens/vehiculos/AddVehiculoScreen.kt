package com.megatransportes.yokoh.ui.screens.vehiculos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.FileData
import com.megatransportes.yokoh.utils.InitializeFilePickerIfNeeded
import com.megatransportes.yokoh.utils.createFilePickerUtils
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import com.megatransportes.yokoh.ui.components.PhotoPickerDialog
import com.megatransportes.yokoh.ui.components.PhotoSlot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.models.TipoVehiculo
import com.megatransportes.yokoh.data.models.VehiculoCreateRequest
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.ui.screens.parametros.TipoVehiculosAdminScreen
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehiculoScreen(
    repository: YokohamaRepository,
    flota: Flota,
    onVehiculoCreated: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    var numero by remember { mutableStateOf("") }
    var odometro by remember { mutableStateOf("") }
    var tiposVehiculos by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }
    var selectedTipoVehiculo by remember { mutableStateOf<TipoVehiculo?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingTipos by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pickedFile by remember { mutableStateOf<FileData?>(null) }
    var pickedBase64 by remember { mutableStateOf<String?>(null) }
    var showPreviewAfterUpload by remember { mutableStateOf(false) }
    var uploadedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var uploadedImageBitmapCandidate by remember { mutableStateOf<ImageBitmap?>(null) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    var showTipoVehiculosAdmin by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val filePicker = createFilePickerUtils()

    // Load types
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            repository.getTiposVehiculos()
                .onSuccess { tipos -> tiposVehiculos = tipos; isLoadingTipos = false }
                .onFailure { e -> isLoadingTipos = false; errorMessage = ErrorUtils.userMessage(e, "Error cargando tipos") }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Agregar Vehículo a ${flota.FlotasNombre}") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Regresar") } },
            actions = { IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Flota") } }
        )
    }) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InitializeFilePickerIfNeeded()

            OutlinedTextField(value = numero, onValueChange = { numero = it; errorMessage = null }, label = { Text("Número del Vehículo") }, modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var searchTipo by remember { mutableStateOf("") }
                var showSuggestionsTipo by remember { mutableStateOf(false) }
                var filteredTipos by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }

                // Update filtered list when search changes
                LaunchedEffect(searchTipo, tiposVehiculos) {
                    if (searchTipo.isNotBlank() && selectedTipoVehiculo == null) {
                        filteredTipos = tiposVehiculos.filter {
                            it.TipoVehiculosNombre.contains(searchTipo, ignoreCase = true)
                        }.take(10)
                        showSuggestionsTipo = filteredTipos.isNotEmpty()
                    } else {
                        filteredTipos = emptyList()
                        showSuggestionsTipo = false
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = selectedTipoVehiculo?.TipoVehiculosNombre ?: searchTipo,
                            onValueChange = { new ->
                                if (selectedTipoVehiculo == null) {
                                    searchTipo = new
                                } else {
                                    // clear selection if user starts editing
                                    selectedTipoVehiculo = null
                                    searchTipo = new
                                }
                            },
                            label = { Text("Tipo de Vehículo") },
                            placeholder = { Text("Escribe para buscar tipo...") },
                            trailingIcon = {
                                if (selectedTipoVehiculo != null) {
                                    IconButton(onClick = { selectedTipoVehiculo = null }) { Icon(Icons.Default.Clear, contentDescription = "Limpiar") }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && selectedTipoVehiculo == null) {
                                        if (searchTipo.isBlank()) {
                                            filteredTipos = tiposVehiculos.take(10)
                                            showSuggestionsTipo = filteredTipos.isNotEmpty()
                                        }
                                    }
                                },
                            singleLine = true,
                            readOnly = false
                        )

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

                    if (showSuggestionsTipo && selectedTipoVehiculo == null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                filteredTipos.forEach { tipo ->
                                    TextButton(
                                        onClick = {
                                            selectedTipoVehiculo = tipo
                                            searchTipo = ""
                                            showSuggestionsTipo = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = tipo.TipoVehiculosNombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                                    }
                                    if (tipo != filteredTipos.last()) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(value = odometro, onValueChange = { odometro = it; errorMessage = null }, label = { Text("Odómetro (km)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            // Image picker row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showPhotoPickerDialog = true }, modifier = Modifier.size(84.dp)) { Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = "Seleccionar imagen", modifier = Modifier.size(52.dp)) }

                        if (pickedFile != null) {
                            OutlinedButton(onClick = { pickedFile = null; pickedBase64 = null; showPreviewAfterUpload = false; uploadedImageBitmap = null; uploadedImageBitmapCandidate = null }) { Text("Eliminar imagen") }
                        }
                    }
                }
            }

            if (errorMessage != null) { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(text = errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) } }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                when {
                    numero.isBlank() -> { errorMessage = "El número del vehículo es obligatorio"; return@Button }
                    selectedTipoVehiculo == null -> { errorMessage = "Debes seleccionar un tipo de vehículo"; return@Button }
                    odometro.isBlank() -> { errorMessage = "El odómetro es obligatorio"; return@Button }
                    odometro.toFloatOrNull() == null -> { errorMessage = "El odómetro debe ser un número válido"; return@Button }
                    odometro.toFloat() < 0 -> { errorMessage = "El odómetro no puede ser negativo"; return@Button }
                }

                val vehiculoRequest = VehiculoCreateRequest(Flotas_idFlotas = flota.idFlotas, VehiculosNumero = numero.trim(), TipoVehiculos_idTipoVehiculos = selectedTipoVehiculo!!.idTipoVehiculos, VehiculosOdometro = odometro.toFloat(), VehiculosImagen = pickedBase64)

                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null

                    repository.createVehiculo(vehiculoRequest)
                        .onSuccess {
                            isLoading = false
                            // Optionally still set preview image, but navigate immediately to the list
                            showPreviewAfterUpload = true
                            uploadedImageBitmap = uploadedImageBitmapCandidate ?: runCatching {
                                if (pickedFile != null) byteArrayToImageBitmap(pickedFile!!.data)
                                else if (!pickedBase64.isNullOrBlank()) byteArrayToImageBitmap(FileConverter.base64ToByteArray(pickedBase64!!))
                                else null
                            }.getOrNull()
                            // Notify parent to go back to the list immediately
                            onVehiculoCreated()
                        }
                        .onFailure { error -> isLoading = false; errorMessage = when {
                            error.message?.contains("duplicate", ignoreCase = true) == true -> "Ya existe un vehículo con este número en la flota"
                            error.message?.contains("connection", ignoreCase = true) == true -> "Error de conexión. Verifica tu conexión a internet"
                            else -> ErrorUtils.userMessage(error, "No se pudo crear el vehículo")
                        } }
                }
            }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !isLoading && !isLoadingTipos) {
                if (isLoading) { Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)); Text("Creando vehículo...") } }
                else Text("Crear Vehículo", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("Cancelar") }

            if (showPreviewAfterUpload) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Imagen subida correctamente", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onVehiculoCreated() }) { Text("Volver a la lista") }
                    }
                }
            }
        }
    }
    
    if (showPhotoPickerDialog) {
        PhotoPickerDialog(
            photoSlots = listOf(
                PhotoSlot(
                    index = 0,
                    base64Data = pickedBase64 ?: "",
                    fileName = pickedFile?.name ?: "",
                    fileSize = pickedFile?.size ?: 0L
                )
            ),
            onPhotosChanged = { updatedSlots ->
                val slot = updatedSlots.firstOrNull()
                val base64 = slot?.base64Data
                if (!base64.isNullOrBlank()) {
                    pickedBase64 = base64
                    // Decode the base64 to get the bitmap for preview
                    val byteArray = FileConverter.base64ToByteArray(base64)
                    uploadedImageBitmapCandidate = runCatching { byteArrayToImageBitmap(byteArray) }.getOrNull()
                    showPreviewAfterUpload = false
                    errorMessage = null
                }
            },
            onDismiss = { showPhotoPickerDialog = false },
            filePickerUtils = filePicker
        )
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
                                .onSuccess { tipos -> tiposVehiculos = tipos }
                        }
                    }
                )
            }
        }
    }
}
