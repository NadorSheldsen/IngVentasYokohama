package com.megatransportes.yokoh.ui.screens.flotas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.models.FlotasUsuariosCreateRequest
import com.megatransportes.yokoh.data.models.Usuario
import com.megatransportes.yokoh.data.models.UsuarioWithFlotaUsuarioId
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.utils.ErrorUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlotaUsuariosScreen(
    repository: YokohamaRepository,
    flota: Flota,
    onBack: () -> Unit
) {
    var usuariosAsociados by remember { mutableStateOf<List<UsuarioWithFlotaUsuarioId>>(emptyList()) }
    var usuariosNoAsociados by remember { mutableStateOf<List<Usuario>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun loadUsuarios() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            repository.getUsuariosByFlotaId(flota.idFlotas)
                .onSuccess { usuarios ->
                    usuariosAsociados = usuarios
                    isLoading = false
                }
                .onFailure { error ->
                    isLoading = false
                    errorMessage = ErrorUtils.userMessage(error, "Error cargando usuarios")
                }
        }
    }

    fun loadUsuariosNoAsociados() {
        coroutineScope.launch {
            repository.getUsuariosNoAsociadosByFlotaId(flota.idFlotas)
                .onSuccess { usuarios -> usuariosNoAsociados = usuarios }
                .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando usuarios disponibles") }
        }
    }

    LaunchedEffect(flota.idFlotas) { loadUsuarios() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios de ${flota.FlotasNombre}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Regresar") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    loadUsuariosNoAsociados()
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "Agregar usuario") }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                errorMessage != null -> Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                usuariosAsociados.isEmpty() -> Text(
                    text = "No hay usuarios asociados a esta flota",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(usuariosAsociados, key = { it.idUsuarios }) { usuario ->
                            UsuarioAsociadoItem(
                                usuario = usuario,
                                onDesasociar = {
                                    coroutineScope.launch {
                                        repository.deleteFlotaUsuario(usuario.idFlotasUsuarios)
                                            .onSuccess { loadUsuarios() }
                                            .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error al desasociar usuario") }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Agregar Usuario", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar usuario...") },
                        leadingIcon = { Icon(Icons.Default.Search, "Buscar") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, "Limpiar") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredUsuarios = remember(usuariosNoAsociados, searchQuery) {
                        if (searchQuery.isBlank()) usuariosNoAsociados
                        else usuariosNoAsociados.filter { usuario ->
                            usuario.UsuariosNombre.contains(searchQuery, ignoreCase = true) ||
                            usuario.UsuariosCorreo.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filteredUsuarios.isEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank()) "No hay usuarios disponibles para asociar" else "No se encontraron usuarios que coincidan",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredUsuarios, key = { it.idUsuarios }) { usuario ->
                                UsuarioDisponibleItem(
                                    usuario = usuario,
                                    onAsociar = {
                                        coroutineScope.launch {
                                            val request = FlotasUsuariosCreateRequest(
                                                Usuarios_idUsuarios = usuario.idUsuarios,
                                                Flotas_idFlotas = flota.idFlotas
                                            )
                                            repository.createFlotaUsuario(request)
                                                .onSuccess {
                                                    showAddDialog = false
                                                    searchQuery = ""
                                                    loadUsuarios()
                                                }
                                                .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error al asociar usuario") }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showAddDialog = false; searchQuery = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
fun UsuarioAsociadoItem(
    usuario: UsuarioWithFlotaUsuarioId,
    onDesasociar: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(usuario.UsuariosNombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(usuario.UsuariosCorreo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                usuario.PerfilesUsuarioNombre?.let {
                    Text("Perfil: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDesasociar) { Icon(Icons.Default.Delete, "Desasociar usuario", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun UsuarioDisponibleItem(
    usuario: Usuario,
    onAsociar: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(usuario.UsuariosNombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(usuario.UsuariosCorreo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                usuario.PerfilesUsuarioNombre?.let {
                    Text("Perfil: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onAsociar) { Text("Asociar") }
        }
    }
}
