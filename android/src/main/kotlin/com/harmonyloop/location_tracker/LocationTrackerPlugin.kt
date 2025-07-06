package com.example.distance_tracker

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.distance_tracker.LogHelper
import com.example.distance_tracker.DistanceStorage


/** DistanceTrackerPlugin */
class DistanceTrackerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

  private lateinit var channel: MethodChannel
  private lateinit var context: Context
  private var activityBinding: ActivityPluginBinding? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "distance_tracker")
    channel.setMethodCallHandler(this)
    DistanceStorage.init(context)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityBinding = binding
  }
  override fun onDetachedFromActivity() {
    activityBinding = null
  }
  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}
  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "startTracking" -> {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
          val intent = Intent(context, DistanceTrackingService::class.java)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              context.startForegroundService(intent)
          } else {
              context.startService(intent)
          }
          result.success(true)
      } else {
          result.error("PERMISSION_DENIED", "Location permission not granted", null)
      }
      }

      "stopTracking" -> {
        val intent = Intent(context, DistanceTrackingService::class.java)
        context.stopService(intent)
        result.success(true)
      }

      "isTracking" -> {
        result.success(LocationRepository.isTracking)
      }

      "getDistanceToday" -> {
        result.success(LocationRepository.getDistanceToday())
      }

      "getLastKnownLocation" -> {
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

      else -> result.notImplemented()
    }
  }
}
