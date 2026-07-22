package com.megatransportes.yokoh.ui.screens.parametros

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.megatransportes.yokoh.data.models.TipoVehiculo
import com.megatransportes.yokoh.data.models.TipoVehiculoCreateRequest
import com.megatransportes.yokoh.data.models.TipoVehiculoUpdateRequest
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipoVehiculosAdminScreen(
    repository: YokohamaRepository,
    onBack: () -> Unit,
    showDialogState: MutableState<Boolean>? = null,
    addRequestState: MutableState<Boolean>? = null
) {
    var tipos by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val internalShowDialog = showDialogState ?: remember { mutableStateOf(false) }
    val internalAddRequest = addRequestState ?: remember { mutableStateOf(false) }
    var editingTipo by remember { mutableStateOf<TipoVehiculo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun loadAll() {
        scope.launch {
            isLoading = true
            repository.getTiposVehiculos().fold(onSuccess = { list -> tipos = list; isLoading = false }, onFailure = { e -> errorMessage = ErrorUtils.userMessage(e, "Error cargando tipos de vehículo"); isLoading = false })
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    LaunchedEffect(internalAddRequest.value) {
        if (internalAddRequest.value) {
            editingTipo = null
            internalShowDialog.value = true
            internalAddRequest.value = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tipos de vehículo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    // Always allow adding from this screen via top-right button
                    IconButton(onClick = {
                        editingTipo = null
                        internalShowDialog.value = true
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar tipo")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTipo = null
                internalShowDialog.value = true
            }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar tipo")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(16.dp)) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar por nombre o cantidad") },
                placeholder = { Text("Ej: Remolque o 2") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (tipos.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No hay tipos de vehículo") }
            } else {
                val displayed = if (query.isBlank()) tipos else tipos.filter { t ->
                    val q = query.trim().lowercase()
                    t.TipoVehiculosNombre.lowercase().contains(q) || t.TipoVehiculosCantLlantas.toString().contains(q)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayed, key = { it.idTipoVehiculos }) { tipo ->
                                Card(modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(2.dp, Color.Red), shape = RoundedCornerShape(8.dp))) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = tipo.TipoVehiculosNombre)
                                    Text(text = "Llantas: ${tipo.TipoVehiculosCantLlantas}")
                                }
                                Row {
                                    IconButton(onClick = { editingTipo = tipo; internalShowDialog.value = true }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                    IconButton(onClick = {
                                        scope.launch {
                                            repository.deleteTipoVehiculo(tipo.idTipoVehiculos).fold(onSuccess = { errorMessage = null; loadAll() }, onFailure = { errorMessage = ErrorUtils.userMessage(it, "No se pudo eliminar el tipo de vehículo") })
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
    }

    if (internalShowDialog.value) {
        TipoVehiculoEditDialog(editing = editingTipo, onDismiss = { internalShowDialog.value = false }, onSave = { req, updateId ->
            scope.launch {
                if (updateId == null) {
                    repository.createTipoVehiculo(req).fold(onSuccess = { errorMessage = null; loadAll(); internalShowDialog.value = false }, onFailure = { errorMessage = it.message })
                } else {
                    val updateReq = TipoVehiculoUpdateRequest(TipoVehiculosNombre = req.TipoVehiculosNombre, TipoVehiculosCantLlantas = req.TipoVehiculosCantLlantas, TipoVehiculosLlantasEmp = req.TipoVehiculosLlantasEmp)
                    repository.updateTipoVehiculo(updateId, updateReq).fold(onSuccess = { errorMessage = null; loadAll(); internalShowDialog.value = false }, onFailure = { errorMessage = it.message })
                }
            }
        })
    }
}

@Composable
fun TipoVehiculoEditDialog(editing: TipoVehiculo?, onDismiss: () -> Unit, onSave: (TipoVehiculoCreateRequest, Int?) -> Unit) {
    var nombre by remember { mutableStateOf(editing?.TipoVehiculosNombre ?: "") }
    // Don't prefill with 2 — show example placeholder instead. If editing, show the existing value.
    var cant by remember { mutableStateOf(editing?.TipoVehiculosCantLlantas?.toString() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editing == null) "Agregar Tipo de vehículo" else "Editar Tipo", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") })
                OutlinedTextField(value = cant, onValueChange = { cant = it }, label = { Text("Cantidad de llantas") }, placeholder = { Text("Ej: 2") })

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val cantInt = cant.toIntOrNull() ?: 2
                        val req = TipoVehiculoCreateRequest(TipoVehiculosNombre = nombre, TipoVehiculosCantLlantas = cantInt)
                        onSave(req, editing?.idTipoVehiculos)
                    }) { Text("Guardar") }
                }
            }
        }
    }
}
