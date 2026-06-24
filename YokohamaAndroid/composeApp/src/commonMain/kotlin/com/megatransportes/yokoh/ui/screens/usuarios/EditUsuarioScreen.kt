package com.megatransportes.yokoh.ui.screens.usuarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUsuarioScreen(
    repository: YokohamaRepository,
    usuario: Usuario,
    onUsuarioUpdated: () -> Unit,
    onPerfilesUsuarioClick: () -> Unit,
    onBack: () -> Unit
) {
    var nombre by remember { mutableStateOf(usuario.UsuariosNombre) }
    var telefono by remember { mutableStateOf(usuario.UsuariosTelefono) }
    var correo by remember { mutableStateOf(usuario.UsuariosCorreo) }
    var password by remember { mutableStateOf("") }
    
    var perfilesUsuario by remember { mutableStateOf<List<PerfilesUsuario>>(emptyList()) }
    var selectedPerfil by remember { mutableStateOf<PerfilesUsuario?>(null) }
    var perfilSearch by remember { mutableStateOf("") }
    var showPerfilDropdown by remember { mutableStateOf(false) }

    var distribuidores by remember { mutableStateOf<List<Distribuidor>>(emptyList()) }
    var selectedDistribuidor by remember { mutableStateOf<Distribuidor?>(null) }
    var distributorSearch by remember { mutableStateOf("") }
    var showDistributorDropdown by remember { mutableStateOf(false) }
    
    var flotas by remember { mutableStateOf<List<Flota>>(emptyList()) }
    var flotasAsignadas by remember { mutableStateOf<List<Flota>>(emptyList()) }
    var flotasUsuarios by remember { mutableStateOf<List<FlotasUsuarios>>(emptyList()) }
    var flotaSearch by remember { mutableStateOf("") }
    var showFlotaDialog by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Cargar datos iniciales
    LaunchedEffect(usuario.idUsuarios) {
        coroutineScope.launch {
            // Cargar perfiles de usuario
            repository.getPerfilesUsuario()
                .onSuccess { result -> 
                    perfilesUsuario = result
                    selectedPerfil = result.find { it.idPerfilesUsuario == usuario.PerfilesUsuario_idPerfilesUsuario }
                }

            repository.getDistribuidores()
                .onSuccess { result ->
                    distribuidores = result
                    selectedDistribuidor = result.find { it.idDistribuidor == usuario.Distribuidor_idDistribuidor }
                }
                .onFailure { _ ->
                    distribuidores = emptyList()
                }
            
            // Cargar todas las flotas
            repository.getAllFlotas()
                .onSuccess { result -> flotas = result }
            
            // Cargar flotas asignadas al usuario
            repository.getFlotasByUsuarioId(usuario.idUsuarios)
                .onSuccess { result -> flotasAsignadas = result }
            // Cargar relaciones FlotasUsuarios (necesario para poder desasignar correctamente)
            repository.getFlotasUsuariosByUsuarioId(usuario.idUsuarios)
                .onSuccess { result -> flotasUsuarios = result }
                .onFailure { e ->
                    // If the endpoint is not available, fall back silently (don't show raw error to users)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Usuario") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Regresar")
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
            // Información básica del usuario
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Información Personal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        label = { Text("Teléfono") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = correo,
                        onValueChange = { correo = it },
                        label = { Text("Correo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Nueva Contraseña (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Distribuidor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                }
            }

            // Perfil de usuario
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                            text = "Perfil de Usuario",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onPerfilesUsuarioClick) {
                            Text("Gestionar Perfiles")
                        }
                    }
                    
                    // Dropdown para seleccionar perfil
                    ExposedDropdownMenuBox(
                        expanded = showPerfilDropdown,
                        onExpandedChange = { showPerfilDropdown = !showPerfilDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedPerfil?.PerfilesUsuarioNombre ?: perfilSearch,
                            onValueChange = {
                                perfilSearch = it
                                if (selectedPerfil != null) {
                                    selectedPerfil = null
                                }
                            },
                            label = { Text("Perfil de Usuario") },
                            trailingIcon = {
                                if (selectedPerfil != null) {
                                    IconButton(onClick = { selectedPerfil = null }) {
                                        Icon(Icons.Default.Clear, "Limpiar")
                                    }
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPerfilDropdown)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = selectedPerfil != null
                        )

                        if (showPerfilDropdown) {
                            ExposedDropdownMenu(
                                expanded = showPerfilDropdown,
                                onDismissRequest = { showPerfilDropdown = false }
                            ) {
                                perfilesUsuario.filter { perfil ->
                                    perfil.PerfilesUsuarioNombre.contains(perfilSearch, ignoreCase = true)
                                }.forEach { perfil ->
                                    DropdownMenuItem(
                                        text = { Text(perfil.PerfilesUsuarioNombre) },
                                        onClick = {
                                            selectedPerfil = perfil
                                            showPerfilDropdown = false
                                            perfilSearch = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Flotas asignadas
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                            text = "Flotas Asignadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { showFlotaDialog = true }) {
                            Text("Asignar Flota")
                        }
                    }
                    
                    if (flotasAsignadas.isEmpty()) {
                        Text(
                            text = "No hay flotas asignadas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        flotasAsignadas.forEach { flota ->
                            val flotaUsuario = flotasUsuarios.find { it.Flotas_idFlotas == flota.idFlotas }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = flota.FlotasNombre,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (flotaUsuario != null) {
                                                repository.deleteFlotaUsuario(flotaUsuario.idFlotasUsuarios)
                                                    .onSuccess {
                                                        flotasAsignadas = flotasAsignadas.filter { it.idFlotas != flota.idFlotas }
                                                        flotasUsuarios = flotasUsuarios.filter { it.idFlotasUsuarios != flotaUsuario.idFlotasUsuarios }
                                                    }
                                                    .onFailure { error ->
                                                        errorMessage = ErrorUtils.userMessage(error, "No se pudo desasignar la flota")
                                                    }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, "Desasignar")
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Card(
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

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            
                            val updateRequest = UsuarioUpdateRequest(
                                UsuariosNombre = nombre,
                                UsuariosTelefono = telefono,
                                UsuariosCorreo = correo,
                                UsuariosPassword = password.takeIf { it.isNotBlank() },
                                PerfilesUsuario_idPerfilesUsuario = selectedPerfil?.idPerfilesUsuario ?: usuario.PerfilesUsuario_idPerfilesUsuario,
                                Distribuidor_idDistribuidor = selectedDistribuidor?.idDistribuidor
                            )
                            
                            repository.updateUsuario(usuario.idUsuarios, updateRequest)
                                .onSuccess {
                                    isLoading = false
                                    onUsuarioUpdated()
                                }
                                .onFailure { error ->
                                    isLoading = false
                                    errorMessage = ErrorUtils.userMessage(error, "No se pudo actualizar el usuario")
                                }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    // Diálogo para asignar flotas
    if (showFlotaDialog) {
        FlotaAssignmentDialog(
            flotas = flotas,
            flotasAsignadas = flotasAsignadas,
            onFlotaSelected = { flota ->
                coroutineScope.launch {
                    val request = FlotasUsuariosCreateRequest(
                        Usuarios_idUsuarios = usuario.idUsuarios,
                        Flotas_idFlotas = flota.idFlotas
                    )
                    repository.createFlotaUsuario(request)
                        .onSuccess { flotaUsuario ->
                            flotasAsignadas = flotasAsignadas + flota
                            flotasUsuarios = flotasUsuarios + flotaUsuario
                        }
                        .onFailure { error ->
                            errorMessage = ErrorUtils.userMessage(error, "No se pudo asignar la flota")
                        }
                }
            },
            onDismiss = { showFlotaDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlotaAssignmentDialog(
    flotas: List<Flota>,
    flotasAsignadas: List<Flota>,
    onFlotaSelected: (Flota) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val flotasDisponibles = flotas.filter { flota ->
        !flotasAsignadas.contains(flota) && 
        flota.FlotasNombre.contains(searchText, ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Asignar Flota",
                    style = MaterialTheme.typography.titleLarge
                )
                
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Buscar flota") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Buscar")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(flotasDisponibles, key = { it.idFlotas }) { flota ->
                        Card(
                            onClick = {
                                onFlotaSelected(flota)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = flota.FlotasNombre,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

