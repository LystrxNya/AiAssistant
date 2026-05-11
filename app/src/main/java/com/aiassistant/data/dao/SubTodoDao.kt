package com.aiassistant.data.dao

import androidx.room.*
import com.aiassistant.data.entity.SubTodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubTodoDao {
    @Query("SELECT * FROM sub_todos WHERE parentId = :parentId ORDER BY createdAt ASC")
    fun getSubTodosByParentId(parentId: Long): Flow<List<SubTodoEntity>>

    @Query("SELECT * FROM sub_todos WHERE parentId = :parentId ORDER BY createdAt ASC")
    suspend fun getSubTodosByParentIdOnce(parentId: Long): List<SubTodoEntity>

    @Query("SELECT * FROM sub_todos WHERE id = :id")
    suspend fun getSubTodoById(id: Long): SubTodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subTodo: SubTodoEntity): Long

    @Update
    suspend fun update(subTodo: SubTodoEntity)

    @Delete
    suspend fun delete(subTodo: SubTodoEntity)

    @Query("DELETE FROM sub_todos WHERE parentId = :parentId")
    suspend fun deleteByParentId(parentId: Long)

    @Query("SELECT COALESCE(SUM(totalElapsedSeconds), 0) FROM sub_todos WHERE parentId = :parentId")
    suspend fun getTotalElapsedByParentId(parentId: Long): Long
}
