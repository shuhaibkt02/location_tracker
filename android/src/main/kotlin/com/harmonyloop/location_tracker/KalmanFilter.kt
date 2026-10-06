package com.harmonyloop.location_tracker

import android.location.Location
import kotlin.math.sqrt

/**
 * 2D Kalman filter for smoothing geographic coordinates and attenuating stationary noise.
 */
class KalmanFilter(
    private var processNoise: Float = 3.0f // default process noise (m/s)
) {
    private var timestamp: Long = 0L
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var accuracy: Float = 1.0f
    private var variance: Float = -1.0f // Negative means uninitialized

    /**
     * Processes the incoming location using Kalman filtering.
     * Returns a filtered copy of the Location.
     */
    fun process(location: Location): Location {
        val now = location.time
        val rawAccuracy = if (location.hasAccuracy() && location.accuracy > 0) location.accuracy else 25.0f

        if (variance < 0) {
            // First measurement initialization
            lat = location.latitude
            lng = location.longitude
            accuracy = rawAccuracy
            variance = accuracy * accuracy
            timestamp = now
        } else {
            val dt = (now - timestamp).coerceAtLeast(1) / 1000.0f // seconds
            timestamp = now

            // Predict variance based on time elapsed and process noise
            variance += dt * processNoise * processNoise

            // Kalman gain
            val measurementVariance = rawAccuracy * rawAccuracy
            val k = variance / (variance + measurementVariance)

            // Update estimates
            lat += k * (location.latitude - lat)
            lng += k * (location.longitude - lng)
            accuracy = sqrt((1.0f - k) * variance)

            // Update variance
            variance *= (1.0f - k)
        }

        val filtered = Location(location)
        filtered.latitude = lat
        filtered.longitude = lng
        filtered.accuracy = accuracy

        return filtered
    }

    /**
     * Resets the filter's internal state.
     */
    fun reset() {
        variance = -1.0f
        timestamp = 0L
        lat = 0.0
        lng = 0.0
        accuracy = 1.0f
    }
}
