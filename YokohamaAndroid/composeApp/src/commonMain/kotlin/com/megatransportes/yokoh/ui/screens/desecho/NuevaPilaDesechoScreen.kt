package com.megatransportes.yokoh.ui.screens.desecho

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.models.PruebasDesecho
import com.megatransportes.yokoh.data.models.PruebasDesechoCreateRequest
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.DateFormatter
import com.megatransportes.yokoh.utils.TimeProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaPilaDesechoScreen(
    repository: YokohamaRepository,
    onPilaCreada: (PruebasDesecho) -> Unit,
    onBack: () -> Unit
) {
    // Ahora recibimos un valor numérico (flotante) como texto y lo validamos antes de enviar
    var nombrePila by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Pila de Desecho") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = nombrePila,
                onValueChange = { raw ->
                    // Filtrar para permitir sólo dígitos y un punto decimal
                    val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                    val parts = filtered.split('.')
                    nombrePila = if (parts.size <= 2) filtered else parts.take(2).joinToString(".")
                },
                label = { Text("Valor de la pila") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Fecha: ${DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd")}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Button(
                onClick = {
                    if (nombrePila.isNotBlank() && nombrePila.toFloatOrNull() != null) {
                        isLoading = true
                        coroutineScope.launch {
                            repository.createPruebaDesecho(
                                PruebasDesechoCreateRequest(
                                    PruebasDesechoNombre = nombrePila,
                                    PruebasDesechoFecha = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd")
                                )
                            ).onSuccess { prueba ->
                                onPilaCreada(prueba)
                            }.onFailure {
                                // Manejar error
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = nombrePila.isNotBlank() && nombrePila.toFloatOrNull() != null && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Crear Pila")
                }
            }
        }
    }
}