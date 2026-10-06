package com.harmonyloop.location_tracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** LocationTrackerPlugin */
class LocationTrackerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {

  private lateinit var channel: MethodChannel
  private lateinit var eventChannel: io.flutter.plugin.common.EventChannel
  private lateinit var context: Context
  private var activity: Activity? = null
  private var activityBinding: ActivityPluginBinding? = null
  private var pendingPermissionResult: Result? = null

  companion object {
    private const val PERMISSION_REQUEST_CODE = 9012
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "location_tracker")
    channel.setMethodCallHandler(this)
    DistanceStorage.init(context)

    eventChannel = io.flutter.plugin.common.EventChannel(flutterPluginBinding.binaryMessenger, "location_tracker/events")
    eventChannel.setStreamHandler(object : io.flutter.plugin.common.EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: io.flutter.plugin.common.EventChannel.EventSink?) {
        LocationEventBus.setEventSink(events)
      }

      override fun onCancel(arguments: Any?) {
        LocationEventBus.setEventSink(null)
      }
    })

    if (ConfigStorage.isTrackingActive(context)) {
      LogHelper.log("Auto-resuming active tracking session on Flutter attach")
      LocationRepository.isTracking = true
      LocationEventBus.emitStatus("RESUMED")
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    LocationEventBus.setEventSink(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    activityBinding = binding
    binding.addRequestPermissionsResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activityBinding?.removeRequestPermissionsResultListener(this)
    activity = null
    activityBinding = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
    if (requestCode == PERMISSION_REQUEST_CODE) {
      pendingPermissionResult?.success(getPermissionMap())
      pendingPermissionResult = null
      return true
    }
    return false
  }

  private fun getPermissionMap(): Map<String, Boolean> {
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
    val hasAll = fineGranted && notifGranted
    return mapOf(
      "locationGranted" to (fineGranted || coarseGranted),
      "notificationGranted" to notifGranted,
      "isFineLocation" to fineGranted,
      "hasAllRequired" to hasAll
    )
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android " + Build.VERSION.RELEASE)
      }

      "checkPermissions" -> {
        result.success(getPermissionMap())
      }

      "requestPermissions" -> {
        val perms = getPermissionMap()
        if (perms["hasAllRequired"] == true || activity == null) {
          result.success(perms)
          return
        }

        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
          needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
          needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isEmpty()) {
          result.success(getPermissionMap())
        } else {
          pendingPermissionResult = result
          ActivityCompat.requestPermissions(activity!!, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
      }

      "startTracking" -> {
        val perms = getPermissionMap()
        if (perms["hasAllRequired"] != true) {
          result.error(
            "PERMISSION_DENIED",
            "Required location and notification permissions must be granted before starting tracking.",
            perms
          )
          return
        }

        val intent = Intent(context, DistanceTrackingService::class.java)
        if (call.arguments is Map<*, *>) {
          @Suppress("UNCHECKED_CAST")
          val configMap = call.arguments as Map<String, Any?>
          val current = ConfigStorage.loadConfig(context)
          val parsedConfig = current.copy(
            notificationTitle = configMap["notificationTitle"] as? String ?: current.notificationTitle,
            notificationBodyTemplate = configMap["notificationBodyTemplate"] as? String ?: current.notificationBodyTemplate,
            notificationIconResource = configMap["notificationIconResource"] as? String ?: current.notificationIconResource,
            notificationChannelId = configMap["notificationChannelId"] as? String ?: current.notificationChannelId,
            notificationChannelName = configMap["notificationChannelName"] as? String ?: current.notificationChannelName,
            updateIntervalMs = (configMap["updateIntervalMs"] as? Number)?.toLong() ?: current.updateIntervalMs,
            minDistanceFilterMeters = (configMap["minDistanceFilterMeters"] as? Number)?.toFloat() ?: current.minDistanceFilterMeters,
            speedThresholdMps = (configMap["speedThresholdMps"] as? Number)?.toDouble() ?: current.speedThresholdMps,
            accuracyFilterMeters = (configMap["accuracyFilterMeters"] as? Number)?.toFloat() ?: current.accuracyFilterMeters,
            enableAutoStop = configMap["enableAutoStop"] as? Boolean ?: current.enableAutoStop,
            autoStopHour = (configMap["autoStopHour"] as? Number)?.toInt() ?: current.autoStopHour,
            autoStopMinute = (configMap["autoStopMinute"] as? Number)?.toInt() ?: current.autoStopMinute,
            enableNotificationStopButton = configMap["enableNotificationStopButton"] as? Boolean ?: current.enableNotificationStopButton,
            autoResumeOnBoot = configMap["autoResumeOnBoot"] as? Boolean ?: current.autoResumeOnBoot,
            allowMockLocationsInDebug = configMap["allowMockLocationsInDebug"] as? Boolean ?: current.allowMockLocationsInDebug
          )
          ConfigStorage.saveConfig(context, parsedConfig)

          for ((k, v) in configMap) {
            when (v) {
              is String -> intent.putExtra(k, v)
              is Int -> intent.putExtra(k, v)
              is Long -> intent.putExtra(k, v)
              is Double -> intent.putExtra(k, v)
              is Boolean -> intent.putExtra(k, v)
            }
          }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
        result.success(true)
      }

      "stopTracking" -> {
        ConfigStorage.setTrackingActive(context, false)
        LocationRepository.isTracking = false
        val intent = Intent(context, DistanceTrackingService::class.java)
        context.stopService(intent)
        result.success(true)
      }

      "isTracking" -> {
        result.success(ConfigStorage.isTrackingActive(context) || LocationRepository.isTracking)
      }

      "getTotalDistance", "getDistanceToday" -> {
        result.success(LocationRepository.getDistanceToday())
      }

      "getDailyHistory" -> {
        val days = call.argument<Int>("days") ?: 7
        DistanceStorage.getDailyHistory(days) { list ->
          val resultList = list.map {
            mapOf("date" to it.date, "distance" to it.distance)
          }
          result.success(resultList)
        }
      }

      "getLocationData", "getLastKnownLocation" -> {
        val location = LocationRepository.getLastKnownLocation()
        if (location != null) {
          result.success(mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to location.time,
            "speed" to location.speed
          ))
        } else {
          result.success(null)
        }
      }

      "getLogs" -> {
        result.success(LogHelper.getLogs())
      }

      "updateNotificationTitle" -> {
        val title = call.argument<String>("title")
        if (title != null) {
          val intent = Intent(context, DistanceTrackingService::class.java).apply {
            action = "UPDATE_NOTIFICATION_TITLE"
            putExtra("title", title)
          }
          context.startService(intent)
          result.success(true)
        } else {
          result.error("INVALID_ARGUMENT", "Title is required", null)
        }
      }

      else -> result.notImplemented()
    }
  }
}
