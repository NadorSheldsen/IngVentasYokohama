package com.megatransportes.yokoh.ui.screens.inspecciones

import com.megatransportes.yokoh.utils.ErrorUtils

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.models.PruebaInspeccion
import com.megatransportes.yokoh.data.models.PruebaInspeccionCreateRequest
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.platform.getLastKnownLocation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaPruebaInspeccionScreen(
    repository: YokohamaRepository,
    flota: Flota,
    onPruebaCreada: (PruebaInspeccion) -> Unit,
    onBack: () -> Unit
) {
    var titulo by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Inspección") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Información de la flota
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Flota: ${flota.FlotasNombre}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Creando nueva prueba de inspección",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Text(
                text = "Información de la Inspección",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título de la inspección") },
                placeholder = { Text("Ej: Inspección mensual - Enero 2024") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("La fecha se establecerá automáticamente al día de hoy")
                }
            )

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
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (titulo.isBlank()) {
                        errorMessage = "El título es requerido"
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        val loc = getLastKnownLocation()

                        // El backend automáticamente establece la fecha actual
                        val request = PruebaInspeccionCreateRequest(
                            PruebaInspeccionTitulo = titulo.trim(),
                            Flotas_idFlotas = flota.idFlotas,
                            latitude = loc?.latitude,
                            longitude = loc?.longitude
                        )

                        repository.createPruebaInspeccion(request)
                            .onSuccess { prueba ->
                                isLoading = false
                                onPruebaCreada(prueba)
                            }
                            .onFailure { error ->
                                isLoading = false
                                errorMessage = ErrorUtils.userMessage(error, "No se pudo crear la inspección")
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                enabled = !isLoading && titulo.isNotBlank(),
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
                        Text("Creando inspección...")
                    }
                } else {
                    Text("Crear Inspección", style = MaterialTheme.typography.titleMedium)
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
}