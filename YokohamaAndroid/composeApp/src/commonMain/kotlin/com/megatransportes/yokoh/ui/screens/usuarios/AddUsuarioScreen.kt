package com.megatransportes.yokoh.ui.screens.usuarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUsuarioScreen(
    repository: YokohamaRepository,
    onUsuarioCreated: () -> Unit,
    onBack: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var distribuidores by remember { mutableStateOf<List<Distribuidor>>(emptyList()) }
    var selectedDistribuidor by remember { mutableStateOf<Distribuidor?>(null) }
    var distributorSearch by remember { mutableStateOf("") }
    var showDistributorDropdown by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        repository.getDistribuidores()
            .onSuccess { result -> distribuidores = result }
            .onFailure { distribuidores = emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar Nuevo Usuario") },
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
                        text = "Información del Usuario",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Completa todos los campos para crear un nuevo usuario",
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
                label = { Text("Nombre Completo") },
                placeholder = { Text("Ej: Juan Pérez") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && nombre.isBlank(),
                supportingText = {
                    if (errorMessage != null && nombre.isBlank()) {
                        Text("El nombre es obligatorio")
                    }
                }
            )

            OutlinedTextField(
                value = telefono,
                onValueChange = { 
                    telefono = it
                    errorMessage = null
                },
                label = { Text("Teléfono") },
                placeholder = { Text("Ej: +52 555 123 4567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && telefono.isBlank(),
                supportingText = {
                    if (errorMessage != null && telefono.isBlank()) {
                        Text("El teléfono es obligatorio")
                    }
                }
            )

            OutlinedTextField(
                value = correo,
                onValueChange = { 
                    correo = it
                    errorMessage = null
                },
                label = { Text("Correo Electrónico") },
                placeholder = { Text("Ej: usuario@ejemplo.com") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && correo.isBlank(),
                supportingText = {
                    if (errorMessage != null && correo.isBlank()) {
                        Text("El correo es obligatorio")
                    }
                }
            )

            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    errorMessage = null
                },
                label = { Text("Contraseña") },
                placeholder = { Text("Mínimo 6 caracteres") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && password.isBlank(),
                supportingText = {
                    if (errorMessage != null && password.isBlank()) {
                        Text("La contraseña es obligatoria")
                    } else if (password.isNotEmpty() && password.length < 6) {
                        Text("La contraseña debe tener al menos 6 caracteres")
                    }
                }
            )

            ExposedDropdownMenuBox(
                expanded = showDistributorDropdown,
                onExpandedChange = { showDistributorDropdown = !showDistributorDropdown }
            ) {
                OutlinedTextField(
                    value = selectedDistribuidor?.DistribuidorNombre ?: distributorSearch,
                    onValueChange = {
                        distributorSearch = it
                        if (selectedDistribuidor != null) {
                            selectedDistribuidor = null
                        }
                    },
                    label = { Text("Distribuidor (opcional)") },
                    trailingIcon = {
                        if (selectedDistribuidor != null) {
                            IconButton(onClick = { selectedDistribuidor = null }) {
                                Icon(Icons.Default.Clear, "Limpiar")
                            }
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDistributorDropdown)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    readOnly = selectedDistribuidor != null
                )

                if (showDistributorDropdown) {
                    ExposedDropdownMenu(
                        expanded = showDistributorDropdown,
                        onDismissRequest = { showDistributorDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin distribuidor") },
                            onClick = {
                                selectedDistribuidor = null
                                distributorSearch = ""
                                showDistributorDropdown = false
                            }
                        )
                        distribuidores.filter { it.DistribuidorNombre.contains(distributorSearch, ignoreCase = true) }
                            .forEach { distribuidor ->
                                DropdownMenuItem(
                                    text = { Text(distribuidor.DistribuidorNombre) },
                                    onClick = {
                                        selectedDistribuidor = distribuidor
                                        distributorSearch = ""
                                        showDistributorDropdown = false
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
                    // Validación de campos
                    when {
                        nombre.isBlank() -> {
                            errorMessage = "El nombre es obligatorio"
                            return@Button
                        }
                        telefono.isBlank() -> {
                            errorMessage = "El teléfono es obligatorio"
                            return@Button
                        }
                        correo.isBlank() -> {
                            errorMessage = "El correo electrónico es obligatorio"
                            return@Button
                        }
                        !correo.contains("@") -> {
                            errorMessage = "El correo electrónico no es válido"
                            return@Button
                        }
                        password.isBlank() -> {
                            errorMessage = "La contraseña es obligatoria"
                            return@Button
                        }
                        password.length < 6 -> {
                            errorMessage = "La contraseña debe tener al menos 6 caracteres"
                            return@Button
                        }
                    }
                    
                    val usuarioRequest = UsuarioCreateRequest(
                        UsuariosNombre = nombre.trim(),
                        UsuariosTelefono = telefono.trim(),
                        UsuariosCorreo = correo.trim().lowercase(),
                        UsuariosPassword = password,
                        PerfilesUsuario_idPerfilesUsuario = 1, // Perfil por defecto
                        Distribuidor_idDistribuidor = selectedDistribuidor?.idDistribuidor
                    )
                    
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        repository.createUsuario(usuarioRequest)
                            .onSuccess {
                                isLoading = false
                                onUsuarioCreated()
                            }
                            .onFailure { error ->
                                isLoading = false
                                errorMessage = when {
                                    error.message?.contains("duplicate", ignoreCase = true) == true -> 
                                        "Ya existe un usuario con este correo electrónico"
                                    error.message?.contains("connection", ignoreCase = true) == true -> 
                                        "Error de conexión. Verifica tu conexión a internet"
                                    else -> ErrorUtils.userMessage(error, "No se pudo crear el usuario")
                                }
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
                        Text("Creando usuario...")
                    }
                } else {
                    Text("Crear Usuario", style = MaterialTheme.typography.titleMedium)
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
