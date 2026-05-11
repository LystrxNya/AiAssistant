package com.aiassistant.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiassistant.data.dao.MemoryDao
import com.aiassistant.data.dao.PersonaDao
import com.aiassistant.data.entity.PersonaType
import com.aiassistant.data.repository.MemoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MemoryDecayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryDao: MemoryDao,
    private val personaDao: PersonaDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val personas = personaDao.getPersonasByTypeOnce(PersonaType.ROLEPLAY)
            for (persona in personas) {
                val memories = memoryDao.getMemoriesByPersona(persona.id)
                for (memory in memories) {
                    val newImportance = (memory.importance - MemoryRepository.DECAY_RATE_PER_DAY).coerceAtLeast(0f)
                    memoryDao.updateImportance(memory.id, newImportance)
                }
                memoryDao.deleteLowImportance(persona.id, MemoryRepository.CLEANUP_THRESHOLD)

                val count = memoryDao.getMemoryCount(persona.id)
                if (count > MemoryRepository.MAX_MEMORIES_PER_PERSONA) {
                    memoryDao.deleteLowestImportance(persona.id, count - MemoryRepository.MAX_MEMORIES_PER_PERSONA)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
