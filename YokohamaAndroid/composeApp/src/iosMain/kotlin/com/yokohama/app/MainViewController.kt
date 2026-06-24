package com.yokohama.app



import androidx.compose.ui.window.ComposeUIViewController

import com.russhwolf.settings.NSUserDefaultsSettings

import platform.Foundation.NSUserDefaults

import com.megatransportes.yokohama.App



fun MainViewController() = ComposeUIViewController {

    val settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)

    App(settings)

}

