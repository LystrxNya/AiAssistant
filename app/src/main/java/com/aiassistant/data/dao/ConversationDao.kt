package com.aiassistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiassistant.data.entity.ConversationEntity
import com.aiassistant.data.entity.ConversationType
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE personaId = :personaId ORDER BY updatedAt DESC")
    fun getConversationsByPersona(personaId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE type = :type ORDER BY updatedAt DESC")
    fun getConversationsByType(type: ConversationType): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Query("DELETE FROM conversations WHERE personaId = :personaId")
    suspend fun deleteAllByPersona(personaId: Long)

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun updateTimestamp(id: Long, time: Long = System.currentTimeMillis())
}
