package com.aiassistant.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aiassistant.data.converter.Converters
import com.aiassistant.data.dao.AppLogDao
import com.aiassistant.data.dao.ConversationDao
import com.aiassistant.data.dao.GeneratedMarkdownDao
import com.aiassistant.data.dao.MemoryDao
import com.aiassistant.data.dao.MessageDao
import com.aiassistant.data.dao.PersonaDao
import com.aiassistant.data.dao.SubTodoDao
import com.aiassistant.data.dao.TodoDao
import com.aiassistant.data.entity.AppLogEntity
import com.aiassistant.data.entity.ConversationEntity
import com.aiassistant.data.entity.GeneratedMarkdownEntity
import com.aiassistant.data.entity.MemoryEntity
import com.aiassistant.data.entity.MessageEntity
import com.aiassistant.data.entity.PersonaEntity
import com.aiassistant.data.entity.SubTodoEntity
import com.aiassistant.data.entity.TodoEntity

@Database(
    entities = [
        PersonaEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        TodoEntity::class,
        SubTodoEntity::class,
        GeneratedMarkdownEntity::class,
        AppLogEntity::class,
        MemoryEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personaDao(): PersonaDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun todoDao(): TodoDao
    abstract fun subTodoDao(): SubTodoDao
    abstract fun generatedMarkdownDao(): GeneratedMarkdownDao
    abstract fun appLogDao(): AppLogDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val DATABASE_NAME = "ai_assistant_db"
    }
}
