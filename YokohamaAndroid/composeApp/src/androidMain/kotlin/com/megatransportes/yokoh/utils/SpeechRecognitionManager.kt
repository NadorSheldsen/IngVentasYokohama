package com.megatransportes.yokoh.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.util.Locale
import com.megatransportes.yokoh.platform.ActivityHolder

actual object SpeechRecognitionManager {
    private const val PERMISSION_REQUEST_CODE = 1001
    private var sr: SpeechRecognizer? = null
    private val _lastPartial = MutableStateFlow<String?>(null)
    private var listening = false
    private var lastIntent: Intent? = null
    private var audioLevelCallback: ((Float) -> Unit)? = null
    private var pendingStart: (() -> Unit)? = null

    actual fun start(platformContext: Any?) {
        val context = platformContext as? Context ?: ActivityHolder.activity
        android.util.Log.d("SpeechRecognition", "start called - platformContext: $platformContext, context: $context, ActivityHolder.activity: ${ActivityHolder.activity}")
        
        if (context == null) {
            android.util.Log.e("SpeechRecognition", "Context is null - platformContext: $platformContext, ActivityHolder.activity: ${ActivityHolder.activity}")
            return
        }
        
        // Check permissions
        val perm = android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == perm
        android.util.Log.d("SpeechRecognition", "Has RECORD_AUDIO permission: $hasPermission")
        
        if (!hasPermission) {
            android.util.Log.d("SpeechRecognition", "Requesting RECORD_AUDIO permission")
            val activity = context as? Activity ?: ActivityHolder.activity
            if (activity != null) {
                pendingStart = { start(platformContext) }
                ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            } else {
                android.util.Log.e("SpeechRecognition", "Cannot request permission - no Activity available")
            }
            return
        }
        
        if (sr != null) {
            android.util.Log.e("SpeechRecognition", "SpeechRecognizer already initialized")
            return
        }
        try {
            sr = SpeechRecognizer.createSpeechRecognizer(context)
            android.util.Log.d("SpeechRecognition", "SpeechRecognizer created successfully")
        } catch (e: Exception) {
            android.util.Log.e("SpeechRecognition", "Failed to create SpeechRecognizer: ${e.message}", e)
            sr = null
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Use Spanish (Mexico) locale for consistency with iOS
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "MX"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        lastIntent = intent

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("SpeechRecognition", "onReadyForSpeech")
            }
            override fun onBeginningOfSpeech() {
                android.util.Log.d("SpeechRecognition", "onBeginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {
                audioLevelCallback?.invoke(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                android.util.Log.d("SpeechRecognition", "onEndOfSpeech")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                try {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        _lastPartial.value = text
                        android.util.Log.d("SpeechRecognition", "onPartialResults: $text")
                    }
                } catch (_: Exception) {}
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                android.util.Log.e("SpeechRecognition", "onError: $error")
            }
            override fun onResults(results: Bundle?) {
                try {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        _lastPartial.value = text
                        android.util.Log.d("SpeechRecognition", "onResults: $text")
                    }
                } catch (_: Exception) {}
            }
        }

        sr?.setRecognitionListener(listener)
        _lastPartial.value = null
        listening = true
        try {
            sr?.startListening(intent)
            android.util.Log.d("SpeechRecognition", "startListening called successfully")
        } catch (e: Exception) {
            android.util.Log.e("SpeechRecognition", "Failed to startListening: ${e.message}", e)
        }
    }

    actual suspend fun stopAndGet(timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            sr?.stopListening()
        } catch (_: Exception) {}

        // Return the last partial/final immediately (no timeout)
        val result = _lastPartial.value

        try {
            listening = false
            sr?.cancel()
            sr?.destroy()
        } catch (_: Exception) {}
        sr = null
        lastIntent = null
        audioLevelCallback = null
        return@withContext result
    }

    actual fun setAudioLevelCallback(callback: ((Float) -> Unit)?) {
        audioLevelCallback = callback
    }
    
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("SpeechRecognition", "RECORD_AUDIO permission granted")
                pendingStart?.invoke()
                pendingStart = null
            } else {
                android.util.Log.e("SpeechRecognition", "RECORD_AUDIO permission denied")
                pendingStart = null
            }
        }
    }
}
