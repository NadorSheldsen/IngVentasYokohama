package com.megatransportes.yokoh.data.session

import com.megatransportes.yokoh.data.models.Usuario
import com.russhwolf.settings.Settings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionManager(private val settings: Settings) {
    
    companion object {
        private const val KEY_USER_SESSION = "user_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
    }
    
    fun saveUserSession(user: Usuario) {
        settings.putString(KEY_USER_SESSION, Json.encodeToString(user))
        settings.putBoolean(KEY_IS_LOGGED_IN, true)
    }
    
    fun getCurrentUser(): Usuario? {
        val userJson = settings.getString(KEY_USER_SESSION, "")
        if (userJson.isEmpty()) return null
        
        return try {
            Json.decodeFromString(userJson)
        } catch (e: Exception) {
            null
        }
    }
    
    fun isLoggedIn(): Boolean {
        return settings.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun clearSession() {
        settings.remove(KEY_USER_SESSION)
        settings.putBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun saveDarkThemeEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DARK_THEME, enabled)
    }

    fun getDarkThemeEnabled(): Boolean? {
        return if (settings.hasKey(KEY_DARK_THEME)) {
            settings.getBoolean(KEY_DARK_THEME, false)
        } else {
            null
        }
    }
}
