package com.aiassistant.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiassistant.data.database.AppDatabase
import com.aiassistant.data.dao.AppLogDao
import com.aiassistant.data.dao.ConversationDao
import com.aiassistant.data.dao.GeneratedMarkdownDao
import com.aiassistant.data.dao.MemoryDao
import com.aiassistant.data.dao.MessageDao
import com.aiassistant.data.dao.PersonaDao
import com.aiassistant.data.dao.SubTodoDao
import com.aiassistant.data.dao.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // TODO: fallbackToDestructiveMigration() will DESTROY ALL DATA on any schema change
            // (version bump without a matching migration). This is acceptable during development
            // but will cause data loss in production. Replace with proper Migration objects:
            //   .addMigrations(MIGRATION_X_Y, ...)
            // See https://developer.android.com/training/data-storage/room/migrating-db-versions
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    insertDefaultPersonas(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    val cursor = db.query("SELECT COUNT(*) FROM personas")
                    cursor.moveToFirst()
                    val count = cursor.getInt(0)
                    cursor.close()
                    if (count == 0) {
                        insertDefaultPersonas(db)
                    } else {
                        ensureTimeManagerPersona(db)
                    }
                }

                private val TIME_MANAGER_PROMPT = """你是一个专业的时间管理助手，帮助用户管理待办事项和时间安排。

## 工具使用规则

你拥有读取和修改待办事项的工具。请严格遵守以下规则：

### 读取操作（自由执行）
- 查看待办列表、查看待办详情：可以直接执行，无需询问用户。

### 写入操作（必须确认）
- 创建待办、编辑待办、删除待办、标记完成等所有修改操作：**必须先向用户确认**，获得明确同意后才能执行。
- 确认时应列出完整的操作内容，例如：「我将为你创建以下待办：标题=XXX，截止时间=XXX，提醒=截止前15分钟和1小时。确认创建吗？」

### 信息完整性
- 创建待办时，标题和截止时间为必填项。如果用户没有提供，必须追问。
- 提醒次数（reminder_minutes_before）如果用户未指定，可以不设置或建议默认值（如截止前15分钟）。

### 响应风格
- 使用中文回复。
- 操作完成后给出简洁的确认信息。
- 查看待办时，用清晰的列表格式展示，标注截止时间和完成状态。
- 时间使用自然语言描述（如「明天下午2点」「后天上午10点」）。
- 如果待办有子待办，一并展示子待办的完成情况。"""

                private fun ensureTimeManagerPersona(db: SupportSQLiteDatabase) {
                    val cursor = db.query("SELECT id, systemPrompt FROM personas WHERE name = '时间管理大师'")
                    val exists = cursor.moveToFirst()
                    if (exists) {
                        // Update existing persona's prompt if it still has the old text-based tool calling format
                        val existingPrompt = cursor.getString(cursor.getColumnIndexOrThrow("systemPrompt")) ?: ""
                        if (existingPrompt.contains("工具调用方式") || existingPrompt.contains("tool_call")) {
                            val personaId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                            cursor.close()
                            val escaped = TIME_MANAGER_PROMPT.replace("'", "''")
                            db.execSQL("UPDATE personas SET systemPrompt = '$escaped' WHERE id = $personaId")
                        } else {
                            cursor.close()
                        }
                    } else {
                        cursor.close()
                    }
                    if (!exists) {
                        val now = System.currentTimeMillis()
                        val escaped = TIME_MANAGER_PROMPT.replace("'", "''")
                        db.execSQL(
                            """INSERT INTO personas (name, systemPrompt, description, icon, isDefault, modelId, type, createdAt)
                               VALUES ('时间管理大师', '$escaped', 'AI 帮你管理待办和时间', '⏰', 0, '', 'CHAT', $now)"""
                        )
                    }
                }

                private fun insertDefaultPersonas(db: SupportSQLiteDatabase) {
                    val now = System.currentTimeMillis()
                    db.execSQL(
                        """INSERT INTO personas (name, systemPrompt, description, icon, isDefault, modelId, type, createdAt)
                           VALUES ('通用助手', '你是一个有用的AI助手，请用中文回答问题。', '日常问答与对话', '🤖', 1, '', 'CHAT', $now)"""
                    )
                    db.execSQL(
                        """INSERT INTO personas (name, systemPrompt, description, icon, isDefault, modelId, type, createdAt)
                           VALUES ('代码专家', '你是一个资深软件工程师，擅长代码编写、调试和架构设计。请用中文回答，代码部分使用Markdown格式。', '编程与技术问题', '💻', 0, '', 'CHAT', $now)"""
                    )
                    db.execSQL(
                        """INSERT INTO personas (name, systemPrompt, description, icon, isDefault, modelId, type, createdAt)
                           VALUES ('写作助手', '你是一个专业的写作助手，擅长各种文体的写作、润色和翻译。请用中文回答。', '文案写作与润色', '✍️', 0, '', 'CHAT', $now)"""
                    )
                    val escapedInsert = TIME_MANAGER_PROMPT.replace("'", "''")
                    db.execSQL(
                        """INSERT INTO personas (name, systemPrompt, description, icon, isDefault, modelId, type, createdAt)
                           VALUES ('时间管理大师', '$escapedInsert', 'AI 帮你管理待办和时间', '⏰', 0, '', 'CHAT', $now)"""
                    )
                }
            })
            .build()
    }

    @Provides
    fun providePersonaDao(database: AppDatabase): PersonaDao = database.personaDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideSubTodoDao(database: AppDatabase): SubTodoDao = database.subTodoDao()

    @Provides
    fun provideGeneratedMarkdownDao(database: AppDatabase): GeneratedMarkdownDao = database.generatedMarkdownDao()

    @Provides
    fun provideAppLogDao(database: AppDatabase): AppLogDao = database.appLogDao()

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.memoryDao()
}
