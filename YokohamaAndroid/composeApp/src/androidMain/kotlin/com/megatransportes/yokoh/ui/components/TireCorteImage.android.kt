package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@Composable
actual fun TireCorteImagePlatform(
    modifier: Modifier
) {
    val context = LocalContext.current
    val resourceId = context.resources.getIdentifier(
        "llanta_corte_t",
        "drawable",
        context.packageName
    )
    
    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Llanta corte",
            modifier = modifier
        )
    }
}
