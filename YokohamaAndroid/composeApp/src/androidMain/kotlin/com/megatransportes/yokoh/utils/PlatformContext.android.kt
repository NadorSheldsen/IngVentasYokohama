package com.megatransportes.yokoh.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun getPlatformContext(): Any? {
    return LocalContext.current
}
