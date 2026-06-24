package com.megatransportes.yokoh.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

actual class FilePickerUtils {
    
    private var imagePickerLauncher: ActivityResultLauncher<Intent>? = null
    private var filePickerLauncher: ActivityResultLauncher<Intent>? = null
    private var currentContinuation: ((FileData?) -> Unit)? = null
    private var appContext: Context? = null
    private var lastCameraUri: Uri? = null
    
    fun setLaunchers(
        imageLauncher: ActivityResultLauncher<Intent>,
        fileLauncher: ActivityResultLauncher<Intent>
    ) {
        imagePickerLauncher = imageLauncher
        filePickerLauncher = fileLauncher
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }
    
    actual suspend fun pickImageFile(): FileData? {
        return suspendCancellableCoroutine { continuation ->
            val launcher = imagePickerLauncher
            
            if (launcher == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            currentContinuation = { fileData ->
                continuation.resume(fileData)
            }

            // Crear intent para seleccionar imagen desde galería
            val galleryIntent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/gif"))
            }

            // Crear intent para tomar foto con la cámara
            val context = appContext
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            var chooserIntent: Intent = Intent.createChooser(galleryIntent, "Seleccionar imagen")

            if (context != null) {
                try {
                    // Crear archivo temporal para que la cámara escriba la imagen
                    val cacheDir = context.cacheDir
                    val imageFile = File.createTempFile("camera_temp_", ".jpg", cacheDir)
                    val authority = "com.megatransportes.yokoh.fileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, imageFile)
                    lastCameraUri = uri

                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    // Some devices need temporary URI permissions granted to camera activity
                    val resInfoList = context.packageManager.queryIntentActivities(cameraIntent, 0)
                    for (resolveInfo in resInfoList) {
                        val packageName = resolveInfo.activityInfo.packageName
                        context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    // Add camera as an initial intent so chooser shows both options
                    chooserIntent = Intent.createChooser(galleryIntent, "Seleccionar imagen").apply {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    }
                } catch (e: Exception) {
                    // If creating temp file or provider fails, fall back to gallery-only chooser
                    lastCameraUri = null
                }
            }

            launcher.launch(chooserIntent)
        }
    }
    
    actual suspend fun pickFile(vararg extensions: String): FileData? {
        return suspendCancellableCoroutine { continuation ->
            val launcher = filePickerLauncher
            
            if (launcher == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            currentContinuation = { fileData ->
                continuation.resume(fileData)
            }
            
            // Crear intent para seleccionar cualquier archivo
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                
                // Si se especifican extensiones, filtrar por tipo MIME
                if (extensions.isNotEmpty()) {
                    val mimeTypes = extensions.map { ext ->
                        when (ext.lowercase()) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            "pdf" -> "application/pdf"
                            "txt" -> "text/plain"
                            "doc" -> "application/msword"
                            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            else -> "*/*"
                        }
                    }.toTypedArray()
                    
                    putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                }
            }
            
            val chooserIntent = Intent.createChooser(intent, "Seleccionar archivo")
            launcher.launch(chooserIntent)
        }
    }
    
    fun handleImageResult(result: android.content.Intent?, context: Context) {
        val uriFromResult = result?.data?.let { it }
        val uriToUse = uriFromResult ?: lastCameraUri
        if (uriToUse != null) {
            val fileData = uriToFileData(context, uriToUse)
            currentContinuation?.invoke(fileData)
        } else {
            currentContinuation?.invoke(null)
        }
        // Clear lastCameraUri after handling
        lastCameraUri = null
        currentContinuation = null
    }
    
    fun handleFileResult(result: android.content.Intent?, context: Context) {
        result?.data?.let { uri ->
            val fileData = uriToFileData(context, uri)
            currentContinuation?.invoke(fileData)
        } ?: currentContinuation?.invoke(null)
        currentContinuation = null
    }
    
    private fun uriToFileData(context: Context, uri: Uri): FileData? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            
            val cursor = contentResolver.query(uri, null, null, null, null)
            var fileName = "archivo_seleccionado"
            var size = 0L
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
            
            val mimeType = contentResolver.getType(uri)
            
            // Read raw bytes first
            val buffer = ByteArrayOutputStream()
            inputStream.use { input ->
                val temp = ByteArray(1024)
                var read: Int
                while (input.read(temp).also { read = it } != -1) {
                    buffer.write(temp, 0, read)
                }
            }

            var finalBytes = buffer.toByteArray()
            var finalSize = size.takeIf { it > 0 } ?: finalBytes.size.toLong()

            // If this is an image, attempt to normalize resolution and compress to save space
            if (mimeType?.startsWith("image/") == true) {
                try {
                    // Decode bitmap
                    val original = android.graphics.BitmapFactory.decodeByteArray(finalBytes, 0, finalBytes.size)
                    if (original != null) {
                        // Target max dimension (force downscale larger images to uniform max)
                        val maxDim = 640
                        val (w, h) = original.width to original.height
                        var newW = w
                        var newH = h

                        // If larger than maxDim, scale down preserving aspect ratio
                        if (w > maxDim || h > maxDim) {
                            val ratio = w.toFloat() / h.toFloat()
                            if (w >= h) {
                                newW = maxDim
                                newH = (maxDim / ratio).toInt()
                            } else {
                                newH = maxDim
                                newW = (maxDim * ratio).toInt()
                            }
                        }

                        val scaled = if (newW != w || newH != h) {
                            android.graphics.Bitmap.createScaledBitmap(original, newW, newH, true)
                        } else {
                            // Keep original if already small
                            original
                        }

                        // Compress iteratively to meet a target max bytes (after base64 overhead will grow ~33%)
                        val targetBytes = 150 * 1024 // aim for <= ~150KB raw JPEG
                        var quality = 60
                        var compressed: ByteArray
                        do {
                            val outStream = ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outStream)
                            compressed = outStream.toByteArray()
                            quality -= 10
                        } while (compressed.size > targetBytes && quality >= 40)

                        if (compressed.isNotEmpty()) {
                            finalBytes = compressed
                            finalSize = compressed.size.toLong()
                        }
                        // recycle bitmaps if we created a new scaled one
                        if (scaled !== original) scaled.recycle()
                        original.recycle()
                    }
                } catch (e: Exception) {
                    // If anything fails, fall back to original bytes
                }
            }

            FileData(
                name = fileName,
                size = finalSize,
                mimeType = mimeType,
                data = finalBytes
            )
        } catch (e: Exception) {
            null
        }
    }
}

// Variable global para mantener la instancia
private var globalFilePickerUtils: FilePickerUtils? = null

actual fun createFilePickerUtils(): FilePickerUtils {
    return globalFilePickerUtils ?: FilePickerUtils().also {
        globalFilePickerUtils = it
    }
}

@Composable
actual fun InitializeFilePickerIfNeeded() {
    val context = LocalContext.current
    val filePickerUtils = createFilePickerUtils()
    
    // Registrar los launchers durante la composición (no en LaunchedEffect)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            filePickerUtils.handleImageResult(result.data, context)
        } else {
            filePickerUtils.handleImageResult(null, context)
        }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            filePickerUtils.handleFileResult(result.data, context)
        } else {
            filePickerUtils.handleFileResult(null, context)
        }
    }
    
    // Configurar los launchers en el FilePickerUtils
    LaunchedEffect(imagePickerLauncher, filePickerLauncher) {
        filePickerUtils.setContext(context)
        filePickerUtils.setLaunchers(imagePickerLauncher, filePickerLauncher)
    }
}