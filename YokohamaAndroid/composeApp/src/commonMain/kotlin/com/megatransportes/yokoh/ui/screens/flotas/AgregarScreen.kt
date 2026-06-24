package com.megatransportes.yokoh.ui.screens.flotas

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.ui.screens.parametros.LlantasAdminScreen
import com.megatransportes.yokoh.ui.screens.parametros.TipoVehiculosAdminScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgregarScreen(
    repository: YokohamaRepository,
    flota: Flota,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Llantas", "Tipo de Vehículos")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    LlantasAdminScreen(
                        repository = repository,
                        onBack = onBack
                    )
                }
                1 -> {
                    TipoVehiculosAdminScreen(
                        repository = repository,
                        onBack = onBack
                    )
                }
            }
        }
    }
}
