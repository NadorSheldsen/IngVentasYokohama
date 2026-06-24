package com.megatransportes.yokoh

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformName(): String = "iOS"