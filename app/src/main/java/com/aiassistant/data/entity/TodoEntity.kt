package com.aiassistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

data class StartEndTime(
    val startTime: Long,
    val endTime: Long? = null
)

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueTime: Long? = null,
    val reminderMinutesBefore: List<Int> = emptyList(),
    val estimatedDurationSeconds: Long = 0,
    val totalElapsedSeconds: Long = 0,
    val timingRecords: List<StartEndTime> = emptyList(),
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
