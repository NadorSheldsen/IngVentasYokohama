package com.megatransportes.yokoh.utils

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidFilePicker(
    private val activity: ComponentActivity
) : FilePicker {

    private var continuation: (kotlin.coroutines.Continuation<ByteArray?>)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val bytes = uri?.let { activity.contentResolver.openInputStream(it)?.readBytes() }
        continuation?.resume(bytes)
        continuation = null
    }

    override suspend fun pickImage(): ByteArray? =
        suspendCancellableCoroutine { cont ->
            continuation = cont
            launcher.launch("image/*")
        }
}
