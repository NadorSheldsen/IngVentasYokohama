package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import com.megatransportes.yokoh.utils.MicPlatformOverlay

enum class FieldType { NUMBER, TEXT }

data class FieldDescriptor(
    val title: String,
    val type: FieldType = FieldType.TEXT,
    val onFill: (String) -> Unit,
    // If the field has a set of selectable choices (dropdowns), provide them here.
    // The parser will try to match recognized text against these choices.
    val choices: List<String>? = null,
    // Maximum allowed value for NUMBER fields (e.g., for MM fields to not exceed previous value)
    val maxValue: Float? = null
)

/**
 * MicButton composable reusable across screens.
 * - `startListening` (optional) should be provided by the platform to perform real speech recognition
 *   and return the recognized string (or null on cancel).
 * - If `startListening` is not provided, a small dialog is shown to manually type the recognized text
 *   (useful for desktop/testing or until platform implementation is added).
 */
@Composable
fun MicButton(
    fields: List<FieldDescriptor>,
    modifier: Modifier = Modifier,
    // legacy single-call STT
    startListening: (suspend () -> String?)? = null,
    // explicit start/stop flow (preferred on Android): provide start action (non-suspending)
    startListeningAction: (() -> Unit)? = null,
    // stop and return last partial/final
    stopListening: (suspend () -> String?)? = null,
    // Optional callback so host can render an overlay at top level (center provided in window coordinates)
    onOverlayRequested: ((recording: Boolean, centerWindow: Offset, circleDp: Dp) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var audioLevel by remember { mutableStateOf(0f) }

    fun parseAndFill(recognized: String) {
        println("MicInput: parseAndFill called with: '$recognized'")
        val numberRegex = "[0-9]*\\.?[0-9]+".toRegex()
        val filled = mutableSetOf<FieldDescriptor>()

        // Función para separar números concatenados en dígitos individuales (solo para MM)
        fun splitConcatenatedNumbers(text: String): List<String> {
            val nums = numberRegex.findAll(text).map { it.value }.toList()
            val result = mutableListOf<String>()
            
            for (num in nums) {
                // Separar si es un número largo sin punto decimal (más de 2 dígitos)
                // Números como 100, 120, 150 se separarán para MM pero se mantienen completos para presión
                if (num.length > 2 && !num.contains(".")) {
                    // Separar en dígitos individuales
                    num.forEach { digit ->
                        result.add(digit.toString())
                    }
                } else {
                    result.add(num)
                }
            }
            
            return result
        }

        // Allow multiple commands in one utterance separated by commas or semicolons
        val segments = recognized.split(Regex("[,;]\\s*"))

        fun processSegment(segmentRaw: String) {
            val recognizedSeg = segmentRaw.trim()
            if (recognizedSeg.isBlank()) return
            val lower = recognizedSeg.lowercase()

            println("MicInput: Processing segment: '$recognizedSeg'")
            println("MicInput: Lowercase: '$lower'")

            // Special handling: if user says "presión" or "psi" before or after a number, only affect pressure field
            val pressureKeywords = listOf("presión", "presion", "pressure", "psi")
            val hasPressureKeyword = pressureKeywords.any { lower.contains(it) }
            println("MicInput: hasPressureKeyword = $hasPressureKeyword")
            
            if (hasPressureKeyword) {
                val pressureField = fields.firstOrNull { f -> f.title.lowercase() == "presión" || f.title.lowercase() == "presion" }
                println("MicInput: pressureField found = ${pressureField != null}")
                if (pressureField != null) {
                    // Find all numbers and their positions in the segment
                    val numMatches = numberRegex.findAll(recognizedSeg.replace(',', '.')).toList()
                    println("MicInput: numMatches count = ${numMatches.size}")
                    if (numMatches.isNotEmpty()) {
                        // Find the number closest to "presión" keyword
                        val pressureKeywordIndex = recognizedSeg.lowercase().indexOfAny(listOf("presión", "presion", "pressure", "psi"))
                        println("MicInput: pressureKeywordIndex = $pressureKeywordIndex")
                        
                        var closestNum = numMatches.first().value
                        var closestDistance = Int.MAX_VALUE
                        
                        for (match in numMatches) {
                            val distance = kotlin.math.abs(match.range.first - pressureKeywordIndex)
                            if (distance < closestDistance) {
                                closestDistance = distance
                                closestNum = match.value
                            }
                        }
                        
                        pressureField.onFill(closestNum)
                        filled.add(pressureField)
                        println("MicInput: Filled pressure field with $closestNum (distance: $closestDistance)")
                        
                        // Remaining numbers (excluding the one used for pressure) go to MM fields
                        val remainingNums = numMatches.map { it.value }.filter { it != closestNum }
                        println("MicInput: remainingNums for MM = $remainingNums")
                        if (remainingNums.isNotEmpty()) {
                            val mmFields = fields.filter { f ->
                                val t = f.title.lowercase()
                                t.contains("mm") || t.startsWith("mm")
                            }
                            println("MicInput: mmFields count = ${mmFields.size}")
                            remainingNums.forEachIndexed { i, num ->
                                if (i < mmFields.size) {
                                    val field = mmFields[i]
                                    val finalValue = if (field.maxValue != null) {
                                        val parsed = num.toFloatOrNull()
                                        if (parsed != null && parsed > field.maxValue) {
                                            field.maxValue.toString()
                                        } else {
                                            num
                                        }
                                    } else {
                                        num
                                    }
                                    field.onFill(finalValue)
                                    filled.add(field)
                                    println("MicInput: Filled MM field $i with $finalValue (maxValue: ${field.maxValue})")
                                }
                            }
                        }
                        return
                    }
                }
            }

            // Special handling: if user says "milimetro(s)" or "mm", map consecutive numbers to the MM fields
            val mmKeywords = listOf("milimetro", "milímetro", "milimetros", "milímetros", "mm")
            val hasMmKeyword = mmKeywords.any { lower.contains(it) }
            
            // Check for specific MM field patterns like "milimetro 1 numero", "mm 2 numero", etc.
            val specificMmPattern = Regex("(?:milimetro|milímetro|mm)\\s*(\\d+)\\s*([0-9]*\\.?[0-9]+)", RegexOption.IGNORE_CASE)
            val specificMatch = specificMmPattern.find(recognizedSeg)
            
            if (specificMatch != null) {
                val mmIndex = specificMatch.groupValues[1].toIntOrNull()?.minus(1) // Convert to 0-based index
                val mmValue = specificMatch.groupValues[2]
                println("MicInput: Specific MM pattern detected: MM${mmIndex?.plus(1)} = $mmValue")
                
                if (mmIndex != null) {
                    val mmFields = fields.filter { f ->
                        val t = f.title.lowercase()
                        t.contains("mm") || t.startsWith("mm")
                    }
                    if (mmIndex >= 0 && mmIndex < mmFields.size) {
                        val field = mmFields[mmIndex]
                        val finalValue = if (field.maxValue != null) {
                            val parsed = mmValue.toFloatOrNull()
                            if (parsed != null && parsed > field.maxValue) {
                                field.maxValue.toString()
                            } else {
                                mmValue
                            }
                        } else {
                            mmValue
                        }
                        field.onFill(finalValue)
                        filled.add(field)
                        println("MicInput: Filled specific MM field $mmIndex with $finalValue (maxValue: ${field.maxValue})")
                        return
                    }
                }
            }
            
            if (hasMmKeyword) {
                // Find all fields whose title contains "mm" or "mil"
                val mmFields = fields.filter { f ->
                    val t = f.title.lowercase()
                    mmKeywords.any { kw -> t.contains(kw) } || t == "mm" || t.startsWith("mm")
                }
                if (mmFields.isNotEmpty()) {
                    val nums = splitConcatenatedNumbers(recognizedSeg.replace(',', '.'))
                    if (nums.isNotEmpty()) {
                        nums.forEachIndexed { i, num ->
                            if (i < mmFields.size) {
                                val field = mmFields[i]
                                val finalValue = if (field.maxValue != null) {
                                    val parsed = num.toFloatOrNull()
                                    if (parsed != null && parsed > field.maxValue) {
                                        field.maxValue.toString()
                                    } else {
                                        num
                                    }
                                } else {
                                    num
                                }
                                field.onFill(finalValue)
                                filled.add(field)
                                println("MicInput: Filled MM field $i with $finalValue (maxValue: ${field.maxValue})")
                            }
                        }
                    }
                }
            }

            // If only numbers are mentioned (no pressure keyword), map them to MM fields
            // This handles both: single number (fill first MM) and multiple numbers (fill multiple MM fields)
            val nums = splitConcatenatedNumbers(recognizedSeg.replace(',', '.'))
            if (nums.isNotEmpty() && !hasPressureKeyword) {
                val mmFields = fields.filter { f ->
                    val t = f.title.lowercase()
                    mmKeywords.any { kw -> t.contains(kw) } || t == "mm" || t.startsWith("mm")
                }
                if (mmFields.isNotEmpty()) {
                    println("MicInput: Filling MM fields with numbers (no pressure keyword): $nums")
                    nums.forEachIndexed { i, num ->
                        if (i < mmFields.size) {
                            val field = mmFields[i]
                            val finalValue = if (field.maxValue != null) {
                                val parsed = num.toFloatOrNull()
                                if (parsed != null && parsed > field.maxValue) {
                                    field.maxValue.toString()
                                } else {
                                    num
                                }
                            } else {
                                num
                            }
                            field.onFill(finalValue)
                            filled.add(field)
                            println("MicInput: Filled MM field $i with $finalValue (maxValue: ${field.maxValue})")
                        }
                    }
                    return
                }
            }

            // Generic handling for fields not already filled
            fields.forEach { f ->
                if (filled.contains(f)) return@forEach

                val titleLower = f.title.lowercase()
                if (lower.contains(titleLower)) {
                    // If field defines explicit choices, try to match any choice
                    val choices = f.choices
                    if (choices != null && choices.isNotEmpty()) {
                        fun normalize(s: String) = s
                            .lowercase()
                            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
                            .replace(Regex("^[a-z0-9]+\\s+"), "")
                            .trim()

                        var recNorm = normalize(recognizedSeg)
                        // remove common leading words that users may say before an option
                        recNorm = recNorm.replaceFirst(Regex("^(con|opcion|opción|elección|letra)\\s+"), "")

                        // If the user only spoke a single letter token ("b"), map it to the choice by initial
                        val tokens = recNorm.split(Regex("\\s+"))
                        val singleLetter = tokens.firstOrNull { it.length == 1 && it[0] in 'a'..'z' }
                        if (singleLetter != null) {
                            val matchedByLetter = choices.firstOrNull { choice ->
                                val cNorm = normalize(choice)
                                val firstToken = cNorm.split(Regex("\\s+"))[0]
                                firstToken.startsWith(singleLetter)
                            }
                            if (matchedByLetter != null) {
                                f.onFill(matchedByLetter)
                                filled.add(f)
                                return@forEach
                            }
                        }

                        val matched = choices.firstOrNull { choice ->
                            val cNorm = normalize(choice)
                            // match if recognized contains core of choice, or choice contains recognized fragment,
                            // or any word of recognized appears in the choice core
                            recNorm.contains(cNorm) || cNorm.contains(recNorm) || recNorm.split(" ").any { w -> w.length >= 2 && cNorm.contains(w) }
                        }
                        if (matched != null) {
                            f.onFill(matched)
                            filled.add(f)
                            return@forEach
                        }
                    }

                    if (f.type == FieldType.NUMBER) {
                        val match = numberRegex.find(recognizedSeg.replace(',', '.'))
                        if (match != null) {
                            f.onFill(match.value)
                            filled.add(f)
                        }
                    } else {
                        val idx = lower.indexOf(titleLower)
                        val after = recognizedSeg.substring(idx + f.title.length).trim().trimStart(':', ' ')
                        if (after.isNotBlank()) f.onFill(after) else f.onFill(recognizedSeg)
                        filled.add(f)
                    }
                }
            }
        }

        // Process all segments so multiple commands in one press are applied
        segments.forEach { seg -> processSegment(seg) }

        // If nothing matched explicitly and there's a single field, try to fill it
        if (filled.isEmpty() && fields.size == 1) {
            val fsingle = fields[0]
            if (fsingle.type == FieldType.NUMBER) {
                val match = numberRegex.find(recognized.replace(',', '.'))
                if (match != null) fsingle.onFill(match.value)
            } else {
                fsingle.onFill(recognized)
            }
        }
    }

    // Animated scale for the mic icon and background circle while recording
    val iconScale by animateFloatAsState(if (recording) 1.6f else 1f)
    val circleScale by animateFloatAsState(if (recording) 1.9f else 1f)
    
    // Audio level visualization - scale based on audio level (0-10 dB range)
    // Increased scale multiplier for more visible effect
    val audioScale by animateFloatAsState(
        targetValue = if (recording) 1f + (audioLevel / 5f).coerceIn(0f, 1.2f) else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.4f,
            stiffness = 400f
        )
    )

    // Track global position and size to place an overlay Popup centered on the button
    val density = LocalDensity.current
    var centerWindowPos by remember { mutableStateOf(Offset.Zero) }
    var buttonSizePx by remember { mutableStateOf(IntSize(0, 0)) }

    // Press-and-hold behaviour: start listening while pressed; stop listening when released
    Box(
        modifier = modifier
            .size(40.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.localToWindow(Offset.Zero)
                centerWindowPos = Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f)
                buttonSizePx = coords.size
            }
            .pointerInput(startListening) {
                detectTapGestures(onPress = {
                    // Preferred Android flow: startListeningAction + stopListening
                    if (startListeningAction != null && stopListening != null) {
                        try { startListeningAction() } catch (_: Exception) {}

                        // Set up audio level callback
                        com.megatransportes.yokoh.utils.SpeechRecognitionManager.setAudioLevelCallback { level ->
                            audioLevel = level
                        }

                        // indicate recording immediately so UI updates while user holds
                        recording = true
                        try {
                            try { awaitRelease() } catch (_: Exception) {}

                            // after release, request the final/partial transcript
                            val text = try { stopListening() } catch (_: Exception) { null }
                            if (!text.isNullOrBlank()) parseAndFill(text)
                        } finally {
                            recording = false
                            audioLevel = 0f
                            com.megatransportes.yokoh.utils.SpeechRecognitionManager.setAudioLevelCallback(null)
                        }

                        return@detectTapGestures
                    }

                    // Fallback legacy flow: single-call startListening
                    if (startListening == null) {
                        // open manual dialog while press held
                        showManualInput = true
                        try { awaitRelease() } catch (_: Exception) {}
                        showManualInput = false
                        return@detectTapGestures
                    }

                    val job = coroutineScope.launch {
                        recording = true
                        try {
                            val result = startListening()
                            if (!result.isNullOrBlank()) parseAndFill(result)
                        } finally {
                            recording = false
                        }
                    }

                    try {
                        awaitRelease()
                    } finally {
                        if (job.isActive) job.cancel()
                        recording = false
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Audio wave visualization - multiple concentric circles that pulse with audio level
                if (recording) {
                    // Outer wave - largest, most transparent
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(circleScale * audioScale * 1.5f)
                            .background(
                                MaterialTheme.colorScheme.error.copy(
                                    alpha = 0.2f * (audioLevel / 5f).coerceIn(0.3f, 1f)
                                ),
                                shape = CircleShape
                            )
                    )
                    // Middle wave 1
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(circleScale * audioScale * 1.3f)
                            .background(
                                MaterialTheme.colorScheme.error.copy(
                                    alpha = 0.25f * (audioLevel / 5f).coerceIn(0.3f, 1f)
                                ),
                                shape = CircleShape
                            )
                    )
                    // Middle wave 2
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(circleScale * audioScale * 1.15f)
                            .background(
                                MaterialTheme.colorScheme.error.copy(
                                    alpha = 0.3f * (audioLevel / 5f).coerceIn(0.3f, 1f)
                                ),
                                shape = CircleShape
                            )
                    )
                    // Inner wave - base recording circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(circleScale * audioScale)
                            .background(
                                MaterialTheme.colorScheme.error.copy(
                                    alpha = 0.4f * (audioLevel / 5f).coerceIn(0.5f, 1f)
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Keep the mic icon scaling locally so user sees press feedback
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Micrófono",
                    modifier = Modifier.scale(iconScale),
                    tint = if (recording) MaterialTheme.colorScheme.onError else LocalContentColor.current
                )
            }
    }

    // Notify host about overlay position (if provided) - disabled to prevent duplicate circles
    // if (onOverlayRequested != null) {
    //     LaunchedEffect(recording, centerWindowPos, circleScale) {
    //         val baseDp = 40.dp
    //         onOverlayRequested(recording, centerWindowPos, baseDp * circleScale)
    //     }
    // }

    // Platform overlay disabled to prevent duplicate circles
    // LaunchedEffect(recording, centerWindowPos, circleScale) {
    //     val baseDp = 40.dp
    //     if (recording) {
    //         MicPlatformOverlay.show(centerWindowPos, baseDp * circleScale)
    //     } else {
    //         MicPlatformOverlay.hide()
    //     }
    // }

    // Popup disabled to prevent duplicate circles - only local circle remains
    // if (recording) {
    //     val baseDp = 40.dp
    //     val circlePx = with(density) { (baseDp.toPx() * circleScale).toInt() }
    //     val popupOffset = IntOffset((centerWindowPos.x - circlePx / 2f).toInt(), (centerWindowPos.y - circlePx / 2f).toInt())
    //     Popup(alignment = Alignment.TopStart, offset = popupOffset, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
    //         Box(modifier = Modifier
    //             .size(with(density) { circlePx.toDp() })
    //             .background(MaterialTheme.colorScheme.error.copy(alpha = 0.24f), shape = CircleShape)
    //         )
    //     }
    // }

    if (showManualInput) {
        AlertDialog(onDismissRequest = { showManualInput = false }, confirmButton = {
            TextButton(onClick = {
                showManualInput = false
                if (manualText.isNotBlank()) parseAndFill(manualText)
                manualText = ""
            }) { Text("Aceptar") }
        }, dismissButton = {
            TextButton(onClick = { showManualInput = false }) { Text("Cancelar") }
        }, title = { Text("Simular entrada por voz") }, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(value = manualText, onValueChange = { manualText = it }, label = { Text("Texto reconocido") })
                Spacer(modifier = Modifier.height(8.dp))
                Text("Escribe una frase con el título del campo, por ejemplo: 'Presión 34' para pruebas.")
            }
        })
    }
}
