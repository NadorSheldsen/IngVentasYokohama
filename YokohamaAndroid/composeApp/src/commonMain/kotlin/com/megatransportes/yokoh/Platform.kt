package com.megatransportes.yokoh

expect fun getPlatformName(): String

/**
 * Deshabilita el bounce del UIScrollView en iOS.
 * En Android es no-op.
 */
expect fun disableIosScrollBounce(view: Any?)