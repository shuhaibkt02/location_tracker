package com.harmonyloop.location_tracker

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var wakeLock: PowerManager.WakeLock
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var previousLocation: Location? = null
    private lateinit var locationDatabase: LocationDatabase
    private lateinit var locationProcessor: EnhancedLocationProcessor
    private lateinit var trackingSessionManager: TrackingSessionManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationBuffer = mutableListOf<LocationEntity>()
    private var isIndoor = false
    private var currentSessionId: String? = null
    private val config = TrackingConfig()
    private var isTrackingActive = false

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    private val binder = LocalBinder()

    fun isTracking(): Boolean {
        Log.d(TAG, "isTracking: isTrackingActive=$isTrackingActive, locationCallback=$locationCallback, wakeLockHeld=${wakeLock.isHeld}")
        return isTrackingActive && locationCallback != null && wakeLock.isHeld
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_service_channel"
        const val TAG = "LocationService"
        private const val WAKE_LOCK_TAG = "LocationService::WakeLock"
        private val totalDistanceKm = AtomicReference(0.0)
        fun getTotalDistanceKm(): Double = totalDistanceKm.get()
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationDatabase = LocationDatabase.getInstance(this)
        trackingSessionManager = TrackingSessionManager(this, locationDatabase)
        locationProcessor = EnhancedLocationProcessor()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand called")
    
    when (intent?.action) {
        "START_TRACKING" -> serviceScope.launch {
            try {
                startTracking()
            } catch (e: Exception) {
                Log.e(TAG, "Tracking failed: ${e.message}")
                LocationTrackingRecovery(this@LocationService)
                    .handleLocationServiceError(LocationError.ServiceError(e.message))
            }
        }
        "STOP_TRACKING" -> stopTracking()
    }
    return START_STICKY
    }

    suspend fun startTracking(): Boolean {
    if (isTrackingActive) return true
    
    try {
        // Move main-thread operations to coroutine context
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!wakeLock.isHeld) {
            wakeLock.acquire(TimeUnit.HOURS.toMillis(config.maxTrackingHours.toLong()))
        }
        
        startLocationUpdates()
        isTrackingActive = true
        Log.i(TAG, "Location tracking started")
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Foreground start failed: ${e.message}")
        throw e
    }
}

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, config.updateInterval)
            .setMinUpdateIntervalMillis(config.updateInterval / 2)
            .setMinUpdateDistanceMeters(config.minDistance)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                serviceScope.launch {
                    processLocationBatch(locationResult.locations)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested")
        } catch (e: SecurityException) {
            LocationTrackingRecovery(this).handleLocationServiceError(LocationError.PermissionDenied)
        } catch (e: Exception) {
            LocationTrackingRecovery(this).handleLocationServiceError(LocationError.ServiceError(e.message))
        }
    }

    private fun processLocationBatch(locations: List<Location>) {
        locations.forEach { rawLocation ->
            val processed = locationProcessor.processLocation(rawLocation)
            processed?.let {
                lastLocation = it.location
                isIndoor = it.isIndoor

                previousLocation?.let { prev ->
                    val distanceMeters = prev.distanceTo(it.location)
                    if (distanceMeters >= config.minDistance) {
                        val distanceKm = distanceMeters / 1000.0
                        totalDistanceKm.getAndUpdate { d -> d + distanceKm }

                        saveLocationToDatabase(it.location, distanceKm)
                        updateNotification()
                    }
                }

                previousLocation = it.location
            }
        }
        flushLocationBuffer()
    }

    private fun saveLocationToDatabase(location: Location, distance: Double) {
        serviceScope.launch {
            val locationEntity = LocationEntity(
                timestamp = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                distanceFromPrevious = distance,
                sessionId = currentSessionId ?: trackingSessionManager.startDailySession(),
                isIndoor = isIndoor
            )
            locationBuffer.add(locationEntity)
            if (locationBuffer.size >= 10) flushLocationBuffer()
        }
    }

    private fun flushLocationBuffer() {
        serviceScope.launch {
            if (locationBuffer.isNotEmpty()) {
                locationDatabase.locationDao().insertLocations(locationBuffer.toList())
                locationBuffer.clear()
            }
        }
    }

    private fun updateNotification() {
        val distanceText = String.format("%.2f KM", totalDistanceKm.get())
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ozone Tracking Active")
            .setContentText("Distance: $distanceText")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for sales tracking in the background"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val distanceText = String.format("%.2f KM", totalDistanceKm.get())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Tracking Active")
            .setContentText("Distance: $distanceText")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun getLastLocation(): Location? = lastLocation

    fun isIndoor(): Boolean = isIndoor

    fun pauseTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        Log.d(TAG, "Tracking paused")
    }

    fun resumeTracking() {
        if (isTrackingActive && locationCallback == null) {
            startLocationUpdates()
            Log.d(TAG, "Tracking resumed")
        }
    }

    fun stopTracking(): Boolean {
        if (isTrackingActive) {
            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
                locationCallback = null
            }

            flushLocationBuffer()

            currentSessionId?.let { sessionId ->
                serviceScope.launch {
                    val repo = LocationRepository(this@LocationService)
                    val session = repo.sessionDao.getSession(sessionId)
                    session?.let {
                        repo.sessionDao.completeSession(
                            sessionId = sessionId,
                            endTime = System.currentTimeMillis(),
                            distance = totalDistanceKm.get(),
                            status = SessionStatus.COMPLETED.name
                        )
                    }
                }
            }

            if (wakeLock.isHeld) {
                try {
                    wakeLock.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing wake lock", e)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

            stopSelf()
            isTrackingActive = false
            Log.i(TAG, "Location tracking stopped")
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        Log.d(TAG, "Service destroyed")
    }
}
