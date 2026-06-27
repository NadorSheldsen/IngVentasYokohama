package com.megatransportes.yokoh.ui.screens.flotas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.ui.components.Logo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlotaMenuScreen(
    repository: YokohamaRepository,
    flota: Flota,
    onPruebasRendimientoClick: () -> Unit,
    onSemaforosClick: () -> Unit,
    onInspeccionesClick: () -> Unit,
    onPilasDesechoClick: () -> Unit,
    onParametrosClick: () -> Unit,
    onParametrosGoToLlantas: () -> Unit,
    onAsignarLlantasClick: () -> Unit,
    onBack: () -> Unit
) {
    // Determine permissions to decide whether to show the Parámetros button and where it navigates.
    var showParametrosButton by remember { mutableStateOf(true) }
    var canEditCatalogs by remember { mutableStateOf(false) }
    var canEditParametros by remember { mutableStateOf(false) }
    var canAsignarLlantas by remember { mutableStateOf(false) }
    val currentUser by repository.currentUser.collectAsState()
    LaunchedEffect(currentUser) {
        val perfilId = currentUser?.PerfilesUsuario_idPerfilesUsuario
        if (perfilId == null) {
            showParametrosButton = false
            canEditCatalogs = false
            canEditParametros = false
        } else {
            try {
                val permisos = repository.getPermisosByPerfilId(perfilId).getOrNull().orEmpty()
                canEditCatalogs = permisos.any { it.PermisosNombre == "Editar Catálogos" }
                canEditParametros = permisos.any { it.PermisosNombre == "Editar Parámetros" }
                    canAsignarLlantas = permisos.any { it.PermisosNombre == "Asignar Llantas" }
                showParametrosButton = canEditCatalogs || canEditParametros
            } catch (_: Throwable) {
                showParametrosButton = false
                canEditCatalogs = false
                canEditParametros = false
                    canAsignarLlantas = false
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Logo(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth(0.3f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Encabezado: ahora con menú desplegable que contiene acciones de Parámetros y Agregar
            var headerMenuExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { headerMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = flota.FlotasNombre.uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Giro: ${flota.FlotasClasificacion}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Zona: ${flota.FlotasZona}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                DropdownMenu(
                    expanded = headerMenuExpanded,
                    onDismissRequest = { headerMenuExpanded = false },
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    if (showParametrosButton) {
                        DropdownMenuItem(
                            text = { Text(text = "Parámetros") },
                            onClick = {
                                headerMenuExpanded = false
                                when {
                                    canEditCatalogs && !canEditParametros -> onParametrosGoToLlantas()
                                    else -> onParametrosClick()
                                }
                            }
                        )
                    }

                    if (canAsignarLlantas) {
                        DropdownMenuItem(
                            text = { Text(text = "Agregar") },
                            onClick = {
                                headerMenuExpanded = false
                                onAsignarLlantasClick()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botones de menú
            MenuButton(
                text = "Pruebas de Rendimientos Controladas",
                onClick = onPruebasRendimientoClick
            )

            MenuButton(
                text = "Semáforos",
                onClick = onSemaforosClick
            )

            // Invertir el orden: mostrar 'Pilas de Desecho' antes que 'Inspecciones Vehiculares'
            MenuButton(
                text = "Pilas de Desecho",
                onClick = onPilasDesechoClick
            )

            MenuButton(
                text = "Inspecciones Vehiculares",
                onClick = onInspeccionesClick
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Red),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(vertical = 6.dp),
        // keep default elevation behavior for OutlinedButton
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: centered text (no icon)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            // Right chevron '>'
            Text(
                text = ">",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
