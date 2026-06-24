package com.megatransportes.yokoh.ui.screens.vehiculos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megatransportes.yokoh.data.models.LlantaRendimiento
import com.megatransportes.yokoh.data.models.PruebaRendimiento
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.utils.formatDateOnly
import com.megatransportes.yokoh.utils.NumberFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlantaBitacoraScreen(
    repository: YokohamaRepository,
    llantaVehiculo: com.megatransportes.yokoh.data.models.LlantaVehiculo,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<LlantaRendimiento>>(emptyList()) }
    var pruebasMap by remember { mutableStateOf<Map<Int, PruebaRendimiento>>(emptyMap()) }
    var initialKm by remember { mutableStateOf(0) }
    var initialMm by remember { mutableStateOf(0f) }
    var initialPsi by remember { mutableStateOf(0f) }

    LaunchedEffect(llantaVehiculo.idLlantasVehiculos) {
        isLoading = true
        try {
            val res = repository.getLlantasRendimientoByLlantaVehiculoId(llantaVehiculo.idLlantasVehiculos)
            if (res.isSuccess) {
                val list = res.getOrNull() ?: emptyList()
                entries = list.sortedBy { it.idLlantasRendimiento }
                val pruebaIds = entries.mapNotNull { it.PruebaRendimiento_idPruebaRendimiento }.distinct()
                val map = mutableMapOf<Int, PruebaRendimiento>()
                pruebaIds.forEach { pid ->
                    repository.getPruebaRendimientoById(pid).getOrNull()?.let { map[pid] = it }
                }
                pruebasMap = map
                // Inicializar valores para la instalación inicial:
                // KM -> Vehiculos.VehiculosOdometro del vehículo asociado
                // MM -> LlantasVehiculosMM1 desde la entidad llantaVehiculo
                // PSI -> LlantasVehiculosPresion desde la entidad llantaVehiculo
                try {
                    val vehRes = repository.getVehiculoById(llantaVehiculo.Vehiculos_idVehiculos)
                    if (vehRes.isSuccess) {
                        val veh = vehRes.getOrNull()!!
                        initialKm = veh.VehiculosOdometro.toInt()
                    } else {
                        initialKm = 0
                    }
                } catch (_: Exception) {
                    initialKm = 0
                }
                initialMm = llantaVehiculo.LlantasVehiculosMM1
                initialPsi = llantaVehiculo.LlantasVehiculosPresion.toFloat()
                    // Debug: imprimir muestra de entradas y pruebas recibidas
                    try {
                        println("[LlantaBitacora] fetched entries=${entries.size} pruebaIds=${pruebaIds}")
                        val debugSamples = entries.take(5).map { entry ->
                            val pruebaOdo = pruebasMap[entry.PruebaRendimiento_idPruebaRendimiento]?.PruebaRendimientoOdometro ?: entry.PruebaRendimientoOdometro ?: 0f
                            val vehOdo = entry.VehiculosOdometro ?: initialKm.toFloat()
                            val kmServer = entry.kmRecorrido
                            val kmCalc = if (kmServer != null) {
                                println("[LlantaBitacora] using server km for id=${entry.idLlantasRendimiento}: $kmServer")
                                kmServer.toInt()
                            } else {
                                val c = ((pruebaOdo - vehOdo).coerceAtLeast(0f)).toInt()
                                println("[LlantaBitacora] computed local km for id=${entry.idLlantasRendimiento}: $c")
                                c
                            }
                            mapOf("id" to entry.idLlantasRendimiento, "pruebaOdo" to pruebaOdo, "vehOdo" to vehOdo, "kmServer" to kmServer, "kmCalc" to kmCalc)
                        }
                        println("[LlantaBitacora] computedSample: $debugSamples")
                    } catch (e: Exception) {
                        println("[LlantaBitacora] debug print error: ${e.message}")
                    }
            } else {
                errorMessage = "Error al cargar bitácora"
            }
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bitácora llanta: ${llantaVehiculo.LlantasVehiculosNoQuemado}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                errorMessage != null -> Text(errorMessage!!, color = Color.Red)
                else -> {
                    var boxRootPos by remember { mutableStateOf(Offset.Zero) }
                    var dotCenterRootX by remember { mutableStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .onGloballyPositioned { coords -> boxRootPos = coords.localToRoot(Offset.Zero) }
                    ) {
                        // global vertical line; localX is relative to Canvas size
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val lineColor = Color.Gray.copy(alpha = 0.4f)
                            val localX = (dotCenterRootX - boxRootPos.x).coerceIn(0f, size.width)
                            drawLine(
                                color = lineColor,
                                start = Offset(x = localX, y = 0f),
                                end = Offset(x = localX, y = size.height),
                                strokeWidth = 4f
                            )
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                TimelineItem(
                                        date = formatDateOnly(llantaVehiculo.LlantasVehiculosFechaInicio),
                                        subtitle = "Instalación inicial",
                                        km = initialKm,
                                        mm = initialMm,
                                        psi = initialPsi,
                                        isFirst = true,
                                        onDotMeasured = { x -> dotCenterRootX = x }
                                    )
                            }

                            // Filtrar entries para mostrar solo aquellas donde cambió odómetro o mm
                            // Filtrar: mostrar solo si cambió KM o MM
                            val filteredEntries = entries.filterIndexed { index, entry ->
                                val prueba = pruebasMap[entry.PruebaRendimiento_idPruebaRendimiento]
                                // KM = PruebaOdometro - VehiculoOdometro
                                val pruebaOdo = prueba?.PruebaRendimientoOdometro ?: entry.PruebaRendimientoOdometro ?: 0f
                                val vehOdo = entry.VehiculosOdometro ?: initialKm.toFloat()
                                val currentKm = entry.kmRecorrido?.toInt() ?: ((pruebaOdo - vehOdo).coerceAtLeast(0f)).toInt()
                                
                                val mmVals = listOf(
                                    entry.LlantasRendimientoMm1,
                                    entry.LlantasRendimientoMm2,
                                    entry.LlantasRendimientoMm3,
                                    entry.LlantasRendimientoMm4
                                ).filterNotNull()
                                val currentMm = mmVals.minOrNull() ?: 0f
                                
                                // Obtener los valores previos
                                val prevKm = if (index == 0) {
                                    initialKm
                                } else {
                                    val prevEntry = entries[index - 1]
                                    val prevPrueba = pruebasMap[prevEntry.PruebaRendimiento_idPruebaRendimiento]
                                        val prevPruebaOdo = prevPrueba?.PruebaRendimientoOdometro ?: prevEntry.PruebaRendimientoOdometro ?: 0f
                                        val prevVehOdo = prevEntry.VehiculosOdometro ?: initialKm.toFloat()
                                        prevEntry.kmRecorrido?.toInt() ?: ((prevPruebaOdo - prevVehOdo).coerceAtLeast(0f)).toInt()
                                }
                                
                                val prevMmVals = if (index == 0) {
                                    listOf(initialMm)
                                } else {
                                    val prevEntry = entries[index - 1]
                                    listOf(
                                        prevEntry.LlantasRendimientoMm1,
                                        prevEntry.LlantasRendimientoMm2,
                                        prevEntry.LlantasRendimientoMm3,
                                        prevEntry.LlantasRendimientoMm4
                                    ).filterNotNull()
                                }
                                val prevMm = prevMmVals.minOrNull() ?: initialMm
                                
                                // Mostrar si cambió km O si cambió mm
                                currentKm != prevKm || currentMm != prevMm
                            }

                            items(filteredEntries, key = { it.idLlantasRendimiento }) { entry ->
                                val prueba = pruebasMap[entry.PruebaRendimiento_idPruebaRendimiento]
                                val fecha = prueba?.PruebaRendimientoFecha?.take(10) ?: "-"
                                // KM: prefer server-provided `kmRecorrido`, otherwise compute locally
                                val pruebaOdo = prueba?.PruebaRendimientoOdometro ?: entry.PruebaRendimientoOdometro ?: 0f
                                val vehOdo = entry.VehiculosOdometro ?: initialKm.toFloat()
                                val km = entry.kmRecorrido?.toInt() ?: ((pruebaOdo - vehOdo).coerceAtLeast(0f)).toInt()
                                val psi = entry.LlantasRendimientoPresion.toFloat()
                                val mmVals = listOf(
                                    entry.LlantasRendimientoMm1,
                                    entry.LlantasRendimientoMm2,
                                    entry.LlantasRendimientoMm3,
                                    entry.LlantasRendimientoMm4
                                ).filterNotNull()
                                val minMm = mmVals.minOrNull() ?: 0f

                                TimelineItem(
                                    date = fecha,
                                    subtitle = "Actualizada",
                                    km = km,
                                    mm = minMm,
                                    psi = psi,
                                    isFirst = false,
                                    onDotMeasured = { x -> dotCenterRootX = x }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(
    date: String,
    subtitle: String,
    km: Int,
    mm: Float,
    psi: Float,
    isFirst: Boolean,
    onDotMeasured: ((Float) -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Date column on the far left
        Column(
            modifier = Modifier
                .width(72.dp)
                .padding(top = if (isFirst) 8.dp else 0.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = date, style = MaterialTheme.typography.bodySmall)
        }

        // Timeline column: dot
        Column(
            modifier = Modifier
                .width(40.dp)
                .padding(top = if (isFirst) 10.dp else 0.dp)
                .onGloballyPositioned { coords -> onDotMeasured?.invoke(coords.localToRoot(Offset.Zero).x + coords.size.width / 2f) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val dotColor = MaterialTheme.colorScheme.primary
            val maskColor = MaterialTheme.colorScheme.surface
            Canvas(modifier = Modifier.size(16.dp)) {
                val r = size.minDimension / 2f
                if (isFirst) {
                    // filled dot for created
                    drawCircle(color = dotColor, radius = r)
                } else {
                    // mask the global line underneath
                    drawCircle(color = maskColor, radius = r)
                    // draw the hollow outline
                    drawCircle(color = dotColor, radius = r, style = Stroke(width = 3f))
                }
            }
        }

        // Content card on the right (no date here)
        Column(
            modifier = Modifier
                .padding(start = 12.dp, bottom = 24.dp)
                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("KM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(NumberFormatter.formatWithComma(km.toDouble()))
                }
                Column {
                    Text("MM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(NumberFormatter.formatWithComma(mm, 1))
                }
                Column {
                    Text("PSI", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(NumberFormatter.formatWithComma(psi, 1))
                }
            }
        }
    }
}
