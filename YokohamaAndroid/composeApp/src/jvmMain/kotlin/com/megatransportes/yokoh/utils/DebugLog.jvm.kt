package com.megatransportes.yokoh.utils

actual object DebugLog {
    actual fun d(tag: String, message: String) {
        println("[$tag] $message")
    }
}
