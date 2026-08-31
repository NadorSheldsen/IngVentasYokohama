package com.megatransportes.yokoh.ui.screens.desecho

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.megatransportes.yokoh.ui.components.PlatformLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.megatransportes.yokoh.utils.getPlatformContext
import com.megatransportes.yokoh.utils.FileSaveUtils
import com.megatransportes.yokoh.utils.OpenFileUtils
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.utils.NumberFormatter
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import com.megatransportes.yokoh.ui.components.TireCorteImage

private fun truncate(s: String?, n: Int): String {
    if (s == null) return ""
    return if (s.length <= n) s else s.substring(0, n - 1) + "…"
}

@Composable
private fun TireImageWithLabels(llantas: List<LlantasDesecho> = emptyList(), selectedUbi: String? = null) {
    // Calculate percentages for each location
    val locationCounts = llantas.groupingBy { it.LlantasDesechoUbi ?: "Sin ubicación" }.eachCount()
    val total = llantas.size.coerceAtLeast(1)
    
    val locations = mapOf(
        "Banda de rodamiento" to (locationCounts["Banda de rodamiento"] ?: 0),
        "Costado" to (locationCounts["Costado"] ?: 0),
        "Hombro" to (locationCounts["Hombro"] ?: 0),
        "Caja / Pestaña" to (locationCounts["Caja / Pestaña"] ?: 0),
        "Liner" to (locationCounts["Liner"] ?: 0)
    )
    
    fun getPercentage(count: Int): String = if (total > 0) "${(count * 100) / total}%" else "0%"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top label: Banda de rodamiento
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Banda de rodamiento",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selectedUbi == "Banda de rodamiento") FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedUbi == "Banda de rodamiento") Color.Red else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = getPercentage(locations["Banda de rodamiento"] ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Tire image with side labels
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Left label: Costado
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = "Costado",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (selectedUbi == "Costado") FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedUbi == "Costado") Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = getPercentage(locations["Costado"] ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Center image
            TireCorteImage(
                modifier = Modifier.size(180.dp)
            )

            // Right label: Hombro
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 8.dp)
            ) {
                Text(
                    text = "Hombro",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (selectedUbi == "Hombro") FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedUbi == "Hombro") Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = getPercentage(locations["Hombro"] ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom-right label: Caja / Pestaña
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 8.dp)
            ) {
                Text(
                    text = "Caja / Pestaña",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (selectedUbi == "Caja / Pestaña") FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedUbi == "Caja / Pestaña") Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = getPercentage(locations["Caja / Pestaña"] ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bottom label: Liner
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Liner",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selectedUbi == "Liner") FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedUbi == "Liner") Color.Red else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = getPercentage(locations["Liner"] ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruebaDesechoReportScreen(
    repository: YokohamaRepository,
    flota: Flota,
    prueba: PruebasDesecho,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var llantas by remember { mutableStateOf<List<LlantasDesecho>>(emptyList()) }
    var catalog by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val platformContext = getPlatformContext()

    LaunchedEffect(prueba.idPruebasDesecho) {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            repository.getLlantasDesechoByPruebaId(prueba.idPruebasDesecho).onSuccess { llantas = it }
            repository.getLlantasByFlota(flota.idFlotas).onSuccess { catalog = it }
            isLoading = false
        }
    }

    // helper maps
    val brandMap = catalog.associateBy({ it.idLlantas }) { it.LlantasMarca }
    val catalogMap = catalog.associateBy { it.idLlantas }

    // Aggregations
    val causaCounts = llantas
        .map { it.LlantasDesechoCausaDes ?: "Sin causa" }
        .groupingBy { it }
        .eachCount()
    val marcaCounts = llantas.map { brandMap[it.Llantas_idLlantas] ?: "Otras" }.groupingBy { it }.eachCount()
    val remanentes = llantas.mapNotNull { it.LlantasDesechoRemanente }
    val dateCounts = llantas.groupingBy { it.LlantasDesechoFecha?.split('T',' ')?.firstOrNull() ?: "Sin fecha" }.eachCount()

    // Pie dialog state (used for pies like Tipo de piso)
    var showPieDialog by remember { mutableStateOf(false) }
    var pieTitle by remember { mutableStateOf("") }
    var pieData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var pieColors by remember { mutableStateOf<List<Color>>(listOf(Color(0xFF1976D2), Color(0xFF6D4C41), Color(0xFFBDBDBD))) }
    // Details toggle state
    var showDetails by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reporte - ${prueba.PruebasDesechoNombre}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Home") }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                val jsonLib = Json { prettyPrint = false; isLenient = true; ignoreUnknownKeys = true }

                                fun toJsonElem(value: Any?): kotlinx.serialization.json.JsonElement {
                                    return when (value) {
                                        null -> JsonNull
                                        is Number -> JsonPrimitive(value)
                                        is Boolean -> JsonPrimitive(value)
                                        is String -> JsonPrimitive(value)
                                        is Map<*, *> -> {
                                            val map = value.entries.associate { (k, v) -> (k.toString()) to toJsonElem(v) }
                                            kotlinx.serialization.json.JsonObject(map)
                                        }
                                        is List<*> -> kotlinx.serialization.json.JsonArray(value.map { toJsonElem(it) })
                                        else -> JsonPrimitive(value.toString())
                                    }
                                }

                                // Build payload similar shape to other report screens
                                val pruebaMap = mapOf(
                                    "idPruebasDesecho" to prueba.idPruebasDesecho,
                                    "PruebasDesechoNombre" to prueba.PruebasDesechoNombre
                                )

                                val flotaMap = mapOf(
                                    "idFlotas" to flota.idFlotas,
                                    "FlotasNombre" to flota.FlotasNombre
                                )

                                val llantasList = llantas.map { ll ->
                                    mapOf(
                                        "idLlantasDesecho" to ll.idLlantasDesecho,
                                        "Llantas_idLlantas" to ll.Llantas_idLlantas,
                                        "LlantasDesechoRemanente" to ll.LlantasDesechoRemanente,
                                        "LlantasDesechoCausaDes" to ll.LlantasDesechoCausaDes,
                                        "LlantasDesechoPiso" to ll.LlantasDesechoPiso,
                                        "LlantasDesechoFecha" to ll.LlantasDesechoFecha,
                                        "LlantasDesechoComentarios" to ll.LlantasDesechoComentarios,
                                        "LlantasDesechoNoLlanta" to ll.LlantasDesechoNoLlanta,
                                        "LlantasDesechoFoto1" to ll.LlantasDesechoFoto1,
                                        "LlantasDesechoFoto2" to ll.LlantasDesechoFoto2
                                    )
                                }

                                val llantaCatalogList = catalog.map { c -> mapOf("idLlantas" to c.idLlantas, "LlantasMarca" to c.LlantasMarca) }

                                val payloadObj = kotlinx.serialization.json.JsonObject(mapOf(
                                    "prueba" to toJsonElem(pruebaMap),
                                    "flota" to toJsonElem(flotaMap),
                                    "llantas" to toJsonElem(llantasList),
                                    "llantaCatalog" to toJsonElem(llantaCatalogList)
                                ))

                                val payloadStr = jsonLib.encodeToString(payloadObj)

                                repository.downloadDesechoReportPdf(payloadStr).onSuccess { bytes ->
                                    try {
                                        // basic PDF sanity check
                                        val isPdf = bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
                                        if (!isPdf) {
                                            snackbarHostState.showSnackbar("El archivo descargado no parece un PDF")
                                            return@onSuccess
                                        }
                                        val fname = "prueba_desecho_${prueba.idPruebasDesecho}_report.pdf"
                                        val saved = FileSaveUtils.saveBytesToCache(fname, bytes, platformContext)
                                        snackbarHostState.showSnackbar("PDF guardado: $saved")
                                        try {
                                            OpenFileUtils.shareFile(saved, "application/pdf", platformContext = platformContext)
                                        } catch (openEx: Exception) {
                                            try {
                                                OpenFileUtils.openFile(saved, platformContext = platformContext)
                                            } catch (openEx2: Exception) {
                                                snackbarHostState.showSnackbar(ErrorUtils.userMessage(openEx2, "PDF guardado pero no se pudo abrir automáticamente"))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(ErrorUtils.userMessage(e, "Error guardando PDF"))
                                    }
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error descargando PDF"))
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(ErrorUtils.userMessage(e, "Error preparando payload"))
                            }
                        }
                    }) { Icon(Icons.Default.Share, contentDescription = "Exportar PDF") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Scaffold
            }

            PlatformLazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            TireImageWithLabels(llantas = llantas, selectedUbi = llantas.firstOrNull()?.LlantasDesechoUbi)
                        }
                    }
                }

                item {
                    Text("Causa de desecho", style = MaterialTheme.typography.titleMedium)
                    PieChartWithLegend(causaCounts, "Causa de desecho") { display, colors, title ->
                        pieTitle = title
                        pieData = display
                        pieColors = colors
                        showPieDialog = true
                    }
                }

                item {
                    Text("Remanente (mm)", style = MaterialTheme.typography.titleMedium)
                    BarChartRemanente(remanentes)
                }

                item {
                    Text("Marca", style = MaterialTheme.typography.titleMedium)
                    PieChartWithLegend(marcaCounts, "Marca") { display, colors, title ->
                        pieTitle = title
                        pieData = display
                        pieColors = colors
                        showPieDialog = true
                    }
                }

                item {
                    Text("Tipo de piso", style = MaterialTheme.typography.titleMedium)
                    // Match the Semaforo/Inspeccion style: segmented bar + clickable pie dialog
                    val originalCount = llantas.count { it.LlantasDesechoPiso == "Original" }
                    val vitalizadoCount = llantas.count { it.LlantasDesechoPiso == "Vitalizado" }
                    val otherPisoCount = (llantas.size - originalCount - vitalizadoCount).coerceAtLeast(0)

                    Column(modifier = Modifier.fillMaxWidth().clickable {
                        pieTitle = "Tipo de piso"
                        pieData = listOf("Original" to originalCount, "Vitalizado" to vitalizadoCount, "Otros" to otherPisoCount)
                        val pisoColors = listOf(Color(0xFF1976D2), Color(0xFF6D4C41), Color(0xFFBDBDBD))
                        pieColors = pisoColors.take(pieData.size)
                        showPieDialog = true
                    }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                            if (originalCount > 0) Box(modifier = Modifier.weight(originalCount.toFloat()).fillMaxHeight().background(Color(0xFF1976D2)))
                            if (vitalizadoCount > 0) Box(modifier = Modifier.weight(vitalizadoCount.toFloat()).fillMaxHeight().background(Color(0xFF6D4C41)))
                            if (otherPisoCount > 0) Box(modifier = Modifier.weight(otherPisoCount.toFloat()).fillMaxHeight().background(Color(0xFFBDBDBD)))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (otherPisoCount > 0) Text("Otros: ${otherPisoCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item {
                    Text("Cantidad de llantas por fecha", style = MaterialTheme.typography.titleMedium)
                    LineChartDates(dateCounts)
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ElevatedButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Detalle de llantas" else "Detalle de llantas")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showDetails) "Plegar" else "Desplegar"
                            )
                        }
                    }
                }

                // Conditionally show the detailed list
                if (showDetails) {
                    items(llantas, key = { it.idLlantasDesecho }) { ll ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Llanta (marca + modelo) y número de llanta si aplica
                                    val llCatalog = catalogMap[ll.Llantas_idLlantas]
                                    val marca = llCatalog?.LlantasMarca ?: brandMap[ll.Llantas_idLlantas] ?: "-"
                                    val modelo = llCatalog?.LlantasModelo ?: ""
                                    Text("Llanta: ${marca} ${modelo} ${ll.LlantasDesechoNoLlanta ?: ""}", style = MaterialTheme.typography.bodyLarge)

                                    // Medida y costo desecho
                                    val medida = llCatalog?.LlantasMedida ?: "-"
                                    val precio = llCatalog?.LlantasPrecio?.let { NumberFormatter.formatWithComma(it, 2) } ?: "-"
                                    Text("Medida: ${medida} — Costo desecho $${precio}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    // Causa / Piso / Remanente
                                    Text(truncate(ll.LlantasDesechoCausaDes, 30), style = MaterialTheme.typography.bodySmall)
                                    Text("Piso: ${truncate(ll.LlantasDesechoPiso,12)} — Remanente: ${ll.LlantasDesechoRemanente?.toString() ?: "-"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    // Fotos (si existen)
                                    val fotos = listOfNotNull(
                                        ll.LlantasDesechoFoto1?.takeIf { it.isNotBlank() },
                                        ll.LlantasDesechoFoto2?.takeIf { it.isNotBlank() }
                                    )
                                    if (fotos.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            for (f in fotos) {
                                                val bytes = runCatching { FileConverter.safeStringToByteArray(f) }.getOrNull()
                                                val bmp = bytes?.let { runCatching { byteArrayToImageBitmap(it) }.getOrNull() }
                                                if (bmp != null) {
                                                    Image(bitmap = bmp, contentDescription = "Foto llanta", modifier = Modifier.size(64.dp))
                                                } else {
                                                    Text("Foto: Sí", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Remanente a la derecha
                                Text(ll.LlantasDesechoRemanente?.toString() ?: "-", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            // Pie chart dialog (reused for Tipo de piso and other pies)
            if (showPieDialog) {
                Dialog(onDismissRequest = { showPieDialog = false }) {
                    Card(modifier = Modifier.fillMaxWidth(0.9f).padding(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(pieTitle, style = MaterialTheme.typography.titleLarge)
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(180.dp)) {
                                    val total = pieData.sumOf { it.second }.coerceAtLeast(1)
                                    var start = -90f
                                    val colors = pieColors
                                    for ((i, pair) in pieData.withIndex()) {
                                        val sweep = pair.second.toFloat() / total.toFloat() * 360f
                                        drawArc(color = colors[i % colors.size], startAngle = start, sweepAngle = sweep, useCenter = true)
                                        start += sweep
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val total = pieData.sumOf { it.second }.coerceAtLeast(1)
                                for ((index, pair) in pieData.withIndex()) {
                                    val (label, cnt) = pair
                                    val pct = (cnt * 100) / total
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(pieColors[index % pieColors.size])
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$label (${pct}%)",
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Button(onClick = { showPieDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PieChartWithLegend(
    counts: Map<String, Int>,
    title: String,
    onOpenDialog: ((display: List<Pair<String, Int>>, colors: List<Color>, title: String) -> Unit)? = null
) {
    if (counts.isEmpty()) { Text("Sin datos"); return }
    val total = counts.values.sum().coerceAtLeast(1)
    val colors = listOf(Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF2196F3), Color(0xFFF44336), Color(0xFF9C27B0), Color.Gray)

    // prepare top 3 + Others
    val sorted = counts.entries.sortedByDescending { it.value }
    val topN = 3
    val top = sorted.take(topN)
    val othersCount = sorted.drop(topN).sumOf { it.value }
    val display = top.map { it.key to it.value }.toMutableList()
    if (othersCount > 0) display.add("Otros" to othersCount)

    val rowModifier = if (onOpenDialog != null) Modifier.fillMaxWidth().clickable {
        onOpenDialog.invoke(display, colors.take(display.size), title)
    } else Modifier.fillMaxWidth()

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((i, pair) in display.withIndex()) {
                val (label, cnt) = pair
                val pct = (cnt * 100) / total
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(colors[i % colors.size]))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$label (${pct}%)",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(modifier = Modifier.size(100.dp).padding(start = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(100.dp)) {
                var start = -90f
                for ((i, pair) in display.withIndex()) {
                    val sweep = pair.second.toFloat() / total.toFloat() * 360f
                    drawArc(color = colors[i % colors.size], startAngle = start, sweepAngle = sweep, useCenter = true)
                    start += sweep
                }
            }
        }
    }
}

@Composable
private fun BarChartRemanente(values: List<Float>) {
    if (values.isEmpty()) { Text("Sin datos"); return }
    // Group by rounded mm value and count occurrences
    val counts = values.map { it.roundToInt() }.groupingBy { it }.eachCount().entries.sortedBy { it.key }
    if (counts.isEmpty()) { Text("Sin datos"); return }
    val maxCount = counts.maxOf { it.value }

    Row(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val barColors = listOf(Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688), Color(0xFFFF9800), Color(0xFFD32F2F), Color(0xFF9C27B0), Color.Gray)
        val maxBarDp = 60.dp
        counts.forEachIndexed { index, entry ->
            val mm: Int = entry.key
            val cnt: Int = entry.value
            val proportion: Float = if (maxCount > 0) cnt.toFloat() / maxCount.toFloat() else 0f
            val barHeightDp = (proportion * maxBarDp.value).dp
            val labelOffset = -barHeightDp - 6.dp
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // Place the count just above the bar by offsetting upwards by the bar height
                Box(modifier = Modifier.height(maxBarDp + 20.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeightDp)
                        .background(barColors[index % barColors.size]))
                    Text(
                        text = cnt.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.offset(y = labelOffset)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "$mm mm", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LineChartSimple(counts: Map<String, Int>) {
    if (counts.isEmpty()) { Text("Sin datos"); return }
    val entries = counts.entries.toList()
    val max = entries.maxOf { it.value }
    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val w = size.width
        val h = size.height
        val stepX = w / (entries.size.coerceAtLeast(1))
        entries.forEachIndexed { i, e ->
            val x = i * stepX + stepX / 2
            val y = h - (e.value.toFloat() / max) * h
            drawCircle(Color(0xFF1976D2), radius = 3f, center = Offset(x, y))
            if (i > 0) {
                val prev = entries[i - 1]
                val px = (i - 1) * stepX + stepX / 2
                val py = h - (prev.value.toFloat() / max) * h
                drawLine(Color(0xFF1976D2), Offset(px, py), Offset(x, y), strokeWidth = 2f)
            }
        }
    }
}

@Composable
private fun monthLabel(ym: String): String {
    val parts = ym.split('-')
    if (parts.size >= 2) {
        val m = parts[1].toIntOrNull() ?: return ym
        val months = listOf("Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic")
        return months.getOrNull((m - 1).coerceIn(0, 11)) ?: parts[1]
    }
    return ym
}

@Composable
private fun LineChartDates(counts: Map<String, Int>) {
    if (counts.isEmpty()) { Text("Sin datos"); return }
    // Aggregate by YYYY-MM
    val monthly = mutableMapOf<String, Int>()
    for ((k, v) in counts) {
        val parts = k.split('T', ' ', '-')
        val ym = if (parts.size >= 2) "${parts[0]}-${parts[1].padStart(2, '0')}" else k
        monthly[ym] = (monthly[ym] ?: 0) + v
    }
    val entries = monthly.entries.sortedBy { it.key }
    if (entries.isEmpty()) { Text("Sin datos"); return }
    val maxVal = entries.maxOf { it.value }
    val chartHeight = 120.dp

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val w = size.width
            val h = size.height
            val leftPad = 12f
            val rightPad = 12f
            val topPad = 10f
            val bottomPad = 16f
            val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
            val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

            // Base line
            drawLine(
                color = Color(0x33212121),
                start = Offset(leftPad, topPad + plotH),
                end = Offset(leftPad + plotW, topPad + plotH),
                strokeWidth = 1.5f
            )

            val stepX = if (entries.size <= 1) 0f else plotW / (entries.size - 1)
            var prev: Offset? = null
            entries.forEachIndexed { i, e ->
                val x = if (entries.size <= 1) leftPad + plotW / 2f else leftPad + i * stepX
                val proportion = if (maxVal > 0) e.value.toFloat() / maxVal.toFloat() else 0f
                val y = topPad + (1f - proportion) * plotH
                val pt = Offset(x, y)
                if (prev != null) {
                    drawLine(Color(0xFF1976D2), prev!!, pt, strokeWidth = 2.5f)
                }
                drawCircle(Color(0xFF1976D2), radius = 4f, center = pt)
                prev = pt
            }
        }

        // Value labels by point (separate row to avoid overlap with chart canvas)
        Row(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { e ->
                Text(
                    text = e.value.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // X axis labels (month abbreviations)
        Row(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { e ->
                Text(
                    text = monthLabel(e.key),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
