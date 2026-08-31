package com.megatransportes.yokoh.utils

actual object SpeechRecognitionManager {
    actual fun start(platformContext: Any?) {
        // No-op on JVM target.
    }

    actual suspend fun stopAndGet(timeoutMs: Long): String? = null

    actual fun setAudioLevelCallback(callback: ((Float) -> Unit)?) {
        // No-op on JVM target.
    }
}
