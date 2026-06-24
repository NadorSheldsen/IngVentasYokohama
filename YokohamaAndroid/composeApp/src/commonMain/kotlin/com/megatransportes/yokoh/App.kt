package com.megatransportes.yokoh

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.megatransportes.yokoh.data.api.ApiClient
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.data.session.SessionManager
import com.megatransportes.yokoh.ui.AppNavigation
import com.megatransportes.yokoh.ui.Navigator
import com.megatransportes.yokoh.ui.Screen
import com.megatransportes.yokoh.ui.theme.YokohamaTheme
import com.russhwolf.settings.Settings

@Composable
fun App(navigator: Navigator? = null) {
    App(settings = Settings(), navigator = navigator)
}

@Composable
fun App(settings: Settings, navigator: Navigator? = null) {
    val sessionManager = remember { SessionManager(settings) }
    val systemDarkTheme = isSystemInDarkTheme()
    var darkThemeEnabled by remember {
        mutableStateOf(sessionManager.getDarkThemeEnabled() ?: systemDarkTheme)
    }

    YokohamaTheme(darkTheme = darkThemeEnabled) {
    // ApiClient default uses the production HTTPS host; override here for local dev if needed.
    // For local emulator testing you can pass "https://pensive-saha.207-210-229-77.plesk.page/api" when running locally.
    val apiClient = remember { ApiClient("http://attractive-rose-beagle.201-131-126-228.cpanel.site/api") }
        val repository = remember { YokohamaRepository(apiClient, sessionManager) }
        val nav = remember {
            navigator ?: Navigator(if (repository.isLoggedIn()) Screen.Flotas else Screen.Login)
        }

        // If a navigator was injected (from Android MainActivity), ensure its root matches login state
        if (navigator != null) {
            LaunchedEffect(repository.isLoggedIn()) {
                val root = if (repository.isLoggedIn()) Screen.Flotas else Screen.Login
                nav.setRoot(root)
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(
                repository = repository,
                navigator = nav,
                isDarkTheme = darkThemeEnabled,
                onToggleTheme = {
                    val newValue = !darkThemeEnabled
                    darkThemeEnabled = newValue
                    sessionManager.saveDarkThemeEnabled(newValue)
                }
            )
        }
    }
}