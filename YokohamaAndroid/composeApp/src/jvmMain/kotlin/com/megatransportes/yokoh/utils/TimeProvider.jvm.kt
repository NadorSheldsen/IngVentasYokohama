package com.megatransportes.yokoh.utils

actual object TimeProvider {
    actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
}
