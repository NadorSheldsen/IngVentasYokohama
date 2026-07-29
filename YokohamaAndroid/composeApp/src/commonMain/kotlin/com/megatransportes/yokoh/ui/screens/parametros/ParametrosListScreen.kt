package com.megatransportes.yokoh.ui.screens.parametros



import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import com.megatransportes.yokoh.ui.components.PlatformLazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.Search

import androidx.compose.material.icons.filled.RadioButtonUnchecked

import androidx.compose.material.icons.filled.TireRepair

import androidx.compose.material.icons.filled.DirectionsCar

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.MenuBook

import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.filled.Warning

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.border

import androidx.compose.foundation.background

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.sp

import androidx.compose.ui.window.Dialog

import com.megatransportes.yokoh.data.models.Flota

import com.megatransportes.yokoh.data.models.Llanta

import com.megatransportes.yokoh.data.models.Parametro

import com.megatransportes.yokoh.data.repository.YokohamaRepository

import com.megatransportes.yokoh.ui.components.ErrorCard

import com.megatransportes.yokoh.ui.components.ErrorScreen

import kotlinx.coroutines.launch

import com.megatransportes.yokoh.utils.ErrorUtils



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun ParametrosListScreen(

    repository: YokohamaRepository,

    flota: Flota,

    onParametroClick: (Parametro) -> Unit,

    onAddParametroClick: (Int) -> Unit,

    onBack: () -> Unit,

    initialTab: Int = 0,

    onHome: () -> Unit = {},

    suggestedLlantaIds: List<Int> = emptyList()

) {

    var parametros by remember { mutableStateOf<List<Parametro>>(emptyList()) }

    var suggestedLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showSearchDialog by remember { mutableStateOf(false) }

    var parametroQuery by remember { mutableStateOf("") }

    

    val scope = rememberCoroutineScope()



    // Helper to load parámetros from current flota only

    val loadParametros: suspend () -> Unit = {

        isLoading = true

        errorMessage = null

        try {

            repository.getParametrosByFlotaId(flota.idFlotas).fold(

                onSuccess = { list -> 

                    parametros = list

                },

                onFailure = { error ->

                    errorMessage = ErrorUtils.userMessage(error, "Error al cargar parámetros")

                }

            )

        } catch (e: Throwable) {

            errorMessage = ErrorUtils.userMessage(e, "Error al cargar parámetros")

        } finally {

            isLoading = false

        }

    }



    LaunchedEffect(flota.idFlotas) {

        loadParametros()

    }

    

    // Cargar llantas sugeridas (sin duplicados)

    LaunchedEffect(suggestedLlantaIds) {

        if (suggestedLlantaIds.isNotEmpty()) {

            val loaded = mutableListOf<Llanta>()

            suggestedLlantaIds.distinct().forEach { llantaId ->

                repository.getLlantaWithDetails(llantaId).onSuccess { loaded.add(it) }

            }

            suggestedLlantas = loaded

        }

    }



    // selectedTab controlled by initialTab and available permissions

    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0..2)) } // 0: Parametros, 1: Llantas, 2: Tipos



    // Permissions: decide which UI pieces to show

    val currentUser by repository.currentUser.collectAsState()

    var canEditCatalogs by remember { mutableStateOf(false) }

    var canEditParametros by remember { mutableStateOf(false) }



    LaunchedEffect(currentUser) {

        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario

        if (perfilId != null) {

            try {

                val permisos = repository.getPermisosByPerfilId(perfilId).getOrNull().orEmpty()

                canEditCatalogs = permisos.any { it.PermisosNombre == "Editar Catálogos" }

                canEditParametros = permisos.any { it.PermisosNombre == "Editar Parámetros" }

            } catch (_: Throwable) {

                canEditCatalogs = false

                canEditParametros = false

            }

        } else {

            canEditCatalogs = false

            canEditParametros = false

        }

    }



    val childDialogState = remember { mutableStateOf(false) }

    val childAddRequest = remember { mutableStateOf(false) }



    // Ensure selectedTab is valid given permissions: if they can't edit parametros, default to Llantas if available

    LaunchedEffect(canEditCatalogs, canEditParametros, initialTab) {

        if (!canEditParametros) {

            // If user can't edit parametros, prefer Llantas (1) if catalogs editable, else stay at 0

            selectedTab = if (canEditCatalogs) 1 else 0

        } else {

            // If initialTab requests a non-available tab (e.g., 1 but can't edit catalogs), pick first available

            selectedTab = when {

                initialTab == 1 && !canEditCatalogs -> if (canEditParametros) 0 else 1

                else -> initialTab.coerceIn(0..2)

            }

        }

    }



    // If the user cannot edit catalogs, we hide the bottomBar completely.

    // Scaffold.bottomBar expects a non-null lambda, so we always pass one and conditionally render inside it.

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,

        topBar = {

            TopAppBar(

                title = { Text("Parámetros - ${flota.FlotasNombre}") },

                navigationIcon = {

                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }

                },

                actions = {

                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Flota") }

                }

            )

        },

        floatingActionButton = {

            // Show FAB only for tabs the user is allowed to edit

            when (selectedTab) {

                0 -> if (canEditParametros) FloatingActionButton(onClick = { showSearchDialog = true }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, contentDescription = "Agregar parámetro") }

                1 -> if (canEditCatalogs) FloatingActionButton(onClick = { childAddRequest.value = true }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, contentDescription = "Agregar llanta") }

                2 -> if (canEditCatalogs) FloatingActionButton(onClick = { childAddRequest.value = true }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, contentDescription = "Agregar tipo") }

            }

        },

        bottomBar = {

            // Bottom bar removed - navigation tabs no longer shown

        }

    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {

            when (selectedTab) {

                0 -> {

                    // Parámetros con búsqueda

                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                        OutlinedTextField(

                            value = parametroQuery,

                            onValueChange = { parametroQuery = it },

                            label = { Text("Buscar parámetros") },

                            placeholder = { Text("Buscar por medida, marca o modelo") },

                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },

                            singleLine = true,

                            modifier = Modifier.fillMaxWidth()

                        )



                        Spacer(modifier = Modifier.height(12.dp))



                        val displayed = if (parametroQuery.isBlank()) parametros else parametros.filter { p ->

                            val q = parametroQuery.trim().lowercase()

                            listOfNotNull(p.LlantasMedida, p.LlantasMarca, p.LlantasModelo, p.ParametrosRC).joinToString(" ").lowercase().contains(q)

                        }



                        when {

                            isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                            errorMessage != null -> ErrorCard(

                                errorMessage = errorMessage!!,

                                onRetry = { scope.launch { loadParametros() } },

                                modifier = Modifier.padding(16.dp)

                            )

                            displayed.isEmpty() && suggestedLlantas.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(text = "No hay parámetros registrados.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }

                            else -> PlatformLazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                // Mostrar llantas sugeridas primero

                                items(suggestedLlantas, key = { it.idLlantas }) { llanta ->

                                    SuggestedLlantaCard(

                                        llanta = llanta,

                                        onClick = { onAddParametroClick(llanta.idLlantas) }

                                    )

                                }

                                // Luego los parámetros existentes

                                items(displayed, key = { it.idParametros }) { parametro -> ParametroCard(parametro = parametro, onClick = { onParametroClick(parametro) }) }

                            }

                        }

                    }

                }

                1 -> {

                    LlantasAdminScreen(repository = repository, onBack = onBack, showDialogState = childDialogState, addRequestState = childAddRequest, flotaId = flota.idFlotas)

                }

                2 -> {

                    TipoVehiculosAdminScreen(repository = repository, onBack = onBack, showDialogState = childDialogState, addRequestState = childAddRequest)

                }

            }



            if (showSearchDialog && selectedTab == 0) {

                val excludedLlantaIds = parametros.mapNotNull { it.Llantas_idLlantas }

                LlantaSearchDialog(

                    repository = repository,

                    excludedLlantaIds = excludedLlantaIds,

                    suggestedLlantaIds = suggestedLlantaIds,

                    onLlantaSelected = { llanta -> showSearchDialog = false; onAddParametroClick(llanta.idLlantas) },

                    onDismiss = { showSearchDialog = false }

                )

            }

        }

    }

}



@Composable

private fun SuggestedLlantaCard(

    llanta: Llanta,

    onClick: () -> Unit

) {

    Card(

        modifier = Modifier

            .fillMaxWidth()

            .border(BorderStroke(2.dp, Color(0xFFFFA500)), shape = RoundedCornerShape(12.dp))

            .clickable(onClick = onClick),

        shape = RoundedCornerShape(12.dp),

        colors = CardDefaults.cardColors(

            containerColor = Color(0xFFFFF8E1) // Amarillo claro

        )

    ) {

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(16.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(

                modifier = Modifier.weight(1f)

            ) {

                Row(

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(

                        imageVector = Icons.Default.Warning,

                        contentDescription = null,

                        tint = Color(0xFFFFA500),

                        modifier = Modifier.size(24.dp)

                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(

                        text = "Sin parámetros",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Medium,

                        color = Color(0xFFFF6F00)

                    )

                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(

                    text = "Medida: ${llanta.LlantasMedida}",

                    fontSize = 20.sp,

                    fontWeight = FontWeight.Bold,

                    color = MaterialTheme.colorScheme.primary

                )

                if (!llanta.LlantasMarca.isNullOrBlank() || !llanta.LlantasModelo.isNullOrBlank()) {

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(

                        text = "${llanta.LlantasMarca} ${llanta.LlantasModelo}".trim(),

                        fontSize = 14.sp,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                }

            }

            Icon(

                imageVector = Icons.Default.Add,

                contentDescription = "Agregar parámetros",

                tint = Color(0xFFFFA500),

                modifier = Modifier.size(32.dp)

            )

        }

    }

}



@Composable

private fun ParametroCard(

    parametro: Parametro,

    onClick: () -> Unit

) {

    Card(

        modifier = Modifier

            .fillMaxWidth()

            .border(BorderStroke(2.dp, Color.Red), shape = RoundedCornerShape(12.dp))

            .clickable(onClick = onClick),

        shape = RoundedCornerShape(12.dp)

    ) {

        Column(

            modifier = Modifier

                .fillMaxWidth()

                .padding(16.dp)

        ) {

            // Medida de llanta principal

            Text(

                text = "Medida: ${parametro.LlantasMedida ?: "N/A"}",

                fontSize = 20.sp,

                fontWeight = FontWeight.Bold,

                color = MaterialTheme.colorScheme.primary

            )



            Spacer(modifier = Modifier.height(8.dp))



            // Marca y modelo

            /*Text(

                text = "${parametro.LlantasMarca ?: ""} ${parametro.LlantasModelo ?: ""}",

                fontSize = 16.sp,

                color = MaterialTheme.colorScheme.onSurfaceVariant

            )



            Spacer(modifier = Modifier.height(12.dp))



            HorizontalDivider()



            Spacer(modifier = Modifier.height(12.dp))*/



            // Información de presión

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Column {

                    Text(

                        text = "Presión Mín:",

                        fontSize = 12.sp,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Text(

                        text = "${parametro.ParametrosPMin} PSI",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.SemiBold

                    )

                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(

                        text = "Presión Sug:",

                        fontSize = 12.sp,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Text(

                        text = "${parametro.ParametrosPSug} PSI",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.SemiBold,

                        color = MaterialTheme.colorScheme.primary

                    )

                }

                Column(horizontalAlignment = Alignment.End) {

                    Text(

                        text = "Presión Máx:",

                        fontSize = 12.sp,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Text(

                        text = "${parametro.ParametrosPMax} PSI",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.SemiBold

                    )

                }

            }



            Spacer(modifier = Modifier.height(8.dp))



            // Información de profundidad

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Column {

                    Text(

                        text = "Prof. Mín:",

                        fontSize = 12.sp,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Text(

                        text = "${parametro.ParametrosProfMin} mm",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.SemiBold

                    )

                }

                Column(horizontalAlignment = Alignment.End) {

                    Text(

                        text = "Prof. Máx:",

                        fontSize = 12.sp,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Text(

                        text = "${parametro.ParametrosProfMax} mm",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.SemiBold

                    )

                }

            }



            Spacer(modifier = Modifier.height(8.dp))



            // RC 

            Row(

                verticalAlignment = Alignment.CenterVertically

            ) {

                Text(

                    text = "RC: ",

                    fontSize = 12.sp,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

                Text(

                    text = parametro.ParametrosRC,

                    fontSize = 14.sp,

                    fontWeight = FontWeight.SemiBold,

                    color = if (parametro.ParametrosRC == "S") 

                        MaterialTheme.colorScheme.primary 

                    else 

                        MaterialTheme.colorScheme.error

                )

            }

        }

    }

}



@Composable

private fun LlantaSearchDialog(

    repository: YokohamaRepository,

    excludedLlantaIds: List<Int> = emptyList(),

    suggestedLlantaIds: List<Int> = emptyList(),

    onLlantaSelected: (Llanta) -> Unit,

    onDismiss: () -> Unit

) {

    var searchQuery by remember { mutableStateOf("") }

    var searchResults by remember { mutableStateOf<List<Llanta>>(emptyList()) }

    var isSearching by remember { mutableStateOf(false) }

    

    val scope = rememberCoroutineScope()

    

    // Load suggested llantas on init

    LaunchedEffect(suggestedLlantaIds) {

        if (suggestedLlantaIds.isNotEmpty()) {

            isSearching = true

            val loaded = mutableListOf<Llanta>()

            suggestedLlantaIds.forEach { llantaId ->

                repository.getLlantaWithDetails(llantaId).onSuccess { loaded.add(it) }

            }

            searchResults = loaded.filter { !excludedLlantaIds.contains(it.idLlantas) }

            isSearching = false

        }

    }



    Dialog(onDismissRequest = onDismiss) {

        Card(

            modifier = Modifier

                .fillMaxWidth()

                .height(500.dp),

            shape = RoundedCornerShape(16.dp)

        ) {

            Column(

                modifier = Modifier

                    .fillMaxSize()

                    .padding(16.dp)

            ) {

                // Header

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Text(

                        text = "Buscar Llanta",

                        fontSize = 20.sp,

                        fontWeight = FontWeight.Bold

                    )

                    IconButton(onClick = onDismiss) {

                        Icon(Icons.Default.Close, contentDescription = "Cerrar")

                    }

                }



                Spacer(modifier = Modifier.height(16.dp))



                // Search field

                OutlinedTextField(

                    value = searchQuery,

                    onValueChange = { query ->

                        searchQuery = query

                        if (query.isNotEmpty()) {

                            scope.launch {

                                isSearching = true

                                repository.searchLlantasByMedida(query).fold(

                                    onSuccess = { results ->

                                        // Excluir llantas que ya tienen un parámetro en esta flota

                                        searchResults = results.filter { llanta ->

                                            !excludedLlantaIds.contains(llanta.idLlantas)

                                        }

                                        isSearching = false

                                    },

                                    onFailure = {

                                        searchResults = emptyList()

                                        isSearching = false

                                    }

                                )

                            }

                        } else {

                            searchResults = emptyList()

                        }

                    },

                    label = { Text("Buscar por medida") },

                    placeholder = { Text("Ej: 295, 11R24.5") },

                    leadingIcon = {

                        Icon(Icons.Default.Search, contentDescription = null)

                    },

                    modifier = Modifier.fillMaxWidth(),

                    singleLine = true

                )



                Spacer(modifier = Modifier.height(16.dp))



                // Results

                if (isSearching) {

                    Box(

                        modifier = Modifier.weight(1f).fillMaxWidth(),

                        contentAlignment = Alignment.Center

                    ) {

                        CircularProgressIndicator()

                    }

                } else if (searchQuery.isEmpty()) {

                    Box(

                        modifier = Modifier.weight(1f).fillMaxWidth(),

                        contentAlignment = Alignment.Center

                    ) {

                        Text(

                            text = "Escribe una medida para buscar",

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }

                } else if (searchResults.isEmpty()) {

                    Box(

                        modifier = Modifier.weight(1f).fillMaxWidth(),

                        contentAlignment = Alignment.Center

                    ) {

                        Text(

                            text = "No se encontraron llantas",

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }

                } else {

                    PlatformLazyColumn(

                        modifier = Modifier.weight(1f).fillMaxWidth(),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        items(searchResults, key = { it.idLlantas }) { llanta ->

                            Card(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .border(BorderStroke(2.dp, Color.Red), shape = RoundedCornerShape(8.dp))

                                    .clickable { onLlantaSelected(llanta) },

                                shape = RoundedCornerShape(8.dp)

                            ) {

                                Column(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(16.dp)

                                ) {

                                    Text(

                                        text = llanta.LlantasMedida,

                                        fontSize = 18.sp,

                                        fontWeight = FontWeight.Medium

                                    )

                                }

                            }

                        }

                    }

                }

            }

        }

    }

}

