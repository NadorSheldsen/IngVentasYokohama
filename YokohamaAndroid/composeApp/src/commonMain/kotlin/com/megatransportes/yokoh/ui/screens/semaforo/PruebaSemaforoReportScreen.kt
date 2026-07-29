package com.megatransportes.yokoh.ui.screens.semaforo

import androidx.compose.foundation.layout.*
import com.megatransportes.yokoh.ui.components.PlatformLazyColumn
import androidx.compose.foundation.lazy.items
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
import com.megatransportes.yokoh.utils.getPlatformContext
import com.megatransportes.yokoh.utils.FileSaveUtils
import com.megatransportes.yokoh.utils.OpenFileUtils
import com.megatransportes.yokoh.utils.ErrorUtils
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Size
import com.megatransportes.yokoh.data.models.PruebasSemaforo
import com.megatransportes.yokoh.data.models.VehiculoSemaforo
import com.megatransportes.yokoh.data.models.LlantasSemaforo
import com.megatransportes.yokoh.data.models.Parametro
import com.megatransportes.yokoh.data.models.Flota
import com.megatransportes.yokoh.data.models.TipoVehiculo
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruebaSemaforoReportScreen(
    repository: YokohamaRepository,
    flota: Flota,
    prueba: PruebasSemaforo,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var vehiculos by remember { mutableStateOf<List<VehiculoSemaforo>>(emptyList()) }
    var llantasPorVehiculo by remember { mutableStateOf<Map<Int, List<LlantasSemaforo>>>(emptyMap()) }
    var llantaCatalog by remember { mutableStateOf<List<com.megatransportes.yokoh.data.models.Llanta>>(emptyList()) }
    var parametros by remember { mutableStateOf<List<Parametro>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val platformContext = getPlatformContext()

    LaunchedEffect(prueba.idPruebasSemaforo) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                repository.getVehiculosSemaforoByPruebaId(prueba.idPruebasSemaforo)
                    .onSuccess { list ->
                        vehiculos = list
                        // fetch llantas for each vehicle in parallel
                        val deferred = list.map { veh ->
                            coroutineScope.async {
                                repository.getLlantasSemaforoByVehiculoId(veh.idVehiculoSemaforo)
                                    .getOrThrow()
                            }
                        }
                        val results = deferred.awaitAll()
                        val map = list.mapIndexed { idx, veh -> veh.idVehiculoSemaforo to results[idx] }.toMap()
                        llantasPorVehiculo = map
                        // fetch llanta catalog for brand names
                        repository.getLlantasByFlota(flota.idFlotas)
                            .onSuccess { catalog -> llantaCatalog = catalog }
                            .onFailure { /* ignore catalog errors, show Otras when unknown */ }
                        // fetch parametros for pressure/prof rules
                        repository.getParametrosByFlotaId(flota.idFlotas)
                            .onSuccess { params -> parametros = params }
                            .onFailure { /* ignore */ }
                        isLoading = false
                    }
                    .onFailure { err ->
                        errorMessage = err.message ?: "Error cargando vehículos"
                        isLoading = false
                    }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error inesperado"
                isLoading = false
            }
        }
    }

    // Helper to resolve coordinates from several possible fields the backend might return
    fun VehiculoSemaforo.resolveCoords(): Pair<Double?, Double?> {
        val candidatesLat = listOfNotNull(this.latitude, this.VehiculoPruebaSemaforoLat, this.VehiculosLatitude)
        val candidatesLon = listOfNotNull(this.longitude, this.VehiculoPruebaSemaforoLon, this.VehiculosLongitude)
        // prefer first non-zero-ish value
        fun firstValid(list: List<Double?>): Double? {
            for (v in list) {
                if (v != null && kotlin.math.abs(v) > 1e-6) return v
            }
            return null
        }
        return Pair(firstValid(candidatesLat), firstValid(candidatesLon))
    }

    // aggregate totals
    val totalLlantas = llantasPorVehiculo.values.sumOf { it.size }
    // map llanta id -> marca from catalog
    val catalogMap = llantaCatalog.associateBy({ it.idLlantas }) { it.LlantasMarca }
    val brandCounts = llantasPorVehiculo.values.flatten().map { ll -> catalogMap[ll.Llantas_idLlantas] ?: "Otras" }
        .groupingBy { it }.eachCount()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reporte - ${prueba.PruebasSemaforoTitulo}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Home") }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                val jsonLib = Json { prettyPrint = false; isLenient = true; ignoreUnknownKeys = true }

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
                                    "idPruebasSemaforo" to prueba.idPruebasSemaforo,
                                    "PruebasSemaforoTitulo" to prueba.PruebasSemaforoTitulo
                                )

                                val flotaMap = mapOf(
                                    "idFlotas" to flota.idFlotas,
                                    "FlotasNombre" to flota.FlotasNombre
                                )

                                val vehiculosList = vehiculos.map { v ->
                                    mapOf(
                                        "idVehiculoSemaforo" to v.idVehiculoSemaforo,
                                        "VehiculoSemaforoNo" to v.VehiculoSemaforoNo,
                                        "TipoVehiculos_idTipoVehiculos" to v.TipoVehiculos_idTipoVehiculos
                                    )
                                }

                                val llantaCatalogList = llantaCatalog.map { l -> mapOf("idLlantas" to l.idLlantas, "LlantasMarca" to l.LlantasMarca) }

                                val parametrosList = parametros.map { p -> mapOf("Llantas_idLlantas" to p.Llantas_idLlantas, "ParametrosPMin" to p.ParametrosPMin, "ParametrosPSug" to p.ParametrosPSug) }

                                val llantasMapStringKeys = llantasPorVehiculo.mapKeys { it.key.toString() }.mapValues { entry ->
                                    entry.value.map { ll ->
                                        mapOf(
                                            "Llantas_idLlantas" to ll.Llantas_idLlantas,
                                            "LlantasSemaforoPresion" to ll.LlantasSemaforoPresion,
                                            "LlantasSemaforoVigia" to ll.LlantasSemaforoVigia,
                                            "LlantasSemaforoPiso" to ll.LlantasSemaforoPiso,
                                            "LlantasSemaforoCondPel" to ll.LlantasSemaforoCondPel,
                                            "LlantasSemaforoObserv" to ll.LlantasSemaforoObserv,
                                            "LlantasSemaforoColor" to ll.LlantasSemaforoColor,
                                            "LlantasSemaforoFoto1" to ll.LlantasSemaforoFoto1,
                                            "LlantasSemaforoFoto2" to ll.LlantasSemaforoFoto2
                                        )
                                    }
                                }

                                val payloadElem = kotlinx.serialization.json.JsonObject(mapOf(
                                    "prueba" to toJsonElem(pruebaMap),
                                    "flota" to toJsonElem(flotaMap),
                                    "vehiculos" to toJsonElem(vehiculosList),
                                    "llantasPorVehiculo" to toJsonElem(llantasMapStringKeys),
                                    "llantaCatalog" to toJsonElem(llantaCatalogList),
                                    "parametros" to toJsonElem(parametrosList)
                                ))

                                val payloadStr = jsonLib.encodeToString(payloadElem)

                                repository.downloadSemaforoReportPdf(payloadStr)
                                    .onSuccess { bytes ->
                                        try {
                                            val isPdf = bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
                                            if (!isPdf) {
                                                snackbarHostState.showSnackbar("El archivo descargado no parece un PDF")
                                                return@onSuccess
                                            }
                                            val fname = "prueba_semaforo_${prueba.idPruebasSemaforo ?: "report"}.pdf"
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
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text(errorMessage ?: "Error")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            // retry
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                // trigger reload by using LaunchedEffect key (prueba id) or repeat same code
                                repository.getVehiculosSemaforoByPruebaId(prueba.idPruebasSemaforo)
                                    .onSuccess { list ->
                                        vehiculos = list
                                        val deferred = list.map { veh ->
                                            coroutineScope.async {
                                                repository.getLlantasSemaforoByVehiculoId(veh.idVehiculoSemaforo).getOrThrow()
                                            }
                                        }
                                        val results = deferred.awaitAll()
                                        val map = list.mapIndexed { idx, veh -> veh.idVehiculoSemaforo to results[idx] }.toMap()
                                        llantasPorVehiculo = map
                                        isLoading = false
                                    }
                                    .onFailure { err ->
                                        errorMessage = err.message ?: "Error cargando vehículos"
                                        isLoading = false
                                    }
                            }
                        }) {
                            Text("Reintentar")
                        }
                    }
                }
                else -> {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var showPieDialog by remember { mutableStateOf(false) }
                    var pieTitle by remember { mutableStateOf("") }
                    var pieData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
                    var pieColors by remember { mutableStateOf<List<Color>>(listOf(Color(0xFF2E7D32), Color(0xFFFFA000), Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF9C27B0), Color.Gray)) }
                        // Presión de inflado: classify each llanta using parametros (PMin, PSug)
                        val flattened = llantasPorVehiculo.values.flatten()

                        // DEBUG: print the list of llantas used for the charts and their presiones and catalog ids
                        try {
                            val perLlanta = flattened.map { ll ->
                                "idSemaforo=${ll.idLlantasSemaforo}, idLlanta=${ll.Llantas_idLlantas}, presion=${ll.LlantasSemaforoPresion}, vigia=${ll.LlantasSemaforoVigia}"
                            }
                            println("DEBUG PruebaSemaforoReport - prueba=${prueba.idPruebasSemaforo} per-llanta:\n${perLlanta.joinToString("\n")}")
                                val parametrosMapDbg = parametros.associateBy { it.Llantas_idLlantas }
                                val parametrosByMedidaDbg = parametros.associateBy { it.LlantasMedida }
                                println("DEBUG PruebaSemaforoReport - parametrosById keys: ${parametrosMapDbg.keys} medidas: ${parametrosByMedidaDbg.keys}")
                        } catch (e: Exception) {
                            println("DEBUG PruebaSemaforoReport - error printing debug mapping: ${e.message}")
                        }
                        // Maps to resolve parameters: by llanta id and by medida (using catalog)
                        val parametrosByLlantaId = parametros.associateBy { it.Llantas_idLlantas }
                        val parametrosByMedida = parametros.associateBy { it.LlantasMedida }
                        val catalogMeasureMap = llantaCatalog.associateBy({ it.idLlantas }) { it.LlantasMedida }
                        var presionRed = 0
                        var presionGreen = 0
                        var presionYellow = 0
                        var presionNoData = 0
                        var vigiaCount = 0

                        for (ll in flattened) {
                            // If the wheel is marked as vigía, count it separately and skip pressure buckets
                            if (ll.LlantasSemaforoVigia == 1) {
                                vigiaCount++
                                continue
                            }

                            val p = ll.LlantasSemaforoPresion?.toFloat()
                            // Resolve parameter first by exact llanta id, then by medida of the catalog for this flota
                            val medida = catalogMeasureMap[ll.Llantas_idLlantas]
                            val param = parametrosByLlantaId[ll.Llantas_idLlantas] ?: (medida?.let { parametrosByMedida[it] })

                            if (p == null) {
                                // No pressure reading at all
                                presionNoData++
                                continue
                            }

                            if (param == null) {
                                // No parametros entry for this llanta id. Fallback: we have a numeric
                                // pressure value from the API, so count it as OK rather than "Sin dato".
                                // This avoids showing "Sin dato" when the server returned a pressure
                                // but the parametros table doesn't contain thresholds for this llanta id.
                                presionGreen++
                                continue
                            }

                            when {
                                p <= param.ParametrosPMin -> presionRed++
                                p <= param.ParametrosPSug -> presionGreen++
                                else -> presionYellow++
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth().clickable {
                            // prepare pressure pie data
                            pieTitle = "Presión"
                            pieData = listOf(
                                "OK" to presionGreen,
                                "Bajo" to presionRed,
                                "Alto" to presionYellow,
                                "Sin dato" to presionNoData,
                                "Vigía/Inac" to vigiaCount
                            )
                            val presColors = listOf(Color(0xFF2E7D32), Color(0xFFD32F2F), Color(0xFFFFA000), Color(0xFFBDBDBD), Color(0xFF7B1FA2))
                            pieColors = listOf(presColors[0], presColors[1], presColors[2], presColors[3], presColors[4])
                            showPieDialog = true
                        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Presión de inflado", fontWeight = FontWeight.Bold)
                            // Main bar: include vigía as a purple segment together with pressure buckets
                            Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                                if (presionGreen > 0) Box(modifier = Modifier.weight(presionGreen.toFloat()).fillMaxHeight().background(Color(0xFF2E7D32)))
                                if (presionRed > 0) Box(modifier = Modifier.weight(presionRed.toFloat()).fillMaxHeight().background(Color(0xFFD32F2F)))
                                if (presionYellow > 0) Box(modifier = Modifier.weight(presionYellow.toFloat()).fillMaxHeight().background(Color(0xFFFFA000)))
                                if (presionNoData > 0) Box(modifier = Modifier.weight(presionNoData.toFloat()).fillMaxHeight().background(Color(0xFFBDBDBD)))
                                if (vigiaCount > 0) Box(modifier = Modifier.weight(vigiaCount.toFloat()).fillMaxHeight().background(Color(0xFF7B1FA2)))
                            }

                            // no textual labels under the pressure segmented bar
                        }

                        // Brand usage summary: names + % on left, pie chart on right
                        if (totalLlantas > 0) {
                            val sorted = brandCounts.entries.sortedByDescending { it.value }
                            val topN = 3
                            val top = sorted.take(topN)
                            val othersCount = sorted.drop(topN).sumOf { it.value }
                            val displayList: List<Pair<String, Int>> = top.map { (k, v) -> k to v }
                                .let { if (othersCount > 0) it + listOf("Otras" to othersCount) else it }

                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                // Prepare brand pie data
                                pieTitle = "Marcas"
                                pieData = displayList
                                // set colors for dialog matching small pie colors
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

                                // Pie chart
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
                        } else {
                            Text("No hay datos de llantas para esta prueba", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Tipo de piso: Mostrar Original vs Vitalizado (porcentajes entre ambos)
                        val originalCount = flattened.count { it.LlantasSemaforoPiso == "Original" }
                        val vitalizadoCount = flattened.count { it.LlantasSemaforoPiso == "Vitalizado" }
                        val otherPisoCount = (flattened.size - originalCount - vitalizadoCount).coerceAtLeast(0)
                        Column(modifier = Modifier.fillMaxWidth().clickable {
                            pieTitle = "Tipo de piso"
                            pieData = listOf("Original" to originalCount, "Vitalizado" to vitalizadoCount, "Otros" to otherPisoCount)
                            val pisoColors = listOf(Color(0xFF1976D2), Color(0xFF6D4C41), Color(0xFFBDBDBD))
                            pieColors = pisoColors.take(pieData.size)
                            showPieDialog = true
                        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tipo de piso", fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                                if (originalCount > 0) Box(modifier = Modifier.weight(originalCount.toFloat()).fillMaxHeight().background(Color(0xFF1976D2)))
                                if (vitalizadoCount > 0) Box(modifier = Modifier.weight(vitalizadoCount.toFloat()).fillMaxHeight().background(Color(0xFF6D4C41)))
                                if (otherPisoCount > 0) Box(modifier = Modifier.weight(otherPisoCount.toFloat()).fillMaxHeight().background(Color(0xFFBDBDBD)))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (otherPisoCount > 0) Text("Otros: ${otherPisoCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Condición peligrosa: mostrar porcentaje de llantas con condición peligrosa (true)
                        // Treat null as Normal (false)
                        val condPelTrue = flattened.count { it.LlantasSemaforoCondPel == true }
                        val condPelFalse = flattened.count { it.LlantasSemaforoCondPel != true }
                        val condPelOther = 0
                        Column(modifier = Modifier.fillMaxWidth().clickable {
                            pieTitle = "Condición peligrosa"
                            pieData = listOf("Peligrosa" to condPelTrue, "Normal" to condPelFalse)
                            val condColors = listOf(Color(0xFFD32F2F), Color(0xFF2E7D32))
                            pieColors = condColors.take(pieData.size)
                            showPieDialog = true
                        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Condición peligrosa", fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                                if (condPelTrue > 0) Box(modifier = Modifier.weight(condPelTrue.toFloat()).fillMaxHeight().background(Color(0xFFD32F2F)))
                                if (condPelFalse > 0) Box(modifier = Modifier.weight(condPelFalse.toFloat()).fillMaxHeight().background(Color(0xFF2E7D32)))
                                if (condPelOther > 0) Box(modifier = Modifier.weight(condPelOther.toFloat()).fillMaxHeight().background(Color(0xFFBDBDBD)))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Labels removed for bar chart
                            }
                        }

                        // Observaciones: agrupar por texto (null/blank -> "LLANTA OK"), mostrar versión simplificada + % y pie clicable
                        run {
                            val obsRaw = flattened.map { it.LlantasSemaforoObserv?.takeIf { s -> s.isNotBlank() } ?: "LLANTA OK" }
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
                                // pieData should contain full labels where possible; use top full labels then Otras
                                val fullList = topObs.map { it.key to it.value } + if (othersObsCount > 0) listOf("Otras" to othersObsCount) else emptyList()
                                pieData = fullList
                                val obsColors = listOf(Color(0xFF1976D2), Color(0xFF9C27B0), Color(0xFF7B1FA2), Color.Gray)
                                pieColors = fullList.mapIndexed { i, _ -> obsColors[i % obsColors.size] }
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

                                // small pie
                                val slicesObs = displayObs.map { it.second }
                                    Box(modifier = Modifier.size(88.dp).padding(start = 12.dp).clickable {
                                    pieTitle = "Observaciones"
                                    val fullList = topObs.map { it.key to it.value } + if (othersObsCount > 0) listOf("Otras" to othersObsCount) else emptyList()
                                    pieData = fullList
                                    val obsColors = listOf(Color(0xFF1976D2), Color(0xFF9C27B0), Color(0xFF7B1FA2), Color.Gray)
                                    pieColors = fullList.mapIndexed { i, _ -> obsColors[i % obsColors.size] }
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
                                val llantas = llantasPorVehiculo[veh.idVehiculoSemaforo] ?: emptyList()
                                val tipoName = tiposMap[veh.TipoVehiculos_idTipoVehiculos] ?: "Tipo ${veh.TipoVehiculos_idTipoVehiculos}"
                                val isExpanded = expandedVehicles.contains(veh.idVehiculoSemaforo)
                                Card(modifier = Modifier.fillMaxWidth().clickable {
                                    expandedVehicles = if (expandedVehicles.contains(veh.idVehiculoSemaforo)) expandedVehicles - veh.idVehiculoSemaforo else expandedVehicles + veh.idVehiculoSemaforo
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
                                                        try {
                                                            println("DEBUG: veh id=${veh.idVehiculoSemaforo} coords fields: latitude=${veh.latitude} VehiculoPruebaSemaforoLat=${veh.VehiculoPruebaSemaforoLat} VehiculosLatitude=${veh.VehiculosLatitude} VehiculoPruebaSemaforoLon=${veh.VehiculoPruebaSemaforoLon} VehiculosLongitude=${veh.VehiculosLongitude}")
                                                        } catch (_: Exception) {}
                                                        repository.getVehiculoSemaforoById(veh.idVehiculoSemaforo)
                                                            .onSuccess { v2 ->
                                                                val (lat2, lng2) = v2.resolveCoords()
                                                                if (lat2 != null && lng2 != null) {
                                                                    val url = "https://www.google.com/maps/search/?api=1&query=${lat2},${lng2}"
                                                                    OpenFileUtils.openUrl(url, platformContext = platformContext)
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Ubicación no disponible para este vehículo")
                                                                }
                                                            }
                                                            .onFailure { err ->
                                                                snackbarHostState.showSnackbar(ErrorUtils.userMessage(err, "Error obteniendo ubicación"))
                                                            }
                                                    }
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(ErrorUtils.userMessage(e, "Error abriendo mapa"))
                                                }
                                            }
                                        }

                                        if (isCompact) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column {
                                                    Text(veh.VehiculoSemaforoNo, fontWeight = FontWeight.Bold)
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
                                                            expandedVehicles = if (isExpanded) expandedVehicles - veh.idVehiculoSemaforo else expandedVehicles + veh.idVehiculoSemaforo
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
                                                    Text(veh.VehiculoSemaforoNo, fontWeight = FontWeight.Bold)
                                                    Text(tipoName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${llantas.size} llantas", style = MaterialTheme.typography.bodyMedium)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(onClick = openLocation) {
                                                        Icon(Icons.Default.Place, contentDescription = "Abrir ubicación")
                                                    }
                                                    IconButton(onClick = {
                                                        expandedVehicles = if (isExpanded) expandedVehicles - veh.idVehiculoSemaforo else expandedVehicles + veh.idVehiculoSemaforo
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

                                if (expandedVehicles.contains(veh.idVehiculoSemaforo)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (ll in llantas) {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    // color swatch similar to SemaforoScreen
                                                    val colorBox = when (ll.LlantasSemaforoColor.lowercase()) {
                                                        "verde" -> Color(0xFF2E7D32)
                                                        "amarillo" -> Color(0xFFFFA000)
                                                        "rojo" -> Color(0xFFD32F2F)
                                                        else -> Color.Gray
                                                    }
                                                    Box(modifier = Modifier.size(20.dp).background(colorBox, shape = MaterialTheme.shapes.small))
                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        val presText = if (ll.LlantasSemaforoVigia == 1) "Vigía/Inac" else ll.LlantasSemaforoPresion.toString()
                                                        Text("Presión: $presText", style = MaterialTheme.typography.bodyMedium)
                                                        Text("Observación: ${ll.LlantasSemaforoObserv ?: "Sin dato"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }

                                                    // Condición peligrosa icon
                                                    val isCond = ll.LlantasSemaforoCondPel == true
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
                        // Pie chart dialog
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
                                                    drawArc(color = colors[i % colors.size], startAngle = start, sweepAngle = sweep, useCenter = true)
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
