package com.megatransportes.yokoh.utils

import java.text.NumberFormat
import java.util.Locale

actual object NumberFormatter {
    actual fun formatWithComma(value: Double, decimals: Int): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        formatter.minimumFractionDigits = decimals
        formatter.maximumFractionDigits = decimals
        return formatter.format(value)
    }
    
    actual fun formatWithComma(value: Float, decimals: Int): String {
        return formatWithComma(value.toDouble(), decimals)
    }
}
