package com.megatransportes.yokoh.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable
fun Logo(
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    LogoPlatform(modifier, contentScale)
}

@Composable
internal expect fun LogoPlatform(
    modifier: Modifier,
    contentScale: ContentScale
)
