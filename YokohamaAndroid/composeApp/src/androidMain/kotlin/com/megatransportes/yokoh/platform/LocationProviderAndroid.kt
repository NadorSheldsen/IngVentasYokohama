package com.megatransportes.yokoh.platform

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun getLastKnownLocation(): Location? = withContext(Dispatchers.IO) {
    val activity = ActivityHolder.activity ?: return@withContext null
    try {
        val ctx = activity
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return@withContext null

        val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (p in providers) {
            try {
                val l: AndroidLocation? = lm.getLastKnownLocation(p)
                if (l != null) {
                    return@withContext Location(l.latitude, l.longitude)
                }
            } catch (_: Exception) { }
        }
    } catch (_: Exception) { }
    return@withContext null
}
