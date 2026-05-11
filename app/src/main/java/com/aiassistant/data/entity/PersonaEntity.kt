package com.aiassistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PersonaType { CHAT, ROLEPLAY }

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val description: String = "",
    val icon: String = "🤖",
    val isDefault: Boolean = false,
    val modelId: String = "",
    val type: PersonaType = PersonaType.CHAT,
    val createdAt: Long = System.currentTimeMillis()
)
