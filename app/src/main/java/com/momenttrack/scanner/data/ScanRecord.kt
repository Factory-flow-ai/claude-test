package com.momenttrack.scanner.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

@Entity(tableName = "scans")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val format: String,
    val timestamp: Long,
    val deviceId: String,
    val locationId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null
)

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(scan: ScanRecord): Long

    @Update
    suspend fun update(scan: ScanRecord)

    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScans(limit: Int): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getScansAfter(startOfDay: Long): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE synced = 0 ORDER BY timestamp ASC")
    fun getPendingScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scans WHERE synced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingScansBatch(limit: Int): List<ScanRecord>

    @Query("UPDATE scans SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE scans SET syncAttempts = syncAttempts + 1, lastSyncAttempt = :timestamp WHERE id = :id")
    suspend fun incrementSyncAttempt(id: Long, timestamp: Long)

    @Query("DELETE FROM scans")
    suspend fun deleteAll()

    @Query("DELETE FROM scans WHERE synced = 1 AND timestamp < :before")
    suspend fun deleteSyncedBefore(before: Long)
}

@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getDatabase(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "momenttrack_scans"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ScanRepository(context: Context) {
    private val dao = ScanDatabase.getDatabase(context).scanDao()

    suspend fun insert(scan: ScanRecord): Long = dao.insert(scan)

    suspend fun update(scan: ScanRecord) = dao.update(scan)

    fun getRecentScans(limit: Int): Flow<List<ScanRecord>> = dao.getRecentScans(limit)

    fun getTodayScans(): Flow<List<ScanRecord>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return dao.getScansAfter(calendar.timeInMillis)
    }

    fun getPendingScans(): Flow<List<ScanRecord>> = dao.getPendingScans()

    suspend fun getPendingScansBatch(limit: Int): List<ScanRecord> = dao.getPendingScansBatch(limit)

    suspend fun markSynced(id: Long) = dao.markSynced(id)

    suspend fun incrementSyncAttempt(id: Long) = dao.incrementSyncAttempt(id, System.currentTimeMillis())

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun cleanupOldSynced(daysOld: Int = 7) {
        val cutoff = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        dao.deleteSyncedBefore(cutoff)
    }
}
