package com.aiassistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiassistant.data.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Delete
    suspend fun deleteTodos(todos: List<TodoEntity>)

    @Query("SELECT * FROM todos ORDER BY isCompleted ASC, dueTime ASC, createdAt DESC")
    fun getAllTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isCompleted = 0 ORDER BY dueTime ASC, createdAt DESC")
    fun getPendingTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE isCompleted = 1 ORDER BY updatedAt DESC")
    fun getCompletedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getTodoById(id: Long): TodoEntity?

    @Query("DELETE FROM todos WHERE isCompleted = 1")
    suspend fun deleteAllCompleted()

    @Query("SELECT * FROM todos ORDER BY isCompleted ASC, dueTime ASC, createdAt DESC")
    suspend fun getAllTodosOnce(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE isCompleted = 0 ORDER BY dueTime ASC, createdAt DESC")
    suspend fun getPendingTodosOnce(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE isCompleted = 1 ORDER BY updatedAt DESC")
    suspend fun getCompletedTodosOnce(): List<TodoEntity>
}
