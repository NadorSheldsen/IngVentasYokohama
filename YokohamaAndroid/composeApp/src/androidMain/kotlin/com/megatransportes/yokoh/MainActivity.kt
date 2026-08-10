package com.megatransportes.yokoh

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.russhwolf.settings.SharedPreferencesSettings
import com.megatransportes.yokoh.utils.MicPlatformOverlay
import com.megatransportes.yokoh.utils.BluetoothCaliperManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.megatransportes.yokoh.platform.ActivityHolder.activity = this
        
        val settings = SharedPreferencesSettings(
            getSharedPreferences("yokohama_prefs", MODE_PRIVATE)
        )
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // create navigator here so Android can intercept back presses
                    val initial = if (/* repository not yet available, default to Login */ false) null else null
                    val nav = remember { 
                        // we can't access repository here; App will initialize navigator with proper initial
                        // but creating a default Login navigator allows BackHandler to call pop()
                        com.megatransportes.yokoh.ui.Navigator(com.megatransportes.yokoh.ui.Screen.Login)
                    }

                    // intercept system back and try to pop navigation stack first
                    BackHandler {
                        // Try to execute the registered screen onBack handler first (same as top-left arrow).
                        val handled = nav.performBack()
                        if (!handled) {
                            // if nothing to pop/handle, finish activity
                            finish()
                        }
                    }

                    App(settings, navigator = nav)
                }
            }
        }
        // register activity for native mic overlay
        MicPlatformOverlay.setHost(this)
    }

    override fun onDestroy() {
        try { com.megatransportes.yokoh.platform.ActivityHolder.activity = null } catch (_: Exception) {}
        super.onDestroy()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        com.megatransportes.yokoh.utils.SpeechRecognitionManager.onRequestPermissionsResult(requestCode, grantResults)
    }

    // Intercepta teclas del calibrador Bluetooth HID (dígitos + Enter) para
    // evitar que escriban en el campo enfocado y salten entre campos.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        android.util.Log.d("Mitutoyo", "dispatchKeyEvent: keyCode=${event.keyCode} action=${event.action} uni=${event.unicodeChar} print=${event.isPrintingKey}")
        if (BluetoothCaliperManager.processKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}