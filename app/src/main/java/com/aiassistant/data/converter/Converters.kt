package com.aiassistant.data.converter

import androidx.room.TypeConverter
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.data.entity.ConversationType
import com.aiassistant.data.entity.MessageRole
import com.aiassistant.data.entity.PersonaType
import com.aiassistant.data.entity.StartEndTime
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStartEndTimeList(value: List<StartEndTime>): String = gson.toJson(value)

    @TypeConverter
    fun toStartEndTimeList(value: String): List<StartEndTime> {
        return try {
            val type = object : TypeToken<List<StartEndTime>>() {}.type
            gson.fromJson(value, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromIntList(value: List<Int>): String = gson.toJson(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val type = object : TypeToken<List<Int>>() {}.type
        return try { gson.fromJson(value, type) } catch (_: Exception) { emptyList() }
    }

    @TypeConverter
    fun fromMessageRole(value: MessageRole): String = value.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun fromConversationState(value: ConversationState): String = value.name

    @TypeConverter
    fun toConversationState(value: String): ConversationState = ConversationState.valueOf(value)

    @TypeConverter
    fun fromPersonaType(value: PersonaType): String = value.name

    @TypeConverter
    fun toPersonaType(value: String): PersonaType = try {
        PersonaType.valueOf(value)
    } catch (_: Exception) {
        PersonaType.CHAT
    }

    @TypeConverter
    fun fromConversationType(value: ConversationType): String = value.name

    @TypeConverter
    fun toConversationType(value: String): ConversationType = try {
        ConversationType.valueOf(value)
    } catch (_: Exception) {
        ConversationType.CHAT
    }
}
