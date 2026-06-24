package com.megatransportes.yokoh.utils

import platform.Foundation.NSDate
import platform.Foundation.NSTimeInterval
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual object TimeProvider {
    actual fun getCurrentTimeMillis(): Long {
        val date = NSDate()
        val timeInterval = date.timeIntervalSinceReferenceDate
        val kCFAbsoluteTimeIntervalSince1970: NSTimeInterval = 978307200.0
        return ((timeInterval + kCFAbsoluteTimeIntervalSince1970) * 1000).toLong()
    }
}
