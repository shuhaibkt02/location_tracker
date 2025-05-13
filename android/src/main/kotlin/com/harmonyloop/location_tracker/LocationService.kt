package com.harmonyloop.location_tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: LocationResult? = null
    private var previousLocation: Location? = null
    private val totalDistanceKm = AtomicReference<Double>(0.0)
    private var isTracking = false
    private val executor = Executors.newSingleThreadExecutor()

    private val kalmanFilter = KalmanFilter()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            lastLocation = locationResult
            Log.d(TAG, "Location update received: ${locationResult.lastLocation}")
            updateDistanceAndNotification(locationResult)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    private val binder = LocalBinder()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_service_channel"
        private const val TAG = "LocationService"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        Log.d(TAG, "Service created, waiting for startTracking")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_STICKY
    }

    fun startTracking(): Boolean {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission, cannot start tracking")
            return false
        }
        if (!isTracking) {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            startLocationUpdates()
            isTracking = true
        }
        return true
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20 * 1000)
            .setMinUpdateIntervalMillis(10 * 1000)
            .setMinUpdateDistanceMeters(5f)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
    private fun updateDistanceAndNotification(locationResult: LocationResult) {
    val currentLocation = locationResult.lastLocation ?: return


    Log.d(TAG, "Location update received: $currentLocation")


    if (currentLocation.accuracy > 20) {
        Log.w(TAG, "Ignoring location due to poor accuracy: ${currentLocation.accuracy} meters")
        return
    }


    if (previousLocation == null) {
        previousLocation = currentLocation
        Log.d(TAG, "Initialized previousLocation: $previousLocation")
        return
    }

    val distanceMeters = previousLocation!!.distanceTo(currentLocation)


    if (distanceMeters > 500) {
        Log.w(TAG, "Ignoring large distance: $distanceMeters meters")
        previousLocation = currentLocation
        return
    }

    Log.d(TAG, "Raw Distance: $distanceMeters meters")

    if (distanceMeters > 5) {
        val distanceKm = distanceMeters / 1000.0
        totalDistanceKm.getAndUpdate { it + distanceKm }
        Log.d(TAG, "Accumulated Distance: ${totalDistanceKm.get()} km")
    }

    previousLocation = currentLocation
}


    private fun updateNotification() {
        val distanceText = String.format("%.2f KM", totalDistanceKm.get())
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val distanceText = String.format("%.2f KM", totalDistanceKm.get())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Tracking")
            .setContentText("Distance covered: $distanceText")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun getLastLocation(): Location? = lastLocation?.lastLocation

    fun getTotalDistanceKm(): Double = totalDistanceKm.get()

    fun isTracking(): Boolean = isTracking

    fun stopTracking(): Boolean {
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            stopForeground(true)
            stopSelf()
            isTracking = false
        }
        return true
    }
}

class KalmanFilter {

    private var estimate: Location? = null
    private var variance = 1.0
    private var velocity = 0.0
    private var acceleration = 0.0
    private var bearing = 0.0
    private val baseProcessNoise = 1.0
    private val minSpeedThreshold = 0.1
    private val correctionFactor = 1.05

    fun applyFilter(location: Location): Location {
        if (estimate == null) {
            estimate = location
            velocity = if (location.hasSpeed()) location.speed.toDouble() else 0.0
            bearing = if (location.hasBearing()) location.bearing.toDouble() else 0.0
            return estimate as Location
        }

        val timeDelta = (location.time - (estimate!!.time)).toDouble() / 1000.0
        val accuracy = location.accuracy.toDouble()
        val speed = if (location.hasSpeed()) location.speed.toDouble() else velocity
        val processNoise = baseProcessNoise + (accuracy / 10.0) + (Math.abs(acceleration) / 10.0)
        val measurementNoise = accuracy * accuracy
        val gain = variance / (variance + measurementNoise)

        val predictedLat = estimate!!.latitude + velocity * timeDelta * Math.cos(Math.toRadians(bearing)) / 111320.0
        val predictedLon = estimate!!.longitude + velocity * timeDelta * Math.sin(Math.toRadians(bearing)) / (111320.0 * Math.cos(Math.toRadians(estimate!!.latitude)))

        val newLatitude = predictedLat + gain * (location.latitude - predictedLat)
        val newLongitude = predictedLon + gain * (location.longitude - predictedLon)

        estimate?.latitude = newLatitude
        estimate?.longitude = newLongitude

        if (speed > minSpeedThreshold) {
            acceleration = (speed - velocity) / timeDelta
            velocity = speed
        } else {
            velocity = 0.0
            acceleration = 0.0
        }

        if (location.hasBearing()) {
            bearing = location.bearing.toDouble()
        }

        variance = (1 - gain) * variance + processNoise

        estimate?.latitude = estimate!!.latitude * correctionFactor
        estimate?.longitude = estimate!!.longitude * correctionFactor

        return estimate as Location
    }
}
