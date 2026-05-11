package com.aiassistant.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole { USER, ASSISTANT, SYSTEM }

enum class ConversationState { IDLE, THINKING, STREAMING, SUCCESS, ERROR }

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val thinkingContent: String = "",
    val imagePaths: List<String> = emptyList(),
    val state: ConversationState = ConversationState.SUCCESS,
    val createdAt: Long = System.currentTimeMillis()
)
