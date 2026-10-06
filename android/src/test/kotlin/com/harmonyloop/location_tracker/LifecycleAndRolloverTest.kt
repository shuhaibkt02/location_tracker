package com.harmonyloop.location_tracker

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LifecycleAndRolloverTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: DistanceDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.distanceDao()
        DistanceStorage.initWithDao(dao)
        AppDatabase.setInstanceForTesting(db)
    }

    @After
    fun tearDown() {
        AppDatabase.setInstanceForTesting(null)
        db.close()
    }

    @Test
    fun testDateHelperProducesIsoDateAndYesterday() {
        val today = DateHelper.getFormattedDate()
        val yesterday = DateHelper.getYesterdayDate()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        // Ensure both parse cleanly
        val todayDate = sdf.parse(today)
        val yesterdayDate = sdf.parse(yesterday)
        assertNotNull(todayDate)
        assertNotNull(yesterdayDate)

        // Yesterday should be roughly 24h before today
        val diffMs = todayDate.time - yesterdayDate.time
        assertTrue(diffMs in (23 * 3600 * 1000L)..(25 * 3600 * 1000L))
    }

    @Test
    fun testOnDestroyPreservesTrackingActiveOnUnexpectedKill() {
        ConfigStorage.setTrackingActive(context, true)
        LocationRepository.isTracking = true

        val controller: ServiceController<DistanceTrackingService> =
            Robolectric.buildService(DistanceTrackingService::class.java)
        controller.create()

        // Simulating unexpected OS kill or swipe-away (no STOP_ACTION sent)
        controller.destroy()

        // is_tracking_active MUST remain true for auto-resume
        assertTrue(
            ConfigStorage.isTrackingActive(context),
            "is_tracking_active must remain true across unexpected service destruction"
        )
    }

    @Test
    fun testOnDestroyClearsTrackingActiveOnIntentionalStop() {
        ConfigStorage.setTrackingActive(context, true)
        LocationRepository.isTracking = true

        val controller: ServiceController<DistanceTrackingService> =
            Robolectric.buildService(DistanceTrackingService::class.java)
        controller.create()

        val stopIntent = Intent(context, DistanceTrackingService::class.java).apply {
            action = DistanceTrackingService.STOP_ACTION
        }
        controller.get().onStartCommand(stopIntent, 0, 1)

        // Now destroy
        controller.destroy()

        // is_tracking_active MUST be false after intentional stop
        assertFalse(
            ConfigStorage.isTrackingActive(context),
            "is_tracking_active must be cleared upon intentional stop"
        )
        assertFalse(LocationRepository.isTracking)
    }

    @Test
    fun testPerformShiftRolloverArchivesToYesterdayAndResetsToday() = runBlocking {
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(enableAutoStop = false) // continuous tracking
        )

        val controller: ServiceController<DistanceTrackingService> =
            Robolectric.buildService(DistanceTrackingService::class.java)
        val service = controller.create().get()

        service.repository.updateTotalDistance(3450.0)

        val simulatedYesterday = "2026-10-05"
        val simulatedToday = DateHelper.getFormattedDate()

        // Execute rollover specifying yesterday's date
        service.performShiftRollover(simulatedYesterday)

        // Wait for IO coroutine to write to Room DB
        var yesterdayRecord: DailyDistanceEntity? = null
        for (i in 1..30) {
            yesterdayRecord = dao.getToday(simulatedYesterday)
            if (yesterdayRecord != null) break
            kotlinx.coroutines.delay(50)
        }

        assertNotNull(yesterdayRecord, "Yesterday's record should exist in Room DB")
        assertEquals(3450.0, yesterdayRecord.distance, 0.001)

        // Verify today's record initialized with 0.0 m in Room DB
        var todayRecord: DailyDistanceEntity? = null
        for (i in 1..30) {
            todayRecord = dao.getToday(simulatedToday)
            if (todayRecord != null) break
            kotlinx.coroutines.delay(50)
        }
        assertNotNull(todayRecord, "Today's record should exist in Room DB")
        assertEquals(0.0, todayRecord.distance, 0.001)

        // In continuous tracking mode, repository should be reset to 0.0 m
        assertEquals(0.0, service.repository.getTotalDistanceToday(), 0.001)
    }

    @Test
    fun testPerformShiftRolloverWithAutoStopStopsTracking() = runBlocking {
        ConfigStorage.saveConfig(
            context,
            TrackingConfigData(enableAutoStop = true)
        )
        ConfigStorage.setTrackingActive(context, true)
        LocationRepository.isTracking = true

        val controller: ServiceController<DistanceTrackingService> =
            Robolectric.buildService(DistanceTrackingService::class.java)
        val service = controller.create().get()

        service.repository.updateTotalDistance(1800.0)

        val yesterday = "2026-10-05"
        service.performShiftRollover(yesterday)

        // Verifications
        var archived: DailyDistanceEntity? = null
        for (i in 1..30) {
            archived = dao.getToday(yesterday)
            if (archived != null) break
            kotlinx.coroutines.delay(50)
        }
        assertNotNull(archived)
        assertEquals(1800.0, archived.distance, 0.001)

        // Auto-stop should disable tracking
        assertFalse(ConfigStorage.isTrackingActive(context))
        assertFalse(LocationRepository.isTracking)
    }
}
