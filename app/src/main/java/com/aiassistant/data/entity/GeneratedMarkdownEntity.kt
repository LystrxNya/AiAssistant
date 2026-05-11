package com.aiassistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generated_markdowns")
data class GeneratedMarkdownEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val sourceModule: String,
    val createdAt: Long = System.currentTimeMillis()
)
