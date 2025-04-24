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

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: LocationResult? = null
    private var previousLocation: Location? = null
    private var totalDistanceKm: Double = 0.0
    private var isTracking = false

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

    fun startTracking() {
        if (!isTracking) {
            Log.d(TAG, "startTracking called, starting foreground service")
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            startLocationUpdates()
            isTracking = true
        } else {
            Log.d(TAG, "startTracking called, already tracking, updating notification")
            updateNotification()
        }
    }

    fun stopTracking() {
        Log.d(TAG, "stopTracking called")
        if (isTracking) {
            stopForeground(true)
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTracking = false
        }
        stopSelf()
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
             checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        ) {
            Log.e(TAG, "Missing required permissions, stopping service")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20 * 1000)
            .setMinUpdateIntervalMillis(10 * 1000)
            .setMinUpdateDistanceMeters(5f)
            .build()

        Log.d(TAG, "Requesting location updates")
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun updateDistanceAndNotification(locationResult: LocationResult) {
        val currentLocation = locationResult.lastLocation ?: run {
            Log.w(TAG, "Location is null. Will try again shortly.")
            return
        }

        checkAndResetDailyDistance()

        if (currentLocation.accuracy > 20f) {
            Log.d(TAG, "Ignored update due to poor accuracy: ${currentLocation.accuracy} meters")
            return
        }

        if (currentLocation.hasSpeed() && currentLocation.speed < 0.1f) {
            Log.d(TAG, "Ignored update due to low speed: ${currentLocation.speed} m/s")
            return
        }

        if (previousLocation != null) {
            val distanceMeters = previousLocation!!.distanceTo(currentLocation)
            val distanceKm = distanceMeters / 1000.0
            totalDistanceKm += distanceKm
            Log.d(TAG, "Distance updated: $totalDistanceKm KM")
        } else {
            Log.d(TAG, "First location received, no distance yet")
        }

        previousLocation = currentLocation
        if (isTracking) updateNotification()
    }

    private fun checkAndResetDailyDistance() {
        val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
        val lastReset = prefs.getLong("last_reset", 0L)

        // India-specific reset (00:00 IST)
        val istCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayIST = istCalendar.timeInMillis

        if (lastReset < todayIST) {
            totalDistanceKm = 0.0
            prefs.edit().putLong("last_reset", todayIST).apply()
            Log.d(TAG, "Reset at IST: ${istCalendar.time}")
        }
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
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        return buildNotification()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Notification updated with distance: $totalDistanceKm KM")
    }

    private fun buildNotification(): Notification {
        val distanceText = String.format("%.2f KM", totalDistanceKm)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Location")
            .setContentText("Salesman tracking is active | Distance: $distanceText")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun getLastLocation(): Location? = lastLocation?.lastLocation

    fun getTotalDistanceKm(): Double = totalDistanceKm

    fun isTracking(): Boolean = isTracking
}
