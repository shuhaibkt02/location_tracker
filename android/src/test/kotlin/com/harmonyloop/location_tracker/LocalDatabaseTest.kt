package com.harmonyloop.location_tracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LocalDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DistanceDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.distanceDao()
        DistanceStorage.initWithDao(dao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetTodayDistance() = runBlocking {
        val today = "2026-10-06"
        val entity = DailyDistanceEntity(date = today, distance = 1250.5)
        dao.insertOrUpdate(entity)

        val retrieved = dao.getToday(today)
        assertNotNull(retrieved)
        assertEquals(today, retrieved.date)
        assertEquals(1250.5, retrieved.distance, 0.001)
    }

    @Test
    fun testUpdateReplacesExistingRecordWithoutDuplicates() = runBlocking {
        val today = "2026-10-06"
        dao.insertOrUpdate(DailyDistanceEntity(date = today, distance = 500.0))
        dao.insertOrUpdate(DailyDistanceEntity(date = today, distance = 1500.0))

        val retrieved = dao.getToday(today)
        assertNotNull(retrieved)
        assertEquals(1500.0, retrieved.distance, 0.001)

        val history = dao.getHistory(10)
        assertEquals(1, history.size)
    }

    @Test
    fun testQueryHistoryOrderedAndLimited() = runBlocking {
        val dates = listOf(
            "2026-10-01" to 100.0,
            "2026-10-02" to 200.0,
            "2026-10-03" to 300.0,
            "2026-10-04" to 400.0,
            "2026-10-05" to 500.0
        )
        for ((d, dist) in dates) {
            dao.insertOrUpdate(DailyDistanceEntity(date = d, distance = dist))
        }

        val history3 = dao.getHistory(3)
        assertEquals(3, history3.size)
        assertEquals("2026-10-05", history3[0].date)
        assertEquals(500.0, history3[0].distance, 0.001)
        assertEquals("2026-10-04", history3[1].date)
        assertEquals(400.0, history3[1].distance, 0.001)
        assertEquals("2026-10-03", history3[2].date)
        assertEquals(300.0, history3[2].distance, 0.001)
    }

    @Test
    fun testPruneOldRecordsRetainsLast7Days() = runBlocking {
        for (day in 1..10) {
            val dateStr = String.format("2026-10-%02d", day)
            dao.insertOrUpdate(DailyDistanceEntity(date = dateStr, distance = day * 100.0))
        }

        val beforePrune = dao.getHistory(20)
        assertEquals(10, beforePrune.size)

        dao.pruneOldest(7)

        val afterPrune = dao.getHistory(20)
        assertEquals(7, afterPrune.size)
        assertEquals("2026-10-10", afterPrune.first().date)
        assertEquals("2026-10-04", afterPrune.last().date)
    }

    @Test
    fun testGetTodayWhenNoRecordExistsReturnsNull() = runBlocking {
        val retrieved = dao.getToday("2026-10-99")
        assertNull(retrieved)
    }
}
