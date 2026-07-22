package com.megatransportes.yokoh.ui.screens.parametros



import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material.icons.Icons

// navigationIcon removed per request

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

import androidx.compose.foundation.background

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.border

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.window.Dialog

import com.megatransportes.yokoh.data.models.Llanta

import com.megatransportes.yokoh.data.models.LlantaCreateRequest

import com.megatransportes.yokoh.data.models.LlantaUpdateRequest

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import kotlinx.coroutines.launch

import com.megatransportes.yokoh.utils.ErrorUtils

import com.megatransportes.yokoh.ui.components.BluetoothCaliperButton



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun LlantasAdminScreen(

    repository: YokohamaRepository,

    onBack: () -> Unit,

    showDialogState: MutableState<Boolean>? = null,

    addRequestState: MutableState<Boolean>? = null

) {

    // allLlantas keeps the full list from the server; llantas is the filtered view shown in the UI

    var allLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }

    var llantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }

    var query by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }

    val internalShowDialog = showDialogState ?: remember { mutableStateOf(false) }

    val internalAddRequest = addRequestState ?: remember { mutableStateOf(false) }

    var editingLlanta by remember { mutableStateOf<Llanta?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }



    val scope = rememberCoroutineScope()



    fun loadAll() {

        scope.launch {

            isLoading = true

            repository.getAllLlantas().fold(onSuccess = { list -> allLlantas = list; llantas = list; isLoading = false }, onFailure = { e -> errorMessage = ErrorUtils.userMessage(e, "Error cargando llantas"); isLoading = false })

        }

    }



    LaunchedEffect(Unit) { loadAll() }



    // If parent requested an "add", open dialog and reset the request flag

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

                        // restore full list

                        llantas = allLlantas

                    } else {

                        // Filter locally by medida, marca or modelo (case-insensitive)

                        val lower = q.trim().lowercase()

                        llantas = allLlantas.filter { l ->

                            val medida = l.LlantasMedida

                            val marca = l.LlantasMarca

                            val modelo = l.LlantasModelo

                            medida.lowercase().contains(lower) || marca.lowercase().contains(lower) || modelo.lowercase().contains(lower)

                        }

                        // If we don't have the full list yet (rare), fallback to server search by medida

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

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {

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

                scope.launch {

                    if (updateId == null) {

                        repository.createLlanta(createReq).fold(onSuccess = { errorMessage = null; loadAll(); internalShowDialog.value = false }, onFailure = { errorMessage = ErrorUtils.userMessage(it, "No se pudo crear la llanta") })

                    } else {

                        repository.updateLlanta(updateId, LlantaUpdateRequest(createReq.LlantasMarca, createReq.LlantasModelo, createReq.LlantasPrecio, createReq.LlantasMedida, createReq.LlantasMm)).fold(onSuccess = { errorMessage = null; loadAll(); internalShowDialog.value = false }, onFailure = { errorMessage = ErrorUtils.userMessage(it, "No se pudo actualizar la llanta") })

                    }

                }

            })

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



    Dialog(onDismissRequest = onDismiss) {

        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                Text(text = if (editing == null) "Agregar llanta" else "Editar llanta", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(value = medida, onValueChange = { medida = it }, label = { Text("Medida") }, modifier = Modifier.fillMaxWidth())

                

                // MM field with Bluetooth caliper button

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(8.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    OutlinedTextField(

                        value = mm,

                        onValueChange = { mm = it },

                        label = { Text("Mm") },

                        modifier = Modifier.weight(1f),

                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)

                    )

                    BluetoothCaliperButton(

                        onMeasurementReceived = { value ->

                            mm = value.toString()

                        },

                        modifier = Modifier.size(44.dp)

                    )

                }

                

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

