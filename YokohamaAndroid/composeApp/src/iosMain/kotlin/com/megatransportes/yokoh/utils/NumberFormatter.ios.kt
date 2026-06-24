package com.megatransportes.yokoh.utils

import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterNoStyle
import platform.Foundation.NSNumber

actual object NumberFormatter {
    actual fun formatWithComma(value: Double, decimals: Int): String {
        val formatter = NSNumberFormatter()
        formatter.numberStyle = NSNumberFormatterDecimalStyle
        formatter.minimumFractionDigits = decimals.toULong()
        formatter.maximumFractionDigits = decimals.toULong()
        return formatter.stringFromNumber(NSNumber(value)) ?: value.toString()
    }
    
    actual fun formatWithComma(value: Float, decimals: Int): String {
        return formatWithComma(value.toDouble(), decimals)
    }
}
