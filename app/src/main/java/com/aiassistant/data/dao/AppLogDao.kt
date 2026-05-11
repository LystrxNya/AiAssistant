package com.aiassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiassistant.data.entity.AppLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AppLogEntity): Long

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AppLogEntity>>

    @Query("SELECT * FROM app_logs WHERE module = :module ORDER BY timestamp DESC")
    fun getLogsByModule(module: String): Flow<List<AppLogEntity>>

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<AppLogEntity>

    @Query("DELETE FROM app_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM app_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsOnce(): List<AppLogEntity>
}
