package com.megatransportes.yokoh.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun startSpeechRecognitionSuspend(context: Context): String? = suspendCancellableCoroutine { cont ->
    val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
    if (perm != PermissionChecker.PERMISSION_GRANTED) {
        Toast.makeText(context, "Se requiere permiso de micrófono", Toast.LENGTH_SHORT).show()
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    val sr = try { SpeechRecognizer.createSpeechRecognizer(context) } catch (e: Exception) {
        cont.resume(null); return@suspendCancellableCoroutine
    }

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    var lastPartial: String? = null
    val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {
            try {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()
                if (!text.isNullOrBlank()) lastPartial = text
            } catch (_: Exception) {}
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onError(error: Int) {
            try { sr.destroy() } catch (_: Exception) {}
            if (!cont.isCompleted) cont.resume(null)
        }

        override fun onResults(results: Bundle?) {
            try {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()
                if (!cont.isCompleted) cont.resume(text ?: lastPartial)
            } catch (e: Exception) {
                if (!cont.isCompleted) cont.resume(lastPartial)
            } finally {
                try { sr.destroy() } catch (_: Exception) {}
            }
        }
    }

    sr.setRecognitionListener(listener)
    try {
        sr.startListening(intent)
    } catch (e: Exception) {
        try { sr.destroy() } catch (_: Exception) {}
        if (!cont.isCompleted) cont.resumeWithException(e)
    }

    cont.invokeOnCancellation {
        try {
            sr.cancel()
            sr.destroy()
        } catch (_: Exception) {}
        // If coroutine was cancelled (user released), resume with last partial if available
        try {
            if (!cont.isCompleted) cont.resume(lastPartial)
        } catch (_: Exception) {}
    }
}
