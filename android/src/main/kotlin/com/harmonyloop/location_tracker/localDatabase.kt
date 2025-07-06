package com.harmonyloop.location_tracker

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*


@Entity(tableName = "daily_distance")
data class DailyDistanceEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val distance: Double
)

@Dao
interface DistanceDao {
    @Query("SELECT * FROM daily_distance ORDER BY date DESC LIMIT 7")
    suspend fun getLast7Days(): List<DailyDistanceEntity>

    @Query("SELECT * FROM daily_distance WHERE date = :today LIMIT 1")
    suspend fun getToday(today: String): DailyDistanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DailyDistanceEntity)

    @Query("DELETE FROM daily_distance WHERE date NOT IN (SELECT date FROM daily_distance ORDER BY date DESC LIMIT 7)")
    suspend fun deleteOldest()
}

@Database(entities = [DailyDistanceEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun distanceDao(): DistanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "distance_tracker_db"
                ).build().also { INSTANCE = it }
            }
    }
}

object DistanceStorage {
    private lateinit var dao: DistanceDao

    fun init(context: Context) {
        dao = AppDatabase.getInstance(context).distanceDao()
    }

    fun saveTodayDistance(distance: Double) {
        val today = todayDate()
        CoroutineScope(Dispatchers.IO).launch {
            dao.insertOrUpdate(DailyDistanceEntity(today, distance))
            dao.deleteOldest()
        }
    }

    fun loadTodayDistance(onResult: (Double) -> Unit) {
        val today = todayDate()
        CoroutineScope(Dispatchers.IO).launch {
            val todayData = dao.getToday(today)
            onResult(todayData?.distance ?: 0.0)
        }
    }

    fun getLast7Days(onResult: (List<DailyDistanceEntity>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            onResult(dao.getLast7Days())
        }
    }

    private fun todayDate(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}
