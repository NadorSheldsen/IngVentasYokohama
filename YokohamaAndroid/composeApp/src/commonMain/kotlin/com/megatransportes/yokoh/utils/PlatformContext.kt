package com.megatransportes.yokoh.utils

import androidx.compose.runtime.Composable

/**
 * Platform-agnostic way to get the platform context.
 * On Android: returns Context
 * On iOS: returns UIViewController or null
 * On other platforms: returns null
 */
@Composable
expect fun getPlatformContext(): Any?
