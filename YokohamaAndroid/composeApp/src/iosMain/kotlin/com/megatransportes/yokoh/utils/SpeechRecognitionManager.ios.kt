package com.megatransportes.yokoh.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults

actual object SpeechRecognitionManager {
    private var lastPartial: String? = null
    private var audioLevelCallback: ((Float) -> Unit)? = null

    actual fun start(platformContext: Any?) {
        NSLog("SpeechRecognitionManager.start() called")
        
        // Check authorization
        val authorized = NSUserDefaults.standardUserDefaults.boolForKey("speechRecognitionAuthorized")
        NSLog("Speech recognition authorized: $authorized")
        
        if (!authorized) {
            NSLog("Speech recognition not authorized")
            return
        }
        
        // Signal Swift to start recording
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = "startSpeechRecording")
        NSLog("Signal sent to start speech recording")
    }

    actual suspend fun stopAndGet(timeoutMs: Long): String? = withContext(Dispatchers.Default) {
        NSLog("SpeechRecognitionManager.stopAndGet() called")
        
        // Signal Swift to stop recording
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = "stopSpeechRecording")
        
        // Wait for result with simple counter and monitor audio level
        var result: String? = null
        val iterations = (timeoutMs / 100).toInt()
        
        repeat(iterations) {
            result = NSUserDefaults.standardUserDefaults.stringForKey("speechRecognitionResult")
            if (!result.isNullOrEmpty()) {
                return@repeat
            }
            
            // Monitor audio level during wait
            val audioLevel = NSUserDefaults.standardUserDefaults.floatForKey("audioLevel")
            audioLevelCallback?.invoke(audioLevel)
            
            delay(100)
        }
        
        NSLog("Speech recognition result: $result")
        
        // Clear the signal
        NSUserDefaults.standardUserDefaults.setBool(false, forKey = "stopSpeechRecording")
        NSUserDefaults.standardUserDefaults.setBool(false, forKey = "startSpeechRecording")
        
        audioLevelCallback = null
        return@withContext result
    }

    actual fun setAudioLevelCallback(callback: ((Float) -> Unit)?) {
        audioLevelCallback = callback
    }
}
