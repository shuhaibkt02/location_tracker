package com.harmonyloop.location_tracker

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.text.SimpleDateFormat
import java.io.File
import androidx.security.crypto.EncryptedSharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import java.util.Base64
import java.util.concurrent.TimeUnit
import androidx.room.migration.Migration
import android.location.Location
import android.util.Log
import java.util.ArrayDeque
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.io.IOException
import java.io.InputStream
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.harmonyloop.location_tracker.TrackingSession

@Entity(tableName = "tracking_sessions")
data class TrackingSession(
    @PrimaryKey val sessionId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceKm: Double = 0.0,
    val status: String
)


/**
 * Entity representing a location data point
 */
@Entity(tableName = "location_points")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val distanceFromPrevious: Double,
    val isProcessed: Boolean = false,
    val isIndoor: Boolean = false,
    @ColumnInfo(defaultValue = "0") val syncAttempts: Int = 0
)

/**
 * Entity representing a tracking session
 */
@Entity(tableName = "tracking_sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceKm: Double = 0.0,
    val status: String = SessionStatus.ACTIVE.name,
    val syncStatus: String = SyncStatus.PENDING.name,
    @ColumnInfo(defaultValue = "") val encryptedKey: String = ""
)

enum class SessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ERROR
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

/**
 * Daily tracking summary for reporting
 */
data class DailySummary(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "totalDistance") val totalDistance: Double,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "sessionCount") val locationCount: Int, // Map sessionCount to locationCount
    @ColumnInfo(name = "avgSpeed") val averageSpeed: Double,   // Map avgSpeed to averageSpeed
    @ColumnInfo(name = "stopsCount") val stopsCount: Int
)

/**
 * Data Access Object for location operations
 */
@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: LocationEntity): Long
    
    @Insert
    suspend fun insertLocations(locations: List<LocationEntity>)
    
    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLocationsBySession(sessionId: String): List<LocationEntity>
    
    @Query("SELECT * FROM location_points WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getLocationsBetween(startTime: Long, endTime: Long): List<LocationEntity>
    
    @Query("SELECT * FROM location_points WHERE isProcessed = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnprocessedLocations(limit: Int = 100): List<LocationEntity>
    
    @Update
    suspend fun updateLocation(location: LocationEntity)
    
    @Query("UPDATE location_points SET isProcessed = 1, syncAttempts = syncAttempts + 1 WHERE id IN (:ids)")
    suspend fun markAsProcessed(ids: List<Long>)
    
    @Query("SELECT COUNT(*) FROM location_points WHERE sessionId = :sessionId")
    suspend fun getLocationCountForSession(sessionId: String): Int
    
    @Query("SELECT SUM(distanceFromPrevious) FROM location_points WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalDistanceBetween(startTime: Long, endTime: Long): Double?
    
    @Query("SELECT SUM(distanceFromPrevious) FROM location_points WHERE date(timestamp/1000, 'unixepoch', 'localtime') = :date")
    suspend fun getDailyDistance(date: String): Double?
    
    @Query("SELECT AVG(speed) FROM location_points WHERE timestamp BETWEEN :startTime AND :endTime AND speed IS NOT NULL")
    suspend fun getAverageSpeed(startTime: Long, endTime: Long): Double?
    
    @Query("SELECT COUNT(*) FROM location_points WHERE timestamp BETWEEN :startTime AND :endTime AND speed < 0.5 AND accuracy < 20")
    suspend fun getStopsCount(startTime: Long, endTime: Long): Int
}

/**
 * Data Access Object for session operations
 */
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?
    
    @Query("SELECT * FROM tracking_sessions WHERE status = :status")
    suspend fun getSessionsByStatus(status: String): List<SessionEntity>
    
    @Query("SELECT * FROM tracking_sessions WHERE startTime BETWEEN :startTime AND :endTime ORDER BY startTime DESC")
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<SessionEntity>
    
    @Query("SELECT * FROM tracking_sessions WHERE status = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?
    
    @Query("UPDATE tracking_sessions SET status = :status WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String)
    
    @Query("UPDATE tracking_sessions SET endTime = :endTime, totalDistanceKm = :distance, status = :status WHERE sessionId = :sessionId")
    suspend fun completeSession(sessionId: String, endTime: Long, distance: Double, status: String)
    
    @Query("SELECT date(startTime/1000, 'unixepoch', 'localtime') as date, " +
           "SUM(totalDistanceKm) as totalDistance, " +
           "SUM(CASE WHEN endTime IS NULL THEN (strftime('%s','now') * 1000) - startTime ELSE endTime - startTime END) as duration, " +
           "COUNT(*) as sessionCount, " +
           "(SELECT AVG(speed) FROM location_points WHERE timestamp >= :startTime AND speed IS NOT NULL) as avgSpeed, " +
           "(SELECT COUNT(*) FROM location_points WHERE timestamp >= :startTime AND speed < 0.5 AND accuracy < 20) as stopsCount " +
           "FROM tracking_sessions " +
           "WHERE startTime >= :startTime " +
           "GROUP BY date " +
           "ORDER BY date DESC")
    suspend fun getDailySummaries(startTime: Long): List<DailySummary>
}

/**
 * Database class with migrations
 */
@Database(entities = [LocationEntity::class, SessionEntity::class], version = 2, exportSchema = false)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao
    
    companion object {
        private const val DATABASE_NAME = "location_tracker.db"
        
        @Volatile
        private var INSTANCE: LocationDatabase? = null
        
        fun getInstance(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE location_points ADD COLUMN isIndoor INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE location_points ADD COLUMN syncAttempts INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tracking_sessions ADD COLUMN encryptedKey TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

/**
 * Tracking Session Manager for handling tracking sessions
 */
class TrackingSessionManager(
    private val context: Context,
    private val database: LocationDatabase
) {
    private val sessionDao by lazy { database.sessionDao() }
    
    suspend fun startDailySession(): String {
        return withContext(Dispatchers.IO) {
            val activeSession = sessionDao.getActiveSession()
            if (activeSession != null) {
                return@withContext activeSession.sessionId
            }
            
            val sessionId = "session_${System.currentTimeMillis()}"
            val session = SessionEntity(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                status = SessionStatus.ACTIVE.name
            )
            sessionDao.insertSession(session)
            sessionId
        }
    }
    
    suspend fun pauseSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionDao.updateSessionStatus(sessionId, SessionStatus.PAUSED.name)
        }
    }
    
    suspend fun resumeSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionDao.updateSessionStatus(sessionId, SessionStatus.ACTIVE.name)
        }
    }
    
    suspend fun completeSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            val session = sessionDao.getSession(sessionId) ?: return@withContext
            val endTime = System.currentTimeMillis()
            val totalDistance = database.locationDao().getTotalDistanceBetween(session.startTime, endTime) ?: 0.0
            
            sessionDao.completeSession(
                sessionId = sessionId,
                endTime = endTime,
                distance = totalDistance,
                status = SessionStatus.COMPLETED.name
            )
        }
    }
}


class LocationRepository(private val context: Context) {
    private val database by lazy { LocationDatabase.getInstance(context) }
    private val locationDao by lazy { database.locationDao() }
    val sessionDao by lazy { database.sessionDao() }
    private val config = TrackingConfig()
    
    init {
        scheduleDataRetention()
    }
    
    suspend fun saveLocation(location: LocationEntity): Long {
        return withContext(Dispatchers.IO) {
            locationDao.insertLocation(location)
        }
    }
    
    suspend fun saveLocations(locations: List<LocationEntity>) {
        withContext(Dispatchers.IO) {
            locationDao.insertLocations(locations)
        }
    }
    
    suspend fun startNewSession(): String? {
    return try {
        val sessionId = UUID.randomUUID().toString()
        sessionDao.insertSession(
            SessionEntity(
                sessionId = sessionId,
                startTime = System.currentTimeMillis(),
                status = SessionStatus.ACTIVE.name,
                syncStatus = SyncStatus.PENDING.name,
                encryptedKey = "" // Provide default or computed value
            )
        )
        sessionId
    } catch (e: Exception) {
        Log.e("LocationRepository", "Failed to start session: ${e.message}")
        null
    }
}
    
    suspend fun pauseSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionDao.updateSessionStatus(sessionId, SessionStatus.PAUSED.name)
        }
    }
    
    suspend fun resumeSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionDao.updateSessionStatus(sessionId, SessionStatus.ACTIVE.name)
        }
    }
    
    suspend fun completeSession(sessionId: String): SessionEntity? {
        return withContext(Dispatchers.IO) {
            val session = sessionDao.getSession(sessionId) ?: return@withContext null
            val endTime = System.currentTimeMillis()
            val totalDistance = locationDao.getTotalDistanceBetween(session.startTime, endTime) ?: 0.0
            
            sessionDao.completeSession(
                sessionId = sessionId,
                endTime = endTime,
                distance = totalDistance,
                status = SessionStatus.COMPLETED.name
            )
            
            sessionDao.getSession(sessionId)
        }
    }
    
    suspend fun getActiveSessionId(): String? {
        return withContext(Dispatchers.IO) {
            sessionDao.getActiveSession()?.sessionId
        }
    }
    
    suspend fun getDailySummaries(days: Int): List<DailySummary> {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -days)
            val startTime = calendar.timeInMillis
            
            sessionDao.getDailySummaries(startTime)
        }
    }
    
    suspend fun getDailyDistance(date: String): Double {
        return withContext(Dispatchers.IO) {
            locationDao.getDailyDistance(date) ?: 0.0
        }
    }
    
    suspend fun getWeeklyDistance(): Double {
        return withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val startTime = calendar.timeInMillis
            locationDao.getTotalDistanceBetween(startTime, System.currentTimeMillis()) ?: 0.0
        }
    }
    
    suspend fun getLocationsBySession(sessionId: String): List<LocationEntity> {
        return withContext(Dispatchers.IO) {
            locationDao.getLocationsBySession(sessionId)
        }
    }
    
    suspend fun getUnprocessedLocations(limit: Int = 100): List<LocationEntity> {
        return withContext(Dispatchers.IO) {
            locationDao.getUnprocessedLocations(limit)
        }
    }
    
    suspend fun markLocationsAsProcessed(ids: List<Long>) {
        withContext(Dispatchers.IO) {
            locationDao.markAsProcessed(ids)
        }
    }
    
    private fun generateEncryptedKey(): String {
    try {
        val keyAlias = "location_tracker_key"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // Check if key exists
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }

        // Return the key alias instead of encoding the key
        return keyAlias
    } catch (e: Exception) {
        Log.e("LocationRepository", "Key generation failed: ${e.message}", e)
        // Fallback: Generate a non-KeyStore key if AndroidKeyStore fails
        try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
            keyGenerator.init(256) // 256-bit AES key
            val secretKey = keyGenerator.generateKey()
            val encodedKey = secretKey.encoded
            if (encodedKey != null) {
                return Base64.getEncoder().encodeToString(encodedKey)
            } else {
                throw IllegalStateException("Fallback key encoding failed: encoded key is null")
            }
        } catch (fallbackException: Exception) {
            Log.e("LocationRepository", "Fallback key generation failed: ${fallbackException.message}", fallbackException)
            throw RuntimeException("Failed to generate encrypted key", fallbackException)
        }
    }
}
    
    private fun scheduleDataRetention() {
        val workRequest = PeriodicWorkRequestBuilder<DataRetentionWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "dataRetention",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

/**
 * Data exporter for sharing location data
 */
class DataExporter(private val context: Context) {
    private val repository by lazy { LocationRepository(context) }
    
    suspend fun exportData(format: String = "csv"): File {
        return withContext(Dispatchers.IO) {
            when (format.lowercase()) {
                "csv" -> exportToCsv()
                "gpx" -> exportToGpx()
                else -> exportToCsv()
            }
        }
    }
    
    private suspend fun exportToCsv(): File {
        val file = File(context.getExternalFilesDir(null), "location_data_${System.currentTimeMillis()}.csv")
        
        val sessionId = repository.getActiveSessionId() ?: run {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val sessions = repository.sessionDao.getSessionsBetween(
                calendar.timeInMillis,
                System.currentTimeMillis()
            )
            sessions.firstOrNull()?.sessionId
        }
        
        sessionId?.let { id ->
            val locations = repository.getLocationsBySession(id)
            
            file.bufferedWriter().use { writer ->
                writer.write("timestamp,latitude,longitude,accuracy,altitude,speed,bearing,distance,isIndoor\n")
                locations.forEach { location ->
                    writer.write("${location.timestamp},${location.latitude},${location.longitude}," +
                               "${location.accuracy},${location.altitude ?: ""},${location.speed ?: ""}," +
                               "${location.bearing ?: ""},${location.distanceFromPrevious},${location.isIndoor}\n")
                }
            }
        }
        
        return file
    }
    
    private suspend fun exportToGpx(): File {
        val file = File(context.getExternalFilesDir(null), "location_data_${System.currentTimeMillis()}.gpx")
        
        val sessionId = repository.getActiveSessionId() ?: run {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val sessions = repository.sessionDao.getSessionsBetween(
                calendar.timeInMillis,
                System.currentTimeMillis()
            )
            sessions.firstOrNull()?.sessionId
        }
        
        sessionId?.let { id ->
            val locations = repository.getLocationsBySession(id)
            val session = repository.sessionDao.getSession(id)
            
            file.bufferedWriter().use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
                writer.write("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\">\n")
                writer.write("  <metadata>\n")
                writer.write("    <name>Ozone tracking session ${session?.sessionId}</name>\n")
                writer.write("    <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(session?.startTime ?: 0))}</time>\n")
                writer.write("  </metadata>\n")
                writer.write("  <trk>\n")
                writer.write("    <name>Ozone tracking</name>\n")
                writer.write("    <trkseg>\n")
                
                locations.forEach { location ->
                    val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(location.timestamp))
                    writer.write("      <trkpt lat=\"${location.latitude}\" lon=\"${location.longitude}\">\n")
                    if (location.altitude != null) {
                        writer.write("        <ele>${location.altitude}</ele>\n")
                    }
                    writer.write("        <time>${time}</time>\n")
                    if (location.speed != null) {
                        writer.write("        <speed>${location.speed}</speed>\n")
                    }
                    writer.write("        <extensions>\n")
                    writer.write("          <isIndoor>${location.isIndoor}</isIndoor>\n")
                    writer.write("          <accuracy>${location.accuracy}</accuracy>\n")
                    writer.write("        </extensions>\n")
                    writer.write("      </trkpt>\n")
                }
                
                writer.write("    </trkseg>\n")
                writer.write("  </trk>\n")
                writer.write("</gpx>")
            }
        }
        
        return file
    }
}

/**
 * Configuration for tracking
 */
data class TrackingConfig(
    val accuracyThreshold: Float = 15f,
    val updateInterval: Long = 10000L,
    val minDistance: Float = 5f,
    val enableActivityDetection: Boolean = true,
    val autoStartDaily: Boolean = true,
    val maxTrackingHours: Int = 12,
    val maxSyncAttempts: Int = 3,
    val dataRetentionDays: Int = 30
)

/**
 * Worker for data retention cleanup
 */
class DataRetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = LocationRepository(applicationContext)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -TrackingConfig().dataRetentionDays)
        val cutoffTime = calendar.timeInMillis
        val oldSessions = repository.sessionDao.getSessionsBetween(0, cutoffTime)
        oldSessions.forEach { session ->
            repository.completeSession(session.sessionId)
        }
        return Result.success()
    }
}

/**
 * Error types for location tracking
 */
sealed class LocationError {
    object PermissionDenied : LocationError()
    data class ServiceError(val message: String?) : LocationError()
    object GpsDisabled : LocationError()
}

/**
 * Recovery manager for handling location service errors
 */
class LocationTrackingRecovery(private val context: Context) {
    fun handleLocationServiceError(error: LocationError) {
        when (error) {
            is LocationError.PermissionDenied -> {
                Log.e("LocationTrackingRecovery", "Permission denied")
            }
            is LocationError.ServiceError -> {
                Log.e("LocationTrackingRecovery", "Service error: ${error.message}")
            }
            is LocationError.GpsDisabled -> {
                Log.e("LocationTrackingRecovery", "GPS disabled")
            }
        }
    }
}

/**
 * Enhanced location processor for filtering and improving location data
 */
class EnhancedLocationProcessor {
    private var lastGpsTime = 0L
    private val recentLocations = ArrayDeque<Location>(5)

    fun processLocation(rawLocation: Location): ProcessedLocation? {
        if (rawLocation.time - lastGpsTime > 30000) {
            Log.w("LocationService", "GPS signal may be stale")
        }
        lastGpsTime = rawLocation.time
        
        val isIndoor = rawLocation.accuracy > 50f
        val accuracyThreshold = if (isIndoor) 30f else 15f
        val driftThreshold = if (isIndoor) 10f else 35f
        
        if (rawLocation.accuracy > accuracyThreshold) {
            Log.w("LocationService", "Ignoring location due to poor accuracy: ${rawLocation.accuracy}m")
            return null
        }
        
        // Simplified Kalman filter
        val filteredLocation = applySimpleFilter(rawLocation)
        
        if (isDriftDetected(filteredLocation, driftThreshold)) {
            Log.w("LocationService", "Potential GPS drift detected")
            return null
        }
        
        addToRecentLocations(filteredLocation)
        return ProcessedLocation(filteredLocation, isIndoor)
    }

    private fun applySimpleFilter(location: Location): Location {
        // Simplified filtering for better performance
        val newLocation = Location(location)
        // Basic smoothing of coordinates
        recentLocations.lastOrNull()?.let { last ->
            val weight = 0.7
            newLocation.latitude = weight * last.latitude + (1 - weight) * location.latitude
            newLocation.longitude = weight * last.longitude + (1 - weight) * location.longitude
        }
        return newLocation
    }

    private fun isDriftDetected(location: Location, threshold: Float): Boolean {
        recentLocations.lastOrNull()?.let { prev ->
            val distance = prev.distanceTo(location)
            val timeDelta = (location.time - prev.time) / 1000.0
            val speed = if (timeDelta > 0) distance / timeDelta else 0.0
            
            return speed > threshold || (distance > 100 && timeDelta < 5)
        }
        return false
    }

    private fun addToRecentLocations(location: Location) {
        recentLocations.addLast(location)
        if (recentLocations.size > 5) {
            recentLocations.removeFirst()
        }
    }
}

/**
 * Class to represent processed location data
 */
data class ProcessedLocation(
    val location: Location,
    val isIndoor: Boolean
)
