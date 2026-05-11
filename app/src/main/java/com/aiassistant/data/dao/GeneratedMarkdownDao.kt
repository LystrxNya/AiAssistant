package com.aiassistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiassistant.data.entity.GeneratedMarkdownEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedMarkdownDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(markdown: GeneratedMarkdownEntity): Long

    @Delete
    suspend fun delete(markdown: GeneratedMarkdownEntity)

    @Query("SELECT * FROM generated_markdowns ORDER BY createdAt DESC")
    fun getAllMarkdowns(): Flow<List<GeneratedMarkdownEntity>>

    @Query("SELECT * FROM generated_markdowns WHERE id = :id")
    suspend fun getMarkdownById(id: Long): GeneratedMarkdownEntity?

    @Query("SELECT * FROM generated_markdowns WHERE sourceModule = :module ORDER BY createdAt DESC")
    fun getMarkdownsByModule(module: String): Flow<List<GeneratedMarkdownEntity>>

    @Query("DELETE FROM generated_markdowns")
    suspend fun deleteAll()
}
