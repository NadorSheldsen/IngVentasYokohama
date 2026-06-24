package com.megatransportes.yokoh

class Greeting {
    private val platform = getPlatformName()

    fun greet(): String {
        return "Hello, $platform!"
    }
}