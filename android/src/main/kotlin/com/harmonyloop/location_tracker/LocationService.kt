package com.harmonyloop.location_tracker

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.os.Handler
import java.util.concurrent.TimeUnit
import java.util.Calendar

class DistanceTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: LocationRepository
    private var lastLocation: Location? = null
    private var isBackgroundMode = false
    
    // Enhanced location state management
    private var isWaitingForInitialFix = true
    private var initialLocationAttempts = 0
    private var gpsAcquisitionStartTime = 0L
    private var consecutiveLocationFailures = 0
    private var lastLocationTime = 0L
    private var currentLocationProvider = LocationProvider.UNKNOWN
    
    // WakeLock for preventing CPU sleep during location updates
    private var wakeLock: PowerManager.WakeLock? = null
    private var isWakeLockHeld = false
    
    // Handler for timeout management
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var gpsTimeoutRunnable: Runnable? = null
    private var shiftRolloverRunnable: Runnable? = null
    private var lastRecordedDate: String = ""
    private var isIntentionalStop: Boolean = false
    private var isProviderReceiverRegistered: Boolean = false

    private val providerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkLocationProviderState()
                checkPermissionsMidShift()
            }
        }
    }

    private var notificationTitle: String = "Distance Tracking"

    companion object {
        private const val NOTIF_ID = 101
        private const val CHANNEL_ID = "distance_tracker"
        const val STOP_ACTION = "STOP_TRACKING"
        const val STOP_FROM_MIDNIGHT_ACTION = "STOP_FROM_MIDNIGHT"
        private const val LOCATION_SETTINGS_REQUEST = 1001
        private const val MAX_LOCATION_ACCURACY_M = 50.0f
        
        // Enhanced timing constants
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 8000L
        private const val FOREGROUND_MIN_UPDATE_INTERVAL_MS = 4000L
        private const val BACKGROUND_UPDATE_INTERVAL_MS = 30000L
        private const val BACKGROUND_MIN_UPDATE_INTERVAL_MS = 15000L
        
        // GPS acquisition and timeout settings
        private const val MAX_INITIAL_ATTEMPTS = 5
        private const val GPS_TIMEOUT_MS = 60000L // 1 minute
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val LOCATION_STALENESS_MS = 120000L // 2 minutes
        private const val LAST_KNOWN_LOCATION_MAX_AGE_MS = 300000L // 5 minutes
        
        private const val WAKE_LOCK_TAG = "DistanceTracker:LocationWakeLock"
    }
    
    // Location provider priority enum
    private enum class LocationProvider {
        GPS, NETWORK, PASSIVE, FUSED, UNKNOWN
    }

    override fun onCreate() {
        super.onCreate()

        try {
            LogHelper.log("=== DistanceTrackingService initialization started ===")
            
            // Step 1: Initialize WakeLock first
            initializeWakeLock()
            
            // Step 2: Create notification channel
            createNotificationChannel()

            // Step 3: Initialize persistent storage
            DistanceStorage.init(applicationContext)

            // Step 4: Initialize repository (loads existing data)
            repository = LocationRepository(this)

            // Step 5: Start foreground notification
            val initialNotif = createNotification("Initializing location services...", false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                androidx.core.app.ServiceCompat.startForeground(
                    this,
                    NOTIF_ID,
                    initialNotif,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                )
            } else {
                startForeground(NOTIF_ID, initialNotif)
            }

            // Step 6: Diagnose location services availability
            val diagnostics = diagnoseLocationServices()
            LogHelper.log("Location services diagnostics: $diagnostics")

            // Step 7: Set up GPS with fallback strategy
            setupLocationUpdates()

            // Step 8: Check and configure location settings
            checkLocationSettings()

            // Step 9: Show current distance from loaded data
            val currentDistance = repository.getTotalDistanceToday()
            updateNotification("Ready - Distance: %.2f m".format(currentDistance), true)
            
            // Step 10: Schedule shift rollover
            lastRecordedDate = getTodayDateString()
            scheduleShiftRollover()

            // Step 11: Register provider change receiver for security alert detection
            val filter = android.content.IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            registerReceiver(providerReceiver, filter)
            isProviderReceiverRegistered = true

            LogHelper.log("DistanceTrackingService initialized successfully with distance: %.2f m".format(currentDistance))
            
        } catch (e: Exception) {
            LogHelper.logError("Critical error initializing DistanceTrackingService", e)
            updateNotification("Initialization failed", false)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP_ACTION || intent?.action == STOP_FROM_MIDNIGHT_ACTION) {
            LogHelper.log("Stop action received from notification or midnight alarm")
            isIntentionalStop = true
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == "UPDATE_NOTIFICATION_TITLE") {
            val newTitle = intent.getStringExtra("title")
            if (!newTitle.isNullOrEmpty()) {
                notificationTitle = newTitle
                updateNotification(null, true)
            }
            return START_STICKY
        }

        LogHelper.log("DistanceTrackingService start command received")

        if (intent == null) {
            LogHelper.log("Service restarted by system (intent == null)")
            if (CrashProtector.shouldHaltAutoRestart(this)) {
                LogHelper.logError("Crash circuit breaker tripped: stopping auto-restart")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        timeoutHandler.postDelayed({ CrashProtector.markSuccessfulRun(this) }, 10000L)

        val currentConfig = ConfigStorage.loadConfig(this)
        intent?.let {
            val title = it.getStringExtra("notificationTitle")
            val template = it.getStringExtra("notificationBodyTemplate")
            val icon = it.getStringExtra("notificationIconResource")
            val interval = it.getLongExtra("updateIntervalMs", -1L)
            if (title != null || interval > 0) {
                val updated = currentConfig.copy(
                    notificationTitle = title ?: currentConfig.notificationTitle,
                    notificationBodyTemplate = template ?: currentConfig.notificationBodyTemplate,
                    notificationIconResource = icon ?: currentConfig.notificationIconResource,
                    updateIntervalMs = if (interval > 0) interval else currentConfig.updateIntervalMs
                )
                ConfigStorage.saveConfig(this, updated)
                notificationTitle = updated.notificationTitle
            }
        }
        ConfigStorage.setTrackingActive(this, true)
        LocationRepository.isTracking = true
        
        if (hasAllRequiredPermissions()) {
            startLocationTracking()
        } else {
            LogHelper.logError("Required permissions not granted")
            updateNotification("Location permissions required", false, createAppSettingsIntent())
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            LogHelper.log("=== DistanceTrackingService shutdown started (intentional=$isIntentionalStop) ===")
            if (isIntentionalStop) {
                ConfigStorage.setTrackingActive(this, false)
                LocationRepository.isTracking = false
            } else {
                LogHelper.log("OS teardown / task swipe: preserving is_tracking_active=true for auto-resume")
            }

            if (isProviderReceiverRegistered) {
                try {
                    unregisterReceiver(providerReceiver)
                } catch (_: Exception) {}
                isProviderReceiverRegistered = false
            }
            
            // Cancel any pending timeouts
            gpsTimeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            
            // Stop location updates
            if (::fusedLocationClient.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                LogHelper.log("Location updates stopped")
            }
            
            // Release WakeLock
            releaseWakeLock()
            
            // Stop foreground service
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            // Cancel shift rollover when service stops
            shiftRolloverRunnable?.let { timeoutHandler.removeCallbacks(it) }
            
            LogHelper.log("DistanceTrackingService destroyed successfully")
        } catch (e: Exception) {
            LogHelper.logError("Error during service destruction", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Comprehensive location services diagnostic
     */
    private fun diagnoseLocationServices(): Map<String, Any> {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val diagnostics = mutableMapOf<String, Any>()
        
        // Check individual providers
        diagnostics["gps_enabled"] = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        diagnostics["network_enabled"] = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        diagnostics["passive_enabled"] = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
        
        // Check available providers
        val allProviders = locationManager.getAllProviders()
        val enabledProviders = locationManager.getProviders(true)
        diagnostics["all_providers"] = allProviders
        diagnostics["enabled_providers"] = enabledProviders
        
        // Check system location setting
        try {
            val locationMode = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE
            )
            diagnostics["location_mode"] = when (locationMode) {
                Settings.Secure.LOCATION_MODE_OFF -> "OFF"
                Settings.Secure.LOCATION_MODE_SENSORS_ONLY -> "GPS_ONLY"
                Settings.Secure.LOCATION_MODE_BATTERY_SAVING -> "NETWORK_ONLY"
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY -> "HIGH_ACCURACY"
                else -> "UNKNOWN($locationMode)"
            }
        } catch (e: Exception) {
            diagnostics["location_mode"] = "UNAVAILABLE"
        }
        
        // Check permissions
        diagnostics["fine_location_permission"] = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        diagnostics["coarse_location_permission"] = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        diagnostics["background_location_permission"] = hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        
        return diagnostics
    }

    /**
     * Enhanced location updates setup with multiple strategies
     */
    private fun setupLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val updateInterval = if (isBackgroundMode) BACKGROUND_UPDATE_INTERVAL_MS else FOREGROUND_UPDATE_INTERVAL_MS
        val minUpdateInterval = if (isBackgroundMode) BACKGROUND_MIN_UPDATE_INTERVAL_MS else FOREGROUND_MIN_UPDATE_INTERVAL_MS

        // Primary high-accuracy request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
            .setMinUpdateIntervalMillis(minUpdateInterval)
            .setWaitForAccurateLocation(false) // Don't block for accuracy initially
            .setMaxUpdateDelayMillis(updateInterval * 2)
            .setMinUpdateDistanceMeters(0.5f)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()

        setupLocationCallback()
        LogHelper.log("Location request configured: interval=${updateInterval}ms, background=$isBackgroundMode")
    }

    /**
     * Enhanced location callback with intelligent error handling
     */
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!hasAllRequiredPermissions()) {
                    LogHelper.logError("Permissions revoked mid-tracking session!")
                    updateNotification("Tracking paused: Location permission required", false, createAppSettingsIntent())
                    val intent = Intent("com.harmonyloop.location_tracker.PERMISSION_REVOKED").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                    return
                }
                result.lastLocation?.let { location ->
                    try {
                        handleLocationSuccess(location)
                    } catch (e: Exception) {
                        LogHelper.logError("Error processing location result", e)
                        handleLocationError("Processing error: ${e.message}")
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                handleLocationAvailability(availability)
            }
        }
    }

    /**
     * Handle successful location acquisition
     */
    private fun handleLocationSuccess(location: Location) {
        lastLocationTime = System.currentTimeMillis()
        consecutiveLocationFailures = 0
        
        // Determine location provider
        currentLocationProvider = when {
            location.provider?.contains("gps", ignoreCase = true) == true -> LocationProvider.GPS
            location.provider?.contains("network", ignoreCase = true) == true -> LocationProvider.NETWORK
            location.provider?.contains("fused", ignoreCase = true) == true -> LocationProvider.FUSED
            else -> LocationProvider.UNKNOWN
        }

        if (isWaitingForInitialFix) {
            val acquisitionTime = (System.currentTimeMillis() - gpsAcquisitionStartTime) / 1000.0
            isWaitingForInitialFix = false
            initialLocationAttempts = 0
            
            // Cancel GPS timeout
            gpsTimeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            
            LogHelper.log("Initial location fix acquired in ${acquisitionTime}s via ${currentLocationProvider.name}")
            updateNotification("GPS connected - tracking active", true)
        }

        // Log location quality
        val qualityInfo = "Provider: ${currentLocationProvider.name}, " +
                         "Accuracy: ${location.accuracy}m, " +
                         "Speed: ${if (location.hasSpeed()) "%.1f m/s".format(location.speed) else "N/A"}"
        
        if (location.accuracy <= MAX_LOCATION_ACCURACY_M) {
            LogHelper.log("High quality location received - $qualityInfo")
            handleNewLocation(location)
        } else {
            LogHelper.log("Location accuracy too low (${location.accuracy}m) - $qualityInfo")
            updateNotification("Improving GPS accuracy...", true)
        }
    }

    /**
     * Enhanced location availability handling
     */
    private fun handleLocationAvailability(availability: LocationAvailability) {
        if (!availability.isLocationAvailable) {
            consecutiveLocationFailures++
            
            if (isWaitingForInitialFix) {
                initialLocationAttempts++
                val elapsed = (System.currentTimeMillis() - gpsAcquisitionStartTime) / 1000
                
                LogHelper.log("GPS acquisition attempt $initialLocationAttempts/$MAX_INITIAL_ATTEMPTS (${elapsed}s elapsed)")
                
                when {
                    initialLocationAttempts <= 2 -> {
                        updateNotification("Searching for GPS signal...", false)
                    }
                    initialLocationAttempts <= MAX_INITIAL_ATTEMPTS -> {
                        updateNotification("GPS signal weak - trying network location...", false)
                        // Try to get network-based location as fallback
                        tryNetworkLocationFallback()
                    }
                    else -> {
                        LogHelper.logError("Failed to acquire GPS after $MAX_INITIAL_ATTEMPTS attempts and ${elapsed}s")
                        handleLocationError("GPS unavailable after ${elapsed}s")
                    }
                }
            } else {
                // GPS was working but now lost
                val timeSinceLastLocation = (System.currentTimeMillis() - lastLocationTime) / 1000
                LogHelper.logError("GPS signal lost during tracking (${timeSinceLastLocation}s since last location)")
                
                if (consecutiveLocationFailures >= MAX_CONSECUTIVE_FAILURES) {
                    updateNotification("GPS connection lost - attempting recovery...", true)
                    attemptLocationRecovery()
                } else {
                    updateNotification("GPS signal weak - reconnecting...", true)
                }
            }
            
            broadcastLocationAvailability(false)
            
        } else {
            // Location available again
            if (consecutiveLocationFailures > 0) {
                LogHelper.log("GPS signal restored after $consecutiveLocationFailures failures")
                consecutiveLocationFailures = 0
                
                if (!isWaitingForInitialFix) {
                    updateNotification("GPS signal restored", true)
                }
            }
            
            broadcastLocationAvailability(true)
        }
    }

    /**
     * Handle location errors with appropriate user feedback
     */
    private fun handleLocationError(message: String) {
        LogHelper.logError("Location error: $message")
        
        when {
            !hasAllRequiredPermissions() -> {
                updateNotification("Location permission required", false, createAppSettingsIntent())
            }
            !isLocationEnabled() -> {
                updateNotification("Enable location services", false, createLocationSettingsIntent())
            }
            isWaitingForInitialFix -> {
                updateNotification("GPS unavailable - check location settings", false, createLocationSettingsIntent())
            }
            else -> {
                updateNotification("Location error - trying to recover...", true)
                attemptLocationRecovery()
            }
        }
    }

    /**
     * Try network-based location as fallback when GPS fails
     */
    private fun tryNetworkLocationFallback() {
        if (!hasAllRequiredPermissions()) return
        
        try {
            // Create a network-priority location request as fallback
            val networkRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000L)
                .setMinUpdateIntervalMillis(10000L)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(3) // Only get a few network locations
                .build()

            val networkCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        LogHelper.log("Network fallback location: accuracy=${location.accuracy}m")
                        
                        if (location.accuracy <= 100.0f) { // More lenient for network location
                            handleLocationSuccess(location)
                        }
                        
                        // Remove this callback after getting location
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(networkRequest, networkCallback, Looper.getMainLooper())
            LogHelper.log("Network location fallback initiated")
            
        } catch (e: Exception) {
            LogHelper.logError("Network location fallback failed", e)
        }
    }

    /**
     * Try to get last known location when GPS is unavailable
     */
    private fun tryGetLastKnownLocation() {
        if (!hasAllRequiredPermissions()) return
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val age = System.currentTimeMillis() - it.time
                    LogHelper.log("Last known location found: age=${age/1000}s, accuracy=${it.accuracy}m")
                    
                    if (age < LAST_KNOWN_LOCATION_MAX_AGE_MS && it.accuracy <= 100.0f) {
                        LogHelper.log("Using cached location as fallback")
                        handleLocationSuccess(it)
                        updateNotification("Using cached GPS location", true)
                    } else {
                        LogHelper.log("Cached location too old or inaccurate, discarding")
                    }
                }
            }.addOnFailureListener { exception ->
                LogHelper.logError("Failed to get last known location", exception)
            }
        } catch (e: SecurityException) {
            LogHelper.logError("Permission error getting last location", e)
        }
    }

    /**
     * Attempt to recover from location failures
     */
    private fun attemptLocationRecovery() {
        LogHelper.log("Attempting location service recovery...")
        
        try {
            // Stop current location updates
            if (::fusedLocationClient.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
            
            // Wait a moment then restart
            timeoutHandler.postDelayed({
                if (hasAllRequiredPermissions() && isLocationEnabled()) {
                    LogHelper.log("Restarting location updates after recovery attempt")
                    setupLocationUpdates()
                    startLocationUpdates()
                } else {
                    LogHelper.logError("Cannot recover - permissions or location services unavailable")
                    handleLocationError("Recovery failed - check settings")
                }
            }, 5000) // 5 second delay
            
        } catch (e: Exception) {
            LogHelper.logError("Location recovery attempt failed", e)
        }
    }

    /**
     * Enhanced location tracking startup with timeout management
     */
    private fun startLocationTracking() {
        gpsAcquisitionStartTime = System.currentTimeMillis()
        isWaitingForInitialFix = true
        initialLocationAttempts = 0
        consecutiveLocationFailures = 0
        
        updateNotification("Starting GPS acquisition...", false)
        
        // Set GPS timeout
        gpsTimeoutRunnable = Runnable {
            if (isWaitingForInitialFix) {
                LogHelper.logError("GPS acquisition timeout after ${GPS_TIMEOUT_MS/1000}s")
                handleLocationError("GPS timeout - trying alternative methods")
                tryGetLastKnownLocation()
                tryNetworkLocationFallback()
            }
        }
        timeoutHandler.postDelayed(gpsTimeoutRunnable!!, GPS_TIMEOUT_MS)
        
        startLocationUpdates()
    }

    /**
     * Enhanced location updates startup with comprehensive checks
     */
    private fun startLocationUpdates() {
        if (!hasAllRequiredPermissions()) {
            LogHelper.logError("Cannot start location updates - missing permissions")
            updateNotification("Location permissions required", false, createAppSettingsIntent())
            return
        }

        if (!isLocationEnabled()) {
            LogHelper.logError("Location services disabled")
            updateNotification("Enable location services", false, createLocationSettingsIntent())
            return
        }

        val diagnostics = diagnoseLocationServices()
        val hasGps = diagnostics["gps_enabled"] as Boolean
        val hasNetwork = diagnostics["network_enabled"] as Boolean
        
        if (!hasGps && !hasNetwork) {
            LogHelper.logError("No location providers available")
            updateNotification("No location providers available", false, createLocationSettingsIntent())
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            acquireWakeLock()
            
            LogHelper.log("Location updates started - GPS: $hasGps, Network: $hasNetwork, Background: $isBackgroundMode")
            
            // Try to get immediate location if available
            tryGetLastKnownLocation()
            
        } catch (e: SecurityException) {
            LogHelper.logError("Location permission error during startup", e)
            updateNotification("Location permission denied", false, createAppSettingsIntent())
        } catch (e: Exception) {
            LogHelper.logError("Error starting location updates", e)
            updateNotification("Location service error", false)
        }
    }

    /**
     * Enhanced location processing with better filtering
     */
    private fun handleNewLocation(location: Location) {
        if (!checkPermissionsMidShift()) {
            return
        }

        val currentDate = getTodayDateString()
        if (lastRecordedDate.isNotEmpty() && currentDate != lastRecordedDate) {
            LogHelper.log("Day rollover detected on location fix: $lastRecordedDate -> $currentDate")
            val previousDate = lastRecordedDate
            lastRecordedDate = currentDate
            performShiftRollover(previousDate)
        }

        if (!location.hasAccuracy() || location.accuracy > MAX_LOCATION_ACCURACY_M) {
            LogHelper.log("Location filtered: accuracy=${location.accuracy}m (threshold=${MAX_LOCATION_ACCURACY_M}m)")
            return
        }

        var incrementalDistance = 0.0
        lastLocation?.let { last ->
            incrementalDistance = last.distanceTo(location).toDouble()
        }

        val (filteredLocation, totalDistance) = repository.processLocation(location)

        filteredLocation?.let {
            try {
                LocationEventBus.emitLocation(it)
                DistanceStorage.saveTodayDistance(totalDistance)

                val config = ConfigStorage.loadConfig(this)
                val km = totalDistance / 1000.0
                val formattedDistance = String.format(java.util.Locale.US, "%.2f", km)
                val statusStr = when (repository.getTrackingStatus()) {
                    TrackingStatus.MOVING -> "Moving"
                    TrackingStatus.STATIONARY -> "Stationary"
                    TrackingStatus.PAUSED -> "Paused"
                    else -> "Active"
                }
                val statusText = config.notificationBodyTemplate
                    .replace("{distance}", formattedDistance)
                    .replace("{status}", statusStr)
                
                updateNotification(statusText, true)

                LogHelper.log("Location processed - Total: %.2f m, Increment: %.1f m, Accuracy: %.1f m, Provider: ${currentLocationProvider.name}"
                    .format(totalDistance, incrementalDistance, location.accuracy))
                    
                lastLocation = location
                    
            } catch (e: Exception) {
                LogHelper.logError("Error saving location data", e)
            }
        }
    }

    /**
     * Enhanced permission checking
     */
    private fun hasAllRequiredPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if location services are enabled
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Enhanced location settings check
     */
    private fun checkLocationSettings() {
        val settingsClient: SettingsClient = LocationServices.getSettingsClient(this)
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .setNeedBle(false)
        
        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                LogHelper.log("Location settings satisfied")
            }
            .addOnFailureListener { exception ->
                LogHelper.logError("Location settings check failed", exception)
                
                when (exception) {
                    is ResolvableApiException -> {
                        LogHelper.log("Location settings can be resolved via user dialog")
                        updateNotification("Tap to optimize location settings", false, createLocationSettingsIntent())
                    }
                    else -> {
                        updateNotification("Location settings need attention", false, createLocationSettingsIntent())
                    }
                }
            }
    }

    // Notification and utility methods remain the same but with enhanced error handling
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Distance Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current distance tracking status"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private var lastNotificationTime = 0L
    private var lastNotificationDistance = 0.0

    private fun createNotification(
        content: String?,
        showStopButton: Boolean,
        pendingIntent: PendingIntent? = null
    ): Notification {
        val config = ConfigStorage.loadConfig(this)
        val iconRes = if (!config.notificationIconResource.isNullOrBlank()) {
            val resId = resources.getIdentifier(config.notificationIconResource, "drawable", packageName)
            if (resId != 0) resId else android.R.drawable.ic_menu_mylocation
        } else {
            android.R.drawable.ic_menu_mylocation
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(content ?: "")
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        getAppLaunchIntent()?.let { builder.setContentIntent(it) }
        pendingIntent?.let { builder.setContentIntent(it) }

        if (config.enableNotificationStopButton && showStopButton) {
            val stopIntent = Intent(this, DistanceTrackingService::class.java).apply {
                action = STOP_ACTION
            }
            val stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(
        content: String?,
        showStopButton: Boolean = true,
        pendingIntent: PendingIntent? = null,
        force: Boolean = false
    ) {
        try {
            val now = System.currentTimeMillis()
            val totalDist = if (::repository.isInitialized) repository.getTotalDistanceToday() else 0.0
            if (force || now - lastNotificationTime >= 3000L || Math.abs(totalDist - lastNotificationDistance) >= 10.0) {
                lastNotificationTime = now
                lastNotificationDistance = totalDist
                val notification = createNotification(content ?: "", showStopButton, pendingIntent)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            LogHelper.logError("Error updating notification", e)
        }
    }

    private fun createLocationSettingsIntent(): PendingIntent {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        return PendingIntent.getActivity(
            this, LOCATION_SETTINGS_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createAppSettingsIntent(): PendingIntent {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        return PendingIntent.getActivity(
            this, LOCATION_SETTINGS_REQUEST + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getAppLaunchIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastLocationAvailability(isAvailable: Boolean) {
        val intent = Intent("com.harmonyloop.location_tracker.LOCATION_AVAILABILITY").apply {
            setPackage(packageName)
            putExtra("isAvailable", isAvailable)
            putExtra("provider", currentLocationProvider.name)
            putExtra("consecutiveFailures", consecutiveLocationFailures)
        }
        sendBroadcast(intent)
    }

    fun setBackgroundMode(isBackground: Boolean) {
        if (isBackgroundMode != isBackground) {
            isBackgroundMode = isBackground
            LogHelper.log("Background mode changed to: $isBackground")
            
            if (::fusedLocationClient.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                releaseWakeLock()
                setupLocationUpdates()
                startLocationUpdates()
            }
        }
    }

    private fun initializeWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
        }
        LogHelper.log("WakeLock initialized")
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) initializeWakeLock()
        
        wakeLock?.let { wl ->
            if (!isWakeLockHeld) {
                try {
                    wl.acquire(10 * 60 * 1000L)
                    isWakeLockHeld = true
                    LogHelper.log("WakeLock acquired for location tracking")
                } catch (e: Exception) {
                    LogHelper.logError("Error acquiring WakeLock", e)
                }
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (isWakeLockHeld && wl.isHeld) {
                try {
                    wl.release()
                    isWakeLockHeld = false
                    LogHelper.log("WakeLock released")
                } catch (e: Exception) {
                    LogHelper.logError("Error releasing WakeLock", e)
                }
            }
        }
    }

    private fun getTodayDateString(): String {
        return DateHelper.getFormattedDate()
    }

    private fun scheduleShiftRollover() {
        try {
            shiftRolloverRunnable?.let { timeoutHandler.removeCallbacks(it) }

            val config = ConfigStorage.loadConfig(this)
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, config.autoStopHour)
                set(Calendar.MINUTE, config.autoStopMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delayMs = target.timeInMillis - now.timeInMillis
            LogHelper.log("Scheduling shift rollover for ${target.time} in ${delayMs / 1000} seconds")

            shiftRolloverRunnable = Runnable {
                performShiftRollover()
            }
            timeoutHandler.postDelayed(shiftRolloverRunnable!!, delayMs)
        } catch (e: Exception) {
            LogHelper.logError("Error scheduling shift rollover", e)
        }
    }

    internal fun performShiftRollover(dateToArchive: String? = null) {
        try {
            LogHelper.log("=== Performing shift rollover / auto-stop ===")
            val config = ConfigStorage.loadConfig(this)
            val currentDistance = repository.getTotalDistanceToday()
            val todayDate = DateHelper.getFormattedDate()
            val archiveDate = dateToArchive ?: (if (lastRecordedDate.isNotEmpty() && lastRecordedDate != todayDate) lastRecordedDate else DateHelper.getYesterdayDate())

            LogHelper.log("Archiving odometer ($currentDistance m) under date: $archiveDate")
            DistanceStorage.saveDistanceForDate(archiveDate, currentDistance)
            DistanceStorage.saveDistanceForDate(todayDate, 0.0)
            DistanceStorage.pruneOldest(7)

            if (config.enableAutoStop) {
                LogHelper.log("Auto-stop enabled: stopping service after shift end")
                isIntentionalStop = true
                LocationEventBus.emitStatus(TrackingStatus.STOPPED)
                ConfigStorage.setTrackingActive(this, false)
                LocationRepository.isTracking = false
                updateNotification("Shift completed - Distance: %.2f km".format(currentDistance / 1000.0), false)
                stopSelf()
            } else {
                LogHelper.log("Shift rollover: resetting daily odometer for continuous tracking")
                repository.resetDailyData()
                lastRecordedDate = todayDate
                updateNotification("New shift started - Distance: 0.00 km", true)
                scheduleShiftRollover()
            }
        } catch (e: Exception) {
            LogHelper.logError("Error performing shift rollover", e)
        }
    }

    internal fun checkLocationProviderState() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled) {
            LogHelper.log("Location services disabled mid-tracking")
            LocationEventBus.emitSecurityAlert(SecurityAlertType.LOCATION_DISABLED, mapOf("reason" to "GPS and Network providers disabled"))
            updateNotification("Location services disabled", false, createLocationSettingsIntent())
        }
    }

    internal fun checkPermissionsMidShift(): Boolean {
        if (!hasAllRequiredPermissions()) {
            LogHelper.log("Permissions lost mid-shift")
            LocationEventBus.emitSecurityAlert(SecurityAlertType.PERMISSION_LOST, mapOf("reason" to "Location or notification permission revoked"))
            LocationEventBus.emitStatus(TrackingStatus.PERMISSION_REVOKED)
            updateNotification("Location permissions required", false, createAppSettingsIntent())
            return false
        }
        return true
    }
}
