package com.megatransportes.yokoh.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLLocation
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
    val manager = CLLocationManager()

    val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val loc = didUpdateLocations.lastOrNull() as? CLLocation
            if (!cont.isCompleted) {
                cont.resume(loc?.let { 
                    it.coordinate.useContents {
                        Location(latitude, longitude)
                    }
                })
            }
            manager.stopUpdatingLocation()
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
            if (!cont.isCompleted) cont.resume(null)
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val status = CLLocationManager.authorizationStatus()
            when (status) {
                kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> {
                    manager.requestLocation()
                }
                kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> {
                    if (!cont.isCompleted) cont.resume(null)
                }
            }
        }
    }

    manager.delegate = delegate

    val status = CLLocationManager.authorizationStatus()
    when (status) {
        kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> {
            val last = manager.location
            if (last != null) {
                cont.resume(last.coordinate.useContents { Location(latitude, longitude) })
            } else {
                manager.requestLocation()
            }
        }
        kCLAuthorizationStatusNotDetermined -> {
            manager.requestWhenInUseAuthorization()
        }
        kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> {
            cont.resume(null)
        }
        else -> {
            manager.requestLocation()
        }
    }

    cont.invokeOnCancellation {
        manager.stopUpdatingLocation()
        manager.delegate = null
    }
}
