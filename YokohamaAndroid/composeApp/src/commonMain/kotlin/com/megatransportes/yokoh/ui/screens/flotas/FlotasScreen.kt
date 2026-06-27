package com.megatransportes.yokoh.ui.screens.flotas



import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.rememberLazyListState

import com.megatransportes.yokoh.utils.rememberSmoothFlingBehavior

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

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorScreen

import com.megatransportes.yokoh.ui.components.Logo

import com.megatransportes.yokoh.utils.ErrorUtils

import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun FlotasScreen(

    repository: YokohamaRepository,

    onFlotaSelected: (Flota) -> Unit,

    onAddFlotaClick: () -> Unit,

    onFlotaUsuariosClick: (Flota) -> Unit,

    onNavigateToUsuarios: () -> Unit,

    isDarkTheme: Boolean,

    onToggleTheme: () -> Unit,

    onLogout: () -> Unit

) {

    var flotas by remember { mutableStateOf<List<Flota>>(emptyList()) }

    var filteredFlotas by remember { mutableStateOf<List<Flota>>(emptyList()) }

    var searchText by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    

    // Filtrar flotas cuando cambie el texto de búsqueda

    LaunchedEffect(searchText, flotas) {

        filteredFlotas = if (searchText.isEmpty()) {

            flotas

        } else {

            flotas.filter { flota ->

                flota.FlotasNombre.contains(searchText, ignoreCase = true) ||

                flota.FlotasClasificacion.contains(searchText, ignoreCase = true) ||

                flota.FlotasZona.contains(searchText, ignoreCase = true) ||

                flota.FlotasEstado.contains(searchText, ignoreCase = true) ||

                flota.FlotasCiudad.contains(searchText, ignoreCase = true)

            }

        }

    }

    

    LaunchedEffect(key1 = Unit) {

        isLoading = true

        errorMessage = null

        

        repository.getFlotasForCurrentUser()

            .onSuccess { result ->

                flotas = result

                isLoading = false

            }

            .onFailure { error ->

                isLoading = false

                errorMessage = ErrorUtils.userMessage(error, "Error cargando flotas")

            }

    }

    

    // --- Permission checks: hide Usuarios tab and FAB when perfil lacks permissions ---

    val currentUser by repository.currentUser.collectAsState()

    var hasManageUsersPermission by remember { mutableStateOf(false) }

    var hasManageFlotasPermission by remember { mutableStateOf(false) }



    LaunchedEffect(currentUser) {

        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario

        if (perfilId != null) {

            try {

                val permisosResult = repository.getPermisosByPerfilId(perfilId)

                val permisosList = permisosResult.getOrNull().orEmpty()

                hasManageUsersPermission = permisosList.any { it.PermisosNombre == "Gestionar Usuarios" }

                hasManageFlotasPermission = permisosList.any { it.PermisosNombre == "Gestionar Flotas" }

            } catch (t: Throwable) {

                // If permission fetch fails, default to hiding restricted UI for safety.

                hasManageUsersPermission = false

                hasManageFlotasPermission = false

            }

        } else {

            hasManageUsersPermission = false

            hasManageFlotasPermission = false

        }

    }

    

    Scaffold(

        // Use the app theme background so this screen matches others

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

                        Text("Flotas")

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

            if (hasManageFlotasPermission) {

                FloatingActionButton(

                    onClick = onAddFlotaClick,

                    containerColor = MaterialTheme.colorScheme.primary

                ) {

                    Icon(

                        imageVector = Icons.Default.Add,

                        contentDescription = "Agregar flota"

                    )

                }

            }

        },

        bottomBar = {

            if (hasManageUsersPermission) {

                NavigationBar(

                    modifier = Modifier

                        .border(BorderStroke(2.dp, Color.Red)),

                    // Let the NavigationBar use the themed surface so it matches other screens

                    containerColor = MaterialTheme.colorScheme.surface,

                    tonalElevation = 0.dp,

                    contentColor = MaterialTheme.colorScheme.primary

                ) {

                    NavigationBarItem(

                        icon = {

                            Icon(

                                imageVector = Icons.Default.Home,

                                contentDescription = "Flotas"

                            )

                        },

                        label = { Text("Flotas") },

                        selected = true,

                        onClick = { /* Ya estamos en flotas, no hacer nada */ },

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

                        selected = false,

                        onClick = onNavigateToUsuarios,

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

        }

    ) { paddingValues ->

        Column(

            modifier = Modifier.fillMaxSize().padding(paddingValues)

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

                    placeholder = { Text("Buscar flotas...") },

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

                                    repository.getFlotasForCurrentUser()

                                        .onSuccess { result -> flotas = result }

                                        .onFailure { error -> errorMessage = ErrorUtils.userMessage(error, "Error cargando flotas") }

                                    isLoading = false

                                }

                            }

                        )

                    }

                    flotas.isEmpty() -> {

                        Text(

                            text = "No hay flotas disponibles para tu usuario",

                            modifier = Modifier.align(Alignment.Center).padding(16.dp)

                        )

                    }

                    filteredFlotas.isEmpty() -> {

                        Text(

                            text = "No se encontraron flotas que coincidan con tu búsqueda",

                            modifier = Modifier.align(Alignment.Center).padding(16.dp)

                        )

                    }

                    else -> {

                        val listState = rememberLazyListState()
                        val flingBehavior = rememberSmoothFlingBehavior()

                        LazyColumn(

                            state = listState,
                            flingBehavior = flingBehavior,

                            modifier = Modifier.fillMaxSize(),

                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),

                            verticalArrangement = Arrangement.spacedBy(8.dp)

                        ) {

                            items(filteredFlotas, key = { it.idFlotas }) { flota ->

                                FlotaItem(

                                    flota = flota,

                                    onClick = { onFlotaSelected(flota) },

                                    onUsuariosClick = { onFlotaUsuariosClick(flota) },

                                    showUsuarios = hasManageUsersPermission

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

fun FlotaItem(

    flota: Flota,

    onClick: () -> Unit,

    onUsuariosClick: () -> Unit,

    showUsuarios: Boolean

) {

    Card(

        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),

        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(

                    text = flota.FlotasNombre,

                    style = MaterialTheme.typography.titleLarge,

                    maxLines = 1,

                    overflow = TextOverflow.Ellipsis,

                    color = MaterialTheme.colorScheme.primary,

                    modifier = Modifier.weight(1f)

                )

                if (showUsuarios) {

                    IconButton(onClick = onUsuariosClick) {

                        Icon(imageVector = Icons.Default.Person, contentDescription = "Usuarios de la flota")

                    }

                }

            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(

                text = "Clasificación: ${flota.FlotasClasificacion}",

                style = MaterialTheme.typography.bodyMedium

            )

            Text(

                text = "Zona: ${flota.FlotasZona}",

                style = MaterialTheme.typography.bodyMedium

            )

            Text(

                text = "Ubicación: ${flota.FlotasEstado}, ${flota.FlotasCiudad}",

                style = MaterialTheme.typography.bodyMedium

            )

        }

    }

}