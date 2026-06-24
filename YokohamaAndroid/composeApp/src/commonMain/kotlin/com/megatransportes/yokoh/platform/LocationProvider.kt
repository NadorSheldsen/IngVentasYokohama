package com.megatransportes.yokoh.platform

import kotlinx.serialization.Serializable

@Serializable
data class Location(val latitude: Double, val longitude: Double)

// Return last known location or null if unavailable
expect suspend fun getLastKnownLocation(): Location?
