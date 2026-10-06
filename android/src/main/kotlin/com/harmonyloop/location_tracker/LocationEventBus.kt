package com.harmonyloop.location_tracker

import android.location.Location
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel

object LocationEventBus {
    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun setEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    fun emitLocation(location: Location) {
        val payload = mapOf(
            "type" to "location",
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy.toDouble(),
            "speed" to location.speed.toDouble(),
            "altitude" to location.altitude,
            "provider" to (location.provider ?: "fused"),
            "timestamp" to location.time
        )
        postToSink(payload)
    }

    fun emitStatus(status: String) {
        val payload = mapOf(
            "type" to "status",
            "status" to status,
            "timestamp" to System.currentTimeMillis()
        )
        postToSink(payload)
    }

    fun emitSecurityAlert(alertType: String, details: Map<String, Any?> = emptyMap()) {
        val payload = mapOf(
            "type" to "security_alert",
            "alertType" to alertType,
            "timestamp" to System.currentTimeMillis(),
            "details" to details
        )
        postToSink(payload)
    }

    private fun postToSink(payload: Map<String, Any?>) {
        mainHandler.post {
            synchronized(this) {
                try {
                    eventSink?.success(payload)
                } catch (e: Exception) {
                    LogHelper.logError("Failed to emit event over EventChannel", e)
                }
            }
        }
    }
}
