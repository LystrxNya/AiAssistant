package com.aiassistant.ui.todo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.dao.TodoDao
import com.aiassistant.data.dao.SubTodoDao
import com.aiassistant.data.entity.StartEndTime
import com.aiassistant.data.entity.SubTodoEntity
import com.aiassistant.data.entity.TodoEntity
import com.aiassistant.data.network.model.PromptTemplates
import com.aiassistant.data.repository.ChatRepository
import com.aiassistant.manager.AlarmScheduler
import com.aiassistant.manager.StopwatchManager
import com.aiassistant.manager.StopwatchState
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoFormState(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val dueTime: Long? = System.currentTimeMillis(),
    val reminderMinutesBefore: List<Int> = emptyList(),
    val estimatedDurationSeconds: Long = 0,
    val totalElapsedSeconds: Long = 0,
    val timingRecords: List<StartEndTime> = emptyList(),
    val isEditing: Boolean = false
)

sealed class TodoEvent {
    data class ShowToast(val message: String) : TodoEvent()
    data object NavigateBack : TodoEvent()
    data class AiExtracted(val formState: TodoFormState) : TodoEvent()
}

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoDao: TodoDao,
    private val subTodoDao: SubTodoDao,
    private val chatRepository: ChatRepository,
    private val stopwatchManager: StopwatchManager,
    private val alarmScheduler: AlarmScheduler,
    private val gson: Gson
) : ViewModel() {

    val todos: StateFlow<List<TodoEntity>> = todoDao.getAllTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _subTodos = MutableStateFlow<List<SubTodoEntity>>(emptyList())
    val subTodos: StateFlow<List<SubTodoEntity>> = _subTodos.asStateFlow()

    private val _formState = MutableStateFlow(TodoFormState())
    val formState: StateFlow<TodoFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<TodoEvent>()
    val events: SharedFlow<TodoEvent> = _events.asSharedFlow()

    private val _isAiExtracting = MutableStateFlow(false)
    val isAiExtracting: StateFlow<Boolean> = _isAiExtracting.asStateFlow()

    private val _currentTimingSubTodoId = MutableStateFlow<Long?>(null)
    val currentTimingSubTodoId: StateFlow<Long?> = _currentTimingSubTodoId.asStateFlow()

    private var loadSubTodosJob: Job? = null

    val stopwatchState: StateFlow<StopwatchState> = stopwatchManager.state
    val stopwatchElapsed: SharedFlow<Long> = stopwatchManager.elapsedSeconds

    fun onTitleChange(value: String) {
        _formState.value = _formState.value.copy(title = value)
    }

    fun onDescriptionChange(value: String) {
        _formState.value = _formState.value.copy(description = value)
    }

    fun onDueTimeChange(value: Long?) {
        _formState.value = _formState.value.copy(dueTime = value)
    }

    fun addReminder(minutesBefore: Int) {
        val current = _formState.value.reminderMinutesBefore.toMutableList()
        current.add(minutesBefore.coerceIn(1, 1440))
        _formState.value = _formState.value.copy(reminderMinutesBefore = current)
    }

    fun removeReminder(index: Int) {
        val current = _formState.value.reminderMinutesBefore.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _formState.value = _formState.value.copy(reminderMinutesBefore = current)
        }
    }

    fun updateReminder(index: Int, minutesBefore: Int) {
        val current = _formState.value.reminderMinutesBefore.toMutableList()
        if (index in current.indices) {
            current[index] = minutesBefore.coerceIn(1, 1440)
            _formState.value = _formState.value.copy(reminderMinutesBefore = current)
        }
    }

    fun resetForm() {
        _formState.value = TodoFormState()
        _currentTimingSubTodoId.value = null
    }

    fun loadTodo(id: Long) {
        viewModelScope.launch {
            val todo = todoDao.getTodoById(id) ?: return@launch
            _formState.value = TodoFormState(
                id = todo.id,
                title = todo.title,
                description = todo.description,
                dueTime = todo.dueTime,
                reminderMinutesBefore = todo.reminderMinutesBefore,
                estimatedDurationSeconds = todo.estimatedDurationSeconds,
                totalElapsedSeconds = todo.totalElapsedSeconds,
                timingRecords = todo.timingRecords,
                isEditing = true
            )
        }
    }

    fun saveTodo() {
        val form = _formState.value
        if (form.title.isBlank()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("标题不能为空")) }
            return
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val entity = if (form.isEditing) {
                    val existing = todoDao.getTodoById(form.id)
                    TodoEntity(
                        id = form.id,
                        title = form.title,
                        description = form.description,
                        dueTime = form.dueTime,
                        reminderMinutesBefore = form.reminderMinutesBefore,
                        estimatedDurationSeconds = form.estimatedDurationSeconds,
                        totalElapsedSeconds = form.totalElapsedSeconds,
                        timingRecords = form.timingRecords,
                        isCompleted = existing?.isCompleted ?: false,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                } else {
                    TodoEntity(
                        title = form.title,
                        description = form.description,
                        dueTime = form.dueTime,
                        reminderMinutesBefore = form.reminderMinutesBefore,
                        estimatedDurationSeconds = form.estimatedDurationSeconds,
                        createdAt = now,
                        updatedAt = now
                    )
                }

                val savedId = if (form.isEditing) {
                    todoDao.update(entity)
                    form.id
                } else {
                    todoDao.insert(entity)
                }

                form.dueTime?.let { dueTime ->
                    if (dueTime > System.currentTimeMillis() && form.reminderMinutesBefore.isNotEmpty()) {
                        alarmScheduler.scheduleReminders(savedId, form.title, dueTime, form.reminderMinutesBefore)
                    }
                }

                _events.emit(TodoEvent.ShowToast("已保存"))
                _events.emit(TodoEvent.NavigateBack)
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("保存失败: ${e.message}"))
            }
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            try {
                if (todo.reminderMinutesBefore.isNotEmpty()) {
                    alarmScheduler.cancelReminders(todo.id, todo.reminderMinutesBefore.size)
                }
                todoDao.delete(todo)
                _events.emit(TodoEvent.ShowToast("已删除"))
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("删除失败: ${e.message}"))
            }
        }
    }

    fun toggleComplete(todo: TodoEntity) {
        viewModelScope.launch {
            try {
                val updated = todo.copy(
                    isCompleted = !todo.isCompleted,
                    updatedAt = System.currentTimeMillis()
                )
                todoDao.update(updated)
                if (updated.isCompleted && todo.reminderMinutesBefore.isNotEmpty()) {
                    alarmScheduler.cancelReminders(todo.id, todo.reminderMinutesBefore.size)
                }
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("操作失败: ${e.message}"))
            }
        }
    }

    fun deleteAllCompleted() {
        viewModelScope.launch {
            try {
                todoDao.deleteAllCompleted()
                _events.emit(TodoEvent.ShowToast("已清除所有已完成"))
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("清除失败: ${e.message}"))
            }
        }
    }

    fun deleteTodos(todos: List<TodoEntity>) {
        viewModelScope.launch {
            try {
                for (todo in todos) {
                    if (todo.reminderMinutesBefore.isNotEmpty()) {
                        alarmScheduler.cancelReminders(todo.id, todo.reminderMinutesBefore.size)
                    }
                }
                todoDao.deleteTodos(todos)
                _events.emit(TodoEvent.ShowToast("已删除 ${todos.size} 项"))
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("删除失败: ${e.message}"))
            }
        }
    }

    fun batchToggleComplete(todos: List<TodoEntity>, completed: Boolean) {
        viewModelScope.launch {
            try {
                for (todo in todos) {
                    val updated = todo.copy(isCompleted = completed, updatedAt = System.currentTimeMillis())
                    todoDao.update(updated)
                    if (completed && todo.reminderMinutesBefore.isNotEmpty()) {
                        alarmScheduler.cancelReminders(todo.id, todo.reminderMinutesBefore.size)
                    }
                }
                _events.emit(TodoEvent.ShowToast("已更新 ${todos.size} 项"))
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("操作失败: ${e.message}"))
            }
        }
    }

    private fun stopCurrentTimerAndSave() {
        val elapsed = stopwatchManager.stop()
        if (elapsed > 0) {
            val targetSubTodoId = _currentTimingSubTodoId.value
            if (targetSubTodoId == null) {
                val form = _formState.value
                val newTotal = form.totalElapsedSeconds + elapsed
                val newRecord = StartEndTime(
                    startTime = System.currentTimeMillis() - elapsed * 1000,
                    endTime = System.currentTimeMillis()
                )
                _formState.value = form.copy(
                    totalElapsedSeconds = newTotal,
                    timingRecords = form.timingRecords + newRecord
                )
                viewModelScope.launch { saveTodoSilently() }
            } else {
                viewModelScope.launch {
                    val subTodo = subTodoDao.getSubTodoById(targetSubTodoId) ?: return@launch
                    val newTotal = subTodo.totalElapsedSeconds + elapsed
                    val newRecord = StartEndTime(
                        startTime = System.currentTimeMillis() - elapsed * 1000,
                        endTime = System.currentTimeMillis()
                    )
                    subTodoDao.update(
                        subTodo.copy(
                            totalElapsedSeconds = newTotal,
                            timingRecords = subTodo.timingRecords + newRecord,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        _currentTimingSubTodoId.value = null
    }

    fun startStopwatch() {
        if (stopwatchManager.state.value != StopwatchState.IDLE) {
            stopCurrentTimerAndSave()
        }
        _currentTimingSubTodoId.value = null
        stopwatchManager.start()
    }

    fun startSubTodoStopwatch(subTodoId: Long) {
        if (stopwatchManager.state.value != StopwatchState.IDLE) {
            stopCurrentTimerAndSave()
        }
        _currentTimingSubTodoId.value = subTodoId
        stopwatchManager.start()
    }

    fun pauseStopwatch() {
        stopwatchManager.pause()
    }

    fun resumeStopwatch() {
        stopwatchManager.resume()
    }

    fun stopStopwatch() {
        stopCurrentTimerAndSave()
    }

    private suspend fun saveTodoSilently() {
        val form = _formState.value
        if (form.title.isBlank() || !form.isEditing) return
        try {
            val now = System.currentTimeMillis()
            val existing = todoDao.getTodoById(form.id)
            val entity = TodoEntity(
                id = form.id,
                title = form.title,
                description = form.description,
                dueTime = form.dueTime,
                reminderMinutesBefore = form.reminderMinutesBefore,
                estimatedDurationSeconds = form.estimatedDurationSeconds,
                totalElapsedSeconds = form.totalElapsedSeconds,
                timingRecords = form.timingRecords,
                isCompleted = existing?.isCompleted ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            todoDao.update(entity)
        } catch (_: Exception) {}
    }

    fun restoreStopwatchState(): Long {
        return stopwatchManager.restoreState()
    }

    fun extractTodoWithAi(inputText: String) {
        if (inputText.isBlank()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("请输入文本")) }
            return
        }
        if (!chatRepository.isAuxConfigured()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("请先配置辅助小模型")) }
            return
        }
        viewModelScope.launch {
            _isAiExtracting.value = true
            try {
                val messages = chatRepository.buildMessages(
                    systemPrompt = PromptTemplates.TODO_EXTRACT,
                    conversationMessages = listOf("user" to inputText)
                )
                val result = streamExtract(messages)
                applyExtractedResult(result)
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("AI提取失败: ${e.message}"))
            } finally {
                _isAiExtracting.value = false
            }
        }
    }

    fun extractTodoWithImages(uris: List<Uri>) {
        if (uris.isEmpty()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("请先选择图片")) }
            return
        }
        if (!chatRepository.isVisionConfigured()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("请先配置重度视觉模型")) }
            return
        }
        viewModelScope.launch {
            _isAiExtracting.value = true
            try {
                val base64List = chatRepository.processImageUris(uris)
                if (base64List.isEmpty()) {
                    _events.emit(TodoEvent.ShowToast("图片处理失败，请重试"))
                    return@launch
                }
                val messages = chatRepository.buildMessagesWithImages(
                    systemPrompt = PromptTemplates.TODO_EXTRACT + "\n用户将提供图片，请从图片内容中提取待办信息。",
                    conversationMessages = listOf(
                        Triple("user", "请从这张/这些图片中提取待办事项信息。", base64List)
                    )
                )
                val result = streamExtract(messages, modelType = "vision")
                applyExtractedResult(result)
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("AI图片提取失败: ${e.message}"))
            } finally {
                _isAiExtracting.value = false
            }
        }
    }

    fun extractTodoWithTextAndImages(inputText: String, uris: List<Uri>) {
        if (inputText.isBlank() && uris.isEmpty()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("请输入文本或选择图片")) }
            return
        }
        if (!chatRepository.isVisionConfigured()) {
            viewModelScope.launch { _events.emit(TodoEvent.ShowToast("请先配置重度视觉模型")) }
            return
        }
        viewModelScope.launch {
            _isAiExtracting.value = true
            try {
                val base64List = chatRepository.processImageUris(uris)
                if (base64List.isEmpty()) {
                    _events.emit(TodoEvent.ShowToast("图片处理失败，请重试"))
                    return@launch
                }
                val userText = inputText.ifBlank { "请从图片中提取待办事项信息。" }
                val messages = chatRepository.buildMessagesWithImages(
                    systemPrompt = PromptTemplates.TODO_EXTRACT + "\n用户将提供文字说明和图片，请综合所有信息提取待办事项。",
                    conversationMessages = listOf(
                        Triple("user", userText, base64List)
                    )
                )
                val result = streamExtract(messages, modelType = "vision")
                applyExtractedResult(result)
            } catch (e: Exception) {
                _events.emit(TodoEvent.ShowToast("AI提取失败: ${e.message}"))
            } finally {
                _isAiExtracting.value = false
            }
        }
    }

    private suspend fun streamExtract(
        messages: List<com.aiassistant.data.network.model.ChatMessage>,
        modelType: String = "aux"
    ): String {
        var result = ""
        chatRepository.streamChat(
            messages = messages,
            temperature = 0.1f,
            modelType = modelType
        ).collect { streamResult ->
            if (streamResult.error != null) throw Exception(streamResult.error)
            if (streamResult.isDone) return@collect
            if (streamResult.content.isNotEmpty()) result += streamResult.content
        }
        return result
    }

    private suspend fun applyExtractedResult(rawResult: String) {
        val extracted = parseExtractedJson(rawResult)
        if (extracted != null) {
            _formState.value = _formState.value.copy(
                title = extracted.title,
                dueTime = extracted.dueTime,
                reminderMinutesBefore = extracted.reminderMinutesBefore
            )
            _events.emit(TodoEvent.ShowToast("AI已提取待办信息"))
        } else {
            _formState.value = _formState.value.copy(title = rawResult.trim())
            _events.emit(TodoEvent.ShowToast("AI返回格式异常，已将原文填入标题"))
        }
    }

    private fun parseExtractedJson(raw: String): ExtractedTodo? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            gson.fromJson(cleaned, ExtractedTodo::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    fun formatTime(totalSeconds: Long): String {
        return stopwatchManager.formatTime(totalSeconds)
    }

    fun loadSubTodos(parentId: Long) {
        loadSubTodosJob?.cancel()
        loadSubTodosJob = viewModelScope.launch {
            subTodoDao.getSubTodosByParentId(parentId).collect { list ->
                _subTodos.value = list
            }
        }
    }

    fun addSubTodo(parentId: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val entity = SubTodoEntity(
                parentId = parentId,
                title = title.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            subTodoDao.insert(entity)
        }
    }

    fun updateSubTodo(subTodo: SubTodoEntity) {
        viewModelScope.launch {
            subTodoDao.update(subTodo.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateSubTodoDueTime(subTodo: SubTodoEntity, dueTime: Long?) {
        viewModelScope.launch {
            subTodoDao.update(subTodo.copy(dueTime = dueTime, updatedAt = System.currentTimeMillis()))
        }
    }

    fun addSubTodoReminder(subTodo: SubTodoEntity, minutesBefore: Int) {
        viewModelScope.launch {
            val current = subTodo.reminderMinutesBefore.toMutableList()
            current.add(minutesBefore.coerceIn(1, 1440))
            subTodoDao.update(subTodo.copy(reminderMinutesBefore = current, updatedAt = System.currentTimeMillis()))
        }
    }

    fun removeSubTodoReminder(subTodo: SubTodoEntity, index: Int) {
        viewModelScope.launch {
            val current = subTodo.reminderMinutesBefore.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                subTodoDao.update(subTodo.copy(reminderMinutesBefore = current, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteSubTodo(subTodo: SubTodoEntity) {
        viewModelScope.launch {
            subTodoDao.delete(subTodo)
        }
    }

    fun toggleSubTodoComplete(subTodo: SubTodoEntity) {
        viewModelScope.launch {
            subTodoDao.update(
                subTodo.copy(
                    isCompleted = !subTodo.isCompleted,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getSubTodosTotalElapsed(parentId: Long): Long {
        return subTodoDao.getTotalElapsedByParentId(parentId)
    }
}

data class ExtractedTodo(
    val title: String,
    val dueTime: Long? = null,
    val reminderMinutesBefore: List<Int> = emptyList()
)
