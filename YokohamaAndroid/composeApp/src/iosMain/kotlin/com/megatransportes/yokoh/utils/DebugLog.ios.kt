package com.megatransportes.yokoh.utils

import platform.Foundation.NSLog

actual object DebugLog {
    actual fun d(tag: String, message: String) {
        try {
            NSLog("%@: %@", tag, message)
        } catch (_: Throwable) {
        }
        try {
            println("$tag: $message")
        } catch (_: Throwable) {
        }
    }
}
