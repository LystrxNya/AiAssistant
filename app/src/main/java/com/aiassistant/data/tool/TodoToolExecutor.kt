package com.aiassistant.data.tool

import com.aiassistant.data.dao.SubTodoDao
import com.aiassistant.data.dao.TodoDao
import com.aiassistant.data.entity.SubTodoEntity
import com.aiassistant.data.entity.TodoEntity
import com.aiassistant.data.network.model.FunctionDef
import com.aiassistant.data.network.model.ToolDefinition
import com.aiassistant.manager.AlarmScheduler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoToolExecutor @Inject constructor(
    private val todoDao: TodoDao,
    private val subTodoDao: SubTodoDao,
    private val alarmScheduler: AlarmScheduler,
    private val gson: Gson
) {

    companion object {
        private const val TOOL_NAME = "时间管理大师"
    }

    fun getToolDefinitions(): List<ToolDefinition> = listOf(
        ToolDefinition(function = FunctionDef(
            name = "get_todos",
            description = "获取待办事项列表。可按状态筛选：all（全部）、pending（未完成）、completed（已完成）。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "filter" to mapOf(
                        "type" to "string",
                        "enum" to listOf("all", "pending", "completed"),
                        "description" to "筛选条件，默认为 pending"
                    )
                ),
                "required" to emptyList<String>()
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "get_todo_detail",
            description = "获取单个待办事项的详细信息，包含所有子待办。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "todo_id" to mapOf(
                        "type" to "integer",
                        "description" to "待办事项的 ID"
                    )
                ),
                "required" to listOf("todo_id")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "create_todo",
            description = "创建新的待办事项。此操作会修改数据，你必须先向用户确认所有信息（标题、截止时间、提醒设置）后再调用。缺少必要信息时必须追问用户。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "title" to mapOf("type" to "string", "description" to "待办标题，必填"),
                    "description" to mapOf("type" to "string", "description" to "待办描述，默认为空"),
                    "due_time" to mapOf("type" to "string", "description" to "截止时间，格式为 ISO 8601（如 2026-05-10T14:00:00），必填"),
                    "reminder_minutes_before" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "integer"),
                        "description" to "提醒时间列表，单位为分钟。例如 [15, 60] 表示截止前15分钟和60分钟各提醒一次。默认为空"
                    )
                ),
                "required" to listOf("title", "due_time")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "update_todo",
            description = "更新已有待办事项。此操作会修改数据，你必须先向用户确认修改内容后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "todo_id" to mapOf("type" to "integer", "description" to "待办事项的 ID，必填"),
                    "title" to mapOf("type" to "string", "description" to "新的标题"),
                    "description" to mapOf("type" to "string", "description" to "新的描述"),
                    "due_time" to mapOf("type" to "string", "description" to "新的截止时间，ISO 8601 格式"),
                    "reminder_minutes_before" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "integer"),
                        "description" to "新的提醒时间列表"
                    )
                ),
                "required" to listOf("todo_id")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "delete_todo",
            description = "删除待办事项及其所有子待办。此操作不可逆，你必须先向用户确认后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "todo_id" to mapOf("type" to "integer", "description" to "待办事项的 ID")
                ),
                "required" to listOf("todo_id")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "toggle_todo_complete",
            description = "切换待办事项的完成状态。此操作会修改数据，你必须先向用户确认后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "todo_id" to mapOf("type" to "integer", "description" to "待办事项的 ID")
                ),
                "required" to listOf("todo_id")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "create_sub_todo",
            description = "为待办事项添加子待办。此操作会修改数据，你必须先向用户确认后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "parent_todo_id" to mapOf("type" to "integer", "description" to "父待办的 ID"),
                    "title" to mapOf("type" to "string", "description" to "子待办标题，必填"),
                    "description" to mapOf("type" to "string", "description" to "子待办描述，默认为空"),
                    "due_time" to mapOf("type" to "string", "description" to "子待办截止时间，ISO 8601 格式，可选")
                ),
                "required" to listOf("parent_todo_id", "title")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "update_sub_todo",
            description = "更新子待办。此操作会修改数据，你必须先向用户确认后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "sub_todo_id" to mapOf("type" to "integer", "description" to "子待办的 ID"),
                    "title" to mapOf("type" to "string", "description" to "新的标题"),
                    "description" to mapOf("type" to "string", "description" to "新的描述"),
                    "due_time" to mapOf("type" to "string", "description" to "新的截止时间，ISO 8601 格式")
                ),
                "required" to listOf("sub_todo_id")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "delete_sub_todo",
            description = "删除子待办。此操作不可逆，你必须先向用户确认后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "sub_todo_id" to mapOf("type" to "integer", "description" to "子待办的 ID")
                ),
                "required" to listOf("sub_todo_id")
            )
        )),
        ToolDefinition(function = FunctionDef(
            name = "toggle_sub_todo_complete",
            description = "切换子待办的完成状态。此操作会修改数据，你必须先向用户确认后再调用。",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "sub_todo_id" to mapOf("type" to "integer", "description" to "子待办的 ID")
                ),
                "required" to listOf("sub_todo_id")
            )
        ))
    )

    suspend fun execute(functionName: String, argumentsJson: String): String {
        return try {
            val args: Map<String, Any> = gson.fromJson(
                argumentsJson,
                object : TypeToken<Map<String, Any>>() {}.type
            )
            when (functionName) {
                "get_todos" -> getTodos(args)
                "get_todo_detail" -> getTodoDetail(args)
                "create_todo" -> createTodo(args)
                "update_todo" -> updateTodo(args)
                "delete_todo" -> deleteTodo(args)
                "toggle_todo_complete" -> toggleTodoComplete(args)
                "create_sub_todo" -> createSubTodo(args)
                "update_sub_todo" -> updateSubTodo(args)
                "delete_sub_todo" -> deleteSubTodo(args)
                "toggle_sub_todo_complete" -> toggleSubTodoComplete(args)
                else -> """{"error": "Unknown function: $functionName"}"""
            }
        } catch (e: Exception) {
            """{"error": "${e.message?.replace("\"", "'")}"}"""
        }
    }

    private suspend fun getTodos(args: Map<String, Any>): String {
        val filter = (args["filter"] as? String) ?: "pending"
        val todos = when (filter) {
            "pending" -> todoDao.getPendingTodosOnce()
            "completed" -> todoDao.getCompletedTodosOnce()
            else -> todoDao.getAllTodosOnce()
        }
        val result = todos.map { todoToJson(it) }
        return gson.toJson(mapOf("todos" to result, "count" to result.size))
    }

    private suspend fun getTodoDetail(args: Map<String, Any>): String {
        val todoId = (args["todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing todo_id"}"""
        val todo = todoDao.getTodoById(todoId)
            ?: return """{"error": "Todo not found: $todoId"}"""
        val subTodos = subTodoDao.getSubTodosByParentIdOnce(todoId)
        return gson.toJson(mapOf(
            "todo" to todoToJson(todo),
            "sub_todos" to subTodos.map { subTodoToJson(it) }
        ))
    }

    private suspend fun createTodo(args: Map<String, Any>): String {
        val title = args["title"] as? String
            ?: return """{"error": "Missing title"}"""
        val description = (args["description"] as? String) ?: ""
        val dueTimeStr = args["due_time"] as? String
            ?: return """{"error": "Missing due_time"}"""
        val dueTime = parseIsoTime(dueTimeStr)
            ?: return """{"error": "Invalid due_time format. Use ISO 8601 like 2026-05-10T14:00:00"}"""
        val reminders = parseIntList(args["reminder_minutes_before"])

        val todo = TodoEntity(
            title = title,
            description = description,
            dueTime = dueTime,
            reminderMinutesBefore = reminders
        )
        val id = todoDao.insert(todo)
        if (reminders.isNotEmpty() && dueTime > System.currentTimeMillis()) {
            alarmScheduler.scheduleReminders(id, title, dueTime, reminders)
        }
        return gson.toJson(mapOf("success" to true, "todo_id" to id, "message" to "待办已创建: $title"))
    }

    private suspend fun updateTodo(args: Map<String, Any>): String {
        val todoId = (args["todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing todo_id"}"""
        val existing = todoDao.getTodoById(todoId)
            ?: return """{"error": "Todo not found: $todoId"}"""

        if (existing.reminderMinutesBefore.isNotEmpty()) {
            alarmScheduler.cancelReminders(existing.id, existing.reminderMinutesBefore.size)
        }

        val updated = existing.copy(
            title = (args["title"] as? String) ?: existing.title,
            description = (args["description"] as? String) ?: existing.description,
            dueTime = (args["due_time"] as? String)?.let { parseIsoTime(it) } ?: existing.dueTime,
            reminderMinutesBefore = parseIntList(args["reminder_minutes_before"]).ifEmpty { existing.reminderMinutesBefore },
            updatedAt = System.currentTimeMillis()
        )
        todoDao.update(updated)

        if (updated.reminderMinutesBefore.isNotEmpty() && updated.dueTime != null && updated.dueTime > System.currentTimeMillis()) {
            alarmScheduler.scheduleReminders(updated.id, updated.title, updated.dueTime, updated.reminderMinutesBefore)
        }

        return gson.toJson(mapOf("success" to true, "message" to "待办已更新: ${updated.title}"))
    }

    private suspend fun deleteTodo(args: Map<String, Any>): String {
        val todoId = (args["todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing todo_id"}"""
        val todo = todoDao.getTodoById(todoId)
            ?: return """{"error": "Todo not found: $todoId"}"""
        if (todo.reminderMinutesBefore.isNotEmpty()) {
            alarmScheduler.cancelReminders(todo.id, todo.reminderMinutesBefore.size)
        }
        todoDao.delete(todo)
        return gson.toJson(mapOf("success" to true, "message" to "待办已删除: ${todo.title}"))
    }

    private suspend fun toggleTodoComplete(args: Map<String, Any>): String {
        val todoId = (args["todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing todo_id"}"""
        val todo = todoDao.getTodoById(todoId)
            ?: return """{"error": "Todo not found: $todoId"}"""
        val newCompleted = !todo.isCompleted
        todoDao.update(todo.copy(isCompleted = newCompleted, updatedAt = System.currentTimeMillis()))
        if (newCompleted && todo.reminderMinutesBefore.isNotEmpty()) {
            alarmScheduler.cancelReminders(todo.id, todo.reminderMinutesBefore.size)
        }
        val status = if (newCompleted) "已完成" else "未完成"
        return gson.toJson(mapOf("success" to true, "message" to "待办已标记为$status: ${todo.title}"))
    }

    private suspend fun createSubTodo(args: Map<String, Any>): String {
        val parentId = (args["parent_todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing parent_todo_id"}"""
        val parent = todoDao.getTodoById(parentId)
            ?: return """{"error": "Parent todo not found: $parentId"}"""
        val title = args["title"] as? String
            ?: return """{"error": "Missing title"}"""
        val description = (args["description"] as? String) ?: ""
        val dueTime = (args["due_time"] as? String)?.let { parseIsoTime(it) }

        val subTodo = SubTodoEntity(
            parentId = parentId,
            title = title,
            description = description,
            dueTime = dueTime
        )
        val id = subTodoDao.insert(subTodo)
        return gson.toJson(mapOf("success" to true, "sub_todo_id" to id, "message" to "子待办已创建: $title"))
    }

    private suspend fun updateSubTodo(args: Map<String, Any>): String {
        val subTodoId = (args["sub_todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing sub_todo_id"}"""
        val existing = subTodoDao.getSubTodoById(subTodoId)
            ?: return """{"error": "Sub-todo not found: $subTodoId"}"""

        val updated = existing.copy(
            title = (args["title"] as? String) ?: existing.title,
            description = (args["description"] as? String) ?: existing.description,
            dueTime = (args["due_time"] as? String)?.let { parseIsoTime(it) } ?: existing.dueTime,
            updatedAt = System.currentTimeMillis()
        )
        subTodoDao.update(updated)
        return gson.toJson(mapOf("success" to true, "message" to "子待办已更新: ${updated.title}"))
    }

    private suspend fun deleteSubTodo(args: Map<String, Any>): String {
        val subTodoId = (args["sub_todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing sub_todo_id"}"""
        val subTodo = subTodoDao.getSubTodoById(subTodoId)
            ?: return """{"error": "Sub-todo not found: $subTodoId"}"""
        subTodoDao.delete(subTodo)
        return gson.toJson(mapOf("success" to true, "message" to "子待办已删除: ${subTodo.title}"))
    }

    private suspend fun toggleSubTodoComplete(args: Map<String, Any>): String {
        val subTodoId = (args["sub_todo_id"] as? Number)?.toLong()
            ?: return """{"error": "Missing sub_todo_id"}"""
        val subTodo = subTodoDao.getSubTodoById(subTodoId)
            ?: return """{"error": "Sub-todo not found: $subTodoId"}"""
        val newCompleted = !subTodo.isCompleted
        subTodoDao.update(subTodo.copy(isCompleted = newCompleted, updatedAt = System.currentTimeMillis()))
        val status = if (newCompleted) "已完成" else "未完成"
        return gson.toJson(mapOf("success" to true, "message" to "子待办已标记为$status: ${subTodo.title}"))
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    private fun todoToJson(todo: TodoEntity): Map<String, Any?> = mapOf(
        "id" to todo.id,
        "title" to todo.title,
        "description" to todo.description,
        "due_time" to todo.dueTime?.let { dateFormat.format(it) },
        "reminder_minutes_before" to todo.reminderMinutesBefore,
        "is_completed" to todo.isCompleted,
        "total_elapsed_seconds" to todo.totalElapsedSeconds
    )

    private fun subTodoToJson(subTodo: SubTodoEntity): Map<String, Any?> = mapOf(
        "id" to subTodo.id,
        "parent_id" to subTodo.parentId,
        "title" to subTodo.title,
        "description" to subTodo.description,
        "due_time" to subTodo.dueTime?.let { dateFormat.format(it) },
        "is_completed" to subTodo.isCompleted
    )

    private fun parseIsoTime(timeStr: String): Long? {
        return try {
            dateFormat.parse(timeStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIntList(value: Any?): List<Int> {
        return when (value) {
            is List<*> -> value.mapNotNull { (it as? Number)?.toInt() }
            else -> emptyList()
        }
    }
}
