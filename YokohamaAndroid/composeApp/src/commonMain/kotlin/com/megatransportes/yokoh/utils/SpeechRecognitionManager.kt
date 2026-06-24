package com.megatransportes.yokoh.utils

expect object SpeechRecognitionManager {
    fun start(platformContext: Any? = null)
    suspend fun stopAndGet(timeoutMs: Long = 1200): String?
    fun setAudioLevelCallback(callback: ((Float) -> Unit)?)
}
