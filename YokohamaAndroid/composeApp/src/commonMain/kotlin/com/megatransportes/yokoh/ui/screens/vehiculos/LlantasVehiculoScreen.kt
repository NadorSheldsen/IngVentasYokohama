package com.megatransportes.yokoh.ui.screens.vehiculos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.ui.screens.parametros.ParametrosListScreen
import com.megatransportes.yokoh.ui.screens.parametros.EditParametroScreen
import com.megatransportes.yokoh.ui.screens.parametros.LlantasAdminScreen
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.utils.DateFormatter
import com.megatransportes.yokoh.utils.TimeProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlantasVehiculoScreen(
    repository: YokohamaRepository,
    flota: Flota,
    vehiculo: Vehiculo,
    cantidadLlantas: Int,
    onLlantasRegistradas: () -> Unit,
    onBack: () -> Unit,
    onOpenParametrosList: (llantaIds: List<Int>) -> Unit = {}
) {
    var llantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var parametros by remember { mutableStateOf<List<Parametro>>(emptyList()) }
    var llantasData by remember(cantidadLlantas) { mutableStateOf(List(cantidadLlantas) { LlantaVehiculoFormData() }) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingLlantas by remember { mutableStateOf(true) }
    var validationAttempted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showParametrosDialog by remember { mutableStateOf(false) }
    var suggestedLlantaIdsForParametros by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showEditParametroDialog by remember { mutableStateOf(false) }
    var selectedLlantaIdForParametro by remember { mutableStateOf<Int?>(null) }
    var showLlantasAdmin by remember { mutableStateOf(false) }
    var collapsedForms by remember(cantidadLlantas) { mutableStateOf(List(cantidadLlantas) { true }) }

    val formPositions = remember { mutableStateMapOf<Int, Float>() }

    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Cargar todas las llantas para la búsqueda
    LaunchedEffect(key1 = Unit) {
        coroutineScope.launch {
            repository.getLlantasByFlota(vehiculo.Flotas_idFlotas)
                .onSuccess { result ->
                    llantas = result
                    isLoadingLlantas = false
                }
                .onFailure { error ->
                    isLoadingLlantas = false
                    errorMessage = ErrorUtils.userMessage(error, "Error cargando llantas")
                }

            repository.getParametrosByFlotaId(vehiculo.Flotas_idFlotas)
                .onSuccess { result -> parametros = result }
                .onFailure { _ -> }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro info llantas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
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
                .verticalScroll(scrollState)
                .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() },
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    val titleAnnotated = androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        append("Vehículo: ")
                        append(vehiculo.VehiculosNumero.toString())
                        pop()
                    }

                    val headerTextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.97f)

                    Text(
                        text = titleAnnotated,
                        style = headerTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Registra las $cantidadLlantas llantas de este vehículo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoadingLlantas) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Formularios para cada llanta
                val medidasConParametro = parametros.mapNotNull { it.LlantasMedida }.toSet()
                repeat(cantidadLlantas) { index ->
                    LlantaVehiculoForm(
                        index = index + 1,
                        data = llantasData[index],
                        llantas = llantas,
                        medidasConParametro = medidasConParametro,
                        onOpenLlantasAdmin = { showLlantasAdmin = true },
                        showValidationErrors = validationAttempted,
                        collapsed = collapsedForms[index],
                        onToggleCollapsed = {
                            collapsedForms = collapsedForms.toMutableList().apply {
                                this[index] = !this[index]
                            }
                        },
                        onFormPositioned = { idx, y, h ->
                            formPositions[idx] = y
                        },
                        onDataChange = { newData ->
                            val oldData = llantasData[index]
                            llantasData = llantasData.toMutableList().apply {
                                this[index] = newData
                            }
                            
                            // Si se seleccionó una nueva llanta, auto-llenar las formas siguientes
                            if (newData.selectedLlanta != null && newData.selectedLlanta != oldData.selectedLlanta) {
                                // Auto-llenar las formas siguientes que no tengan llanta seleccionada
                                val updatedList = llantasData.toMutableList()
                                for (i in (index + 1) until updatedList.size) {
                                    updatedList[i] = updatedList[i].copy(
                                        selectedLlanta = newData.selectedLlanta,
                                        precio = "0",
                                        mm1 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm1,
                                        mm2 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm2,
                                        mm3 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm3,
                                        mm4 = newData.selectedLlanta.LlantasMm.toString() ?: updatedList[i].mm4
                                    )
                                }
                                llantasData = updatedList
                            }
                            
                            // Si cambió el precio y hay una llanta seleccionada, actualizar el precio en todos los formularios con la misma llanta
                            if (newData.selectedLlanta != null && newData.precio != oldData.precio) {
                                val updatedList = llantasData.toMutableList()
                                for (i in updatedList.indices) {
                                    if (i != index && updatedList[i].selectedLlanta?.idLlantas == newData.selectedLlanta.idLlantas) {
                                        updatedList[i] = updatedList[i].copy(precio = newData.precio)
                                    }
                                }
                                llantasData = updatedList
                            }
                        }
                    )

                    LaunchedEffect(collapsedForms[index]) {
                        if (!collapsedForms[index]) {
                            val formY = formPositions[index] ?: return@LaunchedEffect
                            val targetVisibleY = (containerHeightPx / 2f).toInt()
                            val currentScroll = scrollState.value
                            val absoluteFormY = (formY + currentScroll).toInt()
                            val targetScroll = (absoluteFormY - targetVisibleY).coerceAtLeast(0)
                            scrollState.animateScrollTo(targetScroll)
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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // Validar todos los formularios
                        validationAttempted = true
                        val invalidIndex = llantasData.indices.firstOrNull { index ->
                            collapsedForms.getOrNull(index) != true && !llantasData[index].isValid()
                        }
                        if (invalidIndex != null) {
                            errorMessage = "Complete todos los campos de la llanta ${invalidIndex + 1}"
                            return@Button
                        }

                        val activeIndexes = collapsedForms.indices.filter { !collapsedForms[it] }
                        if (activeIndexes.isEmpty()) {
                            errorMessage = "Debes dejar al menos una llanta activa para registrar"
                            return@Button
                        }

                        // Validar que todas las llantas tengan parámetros
                        val medidasConParametroSet = parametros.mapNotNull { it.LlantasMedida }.toSet()
                        val missingLlantaIds = llantasData.mapIndexedNotNull { index, data ->
                            if (collapsedForms.getOrNull(index) == true) return@mapIndexedNotNull null
                            val llanta = data.selectedLlanta
                            val medida = llanta?.LlantasMedida
                            if (llanta != null && medida != null && !medidasConParametroSet.contains(medida)) llanta.idLlantas to medida else null
                        }.distinctBy { it.second }.map { it.first }
                        if (missingLlantaIds.isNotEmpty()) {
                            suggestedLlantaIdsForParametros = missingLlantaIds
                            showParametrosDialog = true
                            return@Button
                        }

                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null

                            // Crear todas las llantas vehiculo
                            val requests = llantasData.mapIndexedNotNull { index, data ->
                                if (collapsedForms.getOrNull(index) == true) return@mapIndexedNotNull null
                                LlantaVehiculoCreateRequest(
                                    Llantas_idLlantas = data.selectedLlanta?.idLlantas ?: 0,
                                    Vehiculos_idVehiculos = vehiculo.idVehiculos,
                                    LlantasVehiculosNoQuemado = data.noQuemado,
                                    LlantasVehiculosPresion = data.presion.toIntOrNull() ?: 0,
                                    LlantasVehiculosPrecio = data.precio.toFloatOrNull() ?: 0f,
                                    LlantasVehiculosPiso = data.piso,
                                    LlantasVehiculosFechaInicio = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd'T'HH:mm:ss"),
                                    LlantasVehiculosMM1 = data.mm1.toFloatOrNull() ?: 0f,
                                    LlantasVehiculosMM2 = data.mm2.toFloatOrNull() ?: 0f,
                                    LlantasVehiculosMM3 = data.mm3.toFloatOrNull() ?: 0f,
                                    LlantasVehiculosMM4 = data.mm4.toFloatOrNull() ?: 0f
                                )
                            }

                            if (requests.isEmpty()) {
                                errorMessage = "No hay llantas activas para registrar"
                                isLoading = false
                                return@launch
                            }

                            // Guardar todas las llantas en una sola petición
                            
                            val result = repository.createMultipleLlantasVehiculo(requests)

                            if (result.isSuccess) {
                                onLlantasRegistradas()
                            } else {
                                errorMessage = ErrorUtils.userMessage(result.exceptionOrNull(), "No se pudieron guardar las llantas")
                                isLoading = false
                            }

                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isLoading
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
                            Text("Registrando llantas...")
                        }
                    } else {
                        Text("Registrar Llantas", style = MaterialTheme.typography.titleMedium)
                    }
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Cancelar")
                }
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

                        if (savedId != null) {
                            suggestedLlantaIdsForParametros = suggestedLlantaIdsForParametros.filterNot { it == savedId }
                        }

                        // Recargar parámetros para actualizar validaciones
                        coroutineScope.launch {
                            repository.getParametrosByFlotaId(vehiculo.Flotas_idFlotas)
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
    
    // Dialog para LlantasAdmin
    if (showLlantasAdmin) {
        val childDialogState = remember { mutableStateOf(false) }
        val childAddRequest = remember { mutableStateOf(false) }
        
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
                    showDialogState = childDialogState,
                    addRequestState = childAddRequest
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlantaVehiculoForm(
    index: Int,
    data: LlantaVehiculoFormData,
    llantas: List<Llanta>,
    medidasConParametro: Set<String> = emptySet(),
    onOpenLlantasAdmin: () -> Unit = {},
    showValidationErrors: Boolean = false,
    collapsed: Boolean = false,
    onToggleCollapsed: () -> Unit = {},
    onFormPositioned: (Int, Float, Int) -> Unit = { _, _, _ -> },
    onDataChange: (LlantaVehiculoFormData) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var filteredLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }

    // Filtrar llantas cuando cambie el texto de búsqueda
    LaunchedEffect(searchText) {
            if (searchText.isNotEmpty() && data.selectedLlanta == null) {
                // Mostrar todas las llantas disponibles
                filteredLlantas = llantas.filter { llanta ->
                    llanta.LlantasMarca.contains(searchText, ignoreCase = true) ||
                    llanta.LlantasModelo.contains(searchText, ignoreCase = true) ||
                    llanta.LlantasMedida.toString().contains(searchText)
                }.take(10)
            showSuggestions = filteredLlantas.isNotEmpty()
        } else {
            filteredLlantas = emptyList()
            showSuggestions = false
        }
    }

    val formBorderColor = when {
        data.isValid() -> Color(0xFF2E7D32)
        showValidationErrors && !data.isValid() -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onFormPositioned(index, coords.positionInRoot().y, coords.size.height)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, formBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Llanta $index",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onToggleCollapsed) {
                    Text(
                        text = if (collapsed) "+" else "-",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (collapsed) {
            } else {
                // Campo de búsqueda de llanta
                Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = data.selectedLlanta?.let { "${it.LlantasMarca} ${it.LlantasModelo} - ${it.LlantasMedida}\"" } ?: searchText,
                        onValueChange = { newValue ->
                            if (data.selectedLlanta == null) {
                                searchText = newValue
                            } else {
                                // Si hay una llanta seleccionada y el usuario empieza a escribir, limpiar la selección
                                onDataChange(data.copy(selectedLlanta = null))
                                searchText = newValue
                            }
                        },
                        label = { Text("Buscar Llanta") },
                        placeholder = { Text("Escribe marca, modelo o medida...") },
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
                        isError = showValidationErrors && data.selectedLlanta == null,
                        singleLine = true,
                        readOnly = data.selectedLlanta != null
                    )
                    
                    IconButton(onClick = onOpenLlantasAdmin, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Administrar llantas",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Advertencia inline si la llanta seleccionada no tiene parámetros
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

                // Sugerencias de llantas
                if (showSuggestions && data.selectedLlanta == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            filteredLlantas.forEach { llanta ->
                                TextButton(
                                    onClick = {
                                        // Al seleccionar la llanta, llenar automáticamente los campos
                                        onDataChange(data.copy(
                                                    selectedLlanta = llanta,
                                                    precio = "0",
                                                    mm1 = llanta.LlantasMm.toString(),
                                                    mm2 = llanta.LlantasMm.toString(),
                                                    mm3 = llanta.LlantasMm.toString(),
                                                    mm4 = llanta.LlantasMm.toString()
                                                ))
                                        searchText = ""
                                        showSuggestions = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${llanta.LlantasMarca} ${llanta.LlantasModelo}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (!medidasConParametro.contains(llanta.LlantasMedida)) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "(Sin parámetros)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Medida: ${llanta.LlantasMedida}\" - Precio: $${llanta.LlantasPrecio}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (llanta != filteredLlantas.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
                }

                // Campo Precio
                OutlinedTextField(
                    value = data.precio,
                    onValueChange = { newPrecio ->
                        onDataChange(data.copy(precio = newPrecio))
                    },
                    label = { Text("Precio") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    isError = showValidationErrors && (data.precio.isBlank() || data.precio.toFloatOrNull() == null)
                )

                // Radio buttons para Piso
                Column {
                    Text("Piso:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = data.piso == "Original",
                                onClick = { onDataChange(data.copy(piso = "Original")) }
                            )
                            Text("Original")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = data.piso == "Vitalizado",
                                onClick = { onDataChange(data.copy(piso = "Vitalizado")) }
                            )
                            Text("Vitalizado")
                        }
                    }
                }

                OutlinedTextField(
                    value = data.noQuemado,
                    onValueChange = { onDataChange(data.copy(noQuemado = it)) },
                    label = { Text("No. Quemado / DOT") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = showValidationErrors && data.noQuemado.isBlank()
                )

                OutlinedTextField(
                    value = data.presion,
                    onValueChange = {
                        val raw = it
                        val num = raw.toFloatOrNull()
                        val clamped = if (num != null && num > 160f) "160" else raw
                        onDataChange(data.copy(presion = clamped))
                    },
                    label = { Text("Presión") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Campos MM en una fila
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = data.mm1,
                        onValueChange = { val p = it.toFloatOrNull(); onDataChange(data.copy(mm1 = if (p != null && p > 25.4f) "25.4" else it)) },
                        label = { Text("MM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        isError = showValidationErrors && (data.mm1.isBlank() || data.mm1.toFloatOrNull() == null)
                    )
                    OutlinedTextField(
                        value = data.mm2,
                        onValueChange = { val p = it.toFloatOrNull(); onDataChange(data.copy(mm2 = if (p != null && p > 25.4f) "25.4" else it)) },
                        label = { Text("MM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        isError = showValidationErrors && (data.mm2.isBlank() || data.mm2.toFloatOrNull() == null)
                    )
                    OutlinedTextField(
                        value = data.mm3,
                        onValueChange = { val p = it.toFloatOrNull(); onDataChange(data.copy(mm3 = if (p != null && p > 25.4f) "25.4" else it)) },
                        label = { Text("MM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        isError = showValidationErrors && (data.mm3.isBlank() || data.mm3.toFloatOrNull() == null)
                    )
                    OutlinedTextField(
                        value = data.mm4,
                        onValueChange = { val p = it.toFloatOrNull(); onDataChange(data.copy(mm4 = if (p != null && p > 25.4f) "25.4" else it)) },
                        label = { Text("MM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        isError = showValidationErrors && (data.mm4.isBlank() || data.mm4.toFloatOrNull() == null)
                    )
                }
            }
        }
    }
}

data class LlantaVehiculoFormData(
    val selectedLlanta: Llanta? = null,
    val mm1: String = "",
    val mm2: String = "",
    val mm3: String = "",
    val mm4: String = "",
    val piso: String = "Original",
    val noQuemado: String = "",
    val presion: String = "",
    val precio: String = "0"
) {
    fun isValid(): Boolean {
        return selectedLlanta != null &&
                mm1.isNotBlank() && mm1.toFloatOrNull() != null &&
                mm2.isNotBlank() && mm2.toFloatOrNull() != null &&
                mm3.isNotBlank() && mm3.toFloatOrNull() != null &&
                mm4.isNotBlank() && mm4.toFloatOrNull() != null &&
                noQuemado.isNotBlank() &&
                // Presión no es obligatoria: si se deja vacía está bien,
                // si se proporciona debe ser un número válido y <=160.
                (presion.isBlank() || (presion.toIntOrNull() != null && (presion.toIntOrNull() ?: 0) <= 160)) &&
                precio.isNotBlank() && precio.toFloatOrNull() != null
    }
}