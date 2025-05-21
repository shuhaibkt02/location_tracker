package com.harmonyloop.location_tracker

import android.location.Location
import android.os.Build

fun Location.copy(): Location {
    val newLocation = Location(this.provider)
    newLocation.latitude = this.latitude
    newLocation.longitude = this.longitude
    newLocation.accuracy = this.accuracy
    newLocation.time = this.time
    
    if (this.hasAltitude()) {
        newLocation.altitude = this.altitude
    }
    
    if (this.hasSpeed()) {
        newLocation.speed = this.speed
    }
    
    if (this.hasBearing()) {
        newLocation.bearing = this.bearing
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.hasVerticalAccuracy()) {
        newLocation.verticalAccuracyMeters = this.verticalAccuracyMeters
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.hasSpeedAccuracy()) {
        newLocation.speedAccuracyMetersPerSecond = this.speedAccuracyMetersPerSecond
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.hasBearingAccuracy()) {
        newLocation.bearingAccuracyDegrees = this.bearingAccuracyDegrees
    }
    
    return newLocation
}
