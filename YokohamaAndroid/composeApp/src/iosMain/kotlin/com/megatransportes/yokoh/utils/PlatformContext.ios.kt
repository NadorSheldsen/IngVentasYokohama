package com.megatransportes.yokoh.utils

import androidx.compose.runtime.Composable
import platform.UIKit.UIViewController

@Composable
actual fun getPlatformContext(): Any? {
    // On iOS, we don't have a direct Compose context equivalent
    // The platform-specific implementations can work with null or get the view controller differently
    return null
}
