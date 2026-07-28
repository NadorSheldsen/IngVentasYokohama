package com.megatransportes.yokoh.ui.screens.vehiculos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.min
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.Image
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import com.megatransportes.yokoh.utils.DateFormatter
import com.megatransportes.yokoh.utils.NumberFormatter
import com.megatransportes.yokoh.utils.TimeProvider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.platform.LocalFocusManager
import com.megatransportes.yokoh.utils.getPlatformContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ExperimentalMaterial3Api
import com.megatransportes.yokoh.data.models.*
import com.megatransportes.yokoh.ui.components.MicButton
import com.megatransportes.yokoh.ui.components.FieldDescriptor
import com.megatransportes.yokoh.ui.components.FieldType
import com.megatransportes.yokoh.ui.components.BluetoothCaliperButton
import com.megatransportes.yokoh.ui.screens.parametros.LlantasAdminScreen
import com.megatransportes.yokoh.ui.screens.parametros.ParametrosListScreen
import com.megatransportes.yokoh.ui.screens.parametros.EditParametroScreen
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.utils.FilePickerUtils
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.createFilePickerUtils
import com.megatransportes.yokoh.utils.InitializeFilePickerIfNeeded
import com.megatransportes.yokoh.ui.components.PhotoPickerDialog
import com.megatransportes.yokoh.ui.components.PhotoSlot
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.platform.getLastKnownLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruebaRendimientoScreen(
    repository: YokohamaRepository,
    flota: Flota,
    vehiculo: Vehiculo,
    llantasVehiculo: List<LlantaVehiculo>,
    onPruebaRegistrada: () -> Unit,
    onBack: () -> Unit,
    onOpenBitacora: (llantaVehiculo: com.megatransportes.yokoh.data.models.LlantaVehiculo) -> Unit = {},
    onOpenLlantasAdmin: (() -> Unit)? = null,
    onHome: () -> Unit = {},
    onOpenAddParametro: (llantaId: Int) -> Unit = {},
    onOpenParametrosList: (llantaIds: List<Int>) -> Unit = {}
) {
    // Inicializar FilePicker para Android
    InitializeFilePickerIfNeeded()
    
    // Helper local para formatear números con coma como separador de miles
    fun formatWithComma(value: Float): String {
        return try {
            NumberFormatter.formatWithComma(value, 0)
        } catch (e: Exception) {
            value.toString()
        }
    }
    // Format a string of digits (no separators) with thousand separators
    fun formatDigits(digits: String): String {
        if (digits.isBlank()) return ""
        return try {
            NumberFormatter.formatWithComma(digits.toFloat(), 0)
        } catch (e: Exception) {
            digits
        }
    }
    // Use integer input for odometer to avoid fractional km entries in the dialog
    // Prefer the latest PruebaRendimiento odometer if the repository has published one for this vehicle
    val vehiculoOdometerUpdates by repository.vehiculoOdometerUpdates.collectAsState()
    val initialOdo = vehiculoOdometerUpdates[vehiculo.idVehiculos] ?: vehiculo.VehiculosOdometro
    // Show formatted odometer with thousand separators using TextFieldValue to preserve cursor
    var odometroActual by remember { mutableStateOf(TextFieldValue(formatWithComma(initialOdo))) }
    var showOdometroDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showLlantasAdmin by remember { mutableStateOf(false) }
    var showParametrosDialog by remember { mutableStateOf(false) }
    var suggestedLlantaIdsForParametros by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showEditParametroDialog by remember { mutableStateOf(false) }
    var selectedLlantaIdForParametro by remember { mutableStateOf<Int?>(null) }
    var showConfirmTerminarDialog by remember { mutableStateOf(false) }
    var llantaIdPendienteTerminar by remember { mutableStateOf<Int?>(null) }
    var validationAttempted by remember { mutableStateOf(false) }
    val currentUser by repository.currentUser.collectAsState()
    var userPermissions by remember { mutableStateOf<List<Permisos>>(emptyList()) }
    var canTerminatePrueba by remember { mutableStateOf(false) }

    // Estado para los datos de cada llanta
    // Start empty — we'll fetch authoritative values from llantasrendimiento in batch
    var llantasData by remember { mutableStateOf<List<LlantaRendimientoFormData>>(emptyList()) }
    var readyToRender by remember { mutableStateOf(false) }

    // Último registro conocido por llantaVehiculoId
    var lastRendimientoMap by remember { mutableStateOf<Map<Int, LlantaRendimiento>>(emptyMap()) }
    // Map de pruebaId -> PruebaRendimiento para poder leer PruebaRendimientoOdometro asociado
    var lastPruebaMap by remember { mutableStateOf<Map<Int, PruebaRendimiento>>(emptyMap()) }
    // Lista local editable de llantas del vehículo; un elemento `null` indica
    // que en esa posición hay un espacio para montar una nueva llanta.
    var displayedLlantas by remember { mutableStateOf(mutableListOf<LlantaVehiculo?>().apply { addAll(llantasVehiculo) }) }
    LaunchedEffect(llantasVehiculo) {
        // Map incoming llantasVehiculo into positional slots when possible.
        try {
            // Find max position from piso values like "Pos N"
            val posRegex = Regex("(?i)pos\\s+([0-9]+)")
            val positions = llantasVehiculo.mapNotNull { lv ->
                lv.LlantasVehiculosPiso.let { piso ->
                    val m = posRegex.find(piso)
                    m?.groups?.get(1)?.value?.toIntOrNull()
                }
            }
            val maxPos = (positions.maxOrNull() ?: llantasVehiculo.size).coerceAtLeast(llantasVehiculo.size)
            val slots = MutableList<LlantaVehiculo?>(maxPos) { null }

            // First place those with explicit Pos N
            llantasVehiculo.forEach { lv ->
                val piso = lv.LlantasVehiculosPiso
                val m = piso.let { posRegex.find(it) }
                if (m != null) {
                    val idx = m.groups[1]?.value?.toIntOrNull()?.minus(1) ?: -1
                    if (idx in slots.indices) slots[idx] = lv else slots.add(lv)
                } else {
                    // defer non-pos items
                }
            }

            // Then place remaining items into first empty slots (or append)
            llantasVehiculo.forEach { lv ->
                if (!slots.contains(lv)) {
                    val emptyIdx = slots.indexOfFirst { it == null }
                    if (emptyIdx >= 0) slots[emptyIdx] = lv else slots.add(lv)
                }
            }

            // Do not automatically add an empty slot; prefer showing exactly the vehicle's slots.
            displayedLlantas = slots
        } catch (e: Exception) {
            // Fallback to direct copy on error
            // Fallback: copy current list but ensure an empty slot exists so the form can be shown
            // Fallback: copy current list without forcing an empty slot
            displayedLlantas = mutableListOf<LlantaVehiculo?>().apply { addAll(llantasVehiculo) }
        }
    }

    var showCausaRetiroDialog by remember { mutableStateOf(false) }
    var causaRetiroText by remember { mutableStateOf("") }
    var showCausaRetiroDropdown by remember { mutableStateOf(false) }

    // Helper: determina si alguna llanta cambió en mm o presión comparada con el último registro conocido
    fun hasMeasurementsChanged(): Boolean {
        return llantasData.any { data ->
            val last = lastRendimientoMap[data.llantaVehiculoId]
            val veh = llantasVehiculo.firstOrNull { it.idLlantasVehiculos == data.llantaVehiculoId }

            val origMm1 = last?.LlantasRendimientoMm1 ?: veh?.LlantasVehiculosMM1 ?: 0f
            val origMm2 = last?.LlantasRendimientoMm2 ?: veh?.LlantasVehiculosMM2 ?: 0f
            val origMm3 = last?.LlantasRendimientoMm3 ?: veh?.LlantasVehiculosMM3 ?: 0f
            val origMm4 = last?.LlantasRendimientoMm4 ?: veh?.LlantasVehiculosMM4 ?: 0f
            val origPres = last?.LlantasRendimientoPresion ?: veh?.LlantasVehiculosPresion ?: 0

            val curMm1 = data.mm1.toFloatOrNull() ?: 0f
            val curMm2 = data.mm2.toFloatOrNull() ?: 0f
            val curMm3 = data.mm3.toFloatOrNull() ?: 0f
            val curMm4 = data.mm4.toFloatOrNull() ?: 0f
            val curPres = data.presion.toIntOrNull() ?: 0

            // Considerar cambio si algún valor difiere (precisión simple)
            curMm1 != origMm1 || curMm2 != origMm2 || curMm3 != origMm3 || curMm4 != origMm4 || curPres != origPres
        }
    }

    // Extraer la lógica de registro para poder invocarla desde el botón o desde el diálogo
    fun performRegistration() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            try {
                // Validar que todas las llantas tengan parámetros
                val parametrosResult = repository.getParametrosByFlotaId(vehiculo.Flotas_idFlotas)
                if (parametrosResult.isFailure) {
                    isLoading = false
                    errorMessage = "No se pudieron cargar los parámetros de la flota"
                    return@launch
                }

                val parametros = parametrosResult.getOrNull() ?: emptyList()

                // Validar todas las llantas activas (no terminadas) y navegar si alguna carece de parámetros
                val missingLlantaPairs = llantasData.mapNotNull { data ->
                    if (!data.pTerminada) {
                        val llanta = displayedLlantas.firstOrNull { it?.idLlantasVehiculos == data.llantaVehiculoId }
                        val medida = llanta?.LlantasMedida
                        val llantaCatalogId = llanta?.Llantas_idLlantas
                        if (medida != null && llantaCatalogId != null && parametros.none { it.LlantasMedida == medida }) {
                            Pair(llantaCatalogId, medida)
                        } else null
                    } else null
                }

                val missingLlantaIds = missingLlantaPairs.distinctBy { it.second }.map { it.first }

                if (missingLlantaIds.isNotEmpty()) {
                    isLoading = false
                    errorMessage = null
                    suggestedLlantaIdsForParametros = missingLlantaIds
                    showParametrosDialog = true
                    return@launch
                }

                val fechaActual = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd")
                val odometroActualFloat = odometroActual.text.replace(",", "").toFloatOrNull() ?: 0f
                val loc = getLastKnownLocation()

                val pruebaRequest = PruebaRendimientoCreateRequest(
                    Vehiculos_idVehiculos = vehiculo.idVehiculos,
                    PruebaRendimientoFecha = fechaActual,
                    PruebaRendimientoOdometro = odometroActualFloat,
                    Usuarios_idUsuarios = currentUser?.idUsuarios,
                    latitude = loc?.latitude,
                    longitude = loc?.longitude
                )

                val pruebaResult = repository.createPruebaRendimiento(pruebaRequest)

                if (pruebaResult.isFailure) {
                    errorMessage = ErrorUtils.userMessage(pruebaResult.exceptionOrNull(), "No se pudo crear la prueba")
                    isLoading = false
                    return@launch
                }

                val createdPrueba = pruebaResult.getOrNull()
                val pruebaId = createdPrueba?.idPruebaRendimiento ?: 0

                if (pruebaId == 0) {
                    errorMessage = "Error: No se pudo obtener el ID de la prueba"
                    isLoading = false
                    return@launch
                }

                val hasTerminadaInForm = llantasData.any { it.pTerminada }
                if (hasTerminadaInForm) {
                    try {
                        repository.updateVehiculoTerminada(vehiculo.idVehiculos, true)
                    } catch (e: Exception) {
                        println("Warning: updateVehiculoTerminada(true) failed: ${e.message}")
                    }
                } else {
                    try {
                        repository.updateVehiculoTerminada(vehiculo.idVehiculos, false)
                    } catch (e: Exception) {
                        println("Warning: updateVehiculoTerminada(false) failed: ${e.message}")
                    }
                }

                // Solo crear rendimientos para llantas que siguen instaladas.
                // Si una llanta fue retirada y eliminada de llantasvehiculos,
                // no debe enviarse al batch porque rompe la FK.
                val installedIds = displayedLlantas.mapNotNull { it?.idLlantasVehiculos }.toSet()
                val toCreate = llantasData.filter {
                    it.llantaVehiculoId != 0 && it.llantaVehiculoId in installedIds
                }

                if (toCreate.isNotEmpty()) {
                    val llantasRequests = toCreate.map { data ->
                        LlantaRendimientoCreateRequest(
                            PruebaRendimiento_idPruebaRendimiento = pruebaId,
                            LlantasVehiculos_idLlantasVehiculos = data.llantaVehiculoId,
                            LlantasRendimientoMm1 = data.mm1.toFloatOrNull() ?: 0f,
                            LlantasRendimientoMm2 = data.mm2.toFloatOrNull() ?: 0f,
                            LlantasRendimientoMm3 = data.mm3.toFloatOrNull() ?: 0f,
                            LlantasRendimientoMm4 = data.mm4.toFloatOrNull() ?: 0f,
                            LlantasRendimientoPresion = data.presion.toIntOrNull() ?: 0,
                            LlantasRendimientoCondPel = data.condPel,
                            LlantasRendimientoFoto = data.foto,
                            LlantasRendimientoPTerminada = if (data.pTerminada) 1 else 0,
                            LlantasRendimientoComent = data.comentarios.ifBlank { "Ninguno" },
                            LlantasRendimientoDesgaste = data.desgaste,
                            LlantasRendimientoCausaRetiro = data.causaRetiro
                        )
                    }

                    val llantasResult = repository.createMultipleLlantasRendimiento(llantasRequests)

                    if (llantasResult.isFailure) {
                        errorMessage = ErrorUtils.userMessage(llantasResult.exceptionOrNull(), "No se pudieron guardar las llantas")
                        isLoading = false
                        return@launch
                    }
                }

                onPruebaRegistrada()

            } catch (e: Exception) {
                errorMessage = ErrorUtils.userMessage(e, "Error inesperado")
            } finally {
                isLoading = false
            }
        }
    }

    // Cargar permisos del usuario actual
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            coroutineScope.launch {
                repository.getPermisosByPerfilId(user.PerfilesUsuario_idPerfilesUsuario)
                    .onSuccess { result ->
                        userPermissions = result
                        canTerminatePrueba = result.any { it.PermisosNombre == "Terminar prueba" }
                    }
                    .onFailure {
                        canTerminatePrueba = false
                    }
            }
        }
    }

    // Cargar históricos de forma estricta: primero intentar obtener llantasrendimiento en batch.
    // Incluir llantasVehiculo.size como dependencia para reiniciar cuando cambia la cantidad de llantas
    LaunchedEffect(vehiculo.idVehiculos, llantasVehiculo.size) {
        try {
            isLoading = true
            val ids = llantasVehiculo.map { it.idLlantasVehiculos }

            // 1) Try to get authoritative form data from repository helper (batch, strict priority)
            // Fetch the "ultimos" map early so we can prefer the last saved comment when present,
            // even if getInitialFormDataForAllLlantas returns an initial (cached) list.
            val lastMapResEarly = try { repository.getUltimosLlantasRendimientoMap(ids) } catch (_: Exception) { Result.success(emptyMap()) }
            val lastMapEarly = lastMapResEarly.getOrNull() ?: emptyMap()

            try {
                val initial = repository.getInitialFormDataForAllLlantas(vehiculo.idVehiculos)
                if (initial.isNotEmpty()) {
                    // Override comentarios with the last recorded comment when available
                    llantasData = initial.map { df ->
                        val last = lastMapEarly[df.llantaVehiculoId]
                        if (last != null && !last.LlantasRendimientoComent.isNullOrBlank()) {
                            df.copy(comentarios = last.LlantasRendimientoComent)
                        } else df
                    }
                } else {
                    // If repository returned empty (no data or error), fall back to using the lastMap
                    llantasData = llantasVehiculo.map { lv ->
                        val last = lastMapEarly[lv.idLlantasVehiculos]
                        if (last != null) {
                            LlantaRendimientoFormData(
                                llantaVehiculoId = lv.idLlantasVehiculos,
                                mm1 = last.LlantasRendimientoMm1.toString(),
                                mm2 = last.LlantasRendimientoMm2.toString(),
                                mm3 = last.LlantasRendimientoMm3.toString(),
                                mm4 = last.LlantasRendimientoMm4.toString(),
                                presion = last.LlantasRendimientoPresion.toString(),
                                condPel = last.LlantasRendimientoCondPel,
                                pTerminada = (last.LlantasRendimientoPTerminada == 1),
                                foto = last.LlantasRendimientoFoto,
                                comentarios = last.LlantasRendimientoComent ?: "Ninguno"
                            )
                        } else {
                            LlantaRendimientoFormData(
                                llantaVehiculoId = lv.idLlantasVehiculos,
                                mm1 = lv.LlantasVehiculosMM1.toString(),
                                mm2 = lv.LlantasVehiculosMM2.toString(),
                                mm3 = lv.LlantasVehiculosMM3.toString(),
                                mm4 = lv.LlantasVehiculosMM4.toString(),
                                presion = lv.LlantasVehiculosPresion.toString(),
                                condPel = false,
                                pTerminada = false,
                                comentarios = "Ninguno"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // On unexpected errors, build fallback list strictly per rule using ultimos map when possible
                val lastMap = lastMapEarly
                llantasData = llantasVehiculo.map { lv ->
                    val last = lastMap[lv.idLlantasVehiculos]
                    if (last != null) {
                        LlantaRendimientoFormData(
                            llantaVehiculoId = lv.idLlantasVehiculos,
                            mm1 = last.LlantasRendimientoMm1.toString(),
                            mm2 = last.LlantasRendimientoMm2.toString(),
                            mm3 = last.LlantasRendimientoMm3.toString(),
                            mm4 = last.LlantasRendimientoMm4.toString(),
                            presion = last.LlantasRendimientoPresion.toString(),
                            condPel = last.LlantasRendimientoCondPel,
                            pTerminada = (last.LlantasRendimientoPTerminada == 1),
                            foto = last.LlantasRendimientoFoto,
                            comentarios = last.LlantasRendimientoComent ?: "Ninguno"
                        )
                    } else {
                        LlantaRendimientoFormData(
                            llantaVehiculoId = lv.idLlantasVehiculos,
                            mm1 = lv.LlantasVehiculosMM1.toString(),
                            mm2 = lv.LlantasVehiculosMM2.toString(),
                            mm3 = lv.LlantasVehiculosMM3.toString(),
                            mm4 = lv.LlantasVehiculosMM4.toString(),
                            presion = lv.LlantasVehiculosPresion.toString(),
                            condPel = false,
                            pTerminada = false,
                            comentarios = "Ninguno"
                        )
                    }
                }
            }

            // Also populate lastRendimientoMap used by form clamps/helpers — non-authoritative auxiliary data.
            try {
                val mapRes2 = repository.getUltimosLlantasRendimientoMap(ids)
                lastRendimientoMap = mapRes2.getOrNull() ?: emptyMap()
            } catch (_: Exception) {
                lastRendimientoMap = emptyMap()
            }

            // Optionally fetch PruebaRendimiento entries for km calculations
            try {
                val pruebaIds = lastRendimientoMap.values.mapNotNull { it.PruebaRendimiento_idPruebaRendimiento }.distinct()
                val pruebaMapMutable = mutableMapOf<Int, PruebaRendimiento>()
                pruebaIds.forEach { pid ->
                    try {
                        repository.getPruebaRendimientoById(pid).getOrNull()?.let { pruebaMapMutable[pid] = it }
                    } catch (_: Exception) {}
                }
                lastPruebaMap = pruebaMapMutable
            } catch (_: Exception) {
                lastPruebaMap = emptyMap()
            }

            readyToRender = true
        } finally {
            isLoading = false
        }
    }

    // When opening this screen, ensure we fetch the latest prueba for this vehicle in case repository hasn't published it yet
    LaunchedEffect(vehiculo.idVehiculos) {
        try {
            repository.getUltimoPruebaRendimientoByVehiculo(vehiculo.idVehiculos)
                .onSuccess { prueba ->
                    if (prueba != null) {
                        repository.publishVehiculoOdometerLocal(vehiculo.idVehiculos, prueba.PruebaRendimientoOdometro)
                    }
                }
        } catch (_: Exception) {
            // ignore fetch errors
        }
    }

    // Diálogo para actualizar odómetro
    if (showOdometroDialog) {
        Dialog(onDismissRequest = { /* No permitir cerrar sin ingresar odómetro desde fuera; use la X si desea volver al valor anterior */ }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Close 'X' at top-right — when pressed, restore the previous odometer value and close the dialog
                    IconButton(
                        onClick = {
                            // Restore odometer to the previous known value and close
                            odometroActual = TextFieldValue(formatWithComma(initialOdo))
                            showOdometroDialog = false
                            errorMessage = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier.padding(start = 16.dp, top = 40.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Actualizar Odómetro",
                            style = MaterialTheme.typography.titleLarge
                        )

                        OutlinedTextField(
                            value = odometroActual,
                            onValueChange = { newValue: TextFieldValue ->
                                // Build digits-only string from the incoming text
                                val newDigits = newValue.text.filter { ch -> ch.isDigit() }

                                if (newDigits.isBlank()) {
                                    odometroActual = TextFieldValue("")
                                    return@OutlinedTextField
                                }

                                val formatted = formatDigits(newDigits)

                                // Count digits to the left of the incoming cursor position
                                val digitsBeforeCursor = newValue.text
                                    .take(newValue.selection.start)
                                    .count { it.isDigit() }

                                // Map digitsBeforeCursor to a selection index in the formatted string
                                var seen = 0
                                var newCursor = 0
                                if (digitsBeforeCursor <= 0) {
                                    newCursor = 0
                                } else {
                                    for (i in formatted.indices) {
                                        if (formatted[i].isDigit()) seen++
                                        if (seen == digitsBeforeCursor) {
                                            newCursor = i + 1
                                            break
                                        }
                                    }
                                    if (seen < digitsBeforeCursor) newCursor = formatted.length
                                }

                                odometroActual = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(newCursor)
                                )
                            },
                            label = { Text("Odómetro actual (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                val digits = odometroActual.text.replace(",", "")
                                val parsedInput = digits.toFloatOrNull()
                                if (odometroActual.text.isNotBlank() && parsedInput != null) {
                                    // Compare as floats to keep consistency with vehicle stored odometer (which is Float)
                                    val currentVehiculoOdo = vehiculoOdometerUpdates[vehiculo.idVehiculos] ?: vehiculo.VehiculosOdometro
                                    if (parsedInput >= currentVehiculoOdo) {
                                        errorMessage = null
                                        showOdometroDialog = false
                                        // Ahora que el odómetro fue actualizado/confirmado por el usuario,
                                        // proceder con el registro de la prueba
                                        performRegistration()
                                    } else {
                                        errorMessage = "El odómetro no puede ser menor al actual (${formatWithComma(currentVehiculoOdo)} km)"
                                    }
                                } else {
                                    errorMessage = "Ingrese un valor válido para el odómetro"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continuar")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prueba de Rendimiento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardDefaults.shape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Title with both 'Vehículo:' and the number bolded
                    val titleAnnotated = androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        append("Vehículo: ")
                        append(vehiculo.VehiculosNumero.toString())
                        pop()
                    }

                    // Slightly smaller than titleLarge
                    val headerTextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.97f)

                    Text(
                        text = titleAnnotated,
                        style = headerTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val displayOdometro = odometroActual.text.toIntOrNull()?.let { formatWithComma(it.toFloat()) } ?: odometroActual.text
                    Text(
                        text = "Odómetro actual: $displayOdometro km",
                        style = headerTextStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Compute displayed odometer as float for use in forms (current odometer input)
            val displayedOdometerFloat = odometroActual.text.replace(",", "").toFloatOrNull() ?: initialOdo

            // Formularios para cada llanta — render only after authoritative data is ready to avoid flicker
            if (!readyToRender) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                displayedLlantas.forEachIndexed { index, llanta ->
                    if (llanta == null) {
                        // Slot para montar nueva llanta en esta posición
                        NewLlantaSlotForm(
                            slotLabel = llantasData.getOrNull(index)?.piso?.takeIf { it.isNotBlank() }
                                ?: "Pos ${index + 1}",
                            vehiculoId = vehiculo.idVehiculos,
                            flota = flota,
                            repository = repository,
                            initialFormData = llantasData.getOrNull(index) ?: LlantaRendimientoFormData(),
                            onLlantaCreated = { created ->
                                // Reemplazar slot nulo con la llanta creada
                                displayedLlantas = displayedLlantas.toMutableList().apply { this[index] = created }
                                // Asegurar que llantasData tenga una entrada inicial para esta posición
                                llantasData = llantasData.toMutableList().apply {
                                    while (size <= index) add(LlantaRendimientoFormData())
                                    this[index] = LlantaRendimientoFormData(
                                        llantaVehiculoId = created.idLlantasVehiculos,
                                        mm1 = created.LlantasVehiculosMM1.toString(),
                                        mm2 = created.LlantasVehiculosMM2.toString(),
                                        mm3 = created.LlantasVehiculosMM3.toString(),
                                        mm4 = created.LlantasVehiculosMM4.toString(),
                                        presion = created.LlantasVehiculosPresion.toString(),
                                        piso = created.LlantasVehiculosPiso
                                    )
                                }
                            }
                        )
                    } else {
                        val currentData = llantasData.getOrNull(index) ?: LlantaRendimientoFormData(llantaVehiculoId = llanta.idLlantasVehiculos)
                        val last = lastRendimientoMap[llanta.idLlantasVehiculos]
                        LlantaRendimientoForm(
                            index = index + 1,
                            llanta = llanta,
                            data = currentData,
                            lastRecorded = last,
                            repository = repository,
                            canTerminatePrueba = canTerminatePrueba,
                            showValidationErrors = validationAttempted,
                            onRequestTerminarConfirm = { id ->
                                llantaIdPendienteTerminar = id
                                showConfirmTerminarDialog = true
                            },
                            onOpenBitacora = onOpenBitacora,
                            onOpenLlantasAdmin = { showLlantasAdmin = true },
                            onDataChange = { newData ->
                                llantasData = llantasData.toMutableList().apply {
                                    while (size <= index) add(LlantaRendimientoFormData())
                                    this[index] = newData
                                }
                            }
                        )
                    }

                    if (index < displayedLlantas.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            thickness = 2.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    validationAttempted = true
                    // Validar todos los formularios
                    val invalidIndex = llantasData.indexOfFirst { !it.isValid() }
                    if (invalidIndex != -1) {
                        errorMessage = "Complete todos los campos de la llanta ${invalidIndex + 1}"
                        return@Button
                    }

                    // Si se han modificado mm o presión, pedir odómetro antes de guardar
                    if (hasMeasurementsChanged()) {
                        showOdometroDialog = true
                        return@Button
                    }

                    // Si no hubo cambios en medidas/presión, proceder a registrar sin pedir odómetro
                    performRegistration()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading && !showOdometroDialog
            ) {
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registrando prueba...")
                    }
                } else {
                    Text("Registrar Prueba", style = MaterialTheme.typography.titleMedium)
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Cancelar")
            }
        }
    }

    // Dialog para LlantasAdmin
    if (showLlantasAdmin) {
        Dialog(
            onDismissRequest = { showLlantasAdmin = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                LlantasAdminScreen(
                    repository = repository,
                    onBack = { showLlantasAdmin = false }
                )
            }
        }
    }

    // Dialog de confirmación para marcar como Terminada
    if (showConfirmTerminarDialog && llantaIdPendienteTerminar != null) {
        AlertDialog(
            onDismissRequest = { showConfirmTerminarDialog = false },
            title = { Text("Confirmar") },
            text = { Text("¿Estás seguro de terminar la prueba para esta llanta?") },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        // Abrir diálogo para pedir causa de retiro antes de marcar terminada
                        showConfirmTerminarDialog = false
                        causaRetiroText = ""
                        showCausaRetiroDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Terminar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmTerminarDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Dialog para ParametrosListScreen
    if (showParametrosDialog) {
        Dialog(
            onDismissRequest = { showParametrosDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                ParametrosListScreen(
                    repository = repository,
                    flota = flota,
                    onParametroClick = { /* no-op - abrir en Dialog */ },
                    onAddParametroClick = { llantaId ->
                        selectedLlantaIdForParametro = llantaId
                        showEditParametroDialog = true
                    },
                    onBack = { showParametrosDialog = false },
                    suggestedLlantaIds = suggestedLlantaIdsForParametros
                )
            }
        }
    }

    // Dialog para indicar causa de retiro cuando se marca una llanta como terminada
    if (showCausaRetiroDialog && llantaIdPendienteTerminar != null) {
        Dialog(onDismissRequest = { showCausaRetiroDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Causa de Retiro", style = MaterialTheme.typography.titleLarge)
                    ExposedDropdownMenuBox(
                        expanded = showCausaRetiroDropdown,
                        onExpandedChange = { showCausaRetiroDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = causaRetiroText,
                            onValueChange = {},
                            label = { Text("Causa de retiro") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCausaRetiroDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = true
                        )
                        ExposedDropdownMenu(
                            expanded = showCausaRetiroDropdown,
                            onDismissRequest = { showCausaRetiroDropdown = false }
                        ) {
                            listOf(
                                "Impacto",
                                "Desgaste irregular excesivo",
                                "Presión de inflado insuficiente y/o sobrecarga",
                                "Rodada baja",
                                "Falla de la llanta",
                                "Otro"
                            ).forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        causaRetiroText = opcion
                                        showCausaRetiroDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showCausaRetiroDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancelar")
                        }
                        Button(onClick = {
                            // Aplicar la terminación en UI y crear espacio para nueva llanta
                            val id = llantaIdPendienteTerminar!!
                            val idx = llantasData.indexOfFirst { it.llantaVehiculoId == id }
                            if (idx != -1) {
                                llantasData = llantasData.toMutableList().apply {
                                    this[idx] = this[idx].copy(pTerminada = true, causaRetiro = causaRetiroText.ifBlank { null })
                                }
                            }
                            // Reemplazar la llanta en la lista mostrada por un slot vacío (null)
                            val pos = displayedLlantas.indexOfFirst { it?.idLlantasVehiculos == id }
                            val originalLlanta = if (pos != -1) displayedLlantas[pos] else null
                            if (pos != -1) {
                                displayedLlantas = displayedLlantas.toMutableList().apply { this[pos] = null }
                                llantasData = llantasData.toMutableList().apply {
                                    while (size <= pos) add(LlantaRendimientoFormData())
                                    this[pos] = LlantaRendimientoFormData(
                                        llantaVehiculoId = 0,
                                        piso = "Pos ${pos + 1}",
                                        replacedLlantaVehiculoId = originalLlanta?.idLlantasVehiculos
                                    )
                                }
                            }

                            // Persistir la eliminación en backend para que al recargar la llanta no vuelva a aparecer
                            coroutineScope.launch {
                                isLoading = true
                                val usuarioId = currentUser?.idUsuarios
                                val retireRes = repository.retirarLlantaVehiculo(id, causaRetiroText.ifBlank { null }, usuarioId, null)
                                isLoading = false
                                if (retireRes.isFailure) {
                                    // Revertir UI si falla
                                    errorMessage = retireRes.exceptionOrNull()?.message ?: "No se pudo retirar la llanta del vehículo en el servidor"
                                    if (pos != -1 && originalLlanta != null) {
                                        displayedLlantas = displayedLlantas.toMutableList().apply { this[pos] = originalLlanta }
                                        // revertir pTerminada en llantasData
                                        val idx2 = llantasData.indexOfFirst { it.llantaVehiculoId == id }
                                        if (idx2 != -1) {
                                            llantasData = llantasData.toMutableList().apply { this[idx2] = this[idx2].copy(pTerminada = false, causaRetiro = null) }
                                        }
                                    }
                                }
                            }

                            // Cerrar dialog
                            showCausaRetiroDialog = false
                            llantaIdPendienteTerminar = null
                        }, modifier = Modifier.weight(1f)) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }
    
    // Dialog para EditParametroScreen
    if (showEditParametroDialog && selectedLlantaIdForParametro != null) {
        Dialog(
            onDismissRequest = { showEditParametroDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                EditParametroScreen(
                    repository = repository,
                    flota = flota,
                    llantaId = selectedLlantaIdForParametro!!,
                    parametro = null,
                    onParametroSaved = {
                        showEditParametroDialog = false
                        // Recargar parámetros en ParametrosListScreen
                        showParametrosDialog = true
                    },
                    onBack = { showEditParametroDialog = false },
                    onHome = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlantaRendimientoForm(
    index: Int,
    llanta: LlantaVehiculo,
    data: LlantaRendimientoFormData,
    lastRecorded: LlantaRendimiento?,
    repository: YokohamaRepository,
    canTerminatePrueba: Boolean,
    showValidationErrors: Boolean,
    onRequestTerminarConfirm: (Int) -> Unit,
    onDataChange: (LlantaRendimientoFormData) -> Unit,
    onOpenBitacora: (llantaVehiculo: com.megatransportes.yokoh.data.models.LlantaVehiculo) -> Unit = {},
    onOpenLlantasAdmin: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoadingFile by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    var showPhotoPickerDialog by remember { mutableStateOf(false) }
    // State for updating the "Terminada" flag on the server
    val terminadaUpdating = remember { mutableStateOf(false) }
    var terminadaError by remember { mutableStateOf<String?>(null) }
    
    // Crear FilePickerUtils para la plataforma actual
    val filePickerUtils = remember { createFilePickerUtils() }
    
    // 'condPel' (Condición peligrosa) is now manual-only and not derived from MM values.
    // Focus requesters for MM inputs so IME Next/Done moves between fields
    val focusRequester1 = remember { FocusRequester() }
    val focusRequester2 = remember { FocusRequester() }
    val focusRequester3 = remember { FocusRequester() }
    val focusRequester4 = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var showExtras by remember { mutableStateOf(false) }

    var presionState by remember { mutableStateOf(TextFieldValue(data.presion)) }
    var presionFocused by remember { mutableStateOf(false) }
    LaunchedEffect(data.presion) { if (data.presion != presionState.text) presionState = TextFieldValue(data.presion) }
    LaunchedEffect(presionFocused) { if (presionFocused) presionState = presionState.copy(selection = TextRange(0, presionState.text.length)) }

    var comentariosState by remember { mutableStateOf(TextFieldValue(data.comentarios)) }
    var comentariosFocused by remember { mutableStateOf(false) }
    LaunchedEffect(data.comentarios) { if (data.comentarios != comentariosState.text) comentariosState = TextFieldValue(data.comentarios) }
    LaunchedEffect(comentariosFocused) { if (comentariosFocused) comentariosState = comentariosState.copy(selection = TextRange(0, comentariosState.text.length)) }

    // MM shared state lifted so the single mic (before Presión) can update them without overwriting others
    var mm1State by remember { mutableStateOf(TextFieldValue(data.mm1)) }
    var mm2State by remember { mutableStateOf(TextFieldValue(data.mm2)) }
    var mm3State by remember { mutableStateOf(TextFieldValue(data.mm3)) }
    var mm4State by remember { mutableStateOf(TextFieldValue(data.mm4)) }
    var mm1Focused by remember { mutableStateOf(false) }
    var mm2Focused by remember { mutableStateOf(false) }
    var mm3Focused by remember { mutableStateOf(false) }
    var mm4Focused by remember { mutableStateOf(false) }
    LaunchedEffect(data.mm1) { if (data.mm1 != mm1State.text) mm1State = TextFieldValue(data.mm1) }
    LaunchedEffect(mm1Focused) { if (mm1Focused) mm1State = mm1State.copy(selection = TextRange(0, mm1State.text.length)) }
    LaunchedEffect(data.mm2) { if (data.mm2 != mm2State.text) mm2State = TextFieldValue(data.mm2) }
    LaunchedEffect(mm2Focused) { if (mm2Focused) mm2State = mm2State.copy(selection = TextRange(0, mm2State.text.length)) }
    LaunchedEffect(data.mm3) { if (data.mm3 != mm3State.text) mm3State = TextFieldValue(data.mm3) }
    LaunchedEffect(mm3Focused) { if (mm3Focused) mm3State = mm3State.copy(selection = TextRange(0, mm3State.text.length)) }
    LaunchedEffect(data.mm4) { if (data.mm4 != mm4State.text) mm4State = TextFieldValue(data.mm4) }
    LaunchedEffect(mm4Focused) { if (mm4Focused) mm4State = mm4State.copy(selection = TextRange(0, mm4State.text.length)) }

    val formBorderColor = when {
        data.isValid() -> Color(0xFF2E7D32)
        showValidationErrors -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, formBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Main content (will be dimmed when pTerminada is active)
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .alpha(if (data.pTerminada) 0.3f else 1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // (Header moved out of the dimmed area so it remains visible when pTerminada is active)

                val llantaLabel = "${llanta.LlantasMarca} ${llanta.LlantasModelo} - ${llanta.LlantasVehiculosNoQuemado}"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "$index", style = MaterialTheme.typography.bodyMedium)
                    }
                    CompactOutlinedTextField(
                        value = TextFieldValue(llantaLabel),
                        onValueChange = {},
                        label = "Seleccionar Llanta",
                        readOnly = true,
                        modifier = Modifier.weight(1f).height(56.dp),
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    if (onOpenLlantasAdmin != null) {
                        IconButton(onClick = onOpenLlantasAdmin, modifier = Modifier.size(56.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Administrar llantas",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canTerminatePrueba) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                    .background(
                                        if (data.pTerminada) MaterialTheme.colorScheme.error else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable {
                                        val newValue = !data.pTerminada
                                        if (newValue && !data.pTerminada) {
                                            // Mostrar confirmación solo cuando se intenta activar
                                            onRequestTerminarConfirm(llanta.idLlantasVehiculos)
                                        } else {
                                            // Desactivar sin confirmación
                                            onDataChange(data.copy(pTerminada = newValue))
                                            terminadaError = null
                                            if (lastRecorded != null) {
                                                coroutineScope.launch {
                                                    terminadaUpdating.value = true
                                                    try {
                                                        val llantaVehiculoId = lastRecorded.LlantasVehiculos_idLlantasVehiculos ?: llanta.idLlantasVehiculos
                                                        val res = repository.updateUltimaLlantaRendimiento(llantaVehiculoId, newValue)
                                                        if (res.isFailure) {
                                                            onDataChange(data.copy(pTerminada = !newValue))
                                                            terminadaError = ErrorUtils.userMessage(res.exceptionOrNull(), "No se pudo actualizar estado Terminada")
                                                        }
                                                    } finally {
                                                        terminadaUpdating.value = false
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "T",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (data.pTerminada) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onOpenBitacora(llanta) }) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = "Ver bitácora",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (terminadaUpdating.value) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (terminadaError != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = terminadaError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { showPhotoPickerDialog = true }, enabled = !data.pTerminada) {
                            if (isLoadingFile) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = "Seleccionar imagen",
                                    tint = if (data.foto?.isNotBlank() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (data.foto != null) {
                            IconButton(onClick = {
                                onDataChange(data.copy(foto = null, fotoNombre = null, fotoTamano = null))
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Eliminar foto")
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val _ctx = getPlatformContext()
                            MicButton(
                                fields = listOf(
                                    FieldDescriptor(title = "Presión", type = FieldType.NUMBER, onFill = { value ->
                                        presionState = TextFieldValue(value, selection = TextRange(value.length))
                                        onDataChange(data.copy(presion = value, mm1 = mm1State.text, mm2 = mm2State.text, mm3 = mm3State.text, mm4 = mm4State.text))
                                    }),
                                    FieldDescriptor(title = "MM1", type = FieldType.NUMBER, maxValue = min(lastRecorded?.LlantasRendimientoMm1 ?: llanta.LlantasVehiculosMM1, 25.4f), onFill = { v ->
                                        mm1State = TextFieldValue(v)
                                        onDataChange(data.copy(mm1 = v, mm2 = mm2State.text, mm3 = mm3State.text, mm4 = mm4State.text, presion = presionState.text))
                                    }),
                                    FieldDescriptor(title = "MM2", type = FieldType.NUMBER, maxValue = min(lastRecorded?.LlantasRendimientoMm2 ?: llanta.LlantasVehiculosMM2, 25.4f), onFill = { v ->
                                        mm2State = TextFieldValue(v)
                                        onDataChange(data.copy(mm1 = mm1State.text, mm2 = v, mm3 = mm3State.text, mm4 = mm4State.text, presion = presionState.text))
                                    }),
                                    FieldDescriptor(title = "MM3", type = FieldType.NUMBER, maxValue = min(lastRecorded?.LlantasRendimientoMm3 ?: llanta.LlantasVehiculosMM3, 25.4f), onFill = { v ->
                                        mm3State = TextFieldValue(v)
                                        onDataChange(data.copy(mm1 = mm1State.text, mm2 = mm2State.text, mm3 = v, mm4 = mm4State.text, presion = presionState.text))
                                    }),
                                    FieldDescriptor(title = "MM4", type = FieldType.NUMBER, maxValue = min(lastRecorded?.LlantasRendimientoMm4 ?: llanta.LlantasVehiculosMM4, 25.4f), onFill = { v ->
                                        mm4State = TextFieldValue(v)
                                        onDataChange(data.copy(mm1 = mm1State.text, mm2 = mm2State.text, mm3 = mm3State.text, mm4 = v, presion = presionState.text))
                                    }),
                                    // Desgaste (dropdown) - provide choices so parser can pick matching option
                                    FieldDescriptor(
                                        title = "Desgaste",
                                        type = FieldType.TEXT,
                                        choices = listOf(
                                            "A. SIN DESGASTE IRREGULAR",
                                            "B. CON DESGASTE IRREGULAR",
                                            "C. DESGASTE ESCALONADO EN EL HOMBRO",
                                            "D. DESGASTE COMPLETO DE HOMBRO",
                                            "E. DESGASTE UNILATERAL (CAMBER)",
                                            "F. DESGASTE TIPO CONTRAPELO (CONVERGENCIA)",
                                            "G. DESGASTE TIPO RIO/EROSION",
                                            "H. DESGASTE DE COSTILLAS (DEPRESIONES)",
                                            "I. DESGASTE PUNTA-TALÓN",
                                            "J. DESGASTE PREMATURO",
                                            "K. DESGASTE POR FRENADO DE PÁNICO",
                                            "L. DESGASTE DIAGONAL",
                                            "M. DESGASTE POR PRESIÓN INSUFICIENTE",
                                            "N. DESGASTE POR SOBREINFLADO",
                                            "O. DESGASTE ONDULADO EN HOMBRO",
                                            "P. DESGASTE ALTERNADO DE BLOQUES",
                                            "Q. DESGASTE EN COSTILLAS (DEPRESIÓN ALTERNADA)",
                                            "R. DESGASTE EXCÉNTRICO"
                                        ),
                                        onFill = { chosen ->
                                            onDataChange(data.copy(desgaste = chosen, mm1 = mm1State.text, mm2 = mm2State.text, mm3 = mm3State.text, mm4 = mm4State.text, presion = presionState.text))
                                        }
                                    ),
                                    // Condición peligrosa (checkbox) - accept yes/no words
                                    FieldDescriptor(title = "Condición peligrosa", type = FieldType.TEXT, onFill = { v ->
                                        val low = v.lowercase()
                                        val checked = listOf("sí", "si", "s", "yes", "activar", "marcar", "true").any { low.contains(it) }
                                        val unchecked = listOf("no", "desactivar", "desmarcar", "false").any { low.contains(it) }
                                        val final = when {
                                            checked -> true
                                            unchecked -> false
                                            else -> null
                                        }
                                        if (final != null) onDataChange(data.copy(condPel = final, mm1 = mm1State.text, mm2 = mm2State.text, mm3 = mm3State.text, mm4 = mm4State.text, presion = presionState.text))
                                    }),
                                    // Comentarios (free text)
                                    FieldDescriptor(title = "Comentarios", type = FieldType.TEXT, onFill = { txt ->
                                        onDataChange(data.copy(comentarios = txt, mm1 = mm1State.text, mm2 = mm2State.text, mm3 = mm3State.text, mm4 = mm4State.text, presion = presionState.text))
                                    })
                                ),
                                modifier = Modifier.size(40.dp),
                                startListeningAction = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.start(_ctx) },
                                stopListening = { com.megatransportes.yokoh.utils.SpeechRecognitionManager.stopAndGet() }
                            )

                        CompactOutlinedTextField(
                            value = presionState,
                            onValueChange = {
                                val newTextRaw = it.text
                                presionState = TextFieldValue(newTextRaw, selection = TextRange(newTextRaw.length))
                                onDataChange(data.copy(presion = newTextRaw))
                            },
                            label = "Presión",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.width(120.dp).height(56.dp).onFocusChanged { presionFocused = it.isFocused },
                            singleLine = true,
                            enabled = !data.pTerminada,
                            isError = showValidationErrors && (
                                if (data.pTerminada) {
                                    data.presion.isNotBlank() && (data.presion.toIntOrNull() == null || (data.presion.toIntOrNull() ?: 0) > 160)
                                } else {
                                    data.presion.isBlank() || data.presion.toIntOrNull() == null || (data.presion.toIntOrNull() ?: 0) > 160
                                }
                            )
                        )
                    }

                    IconButton(onClick = { showExtras = !showExtras }, enabled = !data.pTerminada) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (showExtras) "Ocultar extras" else "Mostrar extras",
                            modifier = Modifier.graphicsLayer(rotationZ = if (showExtras) 45f else 0f)
                        )
                    }
                }

                AnimatedVisibility(visible = showExtras) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Desgaste selector
                        var showDesgasteDropdown by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = showDesgasteDropdown,
                            onExpandedChange = { showDesgasteDropdown = !showDesgasteDropdown }
                        ) {
                            OutlinedTextField(
                                value = data.desgaste,
                                onValueChange = {},
                                label = { Text("Desgaste") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDesgasteDropdown) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                readOnly = true,
                                enabled = !data.pTerminada
                            )
                            ExposedDropdownMenu(
                                expanded = showDesgasteDropdown,
                                onDismissRequest = { showDesgasteDropdown = false }
                            ) {
                                listOf(
                                    "A. SIN DESGASTE IRREGULAR",
                                    "B. CON DESGASTE IRREGULAR",
                                    "C. DESGASTE ESCALONADO EN EL HOMBRO",
                                    "D. DESGASTE COMPLETO DE HOMBRO",
                                    "E. DESGASTE UNILATERAL (CAMBER)",
                                    "F. DESGASTE TIPO CONTRAPELO (CONVERGENCIA)",
                                    "G. DESGASTE TIPO RIO/EROSION",
                                    "H. DESGASTE DE COSTILLAS (DEPRESIONES)",
                                    "I. DESGASTE PUNTA-TALÓN",
                                    "J. DESGASTE PREMATURO",
                                    "K. DESGASTE POR FRENADO DE PÁNICO",
                                    "L. DESGASTE DIAGONAL",
                                    "M. DESGASTE POR PRESIÓN INSUFICIENTE",
                                    "N. DESGASTE POR SOBREINFLADO",
                                    "O. DESGASTE ONDULADO EN HOMBRO",
                                    "P. DESGASTE ALTERNADO DE BLOQUES",
                                    "Q. DESGASTE EN COSTILLAS (DEPRESIÓN ALTERNADA)",
                                    "R. DESGASTE EXCÉNTRICO"
                                ).forEach { opcion ->
                                    DropdownMenuItem(
                                        text = { Text(opcion) },
                                        onClick = {
                                            onDataChange(data.copy(desgaste = opcion))
                                            showDesgasteDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = data.condPel,
                                onCheckedChange = { onDataChange(data.copy(condPel = it)) },
                                enabled = !data.pTerminada
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Condición peligrosa", style = MaterialTheme.typography.bodyMedium)
                        }
                        CompactOutlinedTextField(
                            value = comentariosState,
                            onValueChange = {
                                val newText = it.text
                                comentariosState = TextFieldValue(newText, selection = TextRange(newText.length))
                                onDataChange(data.copy(comentarios = newText))
                            },
                            label = "Comentarios",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { showExtras = false; focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().height(56.dp).onFocusChanged { comentariosFocused = it.isFocused },
                            singleLine = true,
                            enabled = !data.pTerminada
                        )
                    }
                }

                // MM fields + helpers (existing implementation preserved)
                Column {
                    // Shared state for MM fields so MicButton can update them
                    var mm1StateLocal by remember { mutableStateOf(TextFieldValue(data.mm1)) }
                    var mm2StateLocal by remember { mutableStateOf(TextFieldValue(data.mm2)) }
                    var mm3StateLocal by remember { mutableStateOf(TextFieldValue(data.mm3)) }
                    var mm4StateLocal by remember { mutableStateOf(TextFieldValue(data.mm4)) }
                    var mm1FocusedLocal by remember { mutableStateOf(false) }
                    var mm2FocusedLocal by remember { mutableStateOf(false) }
                    var mm3FocusedLocal by remember { mutableStateOf(false) }
                    var mm4FocusedLocal by remember { mutableStateOf(false) }
                    LaunchedEffect(data.mm1) { if (data.mm1 != mm1StateLocal.text) mm1StateLocal = TextFieldValue(data.mm1) }
                    LaunchedEffect(mm1FocusedLocal) { if (mm1FocusedLocal) mm1StateLocal = mm1StateLocal.copy(selection = TextRange(0, mm1StateLocal.text.length)) }
                    LaunchedEffect(data.mm2) { if (data.mm2 != mm2StateLocal.text) mm2StateLocal = TextFieldValue(data.mm2) }
                    LaunchedEffect(mm2FocusedLocal) { if (mm2FocusedLocal) mm2StateLocal = mm2StateLocal.copy(selection = TextRange(0, mm2StateLocal.text.length)) }
                    LaunchedEffect(data.mm3) { if (data.mm3 != mm3StateLocal.text) mm3StateLocal = TextFieldValue(data.mm3) }
                    LaunchedEffect(mm3FocusedLocal) { if (mm3FocusedLocal) mm3StateLocal = mm3StateLocal.copy(selection = TextRange(0, mm3StateLocal.text.length)) }
                    LaunchedEffect(data.mm4) { if (data.mm4 != mm4StateLocal.text) mm4StateLocal = TextFieldValue(data.mm4) }
                    LaunchedEffect(mm4FocusedLocal) { if (mm4FocusedLocal) mm4StateLocal = mm4StateLocal.copy(selection = TextRange(0, mm4StateLocal.text.length)) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        // Bluetooth Caliper Button
                        BluetoothCaliperButton(
                            onMeasurementReceived = { value ->
                                // Llenar el primer campo MM vacío con la medición
                                when {
                                    data.mm1.isBlank() -> {
                                        val allowedMax = min(lastRecorded?.LlantasRendimientoMm1 ?: llanta.LlantasVehiculosMM1, 25.4f)
                                        val finalValue = if (value > allowedMax) allowedMax else value
                                        onDataChange(data.copy(mm1 = finalValue.toString()))
                                    }
                                    data.mm2.isBlank() -> {
                                        val allowedMax = min(lastRecorded?.LlantasRendimientoMm2 ?: llanta.LlantasVehiculosMM2, 25.4f)
                                        val finalValue = if (value > allowedMax) allowedMax else value
                                        onDataChange(data.copy(mm2 = finalValue.toString()))
                                    }
                                    data.mm3.isBlank() -> {
                                        val allowedMax = min(lastRecorded?.LlantasRendimientoMm3 ?: llanta.LlantasVehiculosMM3, 25.4f)
                                        val finalValue = if (value > allowedMax) allowedMax else value
                                        onDataChange(data.copy(mm3 = finalValue.toString()))
                                    }
                                    data.mm4.isBlank() -> {
                                        val allowedMax = min(lastRecorded?.LlantasRendimientoMm4 ?: llanta.LlantasVehiculosMM4, 25.4f)
                                        val finalValue = if (value > allowedMax) allowedMax else value
                                        onDataChange(data.copy(mm4 = finalValue.toString()))
                                    }
                                    else -> {
                                        // Si todos están llenos, actualizar MM1
                                        val allowedMax = min(lastRecorded?.LlantasRendimientoMm1 ?: llanta.LlantasVehiculosMM1, 25.4f)
                                        val finalValue = if (value > allowedMax) allowedMax else value
                                        onDataChange(data.copy(mm1 = finalValue.toString()))
                                    }
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        )

                        // MM1
                        CompactOutlinedTextField(
                            value = mm1StateLocal,
                            onValueChange = {
                                val allowedMax = min(lastRecorded?.LlantasRendimientoMm1 ?: llanta.LlantasVehiculosMM1, 25.4f)
                                val newText = it.text
                                val parsed = newText.toFloatOrNull()
                                val finalText = if (parsed != null && parsed > allowedMax) allowedMax.toString() else newText
                                mm1StateLocal = TextFieldValue(finalText, selection = TextRange(finalText.length))
                                onDataChange(data.copy(mm1 = finalText))
                            },
                            label = "MM",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusRequester2.requestFocus() }),
                            modifier = Modifier.weight(1f).height(56.dp).focusRequester(focusRequester1).onFocusChanged { mm1FocusedLocal = it.isFocused },
                            singleLine = true,
                            enabled = !data.pTerminada,
                            isError = showValidationErrors && (data.mm1.isBlank() || data.mm1.toFloatOrNull() == null)
                        )

                        // MM2
                        CompactOutlinedTextField(
                            value = mm2StateLocal,
                            onValueChange = {
                                val allowedMax = min(lastRecorded?.LlantasRendimientoMm2 ?: llanta.LlantasVehiculosMM2, 25.4f)
                                val newText = it.text
                                val parsed = newText.toFloatOrNull()
                                val finalText = if (parsed != null && parsed > allowedMax) allowedMax.toString() else newText
                                mm2StateLocal = TextFieldValue(finalText, selection = TextRange(finalText.length))
                                onDataChange(data.copy(mm2 = finalText))
                            },
                            label = "MM",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusRequester3.requestFocus() }),
                            modifier = Modifier.weight(1f).height(56.dp).focusRequester(focusRequester2).onFocusChanged { mm2FocusedLocal = it.isFocused },
                            singleLine = true,
                            enabled = !data.pTerminada,
                            isError = showValidationErrors && !data.pTerminada && (data.mm2.isBlank() || data.mm2.toFloatOrNull() == null)
                        )

                        // MM3
                        CompactOutlinedTextField(
                            value = mm3StateLocal,
                            onValueChange = {
                                val allowedMax = min(lastRecorded?.LlantasRendimientoMm3 ?: llanta.LlantasVehiculosMM3, 25.4f)
                                val newText = it.text
                                val parsed = newText.toFloatOrNull()
                                val finalText = if (parsed != null && parsed > allowedMax) allowedMax.toString() else newText
                                mm3StateLocal = TextFieldValue(finalText, selection = TextRange(finalText.length))
                                onDataChange(data.copy(mm3 = finalText))
                            },
                            label = "MM",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusRequester4.requestFocus() }),
                            modifier = Modifier.weight(1f).height(56.dp).focusRequester(focusRequester3).onFocusChanged { mm3FocusedLocal = it.isFocused },
                            singleLine = true,
                            enabled = !data.pTerminada,
                            isError = showValidationErrors && !data.pTerminada && (data.mm3.isBlank() || data.mm3.toFloatOrNull() == null)
                        )

                        // MM4
                        CompactOutlinedTextField(
                            value = mm4StateLocal,
                            onValueChange = {
                                val allowedMax = min(lastRecorded?.LlantasRendimientoMm4 ?: llanta.LlantasVehiculosMM4, 25.4f)
                                val newText = it.text
                                val parsed = newText.toFloatOrNull()
                                val finalText = if (parsed != null && parsed > allowedMax) allowedMax.toString() else newText
                                mm4StateLocal = TextFieldValue(finalText, selection = TextRange(finalText.length))
                                onDataChange(data.copy(mm4 = finalText))
                            },
                            label = "MM",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f).height(56.dp).focusRequester(focusRequester4).onFocusChanged { mm4FocusedLocal = it.isFocused },
                            singleLine = true,
                            enabled = !data.pTerminada,
                            isError = showValidationErrors && !data.pTerminada && (data.mm4.isBlank() || data.mm4.toFloatOrNull() == null)
                        )
                    }
                    // No mostrar helper de "Máx"; el valor se clampa automáticamente conforme a la regla estricta.

                    // Calcular diferencia entre el mayor y el menor MM para mostrar advertencia de desgaste irregular
                    val diferencia = remember(data.mm1, data.mm2, data.mm3, data.mm4) {
                        val mm1Val = data.mm1.toFloatOrNull() ?: 0f
                        val mm2Val = data.mm2.toFloatOrNull() ?: 0f
                        val mm3Val = data.mm3.toFloatOrNull() ?: 0f
                        val mm4Val = data.mm4.toFloatOrNull() ?: 0f
                        val valores = listOf(mm1Val, mm2Val, mm3Val, mm4Val)
                        val max = valores.maxOrNull() ?: 0f
                        val min = valores.minOrNull() ?: 0f
                        max - min
                    }

                    if (diferencia > 1f) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Desgaste irregular detectado",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Desgaste irregular, factor delta ${NumberFormatter.formatWithComma(diferencia, 1)} mm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Sección de archivo/foto
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {


                    // Error de archivo si existe
                    if (fileError != null) {
                        Text(
                            text = fileError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // La selección de archivo ahora está en la fila superior junto al checkbox y la presión.
                }
            }

            // Overlay that fills the entire card when pTerminada is active
            if (data.pTerminada) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Request precomputed km from repository: km = lastPrueba.PruebaRendimientoOdometro - vehiculo.VehiculosOdometro
                    val llantaVehiculoId = lastRecorded?.LlantasVehiculos_idLlantasVehiculos ?: llanta.idLlantasVehiculos
                    val kmState = produceState<Result<Float>>(initialValue = Result.success(0f), key1 = llantaVehiculoId) {
                        // repository.getKmRecorridoPorLlanta is suspend -> returns Flow<Result<Float>>
                        val flow = repository.getKmRecorridoPorLlanta(llantaVehiculoId)
                        flow.collect { value = it }
                    }
                    val kmValue = kmState.value.getOrNull() ?: 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .align(Alignment.Center)
                            .offset(y = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("KM: ${kmValue.toInt()} km", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("MM: ${data.mm1}", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("PSI: ${data.presion}", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

        }
        
        if (showPhotoPickerDialog) {
            PhotoPickerDialog(
                photoSlots = listOf(
                    PhotoSlot(
                        index = 0,
                        base64Data = data.foto ?: "",
                        fileName = data.fotoNombre ?: "",
                        fileSize = data.fotoTamano ?: 0L
                    )
                ),
                onPhotosChanged = { updatedSlots ->
                    val slot = updatedSlots.firstOrNull()
                    val base64 = slot?.base64Data
                    onDataChange(
                        data.copy(
                            foto = base64?.takeIf { it.isNotBlank() },
                            fotoNombre = slot?.fileName?.takeIf { it.isNotBlank() },
                            fotoTamano = slot?.fileSize?.takeIf { it > 0 }
                        )
                    )
                },
                onDismiss = { showPhotoPickerDialog = false },
                filePickerUtils = filePickerUtils
            )
        }
    }
}

/**
 * Formatea el tamaño del archivo en una forma legible
 */
private fun formatFileSize(sizeInBytes: Long): String {
    return when {
        sizeInBytes < 1024L -> "$sizeInBytes B"
        sizeInBytes < 1024L * 1024L -> "${sizeInBytes / 1024L} KB"
        else -> "${sizeInBytes / (1024L * 1024L)} MB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isError: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)
    val cursorColor = MaterialTheme.colorScheme.primary

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(cursorColor),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                label = { Text(label) },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                contentPadding = contentPadding,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    )
}

private fun LlantaRendimientoFormData.isValid(): Boolean {
    return if (pTerminada) {
        // If marked 'Terminada' only require a representative MM (mm1) and pressure
        mm1.isNotBlank() && mm1.toFloatOrNull() != null &&
                presion.isNotBlank() && presion.toIntOrNull() != null && (presion.toIntOrNull() ?: 0) <= 160
    } else {
        mm1.isNotBlank() && mm1.toFloatOrNull() != null &&
                mm2.isNotBlank() && mm2.toFloatOrNull() != null &&
                mm3.isNotBlank() && mm3.toFloatOrNull() != null &&
                mm4.isNotBlank() && mm4.toFloatOrNull() != null &&
                presion.isNotBlank() && presion.toIntOrNull() != null && (presion.toIntOrNull() ?: 0) <= 160
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewLlantaSlotForm(
    slotLabel: String,
    vehiculoId: Int,
    flota: Flota,
    repository: YokohamaRepository,
    initialFormData: LlantaRendimientoFormData = LlantaRendimientoFormData(),
    onLlantaCreated: (LlantaVehiculo) -> Unit
) {
    var data by remember { mutableStateOf(LlantaVehiculoFormData(piso = slotLabel)) }
    var searchText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var filteredLlantas by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var llantasCatalog by remember { mutableStateOf<List<Llanta>>(emptyList()) }
    var medidasConParametro by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Cargar catálogo de llantas y parámetros al montar
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val res = repository.getLlantasByFlota(flota.idFlotas)
                if (res.isSuccess) llantasCatalog = res.getOrNull() ?: emptyList()
                else error = "No se pudo cargar catálogo de llantas"

                val resParam = repository.getParametrosByFlotaId(flota.idFlotas)
                if (resParam.isSuccess) {
                    val parametros = resParam.getOrNull() ?: emptyList()
                    medidasConParametro = parametros.mapNotNull { it.LlantasMedida }.toSet()
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    // Filtrar llantas cuando cambie el texto de búsqueda
    LaunchedEffect(searchText) {
        if (searchText.isNotEmpty() && data.selectedLlanta == null) {
            filteredLlantas = llantasCatalog.filter { llanta ->
                llanta.LlantasMarca.contains(searchText, ignoreCase = true) ||
                llanta.LlantasModelo.contains(searchText, ignoreCase = true) ||
                llanta.LlantasMedida.toString().contains(searchText)
            }.take(10)
            showSuggestions = filteredLlantas.isNotEmpty()
        } else {
            filteredLlantas = emptyList()
            showSuggestions = false
        }
    }

    var collapsed by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$slotLabel - Espacio disponible",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { collapsed = !collapsed }) {
                    Text(
                        text = if (collapsed) "+" else "-",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!collapsed) {
            // Campo de búsqueda de llanta
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = data.selectedLlanta?.let { "${it.LlantasMarca} ${it.LlantasModelo} - ${it.LlantasMedida}\"" } ?: searchText,
                        onValueChange = { newValue ->
                            if (data.selectedLlanta == null) {
                                searchText = newValue
                            } else {
                                data = data.copy(selectedLlanta = null)
                                searchText = newValue
                            }
                        },
                        label = { Text("Buscar Llanta") },
                        placeholder = { Text("Escribe marca, modelo o medida...") },
                        trailingIcon = {
                            if (data.selectedLlanta != null) {
                                IconButton(onClick = {
                                    data = data.copy(selectedLlanta = null)
                                    searchText = ""
                                }) {
                                    Icon(Icons.Default.Clear, "Limpiar")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = data.selectedLlanta != null
                    )
                }

                // Advertencia inline si la llanta seleccionada no tiene parámetros
                data.selectedLlanta?.let { llanta ->
                    if (!medidasConParametro.contains(llanta.LlantasMedida)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Sin parámetros",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "La llanta seleccionada no tiene parámetros configurados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Sugerencias de llantas
                if (showSuggestions && data.selectedLlanta == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            filteredLlantas.forEach { llanta ->
                                TextButton(
                                    onClick = {
                                        data = data.copy(
                                            selectedLlanta = llanta,
                                            precio = "0",
                                            mm1 = llanta.LlantasMm.toString(),
                                            mm2 = llanta.LlantasMm.toString(),
                                            mm3 = llanta.LlantasMm.toString(),
                                            mm4 = llanta.LlantasMm.toString()
                                        )
                                        searchText = ""
                                        showSuggestions = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${llanta.LlantasMarca} ${llanta.LlantasModelo}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (!medidasConParametro.contains(llanta.LlantasMedida)) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "(Sin parámetros)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Medida: ${llanta.LlantasMedida}\" - Precio: $${llanta.LlantasPrecio}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (llanta != filteredLlantas.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Campo Precio
            OutlinedTextField(
                value = data.precio,
                onValueChange = { newPrecio ->
                    data = data.copy(precio = newPrecio)
                },
                label = { Text("Precio") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Radio buttons para Piso
            Column {
                Text("Piso:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = data.piso == "Original",
                            onClick = { data = data.copy(piso = "Original") }
                        )
                        Text("Original")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = data.piso == "Vitalizado",
                            onClick = { data = data.copy(piso = "Vitalizado") }
                        )
                        Text("Vitalizado")
                    }
                }
            }

            // No. Quemado
            OutlinedTextField(
                value = data.noQuemado,
                onValueChange = { onValueChange ->
                    data = data.copy(noQuemado = onValueChange)
                },
                label = { Text("No. Quemado / DOT") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Presión
            OutlinedTextField(
                value = data.presion,
                onValueChange = {
                    val raw = it
                    val num = raw.toFloatOrNull()
                    val clamped = if (num != null && num > 160f) "160" else raw
                    data = data.copy(presion = clamped)
                },
                label = { Text("Presión") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Campos MM en una fila
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = data.mm1,
                    onValueChange = { v ->
                        val p = v.toFloatOrNull()
                        data = data.copy(mm1 = if (p != null && p > 25.4f) "25.4" else v)
                    },
                    label = { Text("MM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = data.mm2,
                    onValueChange = { v ->
                        val p = v.toFloatOrNull()
                        data = data.copy(mm2 = if (p != null && p > 25.4f) "25.4" else v)
                    },
                    label = { Text("MM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = data.mm3,
                    onValueChange = { v ->
                        val p = v.toFloatOrNull()
                        data = data.copy(mm3 = if (p != null && p > 25.4f) "25.4" else v)
                    },
                    label = { Text("MM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = data.mm4,
                    onValueChange = { v ->
                        val p = v.toFloatOrNull()
                        data = data.copy(mm4 = if (p != null && p > 25.4f) "25.4" else v)
                    },
                    label = { Text("MM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            // Botón Crear llanta
            Button(
                onClick = {
                    if (data.selectedLlanta == null) {
                        error = "Selecciona una llanta del catálogo"
                        return@Button
                    }
                    if (data.mm1.isBlank() || data.mm2.isBlank() || data.mm3.isBlank() || data.mm4.isBlank()) {
                        error = "Completa todos los valores de MM"
                        return@Button
                    }
                    if (data.noQuemado.isBlank()) {
                        error = "Ingresa el No. Quemado"
                        return@Button
                    }
                    if (data.precio.isBlank() || data.precio.toFloatOrNull() == null) {
                        error = "Ingresa un precio válido"
                        return@Button
                    }
                    val selectedLlanta = data.selectedLlanta ?: return@Button
                    isLoading = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val req = LlantaVehiculoCreateRequest(
                                Llantas_idLlantas = selectedLlanta.idLlantas,
                                Vehiculos_idVehiculos = vehiculoId,
                                LlantasVehiculosNoQuemado = data.noQuemado,
                                LlantasVehiculosPresion = data.presion.toIntOrNull() ?: 0,
                                LlantasVehiculosPrecio = data.precio.toFloatOrNull() ?: 0f,
                                LlantasVehiculosPiso = data.piso,
                                LlantasVehiculosFechaInicio = DateFormatter.format(TimeProvider.getCurrentTimeMillis(), "yyyy-MM-dd'T'HH:mm:ss"),
                                LlantasVehiculosMM1 = data.mm1.toFloatOrNull() ?: 0f,
                                LlantasVehiculosMM2 = data.mm2.toFloatOrNull() ?: 0f,
                                LlantasVehiculosMM3 = data.mm3.toFloatOrNull() ?: 0f,
                                LlantasVehiculosMM4 = data.mm4.toFloatOrNull() ?: 0f,
                                replaceId = initialFormData.replacedLlantaVehiculoId
                            )
                            val res = repository.createLlantaVehiculo(req)
                            if (res.isSuccess) {
                                val created = res.getOrNull()!!
                                onLlantaCreated(created)
                            } else {
                                error = "No se pudo crear la llanta"
                            }
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Crear llanta")
                }
            }

            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            }
        }
    }
}