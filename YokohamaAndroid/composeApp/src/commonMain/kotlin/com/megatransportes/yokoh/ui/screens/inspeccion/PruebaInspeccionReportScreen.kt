package com.megatransportes.yokoh.ui.screens.inspecciones

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import com.megatransportes.yokoh.utils.getPlatformContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.utils.FileSaveUtils
import com.megatransportes.yokoh.utils.OpenFileUtils
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import com.megatransportes.yokoh.utils.NumberFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruebaInspeccionReportScreen(
    repository: YokohamaRepository,
    flota: Flota,
    prueba: PruebaInspeccion,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vehiculos by remember { mutableStateOf<List<VehiculoInspeccion>>(emptyList()) }
    var llantasPorVehiculo by remember { mutableStateOf<Map<Int, List<LlantaInspeccion>>>(emptyMap()) }
    var llantaCatalog by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var parametros by remember { mutableStateOf<List<Parametro>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val platformContext = getPlatformContext()

    LaunchedEffect(prueba.idPruebaInspeccion) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                repository.getVehiculosInspeccionByPruebaId(prueba.idPruebaInspeccion)
                    .onSuccess { list ->
                        vehiculos = list
                        val deferred = list.map { v ->
                            coroutineScope.async {
                                repository.getLlantasInspeccionByVehiculoId(v.idVehiculoInspeccion).getOrThrow()
                            }
                        }
                        val results = deferred.awaitAll()
                        val map = list.mapIndexed { idx, v -> v.idVehiculoInspeccion to results[idx] }.toMap()
                        llantasPorVehiculo = map
                        // fetch llanta catalog for brand names
                        repository.getLlantasByFlota(flota.idFlotas)
                            .onSuccess { catalog -> llantaCatalog = catalog }
                            .onFailure { /* ignore catalog errors */ }
                        // fetch parametros for pressure thresholds
                        repository.getParametrosByFlotaId(flota.idFlotas)
                            .onSuccess { params -> parametros = params }
                            .onFailure { /* ignore */ }
                        isLoading = false
                    }
                    .onFailure { err ->
                        errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos")
                        isLoading = false
                    }
            } catch (e: Exception) {
                errorMessage = ErrorUtils.userMessage(e, "Error inesperado")
                isLoading = false
            }
        }
    }

    fun VehiculoInspeccion.resolveCoords(): Pair<Double?, Double?> {
        val candidatesLat = listOfNotNull(this.latitude, this.VehiculosInspeccionLat, this.VehiculosLatitude)
        val candidatesLon = listOfNotNull(this.longitude, this.VehiculosInspeccionLon, this.VehiculosLongitude)
        fun firstValid(list: List<Double?>): Double? {
            for (v in list) if (v != null && kotlin.math.abs(v) > 1e-6) return v
            return null
        }
        return Pair(firstValid(candidatesLat), firstValid(candidatesLon))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reporte - ${prueba.PruebaInspeccionTitulo}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Home") }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                fun toJsonElem(value: Any?): kotlinx.serialization.json.JsonElement {
                                    return when (value) {
                                        null -> kotlinx.serialization.json.JsonNull
                                        is Number -> kotlinx.serialization.json.JsonPrimitive(value)
                                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                                        is String -> kotlinx.serialization.json.JsonPrimitive(value)
                                        is Map<*, *> -> kotlinx.serialization.json.JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElem(v) })
                                        is Iterable<*> -> kotlinx.serialization.json.JsonArray(value.map { toJsonElem(it) })
                                        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
                                    }
                                }

                                val pruebaMap = mapOf(
                                    "idPruebaInspeccion" to prueba.idPruebaInspeccion,
                                    "PruebaInspeccionTitulo" to prueba.PruebaInspeccionTitulo
                                )

                                val flotaMap = mapOf(
                                    "idFlotas" to flota.idFlotas,
                                    "FlotasNombre" to flota.FlotasNombre
                                )

                                val vehiculosList = vehiculos.map { v ->
                                    mapOf(
                                        "idVehiculoInspeccion" to v.idVehiculoInspeccion,
                                        "VehiculoInspeccionNo" to v.VehiculoInspeccionNo,
                                        "TipoVehiculos_idTipoVehiculos" to v.TipoVehiculos_idTipoVehiculos
                                    )
                                }

                                val llantasMapStringKeys = llantasPorVehiculo.mapKeys { it.key.toString() }.mapValues { entry ->
                                    entry.value.map { ll ->
                                        mapOf(
                                            "Llantas_idLlantas" to ll.Llantas_idLlantas,
                                            "LlantasInspeccionMm1" to ll.LlantasInspeccionMm1,
                                            "LlantasInspeccionMm2" to ll.LlantasInspeccionMm2,
                                            "LlantasInspeccionMm3" to ll.LlantasInspeccionMm3,
                                            "LlantasInspeccionMm4" to ll.LlantasInspeccionMm4,
                                            "LlantasInspeccionPresion" to ll.LlantasInspeccionPresion,
                                            "LlantasInspeccionVigia" to ll.LlantasInspeccionVigia,
                                            "LlantasInspeccionCondPel" to ll.LlantasInspeccionCondPel,
                                            "LlantasInspeccionObservacion" to ll.LlantasInspeccionObservacion,
                                            "LlantasInspeccionPiso" to ll.LlantasInspeccionPiso,
                                            "LlantasInspeccionComentario" to ll.LlantasInspeccionComentario,
                                            "LlantasInspeccionFoto" to ll.LlantasInspeccionFoto,
                                            "LlantasInspeccionFoto2" to ll.LlantasInspeccionFoto2
                                        )
                                    }
                                }

                                val payloadElem = JsonObject(mapOf(
                                    "prueba" to toJsonElem(pruebaMap),
                                    "flota" to toJsonElem(flotaMap),
                                    "vehiculos" to toJsonElem(vehiculosList),
                                    "llantasPorVehiculo" to toJsonElem(llantasMapStringKeys)
                                ))

                                val payloadStr = payloadElem.toString()

                                repository.downloadPruebaInspeccionReportPdf(payloadStr)
                                    .onSuccess { bytes ->
                                        try {
                                            val isPdf = bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
                                            if (!isPdf) {
                                                snackbarHostState.showSnackbar("El archivo descargado no parece un PDF")
                                                return@onSuccess
                                            }
                                            val fname = "prueba_inspeccion_${prueba.idPruebaInspeccion ?: "report"}.pdf"
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
                                    }
                                    .onFailure { err ->
                                        snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error generando PDF"))
                                    }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(ErrorUtils.userMessage(e, "Error preparando payload"))
                            }
                        }
                    }) { Icon(Icons.Default.Share, contentDescription = "Exportar PDF") }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                errorMessage != null -> Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                    Text(errorMessage ?: "Error")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            repository.getVehiculosInspeccionByPruebaId(prueba.idPruebaInspeccion)
                                .onSuccess { list ->
                                    vehiculos = list
                                    val deferred = list.map { v ->
                                        coroutineScope.async { repository.getLlantasInspeccionByVehiculoId(v.idVehiculoInspeccion).getOrThrow() }
                                    }
                                    val results = deferred.awaitAll()
                                    llantasPorVehiculo = list.mapIndexed { idx, v -> v.idVehiculoInspeccion to results[idx] }.toMap()
                                    isLoading = false
                                }
                                .onFailure { err ->
                                    errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos")
                                    isLoading = false
                                }
                        }
                    }) { Text("Reintentar") }
                }
                else -> {
                    val totalLlantas = llantasPorVehiculo.values.sumOf { it.size }
                    val flattened = llantasPorVehiculo.values.flatten()
                    val catalogMap = llantaCatalog.associateBy({ it.idLlantas }) { it.LlantasMarca }
                    val brandCounts = flattened.map { ll -> catalogMap[ll.Llantas_idLlantas] ?: "Otras" }
                        .groupingBy { it }.eachCount()

                    var showPieDialog by remember { mutableStateOf(false) }
                    var pieTitle by remember { mutableStateOf("") }
                    var pieData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
                    var pieColors by remember { mutableStateOf<List<Color>>(listOf(Color(0xFF2E7D32), Color(0xFFFFA000), Color(0xFFD32F2F))) }

                    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Header
                        Text(text = "Vehículos: ${vehiculos.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        // Brands pie + list
                        if (totalLlantas > 0) {
                            val sorted = brandCounts.entries.sortedByDescending { it.value }
                            val topN = 3
                            val top = sorted.take(topN)
                            val othersCount = sorted.drop(topN).sumOf { it.value }
                            val displayList: List<Pair<String, Int>> = top.map { (k, v) -> k to v }
                                .let { if (othersCount > 0) it + listOf("Otras" to othersCount) else it }

                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                pieTitle = "Marcas"
                                pieData = displayList
                                val brandColors = listOf(Color(0xFF2E7D32), Color(0xFFFFA000), Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF9C27B0), Color.Gray)
                                pieColors = displayList.mapIndexed { i, _ -> brandColors[i % brandColors.size] }
                                showPieDialog = true
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for ((brandName, count) in displayList) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            val pct = if (totalLlantas > 0) (count * 100) / totalLlantas else 0
                                            Text(text = brandName)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "${pct}%", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                val slices = displayList.map { it.second }
                                Box(modifier = Modifier.size(120.dp).padding(start = 12.dp).clickable {
                                    pieTitle = "Marcas"
                                    pieData = displayList
                                    val brandColors = listOf(Color(0xFF2E7D32), Color(0xFFFFA000), Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF9C27B0), Color.Gray)
                                    pieColors = displayList.mapIndexed { i, _ -> brandColors[i % brandColors.size] }
                                    showPieDialog = true
                                }, contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(120.dp)) {
                                        var startAngle = -90f
                                        val colors = listOf(Color(0xFF2E7D32), Color(0xFFFFA000), Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF9C27B0), Color.Gray)
                                        for ((i, cnt) in slices.withIndex()) {
                                            val sweep = cnt.toFloat() / totalLlantas.toFloat() * 360f
                                            drawArc(color = colors[i % colors.size], startAngle = startAngle, sweepAngle = sweep, useCenter = true)
                                            startAngle += sweep
                                        }
                                    }
                                }
                            }

                            if (showPieDialog) {
                                Dialog(onDismissRequest = { showPieDialog = false }) {
                                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(text = pieTitle, style = MaterialTheme.typography.titleMedium)
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Canvas(modifier = Modifier.size(220.dp)) {
                                                    val total = pieData.sumOf { it.second }.coerceAtLeast(1)
                                                    var start = -90f
                                                    for ((i, pair) in pieData.withIndex()) {
                                                        val sweep = pair.second.toFloat() / total.toFloat() * 360f
                                                        val color = pieColors.getOrNull(i) ?: Color.Gray
                                                        drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = true)
                                                        start += sweep
                                                    }
                                                }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                for ((label, value) in pieData) {
                                                    Text(text = "$label: $value")
                                                }
                                            }
                                            Button(onClick = { showPieDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
                                        }
                                    }
                                }
                            }

                        // Milimetraje percentage bar: rojo = debajo de profMin, verde = entre profMin y profMax, amarillo = arriba de profMax
                        val catalogMeasureMap = llantaCatalog.associateBy({ it.idLlantas }) { it.LlantasMedida }
                        val parametrosByMedida = parametros.groupBy { it.LlantasMedida }
                        val parametrosByLlantaId = parametros.associateBy { it.Llantas_idLlantas }
                        var mmBelow = 0
                        var mmBetween = 0
                        var mmAbove = 0
                        var mmNoData = 0

                        for (ll in flattened) {
                            val medida = catalogMeasureMap[ll.Llantas_idLlantas]
                            // Prefer parameter matching by exact llanta id; fallback to medida group
                            val param = parametrosByLlantaId[ll.Llantas_idLlantas] ?: parametrosByMedida[medida]?.firstOrNull()
                            if (param == null) {
                                mmNoData++
                                continue
                            }
                            val profMin = param.ParametrosProfMin ?: 0
                            val profMax = param.ParametrosProfMax ?: 0

                            val mmVals = listOfNotNull(
                                ll.LlantasInspeccionMm1.takeIf { it != 0f },
                                ll.LlantasInspeccionMm2.takeIf { it != 0f },
                                ll.LlantasInspeccionMm3.takeIf { it != 0f },
                                ll.LlantasInspeccionMm4.takeIf { it != 0f }
                            )
                            if (mmVals.isEmpty()) {
                                mmNoData++
                                continue
                            }
                            val mmAvg = mmVals.average().toFloat()
                            when {
                                mmAvg < profMin -> mmBelow++
                                mmAvg <= profMax -> mmBetween++
                                else -> mmAbove++
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            pieTitle = "Milimetraje"
                            pieData = listOf("< ProfMin" to mmBelow, "Entre ProfMin-ProfMax" to mmBetween, "> ProfMax" to mmAbove)
                            val mmColors = listOf(Color(0xFFD32F2F), Color(0xFF2E7D32), Color(0xFFFFA000))
                            pieColors = pieData.mapIndexed { i, _ -> mmColors[i % mmColors.size] }
                            showPieDialog = true
                        }) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Milimetraje (porcentaje)", style = MaterialTheme.typography.titleMedium)

                                // segmented bar
                                Row(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                                    if (mmBelow > 0) Box(modifier = Modifier.weight(mmBelow.toFloat()).fillMaxHeight().background(Color(0xFFD32F2F)))
                                    if (mmBetween > 0) Box(modifier = Modifier.weight(mmBetween.toFloat()).fillMaxHeight().background(Color(0xFF2E7D32)))
                                    if (mmAbove > 0) Box(modifier = Modifier.weight(mmAbove.toFloat()).fillMaxHeight().background(Color(0xFFFFA000)))
                                    if (mmBelow + mmBetween + mmAbove == 0) Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)))
                                }

                                // no textual labels under the segmented bar (only title + colored bar)

                                if (mmNoData > 0) Text(text = "Sin dato: ${mmNoData}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Presión de inflado percentage bar: rojo = < pmin, verde = entre pmin y psug, amarillo = > psug
                        var presionRed = 0
                        var presionGreen = 0
                        var presionYellow = 0
                        var presionNoData = 0
                        var vigiaCount = 0

                        // Debug: log parametros map sizes
                        com.megatransportes.yokoh.utils.DebugLog.d(
                            "PRUEBA_INSPECCION",
                            "parametros.size=${parametros.size} parametrosByLlanta.size=${parametrosByLlantaId.size} flattened.size=${flattened.size}"
                        )

                        for (ll in flattened) {
                            if (ll.LlantasInspeccionVigia == 1) {
                                vigiaCount++
                                continue
                            }
                            val p = ll.LlantasInspeccionPresion
                            val medida = catalogMeasureMap[ll.Llantas_idLlantas]
                            val param = parametrosByLlantaId[ll.Llantas_idLlantas] ?: parametrosByMedida[medida]?.firstOrNull()

                            // Debug: print llanta pressure and matched param info
                            com.megatransportes.yokoh.utils.DebugLog.d(
                                "PRUEBA_INSPECCION",
                                "llantaId=${ll.Llantas_idLlantas} vehiculoInspeccion=${ll.vehiculosinspeccion_idVehiculoInspeccion} pres=$p medida=$medida paramId=${param?.idParametros} pmin=${param?.ParametrosPMin} psug=${param?.ParametrosPSug}"
                            )

                            if (param == null) {
                                // No parametros entry for this llanta id; fallback: we have a numeric
                                // pressure value from the API, so count it as OK rather than "Sin dato".
                                presionGreen++
                                continue
                            }

                            val pmin = param.ParametrosPMin?.toFloat() ?: 0f
                            val psug = param.ParametrosPSug?.toFloat() ?: 0f
                            when {
                                p < pmin -> presionRed++
                                p <= psug -> presionGreen++
                                else -> presionYellow++
                            }
                        }

                        com.megatransportes.yokoh.utils.DebugLog.d(
                            "PRUEBA_INSPECCION",
                            "presionRed=$presionRed presionGreen=$presionGreen presionYellow=$presionYellow presionNoData=$presionNoData vigiaCount=$vigiaCount"
                        )

                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            pieTitle = "Presión"
                            pieData = listOf(
                                "OK" to presionGreen,
                                "Bajo" to presionRed,
                                "Alto" to presionYellow,
                                "Sin dato" to presionNoData,
                                "Vigía/Inaccesible" to vigiaCount
                            )
                            val presColors = listOf(Color(0xFF2E7D32), Color(0xFFD32F2F), Color(0xFFFFA000), Color(0xFFBDBDBD), Color(0xFF7B1FA2))
                            pieColors = pieData.mapIndexed { i, _ -> presColors[i % presColors.size] }
                            showPieDialog = true
                        }) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Presión de inflado (porcentaje)", style = MaterialTheme.typography.titleMedium)

                                Row(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                                    if (presionGreen > 0) Box(modifier = Modifier.weight(presionGreen.toFloat()).fillMaxHeight().background(Color(0xFF2E7D32)))
                                    if (presionRed > 0) Box(modifier = Modifier.weight(presionRed.toFloat()).fillMaxHeight().background(Color(0xFFD32F2F)))
                                    if (presionYellow > 0) Box(modifier = Modifier.weight(presionYellow.toFloat()).fillMaxHeight().background(Color(0xFFFFA000)))
                                    if (presionNoData > 0) Box(modifier = Modifier.weight(presionNoData.toFloat()).fillMaxHeight().background(Color(0xFFBDBDBD)))
                                    if (vigiaCount > 0) Box(modifier = Modifier.weight(vigiaCount.toFloat()).fillMaxHeight().background(Color(0xFF7B1FA2)))
                                    if (presionGreen + presionRed + presionYellow + presionNoData + vigiaCount == 0) Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)))
                                }

                                // no textual labels under the pressure segmented bar
                            }
                        }

                        // Tipo de piso: Original vs Vitalizado vs Otros (usar LlantasInspeccionPiso)
                        val originalCount = flattened.count { it.LlantasInspeccionPiso == "Original" }
                        val vitalizadoCount = flattened.count { it.LlantasInspeccionPiso == "Vitalizado" }
                        val otherPisoCount = (flattened.size - originalCount - vitalizadoCount).coerceAtLeast(0)
                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            pieTitle = "Tipo de piso"
                            pieData = listOf("Original" to originalCount, "Vitalizado" to vitalizadoCount, "Otros" to otherPisoCount)
                            val pisoColors = listOf(Color(0xFF1976D2), Color(0xFF6D4C41), Color(0xFFBDBDBD))
                            pieColors = pieData.mapIndexed { i, _ -> pisoColors[i % pisoColors.size] }
                            showPieDialog = true
                        }) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tipo de piso", fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                                    if (originalCount > 0) Box(modifier = Modifier.weight(originalCount.toFloat()).fillMaxHeight().background(Color(0xFF1976D2)))
                                    if (vitalizadoCount > 0) Box(modifier = Modifier.weight(vitalizadoCount.toFloat()).fillMaxHeight().background(Color(0xFF6D4C41)))
                                    if (otherPisoCount > 0) Box(modifier = Modifier.weight(otherPisoCount.toFloat()).fillMaxHeight().background(Color(0xFFBDBDBD)))
                                    if (originalCount + vitalizadoCount + otherPisoCount == 0) Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)))
                                }
                                // no textual labels under the tipo de piso segmented bar
                            }
                        }

                        // Condición peligrosa: mostrar porcentaje de llantas con condición peligrosa (1 == peligrosa)
                        val condPelTrue = flattened.count { it.LlantasInspeccionCondPel == 1 }
                        val condPelFalse = (flattened.size - condPelTrue).coerceAtLeast(0)
                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            pieTitle = "Condición peligrosa"
                            pieData = listOf("Peligrosa" to condPelTrue, "Normal" to condPelFalse)
                            val condColors = listOf(Color(0xFFD32F2F), Color(0xFF2E7D32))
                            pieColors = pieData.mapIndexed { i, _ -> condColors[i % condColors.size] }
                            showPieDialog = true
                        }) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Condición peligrosa", fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                                    if (condPelTrue > 0) Box(modifier = Modifier.weight(condPelTrue.toFloat()).fillMaxHeight().background(Color(0xFFD32F2F)))
                                    if (condPelFalse > 0) Box(modifier = Modifier.weight(condPelFalse.toFloat()).fillMaxHeight().background(Color(0xFF2E7D32)))
                                    if (condPelTrue + condPelFalse == 0) Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)))
                                }
                                // no textual labels under the condición peligrosa segmented bar
                            }
                        }

                        // Observaciones: agrupar por texto (null/blank -> "LLANTA OK"), mostrar top + pie
                        run {
                            val obsRaw = flattened.map { it.LlantasInspeccionObservacion?.takeIf { s -> s.isNotBlank() } ?: "LLANTA OK" }
                            val obsCounts = obsRaw.groupingBy { it.trim() }.eachCount().entries.sortedByDescending { it.value }
                            val topNobs = 3
                            val topObs = obsCounts.take(topNobs)
                            val othersObsCount = obsCounts.drop(topNobs).sumOf { it.value }

                            fun simplifyLabel(s: String): String {
                                val t = s.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                                return if (t.length > 40) t.take(37).trimEnd() + "..." else t
                            }

                            val displayObs: List<Pair<String, Int>> = topObs.map { (k, v) -> simplifyLabel(k) to v }
                                .let { if (othersObsCount > 0) it + listOf("Otras" to othersObsCount) else it }

                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                pieTitle = "Observaciones"
                                val fullList = topObs.map { it.key to it.value } + if (othersObsCount > 0) listOf("Otras" to othersObsCount) else emptyList()
                                pieData = fullList
                                showPieDialog = true
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for ((label, count) in displayObs) {
                                        val pct = if (totalLlantas > 0) (count * 100) / totalLlantas else 0
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = label)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "${pct}%", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                val slicesObs = displayObs.map { it.second }
                                Box(modifier = Modifier.size(88.dp).padding(start = 12.dp).clickable {
                                    pieTitle = "Observaciones"
                                    val fullList = topObs.map { it.key to it.value } + if (othersObsCount > 0) listOf("Otras" to othersObsCount) else emptyList()
                                    pieData = fullList
                                    showPieDialog = true
                                }, contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(88.dp)) {
                                        var startAngle = -90f
                                        val colors = listOf(Color(0xFF1976D2), Color(0xFF9C27B0), Color(0xFF7B1FA2), Color.Gray)
                                        for ((i, cnt) in slicesObs.withIndex()) {
                                            val sweep = cnt.toFloat() / totalLlantas.toFloat() * 360f
                                            drawArc(color = colors[i % colors.size], startAngle = startAngle, sweepAngle = sweep, useCenter = true)
                                            startAngle += sweep
                                        }
                                    }
                                }
                            }
                        }

                        } else {
                            Text("No hay datos de llantas para esta prueba", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // (Se han eliminado las secciones de presión, tipo de piso, condición peligrosa y observaciones)

                        Text("Detalle de los vehículos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                        // Detalle de los vehículos: listado interactivo con expansión por vehículo
                        var expandedVehicles by remember { mutableStateOf(setOf<Int>()) }

                        // Fetch tipos de vehiculo to display names
                        var tiposVeh by remember { mutableStateOf<List<TipoVehiculo>>(emptyList()) }
                        LaunchedEffect(flota.idFlotas) {
                            coroutineScope.launch {
                                repository.getTiposVehiculos()
                                    .onSuccess { tiposVeh = it }
                                    .onFailure { /* ignore */ }
                            }
                        }
                        val tiposMap = tiposVeh.associateBy({ it.idTipoVehiculos }) { it.TipoVehiculosNombre }

                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (veh in vehiculos) {
                                val llantas = llantasPorVehiculo[veh.idVehiculoInspeccion] ?: emptyList()
                                val tipoName = tiposMap[veh.TipoVehiculos_idTipoVehiculos] ?: "Tipo ${veh.TipoVehiculos_idTipoVehiculos}"
                                val isExpanded = expandedVehicles.contains(veh.idVehiculoInspeccion)
                                Card(modifier = Modifier.fillMaxWidth().clickable {
                                    expandedVehicles = if (expandedVehicles.contains(veh.idVehiculoInspeccion)) expandedVehicles - veh.idVehiculoInspeccion else expandedVehicles + veh.idVehiculoInspeccion
                                }) {
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        val isCompact = maxWidth < 420.dp

                                        val openLocation: () -> Unit = {
                                            coroutineScope.launch {
                                                try {
                                                    val (lat, lng) = veh.resolveCoords()
                                                    if (lat != null && lng != null) {
                                                        val url = "https://www.google.com/maps/search/?api=1&query=${lat},${lng}"
                                                        OpenFileUtils.openUrl(url, platformContext = platformContext)
                                                    } else {
                                                        repository.getVehiculoInspeccionById(veh.idVehiculoInspeccion)
                                                            .onSuccess { v2 ->
                                                                val (lat2, lng2) = v2.resolveCoords()
                                                                if (lat2 != null && lng2 != null) {
                                                                    val url = "https://www.google.com/maps/search/?api=1&query=${lat2},${lng2}"
                                                                    OpenFileUtils.openUrl(url, platformContext = platformContext)
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Ubicación no disponible para este vehículo")
                                                                }
                                                            }
                                                            .onFailure { err -> snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error obteniendo ubicación")) }
                                                    }
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(ErrorUtils.userMessage(e, "Error abriendo mapa"))
                                                }
                                            }
                                        }

                                        if (isCompact) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column {
                                                    Text(veh.VehiculoInspeccionNo, fontWeight = FontWeight.Bold)
                                                    Text(tipoName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${llantas.size} llantas", style = MaterialTheme.typography.bodyMedium)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(onClick = openLocation) {
                                                            Icon(Icons.Default.Place, contentDescription = "Abrir ubicación")
                                                        }
                                                        IconButton(onClick = {
                                                            expandedVehicles = if (isExpanded) expandedVehicles - veh.idVehiculoInspeccion else expandedVehicles + veh.idVehiculoInspeccion
                                                        }) {
                                                            Icon(
                                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(veh.VehiculoInspeccionNo, fontWeight = FontWeight.Bold)
                                                    Text(tipoName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${llantas.size} llantas", style = MaterialTheme.typography.bodyMedium)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(onClick = openLocation) {
                                                        Icon(Icons.Default.Place, contentDescription = "Abrir ubicación")
                                                    }
                                                    IconButton(onClick = {
                                                        expandedVehicles = if (isExpanded) expandedVehicles - veh.idVehiculoInspeccion else expandedVehicles + veh.idVehiculoInspeccion
                                                    }) {
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = if (isExpanded) "Colapsar" else "Expandir"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (expandedVehicles.contains(veh.idVehiculoInspeccion)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (ll in llantas) {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        val presText = if (ll.LlantasInspeccionVigia == 1) "Vigía/Inaccesible" else ll.LlantasInspeccionPresion.toString()
                                                        Text("Presión: $presText", style = MaterialTheme.typography.bodyMedium)
                                                        val mmText = listOfNotNull(ll.LlantasInspeccionMm1, ll.LlantasInspeccionMm2, ll.LlantasInspeccionMm3, ll.LlantasInspeccionMm4).joinToString(" / ")
                                                        if (mmText.isNotBlank()) Text("Milimetraje: $mmText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("Piso: ${ll.LlantasInspeccionPiso ?: "Original"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                        // Desgaste irregular / factor delta
                                                        val mmVals = listOfNotNull(
                                                            ll.LlantasInspeccionMm1.takeIf { it != 0f },
                                                            ll.LlantasInspeccionMm2.takeIf { it != 0f },
                                                            ll.LlantasInspeccionMm3.takeIf { it != 0f },
                                                            ll.LlantasInspeccionMm4.takeIf { it != 0f }
                                                        )
                                                        val diferencia = if (mmVals.isNotEmpty()) {
                                                            val max = mmVals.maxOrNull() ?: 0f
                                                            val min = mmVals.minOrNull() ?: 0f
                                                            max - min
                                                        } else 0f

                                                        // Etiqueta solicitada: "Factor delta"
                                                        Text("Factor delta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        if (diferencia > 1f) {
                                                            Text("Desgaste irregular: ${NumberFormatter.formatWithComma(diferencia, 1)} mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                        } else {
                                                            Text("Ok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }

                                                        // Observación
                                                        val obs = ll.LlantasInspeccionObservacion?.takeIf { it.isNotBlank() } ?: "LLANTA OK"
                                                        Text("Observación: $obs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                        // Fotos (si existen) - soporta data URI o Buffer JSON
                                                        val fotos = listOfNotNull(
                                                            ll.LlantasInspeccionFoto?.takeIf { it.isNotBlank() },
                                                            ll.LlantasInspeccionFoto2?.takeIf { it.isNotBlank() }
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

                                                    // Condición peligrosa icon
                                                    val isCond = ll.LlantasInspeccionCondPel == 1
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Condición peligrosa",
                                                        modifier = Modifier.size(20.dp),
                                                        tint = if (isCond) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Pie chart dialog (large)
                        if (showPieDialog) {
                            Dialog(onDismissRequest = { showPieDialog = false }) {
                                Card(modifier = Modifier.fillMaxWidth(0.9f).padding(12.dp)) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(pieTitle, style = MaterialTheme.typography.titleLarge)
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Canvas(modifier = Modifier.size(220.dp)) {
                                                    val total = pieData.sumOf { it.second }.coerceAtLeast(1)
                                                    var start = -90f
                                                    val colors = pieColors
                                                    for ((i, pair) in pieData.withIndex()) {
                                                        val sweep = pair.second.toFloat() / total.toFloat() * 360f
                                                        val color = colors.getOrNull(i) ?: Color.Gray
                                                        drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = true)
                                                        start += sweep
                                                    }
                                                }
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            for ((label, cnt) in pieData) {
                                                val pct = (cnt * 100) / (pieData.sumOf { it.second }.coerceAtLeast(1))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(label)
                                                    Text("${cnt} (${pct}%)", color = MaterialTheme.colorScheme.primary)
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
        }
    }
}
