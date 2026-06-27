package com.megatransportes.yokoh.utils

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual object DateFormatter {
    actual fun format(timestamp: Long, pattern: String): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = pattern
        val date = NSDate(timestamp / 1000.0)
        return formatter.stringFromDate(date)
    }
    
    actual fun format(year: Int, month: Int, day: Int, pattern: String): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = pattern
        
        val calendar = NSCalendar.currentCalendar
        val components = NSDateComponents()
        components.year = year.toLong()
        components.month = month.toLong()
        components.day = day.toLong()
        
        val date = calendar.dateFromComponents(components)
        return formatter.stringFromDate(date ?: NSDate())
    }
}
