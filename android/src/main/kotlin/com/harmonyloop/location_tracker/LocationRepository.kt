package com.harmonyloop.location_tracker

import android.content.Context
import android.location.Location
import androidx.core.location.LocationCompat

class LocationRepository(private val context: Context) {

    private val kalmanFilter = KalmanFilter()
    private var lastFilteredLocation: Location? = null
    private var totalDistanceToday = 0.0
    private var trackingStatus = TrackingStatus.STATIONARY
    private var stationaryTime: Long = 0L
    private val STATIONARY_THRESHOLD_MS = 180000L // 3 minutes

    companion object {
        private const val OUTLIER_DISTANCE_M = 1000.0 // Ignore single jumps > 1 km
        private const val MAX_SPEED_KMH = 200.0 // Reject unrealistic speeds (> 55 m/s)
        var isTracking: Boolean = false

        @Volatile
        private var instance: LocationRepository? = null

        fun getInstance(): LocationRepository? = instance

        fun getDistanceToday(): Double = instance?.getTotalDistanceToday() ?: 0.0

        fun getLastKnownLocation(): Location? = instance?.lastFilteredLocation

        internal fun setInstance(repository: LocationRepository) {
            instance = repository
        }
    }

    init {
        setInstance(this)
        loadTodayDistanceFromStorage()
        LogHelper.log("LocationRepository initialized with distance: %.2f m".format(totalDistanceToday))
    }

    fun processLocation(rawLocation: Location): Pair<Location?, Double> {
        // Detect mock locations (handled in detail in Ticket 10)
        if (LocationCompat.isMock(rawLocation)) {
            LogHelper.log("Mock location detected and rejected.")
            LocationEventBus.emitSecurityAlert("MOCK_LOCATION_DETECTED", mapOf(
                "provider" to (rawLocation.provider ?: "unknown"),
                "timestamp" to rawLocation.time
            ))
            return Pair(null, totalDistanceToday)
        }

        val config = ConfigStorage.loadConfig(context)

        // Filter out inaccurate fixes
        if (rawLocation.hasAccuracy() && rawLocation.accuracy > config.accuracyFilterMeters) {
            LogHelper.log("Location rejected: low accuracy (${rawLocation.accuracy}m > ${config.accuracyFilterMeters}m)")
            return Pair(null, totalDistanceToday)
        }

        val filtered = kalmanFilter.process(rawLocation)

        lastFilteredLocation?.let { lastLoc ->
            val distance = filtered.distanceTo(lastLoc).toDouble()
            val timeDelta = (filtered.time - lastLoc.time) / 1000.0 // seconds
            val speed = if (timeDelta > 0) distance / timeDelta else 0.0

            // Filter out outliers and unrealistic speeds
            if (distance < OUTLIER_DISTANCE_M && speed <= (MAX_SPEED_KMH / 3.6)) {
                if (shouldAccumulate(distance, speed, filtered, config)) {
                    totalDistanceToday += distance
                    updateStatus(TrackingStatus.MOVING)
                    stationaryTime = 0L

                    LogHelper.log("Distance accumulated: +%.2f m, Total: %.2f m, Speed: %.2f m/s"
                        .format(distance, totalDistanceToday, speed))
                } else {
                    updateStatus(TrackingStatus.STATIONARY)
                    if (timeDelta > 0) {
                        stationaryTime += (timeDelta * 1000).toLong()
                    }
                    if (stationaryTime > STATIONARY_THRESHOLD_MS) {
                        updateStatus(TrackingStatus.PAUSED)
                    }
                }
            } else {
                LogHelper.log("Outlier rejected: distance=%.2f m, speed=%.2f m/s".format(distance, speed))
            }
        }

        lastFilteredLocation = filtered
        return Pair(filtered, totalDistanceToday)
    }

    private fun shouldAccumulate(
        distance: Double,
        speed: Double,
        location: Location,
        config: TrackingConfigData
    ): Boolean {
        return distance >= config.minDistanceFilterMeters &&
               speed >= config.speedThresholdMps &&
               (!location.hasAccuracy() || location.accuracy <= config.accuracyFilterMeters)
    }

    private fun updateStatus(newStatus: TrackingStatus) {
        if (trackingStatus != newStatus) {
            trackingStatus = newStatus
            LocationEventBus.emitStatus(newStatus.name)
        }
    }

    private fun loadTodayDistanceFromStorage() {
        try {
            DistanceStorage.loadTodayDistance { distance ->
                totalDistanceToday = distance
                LogHelper.log("Loaded today's distance from storage: %.2f m".format(distance))
            }
        } catch (e: Exception) {
            LogHelper.logError("Error loading distance from storage: ${e.message}")
            totalDistanceToday = 0.0
        }
    }

    fun resetDailyData() {
        totalDistanceToday = 0.0
        lastFilteredLocation = null
        kalmanFilter.reset()
        updateStatus(TrackingStatus.STATIONARY)
        stationaryTime = 0L
        DistanceStorage.saveTodayDistance(0.0)
        LogHelper.log("Daily data reset to 0.0 m.")
    }

    fun getTotalDistanceToday(): Double = totalDistanceToday

    fun getTrackingStatus(): TrackingStatus = trackingStatus

    fun updateTotalDistance(distance: Double) {
        totalDistanceToday = distance
        LogHelper.log("Total distance manually updated to: %.2f m".format(distance))
    }
}