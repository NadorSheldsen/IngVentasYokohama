package com.megatransportes.yokoh.utils

/**
 * Platform-agnostic number formatting utilities.
 */
expect object NumberFormatter {
    /**
     * Format a number with thousand separators.
     * @param value The number to format
     * @param decimals Number of decimal places (default: 0)
     * @return Formatted string
     */
    fun formatWithComma(value: Double, decimals: Int = 0): String
    
    /**
     * Format a number with thousand separators.
     * @param value The number to format
     * @param decimals Number of decimal places (default: 0)
     * @return Formatted string
     */
    fun formatWithComma(value: Float, decimals: Int = 0): String
}
