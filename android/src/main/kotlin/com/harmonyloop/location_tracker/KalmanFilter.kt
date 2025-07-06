package com.example.distance_tracker

import android.location.Location
import kotlin.math.sqrt

class KalmanFilter(
    private var processNoise: Float = 3.0f // default process noise
) {
    private var timestamp: Long = 0L
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var accuracy: Float = 1.0f
    private var variance: Float = -1.0f // Negative means uninitialized

    /**
     * Processes the incoming location using a basic Kalman Filter.
     * Returns a filtered version of the Location.
     */
    fun process(location: Location): Location {
        val now = location.time

        if (variance < 0) {
            // First measurement
            lat = location.latitude
            lng = location.longitude
            accuracy = location.accuracy
            variance = accuracy * accuracy
            timestamp = now
        } else {
            val dt = (now - timestamp).coerceAtLeast(1) / 1000.0f // seconds
            timestamp = now

            // Predict to now
            variance += dt * processNoise * processNoise

            // Kalman gain
            val k = variance / (variance + location.accuracy * location.accuracy)

            // Update estimate
            lat += k * (location.latitude - lat)
            lng += k * (location.longitude - lng)
            accuracy = sqrt((1 - k) * variance)

            // Update variance
            variance *= (1 - k)
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
    }

    /**
     * Allows updating the process noise dynamically.
     */
    fun setProcessNoise(newQ: Float) {
        processNoise = newQ
    }
}
