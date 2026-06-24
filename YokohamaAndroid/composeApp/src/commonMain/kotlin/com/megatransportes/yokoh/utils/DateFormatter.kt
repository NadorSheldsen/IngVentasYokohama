package com.megatransportes.yokoh.utils

/**
 * Platform-agnostic date formatting utilities.
 */
expect object DateFormatter {
    /**
     * Format a timestamp to a date string.
     * @param timestamp The timestamp in milliseconds
     * @param pattern The date pattern (e.g., "dd/MM/yyyy")
     * @return Formatted date string
     */
    fun format(timestamp: Long, pattern: String): String
    
    /**
     * Format a date to a string.
     * @param year Year
     * @param month Month (1-12)
     * @param day Day
     * @param pattern The date pattern (e.g., "dd/MM/yyyy")
     * @return Formatted date string
     */
    fun format(year: Int, month: Int, day: Int, pattern: String): String
}
