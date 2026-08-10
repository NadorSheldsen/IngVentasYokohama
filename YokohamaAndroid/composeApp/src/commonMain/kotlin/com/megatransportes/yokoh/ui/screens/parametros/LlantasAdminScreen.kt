package com.megatransportes.yokoh.ui.screens.parametros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.megatransportes.yokoh.ui.components.PlatformLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.ui.components.BluetoothCaliperAutoListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlantasAdminScreen(
    repository: YokohamaRepository,
    onBack: () -> Unit,
    showDialogState: MutableState<Boolean>? = null,
    addRequestState: MutableState<Boolean>? = null,
    flotaId: Int? = null
) {

    var allLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var llantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val internalShowDialog = showDialogState ?: remember { mutableStateOf(false) }
    val internalAddRequest = addRequestState ?: remember { mutableStateOf(false) }
    var editingLlanta by remember { mutableStateOf<Llanta?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var pendingCreateReq by remember { mutableStateOf<LlantaCreateRequest?>(null) }

    val scope = rememberCoroutineScope()

    fun loadAll() {
        scope.launch {
            isLoading = true
            repository.getAllLlantas().fold(onSuccess = { list -> allLlantas = list; llantas = list; isLoading = false }, onFailure = { e -> errorMessage = ErrorUtils.userMessage(e, "Error cargando llantas"); isLoading = false })
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    LaunchedEffect(internalAddRequest.value) {
        if (internalAddRequest.value) {
            editingLlanta = null
            internalShowDialog.value = true
            internalAddRequest.value = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Llantas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingLlanta = null
                    internalShowDialog.value = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar llanta")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(16.dp)) {

            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    if (q.isEmpty()) {
                        llantas = allLlantas
                    } else {
                        val lower = q.trim().lowercase()
                        llantas = allLlantas.filter { l ->
                            val medida = l.LlantasMedida
                            val marca = l.LlantasMarca
                            val modelo = l.LlantasModelo
                            medida.lowercase().contains(lower) || marca.lowercase().contains(lower) || modelo.lowercase().contains(lower)
                        }
                        if (allLlantas.isEmpty()) {
                            scope.launch {
                                repository.searchLlantasByMedida(q).fold(onSuccess = { llantas = it }, onFailure = { llantas = emptyList() })
                            }
                        }
                    }
                },
                label = { Text("Buscar llantas") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (llantas.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No hay llantas") }
            } else {
                PlatformLazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(llantas, key = { it.idLlantas }) { llanta ->
                        Card(modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(2.dp, Color.Red), shape = RoundedCornerShape(8.dp))) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = llanta.LlantasMedida)
                                    Text(text = "${llanta.LlantasMarca} ${llanta.LlantasModelo}")
                                }

                                Row {
                                    IconButton(onClick = { editingLlanta = llanta; internalShowDialog.value = true }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                    IconButton(onClick = {
                                        scope.launch {
                                            repository.deleteLlanta(llanta.idLlantas).fold(onSuccess = { errorMessage = null; loadAll() }, onFailure = { errorMessage = ErrorUtils.userMessage(it, "No se pudo eliminar la llanta") })
                                        }
                                    }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                                }

                            }

                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }

        }

        if (internalShowDialog.value) {
            LlantaEditDialog(editing = editingLlanta, onDismiss = { internalShowDialog.value = false }, onSave = { createReq, updateId ->
                if (updateId == null && flotaId != null) {
                    // Nueva llanta con flota: guardar request pendiente y abrir parámetros
                    pendingCreateReq = createReq
                    internalShowDialog.value = false
                } else {
                    // Editar o sin flota: guardar directamente
                    scope.launch {
                        if (updateId == null) {
                            repository.createLlanta(createReq).fold(onSuccess = { errorMessage = null; loadAll(); internalShowDialog.value = false }, onFailure = { errorMessage = ErrorUtils.userMessage(it, "No se pudo crear la llanta") })
                        } else {
                            repository.updateLlanta(updateId, LlantaUpdateRequest(createReq.LlantasMarca, createReq.LlantasModelo, createReq.LlantasPrecio, createReq.LlantasMedida, createReq.LlantasMm)).fold(onSuccess = { errorMessage = null; loadAll(); internalShowDialog.value = false }, onFailure = { errorMessage = ErrorUtils.userMessage(it, "No se pudo actualizar la llanta") })
                        }
                    }
                }
            })
        }

        if (pendingCreateReq != null && flotaId != null) {
            ParametrosDialog(
                flotaId = flotaId,
                createReq = pendingCreateReq!!,
                repository = repository,
                onDismiss = {
                    pendingCreateReq = null
                },
                onSaved = {
                    pendingCreateReq = null
                    loadAll()
                }
            )
        }
    }
}

@Composable
fun LlantaEditDialog(editing: Llanta?, onDismiss: () -> Unit, onSave: (LlantaCreateRequest, Int?) -> Unit) {

    var marca by remember { mutableStateOf(editing?.LlantasMarca ?: "") }
    var modelo by remember { mutableStateOf(editing?.LlantasModelo ?: "") }
    var medida by remember { mutableStateOf(editing?.LlantasMedida ?: "") }
    var mm by remember { mutableStateOf((editing?.LlantasMm ?: 0).toString()) }
    var precio by remember { mutableStateOf(editing?.LlantasPrecio?.toString() ?: "") }

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {

        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .imePadding()
            ) {

                Text(text = if (editing == null) "Agregar llanta" else "Editar llanta", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(value = medida, onValueChange = { medida = it }, label = { Text("Medida") }, modifier = Modifier.fillMaxWidth())

                // MM field (el valor se coloca en el campo enfocado con el calibrador Bluetooth)
                BluetoothCaliperAutoListener(
                    onMeasurementReceived = { value ->
                        mm = value.toString()
                    }
                )
                OutlinedTextField(
                    value = mm,
                    onValueChange = { mm = it },
                    label = { Text("Mm") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(value = precio, onValueChange = { precio = it }, label = { Text("Precio") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val p = precio.toFloatOrNull()
                        val mmFloat = mm.toFloatOrNull() ?: 0f
                        val req = LlantaCreateRequest(LlantasMarca = marca, LlantasModelo = modelo, LlantasPrecio = p, LlantasMedida = medida, LlantasMm = mmFloat)
                        onSave(req, editing?.idLlantas)
                    }) { Text("Guardar") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametrosDialog(
    flotaId: Int,
    createReq: LlantaCreateRequest,
    repository: YokohamaRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var parametrosRC by remember { mutableStateOf("A") }
    var expandedRC by remember { mutableStateOf(false) }
    var parametrosPMin by remember { mutableStateOf("") }
    var parametrosPSug by remember { mutableStateOf("") }
    var parametrosPMax by remember { mutableStateOf("") }
    var parametrosProfMin by remember { mutableStateOf("") }
    var parametrosProfMax by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun validateAndSave() {
        if (parametrosPMin.isEmpty() || parametrosPSug.isEmpty() || parametrosPMax.isEmpty() || parametrosProfMin.isEmpty() || parametrosProfMax.isEmpty()) {
            formError = "Todos los campos son obligatorios"
            return
        }
        val pMin = parametrosPMin.toFloatOrNull()
        val pSug = parametrosPSug.toFloatOrNull()
        val pMax = parametrosPMax.toFloatOrNull()
        val profMin = parametrosProfMin.toIntOrNull()
        val profMax = parametrosProfMax.toIntOrNull()
        if (pMin == null || pSug == null || pMax == null || profMin == null || profMax == null) {
            formError = "Valores numéricos inválidos"
            return
        }
        if (pMin > pSug || pSug > pMax) {
            formError = "Presión mínima ≤ sugerida ≤ máxima"
            return
        }
        if (profMin > profMax) {
            formError = "Profundidad mínima debe ser ≤ máxima"
            return
        }
        scope.launch {
            isSaving = true
            formError = null
            // Primero crear la llanta
            repository.createLlanta(createReq).fold(
                onSuccess = { created ->
                    // Luego crear el parámetro con el id de la llanta nueva
                    repository.createParametro(
                        ParametroCreateRequest(
                            Flotas_idFlotas = flotaId,
                            Llantas_idLlantas = created.idLlantas,
                            ParametrosRC = parametrosRC,
                            ParametrosPMin = pMin,
                            ParametrosPSug = pSug,
                            ParametrosPMax = pMax,
                            ParametrosProfMin = profMin,
                            ParametrosProfMax = profMax
                        )
                    ).fold(
                        onSuccess = { isSaving = false; onSaved() },
                        onFailure = { error ->
                            formError = ErrorUtils.userMessage(error, "Error al guardar los parámetros")
                            isSaving = false
                        }
                    )
                },
                onFailure = { error ->
                    formError = ErrorUtils.userMessage(error, "Error al crear la llanta")
                    isSaving = false
                }
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Agregar parámetros", style = MaterialTheme.typography.titleLarge)

                Text(text = "RC (Reencauche)", style = MaterialTheme.typography.bodyMedium)

                ExposedDropdownMenuBox(
                    expanded = expandedRC,
                    onExpandedChange = { expandedRC = !expandedRC }
                ) {
                    OutlinedTextField(
                        value = parametrosRC,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Selecciona RC") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRC) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedRC,
                        onDismissRequest = { expandedRC = false }
                    ) {
                        listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    parametrosRC = option
                                    expandedRC = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(text = "Presión (PSI)", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = parametrosPMin,
                    onValueChange = { parametrosPMin = it },
                    label = { Text("Presión Mínima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parametrosPSug,
                    onValueChange = { parametrosPSug = it },
                    label = { Text("Presión Sugerida") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parametrosPMax,
                    onValueChange = { parametrosPMax = it },
                    label = { Text("Presión Máxima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(text = "Profundidad (mm)", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = parametrosProfMin,
                    onValueChange = { parametrosProfMin = it },
                    label = { Text("Profundidad Mínima") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parametrosProfMax,
                    onValueChange = { parametrosProfMax = it },
                    label = { Text("Profundidad Media") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (formError != null) {
                    Text(text = formError ?: "", color = MaterialTheme.colorScheme.error)
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { validateAndSave() },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}
