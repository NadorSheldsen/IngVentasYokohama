package com.megatransportes.yokoh.utils

actual object ResourceLoader {
    actual fun getResourceId(name: String): Int {
        return try {
            val field = com.megatransportes.yokoh.R.drawable::class.java.getField(name)
            field.getInt(null)
        } catch (e: Exception) {
            try {
                val field = com.megatransportes.yokoh.R.drawable::class.java.getField(name.lowercase())
                field.getInt(null)
            } catch (e2: Exception) {
                0
            }
        }
    }
}
