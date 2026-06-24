package com.megatransportes.yokoh.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TireCorteImage(
    modifier: Modifier = Modifier
) {
    TireCorteImagePlatform(modifier = modifier)
}

@Composable
internal expect fun TireCorteImagePlatform(
    modifier: Modifier
)
