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
        loadDistance()
        loadTrackingState()
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
            Log.d(TAG, "startTracking called, starting foreground service")
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            startLocationUpdates()
            isTracking = true
            saveTrackingState()
        } else {
            Log.d(TAG, "startTracking called, already tracking, updating notification")
            updateNotification()
        }
        return true
    }

    fun stopTracking() {
        Log.d(TAG, "stopTracking called")
        if (isTracking) {
            stopForeground(true)
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTracking = false
            saveTrackingState()
        }
        stopSelf()
    }

    private fun loadTrackingState() {
        val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
        isTracking = prefs.getBoolean("is_tracking", false)
        if (isTracking) {
            Log.d(TAG, "Restoring tracking state after service restart")
            startTracking()
        }
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "ACCESS_FINE_LOCATION permission denied")
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

    private fun saveDistance() {
        executor.execute {
            val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
            prefs.edit().putString("total_distance_km", totalDistanceKm.get().toString()).apply()
        }
    }

    private fun loadDistance() {
        val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
        totalDistanceKm.set(prefs.getString("total_distance_km", "0.0")?.toDoubleOrNull() ?: 0.0)
        Log.d(TAG, "Loaded distance: ${totalDistanceKm.get()} KM")
    }

    private fun saveTrackingState() {
        executor.execute {
            val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("is_tracking", isTracking).apply()
        }
    }

    private fun checkAndResetDailyDistance() {
        val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
        val lastReset = prefs.getLong("last_reset", 0L)
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = calendar.timeInMillis

        if (lastReset < today) {
            totalDistanceKm.set(0.0)
            executor.execute {
                val resetPrefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
                resetPrefs.edit()
                    .putLong("last_reset", today)
                    .putString("total_distance_km", "0.0")
                    .apply()
            }
            Log.d(TAG, "Reset at ${TimeZone.getDefault().id}: ${calendar.time}")
        }
    }

    private fun updateDistanceAndNotification(locationResult: LocationResult) {
        val currentLocation = locationResult.lastLocation ?: run {
            Log.w(TAG, "Location is null. Will try again shortly.")
            return
        }
        checkAndResetDailyDistance()
        val prefs = getSharedPreferences("LocationPrefs", MODE_PRIVATE)
        val accuracyThreshold = prefs.getFloat("accuracy_threshold", 30f)
        if (currentLocation.accuracy > accuracyThreshold) {
            Log.d(TAG, "Ignored update due to poor accuracy: ${currentLocation.accuracy} meters")
            return
        }
        if (currentLocation.hasSpeed() && currentLocation.speed < 0.1f) {
            Log.d(TAG, "Ignored update due to low speed: ${currentLocation.speed} m/s")
            return
        }
        var distanceChanged = false
        synchronized(this) {
            previousLocation?.let { prev ->
                val distanceMeters = prev.distanceTo(currentLocation)
                val distanceKm = distanceMeters / 1000.0
                totalDistanceKm.getAndUpdate { it + distanceKm }
                if (distanceKm >= 0.01) { // Only update if change is significant
                    distanceChanged = true
                }
                saveDistance()
                Log.d(TAG, "Distance updated: ${totalDistanceKm.get()} KM")
            } ?: run {
                Log.d(TAG, "First location received, no distance yet")
            }
            previousLocation = currentLocation
        }
        if (isTracking && distanceChanged) {
            updateNotification()
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
        val distanceText = String.format("%.2f KM", totalDistanceKm.get())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ozone Tracking")
            .setContentText("Tracking is active | Distance covered: $distanceText")
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
            Log.d(TAG, "Location updates removed due to service destruction")
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun getLastLocation(): Location? = lastLocation?.lastLocation

    fun getTotalDistanceKm(): Double = totalDistanceKm.get()

    fun isTracking(): Boolean = isTracking
}
