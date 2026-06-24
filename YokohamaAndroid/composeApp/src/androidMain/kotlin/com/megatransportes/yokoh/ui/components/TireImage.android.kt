package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@Composable
actual fun TireImagePlatform(
    modifier: Modifier,
    tintColor: Color?
) {
    val context = LocalContext.current
    val resourceId = context.resources.getIdentifier(
        "llantacolor",
        "drawable",
        context.packageName
    )
    
    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Llanta",
            modifier = modifier,
            colorFilter = tintColor?.let { ColorFilter.tint(it) }
        )
    }
}
