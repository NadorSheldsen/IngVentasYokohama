package com.megatransportes.yokoh.utils

/**
 * Platform-agnostic resource loader for images/drawables.
 * On Android, this loads from R.drawable.*
 * On iOS, this loads from asset catalog or bundle resources.
 */
expect object ResourceLoader {
    /**
     * Get the resource ID for a drawable by name.
     * @param name The resource name (without extension on Android, with appropriate naming on iOS)
     * @return The resource ID or 0 if not found
     */
    fun getResourceId(name: String): Int
}
