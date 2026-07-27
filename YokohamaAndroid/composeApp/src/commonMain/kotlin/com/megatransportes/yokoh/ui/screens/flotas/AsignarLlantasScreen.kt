package com.megatransportes.yokoh.ui.screens.flotas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.megatransportes.yokoh.ui.components.PlatformLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.Llanta
import com.megatransportes.yokoh.data.repository.YokohamaRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsignarLlantasScreen(
    repository: YokohamaRepository,
    flotaId: Int,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onLlantaAssigned: (llantaId: Int) -> Unit = {}
) {
    var llantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var showOnlyAssociated by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var processingIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(flotaId) {
        loading = true
        repository.getLlantasByFlota(flotaId)
            .onSuccess { list -> llantas = list; loading = false }
            .onFailure { e -> error = e.message; loading = false }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Asignar Llantas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Buscar llantas por marca, modelo o medida") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Checkbox(checked = showOnlyAssociated, onCheckedChange = { showOnlyAssociated = it })
                Text(text = "Mostrar solo asociadas a la flota")
            }

            when {
                loading -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                error != null -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = "Error: $error")
                    }
                }
                else -> {
                    val filtered = llantas.filter { l ->
                        val text = "${l.LlantasMarca} ${l.LlantasModelo} ${l.LlantasMedida}".lowercase()
                        val matches = text.contains(filter.lowercase())
                        val assoc = (l.asociada == 1)
                        (if (showOnlyAssociated) assoc else true) && matches
                    }

                    PlatformLazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
                        items(filtered, key = { it.idLlantas }) { llanta ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        // Keep card click as no-op to avoid accidental changes; actions handled by buttons
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Details text on the left
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "${llanta.LlantasMarca} ${llanta.LlantasModelo}", fontWeight = FontWeight.Bold)
                                        Text(text = llanta.LlantasMedida)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Action area on the right
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (processingIds.contains(llanta.idLlantas)) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        } else {
                                            if (llanta.asociada == 1) {
                                                Text(text = "✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(onClick = {
                                                    coroutineScope.launch {
                                                        processingIds = processingIds + llanta.idLlantas
                                                        val result = repository.disassociateLlantaFromFlotaByLlantaAndFlota(llanta.idLlantas, flotaId)
                                                        if (result.isSuccess) {
                                                            snackbarHostState.showSnackbar("Llanta desasociada")
                                                            repository.getLlantasByFlota(flotaId).onSuccess { llantas = it }
                                                        } else {
                                                            val msg = com.megatransportes.yokoh.utils.ErrorUtils.userMessage(result.exceptionOrNull(), "Error al desasociar")
                                                            snackbarHostState.showSnackbar("Error: $msg")
                                                        }
                                                        processingIds = processingIds - llanta.idLlantas
                                                    }
                                                }) { Text("Desasociar") }
                                            } else {
                                                Button(onClick = {
                                                    coroutineScope.launch {
                                                        processingIds = processingIds + llanta.idLlantas
                                                        val result = repository.associateLlantaToFlota(llanta.idLlantas, flotaId)
                                                        if (result.isSuccess) {
                                                            snackbarHostState.showSnackbar("Llanta asociada")
                                                            // Open the parameter creation/edit screen for the just-assigned llanta
                                                            try { onLlantaAssigned(llanta.idLlantas) } catch (_: Exception) {}
                                                            repository.getLlantasByFlota(flotaId).onSuccess { llantas = it }
                                                        } else {
                                                            val msg = com.megatransportes.yokoh.utils.ErrorUtils.userMessage(result.exceptionOrNull(), "Error al asociar")
                                                            snackbarHostState.showSnackbar("Error: $msg")
                                                        }
                                                        processingIds = processingIds - llanta.idLlantas
                                                    }
                                                }) { Text("Asignar") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
