package com.megatransportes.yokoh

actual fun getPlatformName(): String = "JVM"

actual fun disableIosScrollBounce() {
    // No-op on JVM desktop target.
}
