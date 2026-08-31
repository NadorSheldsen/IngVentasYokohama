package com.megatransportes.yokoh.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual object DateFormatter {
    actual fun format(timestamp: Long, pattern: String): String {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        return formatter.format(Date(timestamp))
    }

    actual fun format(year: Int, month: Int, day: Int, pattern: String): String {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)
        return formatter.format(calendar.time)
    }
}
