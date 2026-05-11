package com.aiassistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiassistant.data.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE personaId = :personaId ORDER BY importance DESC, lastAccessedAt DESC")
    suspend fun getMemoriesByPersona(personaId: Long): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE personaId = :personaId ORDER BY importance DESC, lastAccessedAt DESC")
    fun observeMemoriesByPersona(personaId: Long): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories
        WHERE personaId = :personaId
        AND (summary LIKE '%' || :keyword || '%'
             OR topics LIKE '%' || :keyword || '%'
             OR keyFacts LIKE '%' || :keyword || '%')
        ORDER BY importance DESC
    """)
    suspend fun searchMemories(personaId: Long, keyword: String): List<MemoryEntity>

    @Query("UPDATE memories SET lastAccessedAt = :time WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET importance = :importance WHERE id = :id")
    suspend fun updateImportance(id: Long, importance: Float)

    @Query("SELECT COUNT(*) FROM memories WHERE personaId = :personaId")
    suspend fun getMemoryCount(personaId: Long): Int

    @Query("DELETE FROM memories WHERE personaId = :personaId AND importance < :threshold")
    suspend fun deleteLowImportance(personaId: Long, threshold: Float)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories WHERE personaId = :personaId")
    suspend fun deleteAllByPersona(personaId: Long)

    @Query("""
        DELETE FROM memories WHERE id IN (
            SELECT id FROM memories WHERE personaId = :personaId
            ORDER BY importance ASC, lastAccessedAt ASC
            LIMIT :count
        )
    """)
    suspend fun deleteLowestImportance(personaId: Long, count: Int)
}
