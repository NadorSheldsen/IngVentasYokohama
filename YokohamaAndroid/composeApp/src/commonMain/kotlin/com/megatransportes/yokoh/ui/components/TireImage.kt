package com.megatransportes.yokoh.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun TireImage(
    modifier: Modifier = Modifier,
    tintColor: Color? = null
) {
    TireImagePlatform(modifier = modifier, tintColor = tintColor)
}

@Composable
internal expect fun TireImagePlatform(
    modifier: Modifier,
    tintColor: Color?
)
