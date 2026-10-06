package com.harmonyloop.location_tracker

import android.location.Location
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KalmanFilterTest {

    @Test
    fun testFirstFixInitializesFilter() {
        val filter = KalmanFilter()
        val loc = Location("gps").apply {
            latitude = 37.7749
            longitude = -122.4194
            accuracy = 10.0f
            time = 1000L
        }

        val filtered = filter.process(loc)
        assertEquals(37.7749, filtered.latitude, 0.000001)
        assertEquals(-122.4194, filtered.longitude, 0.000001)
        assertEquals(10.0f, filtered.accuracy, 0.01f)
    }

    @Test
    fun testStationaryJitterIsDamped() {
        val filter = KalmanFilter(processNoise = 1.0f)
        val baseLat = 37.7749
        val baseLng = -122.4194

        var lastFiltered: Location? = null

        // Feed 30 points with oscillating jitter
        for (i in 0..30) {
            val jitter = if (i % 2 == 0) 0.00008 else -0.00008 // ~8 meters jitter
            val loc = Location("gps").apply {
                latitude = baseLat + jitter
                longitude = baseLng + jitter
                accuracy = 12.0f
                time = 1000L + (i * 5000L)
            }
            lastFiltered = filter.process(loc)
        }

        // The filtered point should converge close to base coordinates
        assertTrue(lastFiltered != null)
        assertEquals(baseLat, lastFiltered.latitude, 0.00005)
        assertEquals(baseLng, lastFiltered.longitude, 0.00005)
    }

    @Test
    fun testResetClearsInternalState() {
        val filter = KalmanFilter()
        val loc1 = Location("gps").apply {
            latitude = 10.0
            longitude = 20.0
            accuracy = 5.0f
            time = 1000L
        }
        filter.process(loc1)
        filter.reset()

        val loc2 = Location("gps").apply {
            latitude = 50.0
            longitude = 60.0
            accuracy = 5.0f
            time = 5000L
        }
        val result = filter.process(loc2)
        assertEquals(50.0, result.latitude, 0.000001)
        assertEquals(60.0, result.longitude, 0.000001)
    }
}
