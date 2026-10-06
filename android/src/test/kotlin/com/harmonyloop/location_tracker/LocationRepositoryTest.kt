package com.harmonyloop.location_tracker

import android.content.Context
import android.location.Location
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.flutter.plugin.common.EventChannel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocationRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: LocationRepository
    private val emittedEvents = mutableListOf<Map<String, Any?>>()

    private val testEventSink = object : EventChannel.EventSink {
        override fun success(event: Any?) {
            @Suppress("UNCHECKED_CAST")
            (event as? Map<String, Any?>)?.let { emittedEvents.add(it) }
        }

        override fun error(errorCode: String?, errorMessage: String?, errorDetails: Any?) {}
        override fun endOfStream() {}
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        emittedEvents.clear()
        LocationEventBus.setEventSink(testEventSink)
        repository = LocationRepository(context)
    }

    @After
    fun tearDown() {
        LocationEventBus.setEventSink(null)
    }

    private fun createLocation(latitude: Double, longitude: Double, isMock: Boolean): Location {
        return Location("gps").apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = 5.0f
            this.time = System.currentTimeMillis()
            this.setIsFromMockProvider(isMock)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.isMock = isMock
            }
        }
    }

    @Test
    fun testMockLocationRejectedWhenNotInDebugEvenIfConfigAllows() {
        repository.isDebugMode = false
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(allowMockLocationsInDebug = true)
        )

        val mockLoc = createLocation(37.7749, -122.4194, isMock = true)
        val (filtered, dist) = repository.processLocation(mockLoc)

        assertNull(filtered)
        assertEquals(0.0, dist, 0.001)

        ShadowLooper.idleMainLooper()
        val alertEvent = emittedEvents.firstOrNull { it["type"] == "security_alert" }
        assertNotNull(alertEvent)
        assertEquals("MOCK_LOCATION_DETECTED", alertEvent["alertType"])
    }

    @Test
    fun testMockLocationRejectedInDebugWhenConfigDisabled() {
        repository.isDebugMode = true
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(allowMockLocationsInDebug = false)
        )

        val mockLoc = createLocation(37.7749, -122.4194, isMock = true)
        val (filtered, dist) = repository.processLocation(mockLoc)

        assertNull(filtered)
        assertEquals(0.0, dist, 0.001)

        ShadowLooper.idleMainLooper()
        val alertEvent = emittedEvents.firstOrNull { it["type"] == "security_alert" }
        assertNotNull(alertEvent)
        assertEquals("MOCK_LOCATION_DETECTED", alertEvent["alertType"])
    }

    @Test
    fun testMockLocationAcceptedInDebugWhenConfigEnabled() {
        repository.isDebugMode = true
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(allowMockLocationsInDebug = true)
        )

        val mockLoc = createLocation(37.7749, -122.4194, isMock = true)
        val (filtered, _) = repository.processLocation(mockLoc)

        assertNotNull(filtered)
        assertEquals(37.7749, filtered.latitude, 0.001)

        ShadowLooper.idleMainLooper()
        val alertEvent = emittedEvents.firstOrNull { it["alertType"] == "MOCK_LOCATION_DETECTED" }
        assertNull(alertEvent)
    }

    @Test
    fun testRealLocationAcceptedInBothDebugAndRelease() {
        repository.isDebugMode = false
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(allowMockLocationsInDebug = false)
        )

        val realLoc = createLocation(37.7749, -122.4194, isMock = false)
        val (filtered, _) = repository.processLocation(realLoc)

        assertNotNull(filtered)
        assertEquals(37.7749, filtered.latitude, 0.001)

        ShadowLooper.idleMainLooper()
        val alertEvent = emittedEvents.firstOrNull { it["alertType"] == "MOCK_LOCATION_DETECTED" }
        assertNull(alertEvent)
    }

    @Test
    fun testLowAccuracyLocationRejected() {
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(accuracyFilterMeters = 30.0f)
        )

        val inaccurateLoc = createLocation(37.7749, -122.4194, isMock = false).apply {
            accuracy = 45.0f
        }
        val (filtered, _) = repository.processLocation(inaccurateLoc)
        assertNull(filtered)
    }
}
