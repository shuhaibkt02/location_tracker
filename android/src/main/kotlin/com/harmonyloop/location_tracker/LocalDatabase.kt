package com.harmonyloop.location_tracker

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.*

@Entity(tableName = "daily_distance")
data class DailyDistanceEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val distance: Double
)

@Dao
interface DistanceDao {
    @Query("SELECT * FROM daily_distance ORDER BY date DESC LIMIT :limit")
    suspend fun getHistory(limit: Int): List<DailyDistanceEntity>

    @Query("SELECT * FROM daily_distance WHERE date = :today LIMIT 1")
    suspend fun getToday(today: String): DailyDistanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DailyDistanceEntity)

    @Query("DELETE FROM daily_distance WHERE date NOT IN (SELECT date FROM daily_distance ORDER BY date DESC LIMIT :keepCount)")
    suspend fun pruneOldest(keepCount: Int)
}

@Database(entities = [DailyDistanceEntity::class], version = 1, exportSchema = true)
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

        fun setInstanceForTesting(database: AppDatabase?) {
            INSTANCE = database
        }
    }
}

object DistanceStorage {
    private var dao: DistanceDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        dao = AppDatabase.getInstance(context).distanceDao()
    }

    fun initWithDao(customDao: DistanceDao) {
        dao = customDao
    }

    fun saveDistanceForDate(date: String, distance: Double) {
        scope.launch {
            try {
                dao?.insertOrUpdate(DailyDistanceEntity(date, distance))
            } catch (e: Exception) {
                LogHelper.logError("Error saving daily distance for date $date: ${e.message}")
            }
        }
    }

    fun saveTodayDistance(distance: Double) {
        saveDistanceForDate(DateHelper.getFormattedDate(), distance)
    }

    fun loadTodayDistance(onResult: (Double) -> Unit) {
        val today = DateHelper.getFormattedDate()
        scope.launch {
            try {
                val todayData = dao?.getToday(today)
                withContext(Dispatchers.Main) {
                    onResult(todayData?.distance ?: 0.0)
                }
            } catch (e: Exception) {
                LogHelper.logError("Error loading today's distance: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(0.0)
                }
            }
        }
    }

    fun getDailyHistory(days: Int = 7, onResult: (List<DailyDistanceEntity>) -> Unit) {
        scope.launch {
            try {
                val list = dao?.getHistory(days) ?: emptyList()
                withContext(Dispatchers.Main) {
                    onResult(list)
                }
            } catch (e: Exception) {
                LogHelper.logError("Error fetching daily history: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }

    fun pruneOldest(daysToKeep: Int = 7) {
        scope.launch {
            try {
                dao?.pruneOldest(daysToKeep)
            } catch (e: Exception) {
                LogHelper.logError("Error pruning old daily records: ${e.message}")
            }
        }
    }
}
