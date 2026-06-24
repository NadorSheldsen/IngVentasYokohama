package com.megatransportes.yokoh.utils

/**
 * Platform-agnostic time provider.
 */
expect object TimeProvider {
    /**
     * Get the current time in milliseconds.
     * @return Current time in milliseconds since epoch
     */
    fun getCurrentTimeMillis(): Long
}
