package com.example.distance_tracker

import android.content.Context
import android.location.Location
import androidx.core.location.LocationCompat
import kotlin.math.*
import com.example.distance_tracker.KalmanFilter
import com.example.distance_tracker.DistanceStorage
import com.example.distance_tracker.TrackingStatus

class LocationRepository(private val context: Context) {

    private val kalmanFilter = KalmanFilter()
    private var lastFilteredLocation: Location? = null
    private var totalDistanceToday = 0.0
    private var trackingStatus = TrackingStatus.STATIONARY
    private var stationaryTime: Long = 0L
    private val STATIONARY_THRESHOLD_MS = 300000L // 5 minutes

    companion object {
        private const val SPEED_THRESHOLD_M_S = 1.0 // ~3.6 km/h
        private const val OUTLIER_DISTANCE_M = 1000.0 // Ignore jumps >1 km
        private const val MAX_SPEED_KMH = 200.0 // Filter unrealistic GPS
        var isTracking: Boolean = false
        
        // Singleton instance for static access
        @Volatile
        private var instance: LocationRepository? = null
        
        fun getInstance(): LocationRepository? = instance
        
        fun getDistanceToday(): Double = instance?.getTotalDistanceToday() ?: 0.0
        
        fun getLastKnownLocation(): Location? = instance?.lastFilteredLocation
        
        // Initialize singleton (call this when creating repository)
        internal fun setInstance(repository: LocationRepository) {
            instance = repository
        }
    }

    init {
        // Set singleton instance
        setInstance(this)
        
        // Load existing data from storage/database
        loadTodayDistanceFromStorage()
        loadLastKnownLocation()
        
        LogHelper.log("LocationRepository initialized with distance: %.2f m".format(totalDistanceToday))
    }

    fun processLocation(rawLocation: Location): Pair<Location?, Double> {
        // Ignore mock locations
        if (LocationCompat.isMock(rawLocation)) {
            LogHelper.log("Mock location detected and ignored.")
            return Pair(null, totalDistanceToday)
        }
        
        val filtered = kalmanFilter.process(rawLocation)

        lastFilteredLocation?.let { lastLoc ->
            val distance = filtered.distanceTo(lastLoc).toDouble()
            val timeDelta = (filtered.time - lastLoc.time) / 1000.0 // seconds
            val speed = if (timeDelta > 0) distance / timeDelta else 0.0

            // Filter out outliers and unrealistic speeds
            if (distance < OUTLIER_DISTANCE_M && speed <= (MAX_SPEED_KMH / 3.6)) {
                
                if (shouldAccumulate(distance, speed, filtered)) {
                    totalDistanceToday += distance
                    trackingStatus = TrackingStatus.MOVING
                    stationaryTime = 0L
                    
                    LogHelper.log("Distance added: %.2f m, Total: %.2f m, Speed: %.2f m/s"
                        .format(distance, totalDistanceToday, speed))
                    
                    // Save to database
                    saveLocationToDatabase(filtered, distance)
                    
                } else {
                    trackingStatus = TrackingStatus.STATIONARY
                    stationaryTime += (timeDelta * 1000).toLong()
                    
                    if (stationaryTime > STATIONARY_THRESHOLD_MS) {
                        LogHelper.log("Stationary for ${stationaryTime/1000}s - consider optimizing updates")
                        trackingStatus = TrackingStatus.PAUSED
                    }
                }
            } else {
                LogHelper.log("Outlier rejected: distance=%.2f m, speed=%.2f m/s"
                    .format(distance, speed))
            }
        }

        lastFilteredLocation = filtered
        return Pair(filtered, totalDistanceToday)
    }

    private fun shouldAccumulate(distance: Double, speed: Double, location: Location): Boolean {
        return distance > 0.5 && // Minimum 0.5m movement
               speed >= SPEED_THRESHOLD_M_S && // Above speed threshold
               location.hasAccuracy() && 
               location.accuracy <= 50.0 // Good GPS accuracy
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

    private fun loadLastKnownLocation() {
        // Load last known location from database if available
        try {
            // This would typically load from Room database
            // lastFilteredLocation = database.getLastLocation()
            LogHelper.log("Last known location loading attempted")
        } catch (e: Exception) {
            LogHelper.logError("Error loading last location: ${e.message}")
        }
    }

    private fun saveLocationToDatabase(location: Location, distance: Double) {
        try {
            // Save to Room database
            // This should be done in a background thread in real implementation
            DistanceStorage.saveTodayDistance(distance)
            LogHelper.log("Location saved to database: lat=${location.latitude}, lng=${location.longitude}")
        } catch (e: Exception) {
            LogHelper.logError("Error saving location to database: ${e.message}")
        }
    }

    fun resetDailyData() {
        totalDistanceToday = 0.0
        lastFilteredLocation = null
        kalmanFilter.reset()
        trackingStatus = TrackingStatus.STATIONARY
        stationaryTime = 0L
        
        // Clear from storage
        DistanceStorage.saveTodayDistance(0.0)
        
        LogHelper.log("Daily data reset.")
    }

    fun getTotalDistanceToday(): Double = totalDistanceToday

    fun getTrackingStatus(): TrackingStatus = trackingStatus
    
    fun isMoving(): Boolean = trackingStatus == TrackingStatus.MOVING
    
    fun isPaused(): Boolean = trackingStatus == TrackingStatus.PAUSED
    
    // Method to manually update total distance (for database sync)
    fun updateTotalDistance(distance: Double) {
        totalDistanceToday = distance
        LogHelper.log("Total distance updated to: %.2f m".format(distance))
    }
    
    // Get statistics
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalDistance" to totalDistanceToday,
            "status" to trackingStatus.name,
            "stationaryTime" to stationaryTime,
            "hasLastLocation" to (lastFilteredLocation != null),
            "isTracking" to isTracking
        )
    }
}