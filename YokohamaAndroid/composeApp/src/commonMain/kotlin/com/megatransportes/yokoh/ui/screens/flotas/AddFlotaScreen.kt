package com.megatransportes.yokoh.ui.screens.flotas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.FlotaCreateRequest
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlotaScreen(
    repository: YokohamaRepository,
    onFlotaCreated: () -> Unit,
    onBack: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState()
    var nombre by remember { mutableStateOf("") }
    var clasificacion by remember { mutableStateOf("") }
    var zona by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("") }
    var ciudad by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar Nueva Flota") },
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Información de la Flota",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Completa todos los campos para crear una nueva flota",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { 
                    nombre = it
                    errorMessage = null
                },
                label = { Text("Nombre de la Flota") },
                placeholder = { Text("Ej: Flota Norte") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && nombre.isBlank(),
                supportingText = {
                    if (errorMessage != null && nombre.isBlank()) {
                        Text("El nombre es obligatorio")
                    }
                }
            )

            // Clasificación: seleccionar de opciones predefinidas
            val clasificacionOptions = listOf(
                "Carga en general",
                "Combustolera",
                "Gasera",
                "Granel",
                "Líquidos",
                "Minería",
                "Pasajeros",
                "Químicos",
                "Refrigerados",
                "Pruebas Especiales Yokohama"
            )
            var clasificacionExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = clasificacionExpanded,
                onExpandedChange = { clasificacionExpanded = it }
            ) {
                OutlinedTextField(
                    value = clasificacion,
                    onValueChange = { /* read-only */ },
                    label = { Text("Clasificación") },
                    placeholder = { Text("Selecciona una clasificación") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    isError = errorMessage != null && clasificacion.isBlank(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = clasificacionExpanded)
                    },
                    supportingText = {
                        if (errorMessage != null && clasificacion.isBlank()) {
                            Text("La clasificación es obligatoria")
                        }
                    }
                )

                ExposedDropdownMenu(
                    expanded = clasificacionExpanded,
                    onDismissRequest = { clasificacionExpanded = false }
                ) {
                    clasificacionOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                clasificacion = option
                                errorMessage = null
                                clasificacionExpanded = false
                            }
                        )
                    }
                }
            }

            // Zona: Exposed dropdown anchored to the text field (opens when field clicked)
            val zonaOptions = listOf("Centro", "Centro-Bajío", "Noroeste", "Norte", "Pacífico", "Sureste")
            var zonaExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = zonaExpanded,
                onExpandedChange = { zonaExpanded = it }
            ) {
                OutlinedTextField(
                    value = zona,
                    onValueChange = { /* read-only field */ },
                    label = { Text("Zona") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    isError = errorMessage != null && zona.isBlank(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = zonaExpanded)
                    },
                    supportingText = {
                        if (errorMessage != null && zona.isBlank()) {
                            Text("La zona es obligatoria")
                        }
                    }
                )

                ExposedDropdownMenu(
                    expanded = zonaExpanded,
                    onDismissRequest = { zonaExpanded = false }
                ) {
                    zonaOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                zona = option
                                // reset estado when zona changes so the user must pick an appropriate state
                                estado = ""
                                errorMessage = null
                                zonaExpanded = false
                            }
                        )
                    }
                }
            }

            // Estado: dependent dropdown based on selected Zona
            val estadoOptions = when (zona) {
                "Centro" -> listOf("Cdmx", "Estado de México", "Guerrero", "Morelos", "Puebla")
                "Centro-Bajío" -> listOf("Guanajuato", "Hidalgo", "Querétaro", "San Luis Potosí")
                "Noroeste" -> listOf("Baja California", "Baja California Sur", "Sinaloa", "Sonora")
                "Norte" -> listOf("Chihuahua", "Coahuila", "Durango", "Nuevo León", "Tamaulipas")
                "Pacífico" -> listOf("Aguascalientes", "Colima", "Jalisco", "Michoacán", "Nayarit", "Zacatecas")
                "Sureste" -> listOf("Campeche", "Chiapas", "Oaxaca", "Quintana Roo", "Tabasco", "Tlaxcala", "Veracruz", "Yucatán")
                else -> emptyList()
            }

            var estadoExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = estadoExpanded,
                onExpandedChange = { if (estadoOptions.isNotEmpty()) estadoExpanded = it }
            ) {
                OutlinedTextField(
                    value = estado,
                    onValueChange = { /* read-only */ },
                    label = { Text("Estado") },
                    placeholder = { Text(if (zona.isBlank()) "Selecciona primero una Zona" else "Selecciona un Estado") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = zona.isNotBlank() && estadoOptions.isNotEmpty(),
                    isError = errorMessage != null && estado.isBlank(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = estadoExpanded)
                    },
                    supportingText = {
                        if (errorMessage != null && estado.isBlank()) {
                            Text("El estado es obligatorio")
                        }
                    }
                )

                ExposedDropdownMenu(
                    expanded = estadoExpanded,
                    onDismissRequest = { estadoExpanded = false }
                ) {
                    if (estadoOptions.isEmpty()) {
                        // show a disabled hint if no options available
                        DropdownMenuItem(text = { Text("No hay estados disponibles") }, onClick = { })
                    } else {
                        estadoOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    estado = option
                                    // reset ciudad when estado changes so user must pick a valid city
                                    ciudad = ""
                                    errorMessage = null
                                    estadoExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Ciudad: dropdown dependent on selected Estado
            val ciudadOptions = when (estado) {
                // Centro
                "Cdmx" -> listOf("Ciudad de México")
                "Estado de México" -> listOf("Toluca", "Ecatepec", "Naucalpan")
                "Guerrero" -> listOf("Acapulco", "Chilpancingo")
                "Morelos" -> listOf("Cuernavaca", "Cuautla")
                "Puebla" -> listOf("Puebla", "Tehuacán")

                // Centro-Bajío
                "Guanajuato" -> listOf("Guanajuato", "León")
                "Hidalgo" -> listOf("Pachuca")
                "Querétaro" -> listOf("Querétaro")
                "San Luis Potosí" -> listOf("San Luis Potosí")

                // Noroeste
                "Baja California" -> listOf("Mexicali", "Tijuana")
                "Baja California Sur" -> listOf("La Paz", "Cabo San Lucas")
                "Sinaloa" -> listOf("Culiacán", "Mazatlán")
                "Sonora" -> listOf("Hermosillo", "Nogales")

                // Norte
                "Chihuahua" -> listOf("Chihuahua")
                "Coahuila" -> listOf("Saltillo", "Torreón")
                "Durango" -> listOf("Durango")
                "Nuevo León" -> listOf("Monterrey")
                "Tamaulipas" -> listOf("Ciudad Victoria", "Reynosa")

                // Pacífico
                "Aguascalientes" -> listOf("Aguascalientes")
                "Colima" -> listOf("Colima")
                "Jalisco" -> listOf("Guadalajara", "Zapopan")
                "Michoacán" -> listOf("Morelia", "Uruapan")
                "Nayarit" -> listOf("Tepic")
                "Zacatecas" -> listOf("Zacatecas")

                // Sureste
                "Campeche" -> listOf("Campeche")
                "Chiapas" -> listOf("Tuxtla Gutiérrez", "Tapachula")
                "Oaxaca" -> listOf("Oaxaca de Juárez")
                "Quintana Roo" -> listOf("Cancún", "Chetumal")
                "Tabasco" -> listOf("Villahermosa")
                "Tlaxcala" -> listOf("Tlaxcala")
                "Veracruz" -> listOf("Veracruz", "Xalapa")
                "Yucatán" -> listOf("Mérida")

                else -> emptyList()
            }

            var ciudadExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = ciudadExpanded,
                onExpandedChange = { if (ciudadOptions.isNotEmpty()) ciudadExpanded = it }
            ) {
                OutlinedTextField(
                    value = ciudad,
                    onValueChange = { /* read-only */ },
                    label = { Text("Ciudad") },
                    placeholder = { Text(if (estado.isBlank()) "Selecciona primero un Estado" else "Selecciona una Ciudad") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    enabled = estado.isNotBlank() && ciudadOptions.isNotEmpty(),
                    isError = errorMessage != null && ciudad.isBlank(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = ciudadExpanded)
                    },
                    supportingText = {
                        if (errorMessage != null && ciudad.isBlank()) {
                            Text("La ciudad es obligatoria")
                        }
                    }
                )

                ExposedDropdownMenu(
                    expanded = ciudadExpanded,
                    onDismissRequest = { ciudadExpanded = false }
                ) {
                    if (ciudadOptions.isEmpty()) {
                        DropdownMenuItem(text = { Text("No hay ciudades disponibles") }, onClick = { })
                    } else {
                        ciudadOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    ciudad = option
                                    errorMessage = null
                                    ciudadExpanded = false
                                }
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Validar campos
                    if (nombre.isBlank() || clasificacion.isBlank() || zona.isBlank() || 
                        estado.isBlank() || ciudad.isBlank()) {
                        errorMessage = "Todos los campos son obligatorios"
                        return@Button
                    }
                    
                    val flotaRequest = FlotaCreateRequest(
                        FlotasNombre = nombre.trim(),
                        FlotasClasificacion = clasificacion.trim(),
                        FlotasZona = zona.trim(),
                        FlotasEstado = estado.trim(),
                        FlotasCiudad = ciudad.trim(),
                        Usuarios_idUsuarios = currentUser?.idUsuarios
                    )
                    
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        repository.createFlota(flotaRequest)
                            .onSuccess {
                                isLoading = false
                                onFlotaCreated()
                            }
                            .onFailure { error ->
                                isLoading = false
                                errorMessage = ErrorUtils.userMessage(error, "No se pudo crear la flota")
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
                        Text("Creando flota...")
                    }
                } else {
                    Text("Crear Flota", style = MaterialTheme.typography.titleMedium)
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
