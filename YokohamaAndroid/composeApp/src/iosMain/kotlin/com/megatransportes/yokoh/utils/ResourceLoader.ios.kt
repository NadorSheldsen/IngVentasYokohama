package com.megatransportes.yokoh.utils

actual object ResourceLoader {
    actual fun getResourceId(name: String): Int {
        // On iOS, we use a simple hash of the name as a resource ID
        // This is a placeholder - proper iOS resource loading would require
        // more complex setup with asset catalogs
        return name.hashCode()
    }
}
