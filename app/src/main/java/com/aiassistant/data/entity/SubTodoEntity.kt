package com.aiassistant.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sub_todos",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentId"])]
)
data class SubTodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentId: Long,
    val title: String,
    val description: String = "",
    val dueTime: Long? = null,
    val reminderMinutesBefore: List<Int> = emptyList(),
    val isCompleted: Boolean = false,
    val totalElapsedSeconds: Long = 0,
    val timingRecords: List<StartEndTime> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
