package com.aiassistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiassistant.data.entity.PersonaEntity
import com.aiassistant.data.entity.PersonaType
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaEntity): Long

    @Update
    suspend fun update(persona: PersonaEntity)

    @Delete
    suspend fun delete(persona: PersonaEntity)

    @Query("SELECT * FROM personas ORDER BY createdAt ASC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getPersonaById(id: Long): PersonaEntity?

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPersona(): PersonaEntity?

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    fun observeDefaultPersona(): Flow<PersonaEntity?>

    @Query("SELECT * FROM personas WHERE type = :type ORDER BY createdAt ASC")
    fun getPersonasByType(type: PersonaType): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE type = :type")
    suspend fun getPersonasByTypeOnce(type: PersonaType): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE type = :type AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultByType(type: PersonaType): PersonaEntity?

    @Query("UPDATE personas SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefaultPersona()

    @Query("UPDATE personas SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultPersona(id: Long)
}
