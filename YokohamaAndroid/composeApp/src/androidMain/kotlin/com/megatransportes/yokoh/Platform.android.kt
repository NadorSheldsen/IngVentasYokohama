package com.megatransportes.yokoh

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

actual fun getPlatformName(): String = "Android"

actual fun disableIosScrollBounce() {
    // No-op en Android
}

@Composable
fun getSettings(): Settings {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("yokohama_prefs", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(sharedPreferences)
}