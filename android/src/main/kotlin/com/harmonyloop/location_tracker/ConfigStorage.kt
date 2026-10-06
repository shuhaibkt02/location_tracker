package com.harmonyloop.location_tracker

import android.content.Context
import android.content.SharedPreferences

data class TrackingConfigData(
    val notificationTitle: String = "Workforce Tracking",
    val notificationBodyTemplate: String = "Distance: {distance} km • {status}",
    val notificationIconResource: String? = null,
    val notificationChannelId: String = "location_tracker_channel",
    val notificationChannelName: String = "Location Tracker Service",
    val updateIntervalMs: Long = 8000L,
    val minDistanceFilterMeters: Float = 1.0f,
    val speedThresholdMps: Double = 0.8,
    val accuracyFilterMeters: Float = 35.0f,
    val enableAutoStop: Boolean = true,
    val autoStopHour: Int = 0,
    val autoStopMinute: Int = 0,
    val enableNotificationStopButton: Boolean = false,
    val autoResumeOnBoot: Boolean = true,
    val allowMockLocationsInDebug: Boolean = false
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "notificationTitle" to notificationTitle,
            "notificationBodyTemplate" to notificationBodyTemplate,
            "notificationIconResource" to notificationIconResource,
            "notificationChannelId" to notificationChannelId,
            "notificationChannelName" to notificationChannelName,
            "updateIntervalMs" to updateIntervalMs,
            "minDistanceFilterMeters" to minDistanceFilterMeters,
            "speedThresholdMps" to speedThresholdMps,
            "accuracyFilterMeters" to accuracyFilterMeters,
            "enableAutoStop" to enableAutoStop,
            "autoStopHour" to autoStopHour,
            "autoStopMinute" to autoStopMinute,
            "enableNotificationStopButton" to enableNotificationStopButton,
            "autoResumeOnBoot" to autoResumeOnBoot,
            "allowMockLocationsInDebug" to allowMockLocationsInDebug
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>?, default: TrackingConfigData = TrackingConfigData()): TrackingConfigData {
            if (map == null) return default
            return TrackingConfigData(
                notificationTitle = map["notificationTitle"] as? String ?: default.notificationTitle,
                notificationBodyTemplate = map["notificationBodyTemplate"] as? String ?: default.notificationBodyTemplate,
                notificationIconResource = map["notificationIconResource"] as? String ?: default.notificationIconResource,
                notificationChannelId = map["notificationChannelId"] as? String ?: default.notificationChannelId,
                notificationChannelName = map["notificationChannelName"] as? String ?: default.notificationChannelName,
                updateIntervalMs = (map["updateIntervalMs"] as? Number)?.toLong() ?: default.updateIntervalMs,
                minDistanceFilterMeters = (map["minDistanceFilterMeters"] as? Number)?.toFloat() ?: default.minDistanceFilterMeters,
                speedThresholdMps = (map["speedThresholdMps"] as? Number)?.toDouble() ?: default.speedThresholdMps,
                accuracyFilterMeters = (map["accuracyFilterMeters"] as? Number)?.toFloat() ?: default.accuracyFilterMeters,
                enableAutoStop = map["enableAutoStop"] as? Boolean ?: default.enableAutoStop,
                autoStopHour = (map["autoStopHour"] as? Number)?.toInt() ?: default.autoStopHour,
                autoStopMinute = (map["autoStopMinute"] as? Number)?.toInt() ?: default.autoStopMinute,
                enableNotificationStopButton = map["enableNotificationStopButton"] as? Boolean ?: default.enableNotificationStopButton,
                autoResumeOnBoot = map["autoResumeOnBoot"] as? Boolean ?: default.autoResumeOnBoot,
                allowMockLocationsInDebug = map["allowMockLocationsInDebug"] as? Boolean ?: default.allowMockLocationsInDebug
            )
        }
    }
}

object ConfigStorage {
    private const val PREFS_NAME = "location_tracker_config"
    private const val KEY_IS_TRACKING = "is_tracking"
    private const val KEY_TITLE = "notification_title"
    private const val KEY_BODY_TEMPLATE = "notification_body_template"
    private const val KEY_ICON = "notification_icon"
    private const val KEY_CHANNEL_ID = "channel_id"
    private const val KEY_CHANNEL_NAME = "channel_name"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_DISTANCE_FILTER = "distance_filter"
    private const val KEY_SPEED_THRESHOLD = "speed_threshold"
    private const val KEY_ACCURACY_FILTER = "accuracy_filter"
    private const val KEY_AUTO_STOP = "enable_auto_stop"
    private const val KEY_AUTO_STOP_HOUR = "auto_stop_hour"
    private const val KEY_AUTO_STOP_MINUTE = "auto_stop_minute"
    private const val KEY_STOP_BUTTON = "enable_stop_button"
    private const val KEY_RESUME_BOOT = "auto_resume_boot"
    private const val KEY_ALLOW_MOCK_DEBUG = "allow_mock_debug"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConfig(context: Context, config: TrackingConfigData) {
        getPrefs(context).edit().apply {
            putString(KEY_TITLE, config.notificationTitle)
            putString(KEY_BODY_TEMPLATE, config.notificationBodyTemplate)
            putString(KEY_ICON, config.notificationIconResource)
            putString(KEY_CHANNEL_ID, config.notificationChannelId)
            putString(KEY_CHANNEL_NAME, config.notificationChannelName)
            putLong(KEY_INTERVAL, config.updateIntervalMs)
            putFloat(KEY_DISTANCE_FILTER, config.minDistanceFilterMeters)
            putFloat(KEY_SPEED_THRESHOLD, config.speedThresholdMps.toFloat())
            putFloat(KEY_ACCURACY_FILTER, config.accuracyFilterMeters)
            putBoolean(KEY_AUTO_STOP, config.enableAutoStop)
            putInt(KEY_AUTO_STOP_HOUR, config.autoStopHour)
            putInt(KEY_AUTO_STOP_MINUTE, config.autoStopMinute)
            putBoolean(KEY_STOP_BUTTON, config.enableNotificationStopButton)
            putBoolean(KEY_RESUME_BOOT, config.autoResumeOnBoot)
            putBoolean(KEY_ALLOW_MOCK_DEBUG, config.allowMockLocationsInDebug)
            apply()
        }
    }

    fun loadConfig(context: Context): TrackingConfigData {
        val prefs = getPrefs(context)
        return TrackingConfigData(
            notificationTitle = prefs.getString(KEY_TITLE, "Workforce Tracking") ?: "Workforce Tracking",
            notificationBodyTemplate = prefs.getString(KEY_BODY_TEMPLATE, "Distance: {distance} km • {status}") ?: "Distance: {distance} km • {status}",
            notificationIconResource = prefs.getString(KEY_ICON, null),
            notificationChannelId = prefs.getString(KEY_CHANNEL_ID, "location_tracker_channel") ?: "location_tracker_channel",
            notificationChannelName = prefs.getString(KEY_CHANNEL_NAME, "Location Tracker Service") ?: "Location Tracker Service",
            updateIntervalMs = prefs.getLong(KEY_INTERVAL, 8000L),
            minDistanceFilterMeters = prefs.getFloat(KEY_DISTANCE_FILTER, 1.0f),
            speedThresholdMps = prefs.getFloat(KEY_SPEED_THRESHOLD, 0.8f).toDouble(),
            accuracyFilterMeters = prefs.getFloat(KEY_ACCURACY_FILTER, 35.0f),
            enableAutoStop = prefs.getBoolean(KEY_AUTO_STOP, true),
            autoStopHour = prefs.getInt(KEY_AUTO_STOP_HOUR, 0),
            autoStopMinute = prefs.getInt(KEY_AUTO_STOP_MINUTE, 0),
            enableNotificationStopButton = prefs.getBoolean(KEY_STOP_BUTTON, false),
            autoResumeOnBoot = prefs.getBoolean(KEY_RESUME_BOOT, true),
            allowMockLocationsInDebug = prefs.getBoolean(KEY_ALLOW_MOCK_DEBUG, false)
        )
    }

    fun setTrackingActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_TRACKING, active).apply()
    }

    fun isTrackingActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_TRACKING, false)
    }
}
