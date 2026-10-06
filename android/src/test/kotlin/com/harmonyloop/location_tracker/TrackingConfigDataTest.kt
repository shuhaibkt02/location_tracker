package com.harmonyloop.location_tracker

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingConfigDataTest {

    @Test
    fun testDefaultValues() {
        val config = TrackingConfigData()
        assertEquals("Workforce Tracking", config.notificationTitle)
        assertEquals("Distance: {distance} km • {status}", config.notificationBodyTemplate)
        assertEquals(null, config.notificationIconResource)
        assertEquals("location_tracker_channel", config.notificationChannelId)
        assertEquals("Location Tracker Service", config.notificationChannelName)
        assertEquals(8000L, config.updateIntervalMs)
        assertEquals(1.0f, config.minDistanceFilterMeters)
        assertEquals(0.8, config.speedThresholdMps, 0.001)
        assertEquals(35.0f, config.accuracyFilterMeters)
        assertTrue(config.enableAutoStop)
        assertEquals(0, config.autoStopHour)
        assertEquals(0, config.autoStopMinute)
        assertFalse(config.enableNotificationStopButton)
        assertTrue(config.autoResumeOnBoot)
        assertFalse(config.allowMockLocationsInDebug)
    }

    @Test
    fun testFromMapWithNullReturnsDefault() {
        val config = TrackingConfigData.fromMap(null)
        val default = TrackingConfigData()
        assertEquals(default, config)
    }

    @Test
    fun testFromMapWithPartialMapFallsBackToDefaults() {
        val map = mapOf<String, Any?>(
            "notificationTitle" to "Custom Title",
            "updateIntervalMs" to 15000L,
            "allowMockLocationsInDebug" to true
        )
        val config = TrackingConfigData.fromMap(map)
        assertEquals("Custom Title", config.notificationTitle)
        assertEquals(15000L, config.updateIntervalMs)
        assertTrue(config.allowMockLocationsInDebug)
        // Defaults
        assertEquals(1.0f, config.minDistanceFilterMeters)
        assertEquals(0.8, config.speedThresholdMps, 0.001)
        assertTrue(config.enableAutoStop)
    }

    @Test
    fun testFromMapHandlesNumericTypeConversions() {
        // Test Int passed where Long/Double/Float expected
        val map = mapOf<String, Any?>(
            "updateIntervalMs" to 5000, // Int instead of Long
            "minDistanceFilterMeters" to 5, // Int instead of Float
            "speedThresholdMps" to 1, // Int instead of Double
            "accuracyFilterMeters" to 20 // Int instead of Float
        )
        val config = TrackingConfigData.fromMap(map)
        assertEquals(5000L, config.updateIntervalMs)
        assertEquals(5.0f, config.minDistanceFilterMeters)
        assertEquals(1.0, config.speedThresholdMps, 0.001)
        assertEquals(20.0f, config.accuracyFilterMeters)
    }

    @Test
    fun testToMapAndFromMapRoundTrip() {
        val original = TrackingConfigData(
            notificationTitle = "Shift Tracking",
            notificationBodyTemplate = "{distance} km",
            notificationIconResource = "ic_custom",
            notificationChannelId = "test_channel",
            notificationChannelName = "Test Channel",
            updateIntervalMs = 10000L,
            minDistanceFilterMeters = 2.5f,
            speedThresholdMps = 1.2,
            accuracyFilterMeters = 25.0f,
            enableAutoStop = false,
            autoStopHour = 18,
            autoStopMinute = 30,
            enableNotificationStopButton = true,
            autoResumeOnBoot = false,
            allowMockLocationsInDebug = true
        )

        val map = original.toMap()
        val deserialized = TrackingConfigData.fromMap(map)

        assertEquals(original, deserialized)
    }
}
