package com.megatransportes.yokoh.ui.screens.parametros

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditParametroScreen(
    repository: YokohamaRepository,
    flota: Flota,
    llantaId: Int,
    parametro: Parametro? = null,
    onParametroSaved: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    var llanta by remember { mutableStateOf<Llanta?>(null) }
    var parametrosRC by remember { mutableStateOf(parametro?.ParametrosRC ?: "A") }
    var expandedRC by remember { mutableStateOf(false) }
    var parametrosPMin by remember { mutableStateOf(parametro?.ParametrosPMin?.toString() ?: "") }
    var parametrosPSug by remember { mutableStateOf(parametro?.ParametrosPSug?.toString() ?: "") }
    var parametrosPMax by remember { mutableStateOf(parametro?.ParametrosPMax?.toString() ?: "") }
    var parametrosProfMin by remember { mutableStateOf(parametro?.ParametrosProfMin?.toString() ?: "") }
    var parametrosProfMax by remember { mutableStateOf(parametro?.ParametrosProfMax?.toString() ?: "") }
    
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Cargar información de la llanta
    LaunchedEffect(llantaId) {
        isLoading = true
        // Cargar llantas asociadas a la flota y buscar la llanta por id
        repository.getLlantasByFlota(flota.idFlotas).fold(
            onSuccess = { llantas ->
                llanta = llantas.find { it.idLlantas == llantaId }
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = ErrorUtils.userMessage(error, "Error al cargar la llanta")
                isLoading = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (parametro == null) "Nuevo Parámetro" else "Editar Parámetro") 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Flota") }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                llanta == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Error al cargar la llanta",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Medida de la llanta
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Medida: ${llanta?.LlantasMedida}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // RC (Reencauche)
                        Text(
                            text = "Reencauche (RC)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        val rcOptions = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M")
                        
                        ExposedDropdownMenuBox(
                            expanded = expandedRC,
                            onExpandedChange = { expandedRC = !expandedRC }
                        ) {
                            OutlinedTextField(
                                value = parametrosRC,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selecciona RC") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRC)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedRC,
                                onDismissRequest = { expandedRC = false }
                            ) {
                                rcOptions.forEach { option ->
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

                        // Parámetros de Presión
                        Text(
                            text = "Parámetros de Presión (PSI)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = parametrosPMin,
                            onValueChange = { parametrosPMin = it },
                            label = { Text("Presión Mínima") },
                            placeholder = { Text("Ej: 90.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = parametrosPSug,
                            onValueChange = { parametrosPSug = it },
                            label = { Text("Presión Sugerida") },
                            placeholder = { Text("Ej: 100.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = parametrosPMax,
                            onValueChange = { parametrosPMax = it },
                            label = { Text("Presión Máxima") },
                            placeholder = { Text("Ej: 110.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider()

                        // Parámetros de Profundidad
                        Text(
                            text = "Parámetros de Profundidad (mm)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = parametrosProfMin,
                            onValueChange = { parametrosProfMin = it },
                            label = { Text("Profundidad Mínima") },
                            placeholder = { Text("Ej: 5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = parametrosProfMax,
                            onValueChange = { parametrosProfMax = it },
                            label = { Text("Profundidad Media") },
                            placeholder = { Text("Ej: 20") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Error message
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botones
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving
                            ) {
                                Text("Cancelar")
                            }

                            Button(
                                onClick = {
                                    // Validar campos
                                    if (parametrosPMin.isEmpty() || parametrosPSug.isEmpty() || 
                                        parametrosPMax.isEmpty() || parametrosProfMin.isEmpty() || 
                                        parametrosProfMax.isEmpty()) {
                                        errorMessage = "Todos los campos son obligatorios"
                                        return@Button
                                    }

                                    val pMin = parametrosPMin.toFloatOrNull()
                                    val pSug = parametrosPSug.toFloatOrNull()
                                    val pMax = parametrosPMax.toFloatOrNull()
                                    val profMin = parametrosProfMin.toIntOrNull()
                                    val profMax = parametrosProfMax.toIntOrNull()

                                    if (pMin == null || pSug == null || pMax == null || 
                                        profMin == null || profMax == null) {
                                        errorMessage = "Valores numéricos inválidos"
                                        return@Button
                                    }

                                    if (pMin > pSug || pSug > pMax) {
                                        errorMessage = "Presión mínima ≤ sugerida ≤ máxima"
                                        return@Button
                                    }

                                    if (profMin > profMax) {
                                        errorMessage = "Profundidad mínima debe ser ≤ máxima"
                                        return@Button
                                    }

                                    scope.launch {
                                        isSaving = true
                                        errorMessage = null

                                        val result = if (parametro == null) {
                                            // Crear nuevo
                                            repository.createParametro(
                                                ParametroCreateRequest(
                                                    Flotas_idFlotas = flota.idFlotas,
                                                    Llantas_idLlantas = llantaId,
                                                    ParametrosRC = parametrosRC,
                                                    ParametrosPMin = pMin,
                                                    ParametrosPSug = pSug,
                                                    ParametrosPMax = pMax,
                                                    ParametrosProfMin = profMin,
                                                    ParametrosProfMax = profMax
                                                )
                                            )
                                        } else {
                                            // Actualizar existente
                                            repository.updateParametro(
                                                parametro.idParametros,
                                                ParametroUpdateRequest(
                                                    ParametrosRC = parametrosRC,
                                                    ParametrosPMin = pMin,
                                                    ParametrosPSug = pSug,
                                                    ParametrosPMax = pMax,
                                                    ParametrosProfMin = profMin,
                                                    ParametrosProfMax = profMax
                                                )
                                            )
                                        }

                                        result.fold(
                                            onSuccess = {
                                                isSaving = false
                                                onParametroSaved()
                                            },
                                            onFailure = { error ->
                                                errorMessage = ErrorUtils.userMessage(error, "Error al guardar el parámetro")
                                                isSaving = false
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Guardar")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
