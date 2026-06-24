package com.megatransportes.yokoh.ui.screens.usuarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPerfilUsuarioScreen(
    repository: YokohamaRepository,
    perfil: PerfilesUsuario?,
    onPerfilSaved: () -> Unit,
    onBack: () -> Unit
) {
    var nombrePerfil by remember { mutableStateOf(perfil?.PerfilesUsuarioNombre ?: "") }
    var permisosDisponibles by remember { mutableStateOf<List<String>>(emptyList()) }
    var permisosAsignados by remember { mutableStateOf<List<Permisos>>(emptyList()) }
    // Guardar copia original para detectar cambios al actualizar
    var permisosOriginales by remember { mutableStateOf<List<Permisos>>(emptyList()) }
    var permisoSearch by remember { mutableStateOf("") }
    var showPermisoDropdown by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Lista de permisos predefinidos
    val permisosBase = listOf(
        "Gestionar Usuarios",
        "Gestionar Flotas",
        "Gestionar Vehículos",
        "Eliminar vehículo",
        "Editar Catálogos",
        "Editar Parámetros",
        "Asignar Llantas",
        "Terminar prueba",
    )

    LaunchedEffect(perfil) {
        permisosDisponibles = permisosBase
        
        perfil?.let { perfilExistente ->
            coroutineScope.launch {
                repository.getPermisosByPerfilId(perfilExistente.idPerfilesUsuario)
                    .onSuccess { result -> 
                        permisosAsignados = result
                        permisosOriginales = result
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (perfil == null) "Nuevo Perfil" else "Editar Perfil") 
                },
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
            // Información del perfil
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Información del Perfil",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = nombrePerfil,
                        onValueChange = { nombrePerfil = it },
                        label = { Text("Nombre del Perfil") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Permisos
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Permisos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Dropdown para agregar permisos
                    ExposedDropdownMenuBox(
                        expanded = showPermisoDropdown,
                        onExpandedChange = { showPermisoDropdown = !showPermisoDropdown }
                    ) {
                        OutlinedTextField(
                            value = permisoSearch,
                            onValueChange = { permisoSearch = it },
                            label = { Text("Buscar y agregar permiso") },
                            trailingIcon = {
                                if (permisoSearch.isNotEmpty()) {
                                    IconButton(onClick = { permisoSearch = "" }) {
                                        Icon(Icons.Default.Clear, "Limpiar")
                                    }
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPermisoDropdown)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )

                        if (showPermisoDropdown) {
                            ExposedDropdownMenu(
                                expanded = showPermisoDropdown,
                                onDismissRequest = { showPermisoDropdown = false }
                            ) {
                                val permisosYaAsignados = permisosAsignados.map { it.PermisosNombre }
                                permisosDisponibles.filter { permiso ->
                                    permiso.contains(permisoSearch, ignoreCase = true) &&
                                    !permisosYaAsignados.contains(permiso)
                                }.forEach { permiso ->
                                    DropdownMenuItem(
                                        text = { Text(permiso) },
                                        onClick = {
                                            // Agregar el permiso a la lista
                                            val nuevoPermiso = Permisos(
                                                idPermisos = 0, // Temporal
                                                PerfilesUsuario_idPerfilesUsuario = perfil?.idPerfilesUsuario ?: 0,
                                                PermisosNombre = permiso
                                            )
                                            permisosAsignados = permisosAsignados + nuevoPermiso
                                            showPermisoDropdown = false
                                            permisoSearch = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Lista de permisos asignados
                    if (permisosAsignados.isEmpty()) {
                        Text(
                            text = "No hay permisos asignados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        permisosAsignados.forEach { permiso ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = permiso.PermisosNombre,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        permisosAsignados = permisosAsignados - permiso
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, "Eliminar permiso")
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
                        if (nombrePerfil.isBlank()) {
                            errorMessage = "El nombre del perfil es requerido"
                            return@Button
                        }
                        
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            
                            try {
                                if (perfil == null) {
                                    // Crear nuevo perfil
                                    val createRequest = PerfilesUsuarioCreateRequest(
                                        PerfilesUsuarioNombre = nombrePerfil
                                    )
                                    
                                    repository.createPerfilUsuario(createRequest)
                                        .onSuccess { nuevoPerfil ->
                                            // Crear los permisos asociados
                                            permisosAsignados.forEach { permiso ->
                                                val permisoRequest = PermisosCreateRequest(
                                                    PerfilesUsuario_idPerfilesUsuario = nuevoPerfil.idPerfilesUsuario,
                                                    PermisosNombre = permiso.PermisosNombre
                                                )
                                                repository.createPermiso(permisoRequest)
                                            }
                                            isLoading = false
                                            onPerfilSaved()
                                        }
                                        .onFailure { error ->
                                            isLoading = false
                                            errorMessage = ErrorUtils.userMessage(error, "No se pudo crear el perfil")
                                        }
                                } else {
                                    // Actualizar perfil existente
                                    val updateRequest = PerfilesUsuarioUpdateRequest(
                                        PerfilesUsuarioNombre = nombrePerfil
                                    )
                                    
                                    repository.updatePerfilUsuario(perfil.idPerfilesUsuario, updateRequest)
                                        .onSuccess {
                                                // Actualizar permisos: crear los nuevos y borrar los eliminados
                                                // Permisos nuevos: en permisosAsignados pero no en permisosOriginales (sin id)
                                                val originalesNombres = permisosOriginales.map { it.PermisosNombre }
                                                val actualesNombres = permisosAsignados.map { it.PermisosNombre }

                                                // Crear permisos agregados
                                                permisosAsignados.filter { it.PermisosNombre !in originalesNombres }.forEach { permiso ->
                                                    val permisoRequest = PermisosCreateRequest(
                                                        PerfilesUsuario_idPerfilesUsuario = perfil.idPerfilesUsuario,
                                                        PermisosNombre = permiso.PermisosNombre
                                                    )
                                                    repository.createPermiso(permisoRequest)
                                                }

                                                // Borrar permisos removidos
                                                permisosOriginales.filter { it.PermisosNombre !in actualesNombres }.forEach { permisoRemovido ->
                                                    if (permisoRemovido.idPermisos != 0) {
                                                        repository.deletePermiso(permisoRemovido.idPermisos)
                                                    }
                                                }

                                                isLoading = false
                                                onPerfilSaved()
                                        }
                                        .onFailure { error ->
                                            isLoading = false
                                            errorMessage = ErrorUtils.userMessage(error, "No se pudo actualizar el perfil")
                                        }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = ErrorUtils.userMessage(e, "Error inesperado")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && nombrePerfil.isNotBlank()
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
}
