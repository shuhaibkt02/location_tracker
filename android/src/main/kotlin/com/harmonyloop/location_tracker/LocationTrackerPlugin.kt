package com.harmonyloop.location_tracker

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import android.util.Log

class LocationTrackerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var context: Context? = null
    private var repository: LocationRepository? = null
    private var locationClient: FusedLocationProviderClient? = null
    private var activityRecognitionClient: ActivityRecognitionClient? = null
    private var locationCallback: LocationCallback? = null
    private var result: Result? = null
    private var permissionRequestCode: Int = 1001
    private var backgroundLocationRequestCode: Int = 1002
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "location_tracker")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        repository = LocationRepository(flutterPluginBinding.applicationContext)
        locationClient = LocationServices.getFusedLocationProviderClient(flutterPluginBinding.applicationContext)
        activityRecognitionClient = ActivityRecognition.getClient(flutterPluginBinding.applicationContext)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        this.result = result
        when (call.method) {
            "startTracking" -> startTracking(result)
            "stopTracking" -> stopTracking(result)
            "pauseTracking" -> pauseTracking(result)
            "resumeTracking" -> resumeTracking(result)
            "getDailySummaries" -> getDailySummaries(call, result)
            "exportData" -> exportData(call, result)
            "requestPermissions" -> requestPermissions(result)
            "checkPermissions" -> checkPermissions(result)
            "openLocationSettings" -> openLocationSettings(result)
            "getLocationData" -> getLocationData(result)
            "getTotalDistance" -> getTotalDistance(call, result)
            "isTracking" -> isTracking(call, result)
            else -> result.notImplemented()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    private fun startTracking(result: Result) {
    if (!hasRequiredPermissions()) {
        requestPermissions(result)
        result.success(false)
        return
    }
    if (!isLocationEnabled()) {
        result.error("LOCATION_DISABLED", "Device location services are disabled", null)
        openLocationSettings(result)
        return
    }

    scope.launch {
        try {
            val ctx = context ?: throw IllegalStateException("Context is null")

            // Start the foreground service with START_TRACKING action
            val intent = Intent(ctx, LocationService::class.java).apply {
                action = "START_TRACKING"
            }
            ContextCompat.startForegroundService(ctx, intent)

            val sessionId = repository?.startNewSession() ?: run {
                result.error("SESSION_FAILED", "Failed to start new session", null)
                return@launch
            }

            startLocationUpdates(sessionId)
            startActivityRecognition()

            // Bind to service to check tracking status
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as LocationService.LocalBinder
                    val locationService = binder.getService()
                    Log.d("LocationTrackerPlugin", "Checking isTracking: ${locationService.isTracking()}")
                    if (locationService.isTracking()) {
                        result.success(sessionId)
                    } else {
                        result.error("START_FAILED", "Tracking service failed to start", null)
                    }
                    ctx.unbindService(this)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

            ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("LocationTrackerPlugin", "Error starting tracking: ${e.message}")
            result.error("START_FAILED", "Failed to start tracking: ${e.message}", null)
        }
    }
}


    private fun isTracking(call: MethodCall, result: Result) {
        scope.launch {
            try {
                val ctx = context ?: run {
                    result.error("CONTEXT_NULL", "Context is null", null)
                    return@launch
                }
                val serviceIntent = Intent(ctx, LocationService::class.java)
                val serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val binder = service as LocationService.LocalBinder
                        val locationService = binder.getService()
                        result.success(locationService.isTracking())
                        ctx.unbindService(this)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {}
                }
                ctx.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                result.error("TRACKING_CHECK_FAILED", "Could not check tracking status: ${e.message}", null)
            }
        }
    }

    private fun getTotalDistance(call: MethodCall, result: Result) {
        scope.launch {
            try {
                val totalDistance = LocationService.getTotalDistanceKm()
                result.success(totalDistance)
            } catch (e: Exception) {
                result.error("GET_DISTANCE_FAILED", "Could not get total distance: ${e.message}", null)
            }
        }
    }

    private fun getLocationData(result: Result) {
        if (!hasLocationPermissions()) {
            result.error("PERMISSION_DENIED", "Location permissions not granted", null)
            return
        }

        val locationClient = locationClient ?: run {
            result.error("CLIENT_NULL", "Location client not initialized", null)
            return
        }

        try {
            locationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val locationData = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "accuracy" to location.accuracy.toDouble(),
                        "altitude" to if (location.hasAltitude()) location.altitude else null,
                        "speed" to if (location.hasSpeed()) location.speed.toDouble() else null,
                        "bearing" to if (location.hasBearing()) location.bearing.toDouble() else null,
                        "timestamp" to location.time
                    )
                    result.success(locationData)
                } else {
                    // Try to get a fresh location if last location is null
                    getCurrentLocation(result)
                }
            }.addOnFailureListener { exception ->
                result.error("LOCATION_ERROR", "Failed to get location: ${exception.message}", null)
            }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "Location permission denied", null)
        } catch (e: Exception) {
            result.error("LOCATION_ERROR", "Error getting location: ${e.message}", null)
        }
    }

    private fun getCurrentLocation(result: Result) {
        if (!hasLocationPermissions()) {
            result.error("PERMISSION_DENIED", "Location permissions not granted", null)
            return
        }

        val locationClient = locationClient ?: run {
            result.error("CLIENT_NULL", "Location client not initialized", null)
            return
        }

        val locationRequest = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(60000) // Accept locations up to 1 minute old
            .setDurationMillis(30000) // Wait up to 30 seconds for a fresh location
            .build()

        try {
            locationClient.getCurrentLocation(locationRequest, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val locationData = mapOf(
                            "latitude" to location.latitude,
                            "longitude" to location.longitude,
                            "accuracy" to location.accuracy.toDouble(),
                            "altitude" to if (location.hasAltitude()) location.altitude else null,
                            "speed" to if (location.hasSpeed()) location.speed.toDouble() else null,
                            "bearing" to if (location.hasBearing()) location.bearing.toDouble() else null,
                            "timestamp" to location.time
                        )
                        result.success(locationData)
                    } else {
                        result.error("NO_LOCATION", "Unable to get current location", null)
                    }
                }
                .addOnFailureListener { exception ->
                    result.error("LOCATION_ERROR", "Failed to get current location: ${exception.message}", null)
                }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "Location permission denied", null)
        } catch (e: Exception) {
            result.error("LOCATION_ERROR", "Error getting current location: ${e.message}", null)
        }
    }

    private fun hasLocationPermissions(): Boolean {
        val ctx = context ?: return false
        val fineLocationPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun stopTracking(result: Result) {
        scope.launch {
            val sessionId = repository?.getActiveSessionId()
            if (sessionId != null) {
                repository?.completeSession(sessionId)
            }
            stopLocationUpdates()
            stopActivityRecognition()
            result.success(true)
        }
    }

    private fun pauseTracking(result: Result) {
        scope.launch {
            val sessionId = repository?.getActiveSessionId()
            if (sessionId != null) {
                repository?.pauseSession(sessionId)
                stopLocationUpdates()
                stopActivityRecognition()
                result.success(true)
            } else {
                result.error("NO_ACTIVE_SESSION", "No active session to pause", null)
            }
        }
    }

    private fun resumeTracking(result: Result) {
        if (!hasRequiredPermissions()) {
            requestPermissions(result)
            return
        }

        scope.launch {
            val sessionId = repository?.getActiveSessionId()
            if (sessionId != null) {
                repository?.resumeSession(sessionId)
                startLocationUpdates(sessionId)
                startActivityRecognition()
                result.success(true)
            } else {
                result.error("NO_ACTIVE_SESSION", "No active session to resume", null)
            }
        }
    }

    private fun getDailySummaries(call: MethodCall, result: Result) {
        val days = call.argument<Int>("days") ?: 7
        scope.launch {
            val summaries = repository?.getDailySummaries(days) ?: emptyList()
            val summariesMap = summaries.map { summary ->
                mapOf(
                    "date" to summary.date,
                    "totalDistance" to summary.totalDistance,
                    "duration" to summary.duration,
                    "locationCount" to summary.locationCount,
                    "averageSpeed" to summary.averageSpeed,
                    "stopsCount" to summary.stopsCount
                )
            }
            result.success(summariesMap)
        }
    }

    private fun exportData(call: MethodCall, result: Result) {
        val format = call.argument<String>("format") ?: "csv"
        scope.launch {
            try {
                val appContext = context
                if (appContext != null) {
                    val exporter = DataExporter(appContext)
                    val file = exporter.exportData(format)
                    result.success(file?.absolutePath)
                } else {
                    result.error("CONTEXT_NULL", "Application context is null", null)
                }
            } catch (e: Exception) {
                result.error("EXPORT_FAILED", e.message, null)
            }
        }
    }

    private fun checkPermissions(result: Result? = null) {
        val ctx = context ?: run {
            result?.error("CONTEXT_NULL", "Context is null", null)
            return
        }

        val fineLocationPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val activityRecognitionPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
} else {
    true
}

val permissionMap = mapOf(
    "fineLocation" to fineLocationPermission,
    "coarseLocation" to coarseLocationPermission,
    "backgroundLocation" to backgroundLocationPermission,
    "activityRecognition" to activityRecognitionPermission,
    "notification" to notificationPermission
)

        result?.success(permissionMap)
    }

    private fun hasRequiredPermissions(): Boolean {
        val ctx = context ?: return false
        val fineLocationPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        val activityRecognitionPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
} else {
    PackageManager.PERMISSION_GRANTED
}


return fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
       coarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
       backgroundLocationPermission == PackageManager.PERMISSION_GRANTED &&
       activityRecognitionPermission == PackageManager.PERMISSION_GRANTED &&
       notificationPermission == PackageManager.PERMISSION_GRANTED

    }

    private fun requestPermissions(result: Result) {
        this.result = result
        val activity = activity ?: run {
            result.error("ACTIVITY_NULL", "Activity context is null", null)
            return
        }

        // First, request foreground location permissions
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }


        ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), permissionRequestCode)
    }

    private fun openLocationSettings(result: Result) {
        val activity = activity ?: run {
            result.error("ACTIVITY_NULL", "Activity context is null", null)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
            result.success(true)
        } catch (e: Exception) {
            result.error("SETTINGS_ERROR", "Could not open settings: ${e.message}", null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        when (requestCode) {
            permissionRequestCode -> {
                val permissionResults = permissions.zip(grantResults.toTypedArray()).associate { (perm, grant) ->
                    perm to (grant == PackageManager.PERMISSION_GRANTED)
                }

                val fineLocationGranted = permissionResults[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted = permissionResults[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionResults[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
                } else {
                    true
                }

                // If foreground location permissions are granted, request background location
                if (fineLocationGranted && coarseLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val backgroundGranted = ContextCompat.checkSelfPermission(
                        activity ?: return false,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!backgroundGranted) {
                        // Request background location permission separately
                        ActivityCompat.requestPermissions(
                            activity!!,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            backgroundLocationRequestCode
                        )
                        return true // Don't send result yet, wait for background permission
                    }
                }

                // Return current permission status
                val resultMap = mapOf(
                    "fineLocation" to fineLocationGranted,
                    "coarseLocation" to coarseLocationGranted,
                    "activityRecognition" to activityRecognitionGranted,
                    "backgroundLocation" to (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                            ContextCompat.checkSelfPermission(
                                activity ?: return false,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED)
                )

                result?.success(resultMap)
                result = null
                return true
            }

            backgroundLocationRequestCode -> {
                val backgroundLocationGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                val ctx = context ?: return false
                val resultMap = mapOf(
                    "fineLocation" to (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED),
                    "coarseLocation" to (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED),
                    "activityRecognition" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    },
                    "backgroundLocation" to backgroundLocationGranted
                )

                result?.success(resultMap)
                result = null
                return true
            }
        }
        return false
    }

    private fun startLocationUpdates(sessionId: String) {
        if (!checkLocationPermission()) {
            return
        }

        val config = TrackingConfig()
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            config.updateInterval
        ).apply {
            setMinUpdateIntervalMillis(config.updateInterval / 2)
            setMinUpdateDistanceMeters(config.minDistance.toFloat())
            setMaxUpdateDelayMillis(config.updateInterval * 2)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                scope.launch {
                    val location = locationResult.lastLocation ?: return@launch
                    val config = TrackingConfig()
                    if (location.accuracy > config.accuracyThreshold) return@launch

                    val previousLocations = repository?.getLocationsBySession(sessionId)?.lastOrNull()
                    val distance = if (previousLocations != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            previousLocations.latitude,
                            previousLocations.longitude,
                            location.latitude,
                            location.longitude,
                            results
                        )
                        results[0].toDouble()
                    } else 0.0

                    val locationEntity = LocationEntity(
                        sessionId = sessionId,
                        timestamp = location.time,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        bearing = if (location.hasBearing()) location.bearing else null,
                        distanceFromPrevious = distance
                    )

                    repository?.saveLocation(locationEntity)
                }
            }
        }

        locationClient?.requestLocationUpdates(locationRequest, locationCallback!!, null)
    }

    private fun checkLocationPermission(): Boolean {
        val context = activity ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            locationClient?.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun startActivityRecognition() {
        val config = TrackingConfig()
        if (!config.enableActivityDetection) return
        if (!checkActivityRecognitionPermission()) {
            Log.w("LocationTracker", "Activity recognition permission not granted")
            return
        }

        val context = context ?: return
        val intent = Intent(context, LocationService::class.java).apply {
            action = "ACTIVITY_RECOGNITION"
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val request = ActivityTransitionRequest(createActivityTransitions())

        activityRecognitionClient?.requestActivityTransitionUpdates(request, pendingIntent)
            ?.addOnSuccessListener {
                // Success
            }
            ?.addOnFailureListener {
                result?.error("ACTIVITY_RECOGNITION_FAILED", it.message, null)
            }
    }

    private fun createActivityTransitions(): List<ActivityTransition> {
        return listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
    }

    private fun checkActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val context = activity ?: return false
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun stopActivityRecognition() {
        val intent = Intent(activity?.applicationContext, LocationService::class.java).apply {
            action = "ACTIVITY_RECOGNITION"
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(activity?.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getService(activity?.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        activityRecognitionClient?.removeActivityTransitionUpdates(pendingIntent)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
        stopLocationUpdates()
        stopActivityRecognition()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    fun scheduleSync() {
        val context = activity?.applicationContext ?: return
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
}

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = LocationRepository(applicationContext)
        val unprocessed = repository.getUnprocessedLocations()
        delay(2000) // Simulate delay
        repository.markLocationsAsProcessed(unprocessed.map { it.id })
        return Result.success()
    }
}
