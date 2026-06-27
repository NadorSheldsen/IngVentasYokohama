package com.megatransportes.yokoh.ui.screens.usuarios



import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Clear

import androidx.compose.material.icons.filled.DarkMode

import androidx.compose.material.icons.automirrored.filled.ExitToApp

import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.LightMode

import androidx.compose.material.icons.filled.Search

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.border

import androidx.compose.foundation.background

import androidx.compose.ui.graphics.Color

import com.megatransportes.yokoh.data.models.Usuario

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.ui.components.Logo

import com.megatransportes.yokoh.utils.ErrorUtils

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun UsuariosScreen(

    repository: YokohamaRepository,

    onUsuarioSelected: (Usuario) -> Unit,

    onAddUsuarioClick: () -> Unit,

    onNavigateToFlotas: () -> Unit,

    isDarkTheme: Boolean,

    onToggleTheme: () -> Unit,

    onLogout: () -> Unit

) {

    var usuarios by remember { mutableStateOf<List<Usuario>>(emptyList()) }

    var filteredUsuarios by remember { mutableStateOf<List<Usuario>>(emptyList()) }

    var searchText by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    

    // Filtrar usuarios cuando cambie el texto de búsqueda

    LaunchedEffect(searchText, usuarios) {

        filteredUsuarios = if (searchText.isEmpty()) {

            usuarios

        } else {

            usuarios.filter { usuario ->

                usuario.UsuariosNombre.contains(searchText, ignoreCase = true) ||

                usuario.UsuariosCorreo.contains(searchText, ignoreCase = true) ||

                usuario.UsuariosTelefono.contains(searchText, ignoreCase = true) ||

                (usuario.PerfilesUsuarioNombre?.contains(searchText, ignoreCase = true) == true)

            }

        }

    }

    

    LaunchedEffect(key1 = Unit) {

        isLoading = true

        errorMessage = null

        

        repository.getAllUsuarios()

            .onSuccess { result ->

                usuarios = result

                isLoading = false

            }

            .onFailure { error ->

                isLoading = false

                errorMessage = ErrorUtils.userMessage(error, "Error cargando usuarios")

            }

    }

    

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,

        topBar = {

            TopAppBar(

                title = {

                    Row(

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        Logo(

                            modifier = Modifier

                                .height(32.dp)

                                .fillMaxWidth(0.2f)

                        )

                        Text("Usuarios")

                    }

                },

                actions = {

                    IconButton(onClick = onToggleTheme) {

                        Icon(

                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,

                            contentDescription = if (isDarkTheme) "Cambiar a modo claro" else "Cambiar a modo oscuro"

                        )

                    }

                    IconButton(onClick = onLogout) {

                        Icon(

                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,

                            contentDescription = "Cerrar sesión"

                        )

                    }

                }

            )

        },

        floatingActionButton = {

            FloatingActionButton(

                onClick = onAddUsuarioClick,

                containerColor = MaterialTheme.colorScheme.primary

            ) {

                Icon(

                    imageVector = Icons.Default.Add,

                    contentDescription = "Agregar usuario"

                )

            }

        },

        bottomBar = {

                    NavigationBar(

                    modifier = Modifier

                        .border(BorderStroke(2.dp, Color.Red))

                        .background(MaterialTheme.colorScheme.background),

                    containerColor = MaterialTheme.colorScheme.background,

                    tonalElevation = 0.dp,

                    contentColor = MaterialTheme.colorScheme.primary // Set icon and text color to primary for better contrast

                ) {

                NavigationBarItem(

                    icon = {

                        Icon(

                            imageVector = Icons.Default.Home,

                            contentDescription = "Flotas"

                        )

                    },

                    label = { Text("Flotas") },

                    selected = false,

                    onClick = onNavigateToFlotas,

                    colors = NavigationBarItemDefaults.colors(

                        selectedIconColor = MaterialTheme.colorScheme.primary,

                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,

                        selectedTextColor = MaterialTheme.colorScheme.primary,

                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,

                        indicatorColor = Color.Transparent

                    )

                )

                NavigationBarItem(

                    icon = {

                        Icon(

                            imageVector = Icons.Default.Person,

                            contentDescription = "Usuarios"

                        )

                    },

                    label = { Text("Usuarios") },

                    selected = true,

                    onClick = { /* Ya estamos en usuarios, no hacer nada */ },

                    colors = NavigationBarItemDefaults.colors(

                        selectedIconColor = MaterialTheme.colorScheme.primary,

                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,

                        selectedTextColor = MaterialTheme.colorScheme.primary,

                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,

                        indicatorColor = Color.Transparent

                    )

                )

            }

        }

    ) { paddingValues ->

        Column(

            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)

        ) {

            // Barra de búsqueda

            Card(

                modifier = Modifier.fillMaxWidth().padding(16.dp),

                colors = CardDefaults.cardColors(

                    containerColor = MaterialTheme.colorScheme.surfaceVariant

                )

            ) {

                OutlinedTextField(

                    value = searchText,

                    onValueChange = { searchText = it },

                    placeholder = { Text("Buscar usuarios...") },

                    leadingIcon = {

                        Icon(

                            imageVector = Icons.Default.Search,

                            contentDescription = "Buscar"

                        )

                    },

                    trailingIcon = {

                        if (searchText.isNotEmpty()) {

                            IconButton(onClick = { searchText = "" }) {

                                Icon(

                                    imageVector = Icons.Default.Clear,

                                    contentDescription = "Limpiar búsqueda"

                                )

                            }

                        }

                    },

                    modifier = Modifier.fillMaxWidth().padding(16.dp),

                    singleLine = true

                )

            }

            

            // Contenido principal

            Box(

                modifier = Modifier.fillMaxSize()

            ) {

                when {

                    isLoading -> {

                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    }

                    errorMessage != null -> {

                        ErrorScreen(

                            errorMessage = errorMessage!!,

                            onRetry = {

                                coroutineScope.launch {

                                    isLoading = true

                                    errorMessage = null

                                    repository.getAllUsuarios()

                                        .onSuccess { result -> usuarios = result }

                                        .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando usuarios") }

                                    isLoading = false

                                }

                            }

                        )

                    }

                    usuarios.isEmpty() -> {

                        Text(

                            text = "No hay usuarios registrados",

                            modifier = Modifier.align(Alignment.Center).padding(16.dp)

                        )

                    }

                    filteredUsuarios.isEmpty() -> {

                        Text(

                            text = "No se encontraron usuarios que coincidan con tu búsqueda",

                            modifier = Modifier.align(Alignment.Center).padding(16.dp)

                        )

                    }

                    else -> {

                        LazyColumn(

                            modifier = Modifier.fillMaxSize(),

                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),

                            verticalArrangement = Arrangement.spacedBy(8.dp)

                        ) {

                            items(filteredUsuarios, key = { it.idUsuarios }) { usuario ->

                                UsuarioItem(

                                    usuario = usuario,

                                    onClick = { onUsuarioSelected(usuario) }

                                )

                            }

                        }

                    }

                }

            }

        }

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun UsuarioItem(

    usuario: Usuario,

    onClick: () -> Unit

) {

    Card(

        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),

        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

    ) {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Text(

                text = usuario.UsuariosNombre,

                style = MaterialTheme.typography.titleLarge,

                maxLines = 1,

                overflow = TextOverflow.Ellipsis,

                color = MaterialTheme.colorScheme.primary

            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(

                text = "Email: ${usuario.UsuariosCorreo}",

                style = MaterialTheme.typography.bodyMedium

            )

            Text(

                text = "Teléfono: ${usuario.UsuariosTelefono}",

                style = MaterialTheme.typography.bodyMedium

            )

            Text(

                text = "Perfil: ${usuario.PerfilesUsuarioNombre ?: "No asignado"}",

                style = MaterialTheme.typography.bodyMedium

            )

        }

    }

}

