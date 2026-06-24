package com.megatransportes.yokoh.utils

import android.util.Log

actual object DebugLog {
    actual fun d(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
        }
        try {
            System.err.println("$tag: $message")
        } catch (_: Throwable) {
        }
    }
}
