package com.megatransportes.yokoh.ui.screens.usuarios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.PerfilesUsuario
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilesUsuarioListScreen(
    repository: YokohamaRepository,
    onPerfilClick: (PerfilesUsuario) -> Unit,
    onAddPerfilClick: () -> Unit,
    onBack: () -> Unit
) {
    var perfiles by remember { mutableStateOf<List<PerfilesUsuario>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var reloadTrigger by remember { mutableStateOf(0) }

    // Recarga la lista cada vez que reloadTrigger cambia (por ejemplo, después de crear/editar)
    LaunchedEffect(reloadTrigger) {
        errorMessage = null
        coroutineScope.launch {
            repository.getPerfilesUsuario()
                .onSuccess { result ->
                    perfiles = result
                    isLoading = false
                }
                .onFailure { error ->
                    errorMessage = ErrorUtils.userMessage(error, "Error cargando perfiles")
                    isLoading = false
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfiles de Usuario") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Llama a la pantalla de creación y luego recarga la lista
                onAddPerfilClick()
                reloadTrigger++
            }) {
                Icon(Icons.Default.Add, "Agregar perfil")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { reloadTrigger++ }
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
                
                perfiles.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No hay perfiles de usuario")
                        Text("Presiona + para crear uno nuevo")
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(perfiles, key = { it.idPerfilesUsuario }) { perfil ->
                            PerfilUsuarioCard(
                                perfil = perfil,
                                onClick = {
                                    onPerfilClick(perfil)
                                    reloadTrigger++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerfilUsuarioCard(
    perfil: PerfilesUsuario,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = perfil.PerfilesUsuarioNombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

