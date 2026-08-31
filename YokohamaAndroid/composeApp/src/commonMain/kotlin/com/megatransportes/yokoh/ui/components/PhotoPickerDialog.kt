package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.megatransportes.yokoh.utils.FilePickerUtils
import com.megatransportes.yokoh.utils.FileConverter
import com.megatransportes.yokoh.utils.byteArrayToImageBitmap
import com.megatransportes.yokoh.utils.ErrorUtils
import kotlinx.coroutines.launch

data class PhotoSlot(
    val index: Int,
    val base64Data: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
)

@Composable
fun PhotoPickerDialog(
    photoSlots: List<PhotoSlot>,
    onPhotosChanged: (List<PhotoSlot>) -> Unit,
    onDismiss: () -> Unit,
    filePickerUtils: FilePickerUtils
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Seleccionar Fotos",
                    style = MaterialTheme.typography.titleLarge
                )

                // Grid de slots de fotos
                when (photoSlots.size) {
                    1 -> {
                        // Una sola foto - cuadrado grande centrado
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            PhotoSlotCard(
                                slot = photoSlots[0],
                                onPhotoSelected = { newSlot ->
                                    onPhotosChanged(listOf(newSlot))
                                },
                                onPhotoRemoved = {
                                    onPhotosChanged(listOf(photoSlots[0].copy(base64Data = null, fileName = null, fileSize = null)))
                                },
                                filePickerUtils = filePickerUtils,
                                onError = { errorMessage = it },
                                onLoadingChanged = { isLoading = it }
                            )
                        }
                    }
                    2 -> {
                        // Dos fotos - fila horizontal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            photoSlots.forEach { slot ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                ) {
                                    PhotoSlotCard(
                                        slot = slot,
                                        onPhotoSelected = { newSlot ->
                                            val updated = photoSlots.toMutableList()
                                            updated[slot.index] = newSlot
                                            onPhotosChanged(updated)
                                        },
                                        onPhotoRemoved = {
                                            val updated = photoSlots.toMutableList()
                                            updated[slot.index] = slot.copy(base64Data = null, fileName = null, fileSize = null)
                                            onPhotosChanged(updated)
                                        },
                                        filePickerUtils = filePickerUtils,
                                        onError = { errorMessage = it },
                                        onLoadingChanged = { isLoading = it }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Más de 2 fotos - grid 2x2 o más
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            photoSlots.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { slot ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                        ) {
                                            PhotoSlotCard(
                                                slot = slot,
                                                onPhotoSelected = { newSlot ->
                                                    val updated = photoSlots.toMutableList()
                                                    updated[slot.index] = newSlot
                                                    onPhotosChanged(updated)
                                                },
                                                onPhotoRemoved = {
                                                    val updated = photoSlots.toMutableList()
                                                    updated[slot.index] = slot.copy(base64Data = null, fileName = null, fileSize = null)
                                                    onPhotosChanged(updated)
                                                },
                                                filePickerUtils = filePickerUtils,
                                                onError = { errorMessage = it },
                                                onLoadingChanged = { isLoading = it }
                                            )
                                        }
                                    }
                                    // Rellenar espacio vacío si la fila no está completa
                                    if (row.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Botón cerrar
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

@Composable
private fun PhotoSlotCard(
    slot: PhotoSlot,
    onPhotoSelected: (PhotoSlot) -> Unit,
    onPhotoRemoved: () -> Unit,
    filePickerUtils: FilePickerUtils,
    onError: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showFullScreenImage by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable {
                if (slot.base64Data != null && slot.base64Data.isNotBlank()) {
                    showFullScreenImage = true
                } else {
                    showSourcePicker = true
                }
            },
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (slot.base64Data != null && slot.base64Data.isNotBlank()) {
                // Mostrar preview de la foto
                val imageBitmap: ImageBitmap? = remember(slot.base64Data) {
                    try {
                        val bytes = FileConverter.base64ToByteArray(slot.base64Data)
                        byteArrayToImageBitmap(bytes)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Foto ${slot.index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback si no se puede decodificar la imagen
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = "Cámara",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Botón X para eliminar foto (arriba a la derecha)
                IconButton(
                    onClick = {
                        onPhotoRemoved()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Red.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Eliminar foto",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // Mostrar ícono de cámara
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Cámara",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // Dialog para elegir cámara o galería
    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = { Text("Agregar foto") },
            text = { Text("¿Cómo deseas agregar la foto?") },
            confirmButton = {
                TextButton(onClick = {
                    showSourcePicker = false
                    coroutineScope.launch {
                        onLoadingChanged(true)
                        onError("")
                        try {
                            val fileData = filePickerUtils.pickImageFromCamera()
                            if (fileData != null) {
                                val base64Data = FileConverter.fileDataToBase64(fileData)
                                onPhotoSelected(
                                    slot.copy(
                                        base64Data = base64Data,
                                        fileName = fileData.name,
                                        fileSize = fileData.size
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            onError(ErrorUtils.userMessage(e, "Error al tomar foto"))
                        } finally {
                            onLoadingChanged(false)
                        }
                    }
                }) {
                    Text("Cámara")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourcePicker = false
                    coroutineScope.launch {
                        onLoadingChanged(true)
                        onError("")
                        try {
                            val fileData = filePickerUtils.pickImageFile()
                            if (fileData != null) {
                                val base64Data = FileConverter.fileDataToBase64(fileData)
                                onPhotoSelected(
                                    slot.copy(
                                        base64Data = base64Data,
                                        fileName = fileData.name,
                                        fileSize = fileData.size
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            onError(ErrorUtils.userMessage(e, "Error al seleccionar imagen"))
                        } finally {
                            onLoadingChanged(false)
                        }
                    }
                }) {
                    Text("Galería")
                }
            }
        )
    }

    // Dialog de pantalla completa para ver la imagen
    if (showFullScreenImage && slot.base64Data != null) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showFullScreenImage = false },
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap: ImageBitmap? = remember(slot.base64Data) {
                    try {
                        val bytes = FileConverter.base64ToByteArray(slot.base64Data)
                        byteArrayToImageBitmap(bytes)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Foto ${slot.index + 1} - Vista completa",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Botón cerrar en la esquina superior derecha
                IconButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cerrar vista completa",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
