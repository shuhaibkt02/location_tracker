package com.harmonyloop.location_tracker

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

class LocationTrackerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, PluginRegistry.RequestPermissionsResultListener, ActivityAware {
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private var locationService: LocationService? = null
    private var isBound = false
    private val REQUEST_LOCATION_PERMISSION = 1001
    private val REQUEST_NOTIFICATION_PERMISSION = 1002
    private val TAG = "LocationTrackerPlugin"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as LocationService.LocalBinder
                locationService = binder.getService()
                isBound = true
                Log.d(TAG, "Service connected")
            } catch (e: ClassCastException) {
                Log.e(TAG, "Failed to cast binder", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            locationService = null
            Log.d(TAG, "Service disconnected")
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "location_tracker")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        if (isBound && activity != null) {
            activity?.unbindService(connection)
            isBound = false
            Log.d(TAG, "Unbound from service in onDetachedFromEngine")
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "Method called: ${call.method}")
        when (call.method) {
            "startTracking" -> handleStartTracking(result)
            "stopTracking" -> handleStopTracking(result)
            "getLocationData" -> handleGetLocationData(result)
            "getTotalDistance" -> handleGetTotalDistance(result)
            else -> result.notImplemented()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Location permission granted")
                    bindToService()
                    return true
                } else {
                    Log.d(TAG, "Location permission denied")
                    channel.invokeMethod("locationPermissionDenied", null)
                    return false
                }
            }

            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "Notification permission granted")
                    return true
                } else {
                    Log.d(TAG, "Notification permission denied")
                    channel.invokeMethod("notificationPermissionDenied", null)
                    return false
                }
            }
        }
        return false
    }

    private fun hasLocationPermission(): Boolean {
        val currentActivity = activity ?: return false
        return ActivityCompat.checkSelfPermission(
            currentActivity,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        val currentActivity = activity ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                currentActivity,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestLocationPermission() {
        val currentActivity = activity ?: return
        ActivityCompat.requestPermissions(
            currentActivity,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    private fun bindToService() {
        val currentActivity = activity ?: return
        val intent = Intent(currentActivity, LocationService::class.java)
        currentActivity.startService(intent)
        currentActivity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "Binding to service")
    }

    private fun handleStartTracking(result: MethodChannel.Result) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Permissions not granted for startTracking")
            result.error("PERMISSION_DENIED", "Location permission not granted", null)
            requestLocationPermission()
            return
        }
        if (isBound) {
            locationService?.startTracking()
            Log.d(TAG, "Started location tracking")
            result.success(null)
        } else {
            Log.w(TAG, "Service not bound yet for startTracking")
            result.error("SERVICE_NOT_BOUND", "Service not bound yet", null)
        }
    }

    private fun handleStopTracking(result: MethodChannel.Result) {
        if (isBound) {
            locationService?.stopTracking()
            Log.d(TAG, "Stopped location tracking")
            result.success(null)
        } else {
            Log.w(TAG, "Service not bound yet for stopTracking")
            result.error("SERVICE_NOT_BOUND", "Service not bound yet", null)
        }
    }

    private fun handleGetLocationData(result: MethodChannel.Result) {
        if (!hasLocationPermission()) {
            result.error("PERMISSION_DENIED", "Location permission not granted", null)
            requestLocationPermission()
            return
        }
        val location = locationService?.getLastLocation()
        if (location != null) {
            val data = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "timestamp" to location.time
            )
            result.success(data)
        } else {
            result.error("LOCATION_UNAVAILABLE", "No location data available", null)
        }
    }

    private fun handleGetTotalDistance(result: MethodChannel.Result) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Permissions not granted for getTotalDistance")
            result.error("PERMISSION_DENIED", "Location permission not granted", null)
            requestLocationPermission()
            return
        }
        if (isBound) {
            val distance = locationService?.getTotalDistanceKm() ?: 0.0
            Log.d(TAG, "Returning total distance: $distance")
            result.success(distance)
        } else {
            Log.w(TAG, "Service not bound yet for getTotalDistance")
            result.error("SERVICE_NOT_BOUND", "Service not bound yet", null)
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
        bindToService()
    }

    override fun onDetachedFromActivity() {
        if (isBound) {
            activity?.unbindService(connection)
            isBound = false
            Log.d(TAG, "Unbound from service")
        }
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
        bindToService()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }
}
