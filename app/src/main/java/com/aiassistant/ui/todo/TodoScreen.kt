package com.aiassistant.ui.todo

import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiassistant.R
import com.aiassistant.data.entity.SubTodoEntity
import com.aiassistant.data.entity.TodoEntity
import com.aiassistant.manager.StopwatchState
import com.aiassistant.ui.common.EmphasizedDecelerate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.core.Spring
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.ErrorRedLight
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.SuccessGreen
import com.aiassistant.ui.theme.SurfaceLight
import com.aiassistant.ui.theme.TextPrimary
import com.aiassistant.ui.theme.TextSecondary
import com.aiassistant.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class TodoTab(val label: String) {
    ACTIVE("正常"),
    EXPIRED("已过期"),
    COMPLETED("已完成")
}

private enum class TodoPage { LIST, DETAIL, FORM }

private val CelebrationColors = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFFC107),
    Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFFE91E63),
    Color(0xFF00BCD4), Color(0xFFFF9800), Color(0xFF66BB6A),
    Color(0xFF42A5F5)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel = hiltViewModel()
) {
    val todos by viewModel.todos.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val subTodos by viewModel.subTodos.collectAsState()
    val isAiExtracting by viewModel.isAiExtracting.collectAsState()
    val stopwatchState by viewModel.stopwatchState.collectAsState()
    val currentTimingSubTodoId by viewModel.currentTimingSubTodoId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPage by rememberSaveable { mutableStateOf(TodoPage.LIST.name) }
    val page = try { TodoPage.valueOf(currentPage) } catch (_: Exception) { TodoPage.LIST }
    var stopwatchDisplay by remember { mutableStateOf("00:00") }
    var accumulatedDisplay by remember { mutableStateOf("00:00") }
    var aiInputText by remember { mutableStateOf("") }
    var showAiDialog by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val context = LocalContext.current
    var celebrateSoundId by remember { mutableIntStateOf(0) }
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    DisposableEffect(Unit) {
        celebrateSoundId = soundPool.load(context, R.raw.celebrate, 1)
        onDispose { soundPool.release() }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris = selectedImageUris + uris
        }
    }

    LaunchedEffect(Unit) {
        val restored = viewModel.restoreStopwatchState()
        if (restored > 0) {
            currentPage = TodoPage.FORM.name
        }
    }

    LaunchedEffect(Unit) {
        viewModel.stopwatchElapsed.collect { seconds ->
            stopwatchDisplay = viewModel.formatTime(seconds)
        }
    }

    LaunchedEffect(formState.totalElapsedSeconds) {
        accumulatedDisplay = viewModel.formatTime(formState.totalElapsedSeconds)
    }

    LaunchedEffect(Unit) {
        val scope = this
        viewModel.events.collect { event ->
            when (event) {
                is TodoEvent.ShowToast -> scope.launch { snackbarHostState.showSnackbar(event.message) }
                is TodoEvent.NavigateBack -> currentPage = TodoPage.LIST.name
                is TodoEvent.AiExtracted -> {}
            }
        }
    }

    BackHandler(enabled = page != TodoPage.LIST) {
        when (page) {
            TodoPage.DETAIL -> {
                viewModel.stopStopwatch()
                currentPage = TodoPage.LIST.name
            }
            TodoPage.FORM -> currentPage = TodoPage.DETAIL.name
            else -> currentPage = TodoPage.LIST.name
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Crossfade(
            targetState = page,
            label = "todoCrossfade",
            animationSpec = tween(150, easing = EmphasizedDecelerate)
        ) { activePage ->
            when (activePage) {
                TodoPage.FORM -> {
                    TodoFormPage(
                        formState = formState,
                        stopwatchState = stopwatchState,
                        stopwatchDisplay = stopwatchDisplay,
                        accumulatedDisplay = accumulatedDisplay,
                        isAiExtracting = isAiExtracting,
                        onTitleChange = viewModel::onTitleChange,
                        onDescriptionChange = viewModel::onDescriptionChange,
                        onDueTimeChange = viewModel::onDueTimeChange,
                        onAddReminder = viewModel::addReminder,
                        onRemoveReminder = viewModel::removeReminder,
                        onUpdateReminder = viewModel::updateReminder,
                        onSave = viewModel::saveTodo,
                        onBack = {
                            if (stopwatchState != StopwatchState.IDLE) {
                                viewModel.stopStopwatch()
                            }
                            currentPage = TodoPage.DETAIL.name
                        },
                        onStartStopwatch = viewModel::startStopwatch,
                        onPauseStopwatch = viewModel::pauseStopwatch,
                        onResumeStopwatch = viewModel::resumeStopwatch,
                        onStopStopwatch = viewModel::stopStopwatch,
                        onShowAiDialog = { showAiDialog = true }
                    )
                }
                TodoPage.DETAIL -> {
                    val currentTodos by viewModel.todos.collectAsState()
                    val detailTodo = currentTodos.find { it.id == formState.id }
                    if (detailTodo != null) {
                        val stableTodo = detailTodo!!
                        LaunchedEffect(stableTodo.id) {
                            viewModel.loadSubTodos(stableTodo.id)
                        }
                        LaunchedEffect(currentTodos.size) {
                            if (currentTodos.none { it.id == formState.id }) {
                                currentPage = TodoPage.LIST.name
                            }
                        }
                        TodoDetailPage(
                            todo = stableTodo,
                            subTodos = subTodos,
                            stopwatchState = stopwatchState,
                            stopwatchDisplay = stopwatchDisplay,
                            accumulatedDisplay = accumulatedDisplay,
                            soundPool = soundPool,
                            celebrateSoundId = celebrateSoundId,
                            currentTimingSubTodoId = currentTimingSubTodoId,
                            onEdit = { currentPage = TodoPage.FORM.name },
                            onBack = {
                                if (stopwatchState != StopwatchState.IDLE) {
                                    viewModel.stopStopwatch()
                                }
                                viewModel.resetForm()
                                currentPage = TodoPage.LIST.name
                            },
                            onToggleComplete = { viewModel.toggleComplete(detailTodo!!) },
                            onStartStopwatch = viewModel::startStopwatch,
                            onPauseStopwatch = viewModel::pauseStopwatch,
                            onResumeStopwatch = viewModel::resumeStopwatch,
                            onStopStopwatch = viewModel::stopStopwatch,
                            onStartSubTodoStopwatch = viewModel::startSubTodoStopwatch,
                            onAddSubTodo = { title -> viewModel.addSubTodo(detailTodo!!.id, title) },
                            onToggleSubTodoComplete = viewModel::toggleSubTodoComplete,
                            onDeleteSubTodo = viewModel::deleteSubTodo,
                            updateSubTodo = viewModel::updateSubTodo,
                            updateSubTodoDueTime = { subTodo, time -> viewModel.updateSubTodoDueTime(subTodo, time) },
                            addSubTodoReminder = { subTodo, minutes -> viewModel.addSubTodoReminder(subTodo, minutes) },
                            removeSubTodoReminder = { subTodo, index -> viewModel.removeSubTodoReminder(subTodo, index) },
                            formatTime = viewModel::formatTime
                        )
                    } else {
                        currentPage = TodoPage.LIST.name
                    }
                }
                TodoPage.LIST -> {
                    TodoListPage(
                        todos = todos,
                        soundPool = soundPool,
                        celebrateSoundId = celebrateSoundId,
                        onAddNew = {
                            viewModel.resetForm()
                            currentPage = TodoPage.FORM.name
                        },
                        onEdit = { todo ->
                            viewModel.loadTodo(todo.id)
                            currentPage = TodoPage.DETAIL.name
                        },
                        onToggleComplete = viewModel::toggleComplete,
                        onDelete = viewModel::deleteTodo,
                        onDeleteAllCompleted = viewModel::deleteAllCompleted,
                        onBatchDelete = { viewModel.deleteTodos(it) },
                        onBatchComplete = { list, completed -> viewModel.batchToggleComplete(list, completed) }
                    )
                }
            }
        }

        if (showAiDialog) {
            AiExtractDialog(
                inputText = aiInputText,
                onInputChange = { aiInputText = it },
                selectedImageUris = selectedImageUris,
                onRemoveImage = { uri -> selectedImageUris = selectedImageUris - uri },
                onPickImages = { imagePickerLauncher.launch("image/*") },
                onConfirm = {
                    when {
                        selectedImageUris.isNotEmpty() && aiInputText.isNotBlank() -> {
                            viewModel.extractTodoWithTextAndImages(aiInputText, selectedImageUris)
                        }
                        selectedImageUris.isNotEmpty() -> {
                            viewModel.extractTodoWithImages(selectedImageUris)
                        }
                        else -> {
                            viewModel.extractTodoWithAi(aiInputText)
                        }
                    }
                    aiInputText = ""
                    selectedImageUris = emptyList()
                    showAiDialog = false
                },
                onDismiss = {
                    showAiDialog = false
                    selectedImageUris = emptyList()
                },
                isExtracting = isAiExtracting
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = PrimaryBlue,
                contentColor = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListPage(
    todos: List<TodoEntity>,
    soundPool: SoundPool,
    celebrateSoundId: Int,
    onAddNew: () -> Unit,
    onEdit: (TodoEntity) -> Unit,
    onToggleComplete: (TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    onDeleteAllCompleted: () -> Unit,
    onBatchDelete: (List<TodoEntity>) -> Unit,
    onBatchComplete: (List<TodoEntity>, Boolean) -> Unit
) {
    val now = System.currentTimeMillis()
    val activeTodos = todos.filter { !it.isCompleted && (it.dueTime == null || it.dueTime >= now) }
    val expiredTodos = todos.filter { !it.isCompleted && it.dueTime != null && it.dueTime < now }
    val completedTodos = todos.filter { it.isCompleted }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showConfetti by remember { mutableStateOf(false) }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val tabs = listOf(TodoTab.ACTIVE, TodoTab.EXPIRED, TodoTab.COMPLETED)
    val tabCounts = listOf(activeTodos.size, expiredTodos.size, completedTodos.size)
    val currentList = when (selectedTab) {
        0 -> activeTodos
        1 -> expiredTodos
        2 -> completedTodos
        else -> activeTodos
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "取消选择", tint = TextPrimary)
                    }
                    Text(
                        text = "已选 ${selectedIds.size} 项",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val selected = currentList.filter { it.id in selectedIds }
                        onBatchComplete(selected, selectedTab != 2)
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = if (selectedTab != 2) "标记完成" else "标记未完成",
                            tint = SuccessGreen
                        )
                    }
                    IconButton(onClick = {
                        val selected = currentList.filter { it.id in selectedIds }
                        onBatchDelete(selected)
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "批量删除", tint = ErrorRedLight)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "待办",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (completedTodos.isNotEmpty()) {
                        TextButton(onClick = onDeleteAllCompleted) {
                            Text("清除已完成", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceLight,
                contentColor = PrimaryBlue,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            selectedIds = emptySet()
                        },
                        text = {
                            Text(
                                text = "${tab.label} (${tabCounts[index]})",
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = TextSecondary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (selectedTab) {
                                0 -> "暂无进行中的待办"
                                1 -> "暂无已过期的待办"
                                else -> "暂无已完成的待办"
                            },
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentList, key = { it.id }) { todo ->
                        TodoItem(
                            todo = todo,
                            isSelected = todo.id in selectedIds,
                            isSelectionMode = isSelectionMode,
                            onEdit = { onEdit(todo) },
                            onToggleComplete = { onToggleComplete(todo) },
                            onDelete = { onDelete(todo) },
                            onLongPress = {
                                selectedIds = if (todo.id in selectedIds) {
                                    selectedIds - todo.id
                                } else {
                                    selectedIds + todo.id
                                }
                            },
                            onToggleSelect = {
                                selectedIds = if (todo.id in selectedIds) {
                                    selectedIds - todo.id
                                } else {
                                    selectedIds + todo.id
                                }
                            },
                            onCelebrate = {
                                showConfetti = true
                                confettiTrigger++
                                soundPool.play(celebrateSoundId, 0.4f, 0.4f, 1, 0, 0.9f)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        if (showConfetti && confettiTrigger > 0) {
            ConfettiEffect(trigger = confettiTrigger) {
                showConfetti = false
            }
        }

        FloatingActionButton(
            onClick = onAddNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = PrimaryBlue,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "新增待办")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TodoItem(
    todo: TodoEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onEdit: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onCelebrate: () -> Unit
) {

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!todo.isCompleted) {
                        onCelebrate()
                    }
                    onToggleComplete()
                    true
                }
                else -> false
            }
        }
    )

    Box {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val color = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> SuccessGreen
                    SwipeToDismissBoxValue.EndToStart -> ErrorRedLight
                    else -> Color.Transparent
                }
                val icon = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.CheckCircle
                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                    else -> Icons.Default.CheckCircle
                }
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) onToggleSelect() else onEdit()
                        },
                        onLongClick = onLongPress
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSelected -> PrimaryBlue.copy(alpha = 0.1f)
                        todo.isCompleted -> SurfaceLight.copy(alpha = 0.7f)
                        else -> SurfaceLight
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isSelected) PrimaryBlue else TextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 2.dp)
                        )
                    } else {
                        var isPressed by remember { mutableStateOf(false) }
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.8f else 1f,
                            animationSpec = tween(100),
                            label = "buttonScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .scale(buttonScale)
                                .clip(CircleShape)
                                .background(
                                    if (todo.isCompleted) SuccessGreen.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (!todo.isCompleted) {
                                            isPressed = true
                                            onCelebrate()
                                        }
                                        onToggleComplete()
                                    },
                                    onLongClick = {}
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = if (todo.isCompleted) "标记未完成" else "标记完成",
                                tint = if (todo.isCompleted) SuccessGreen else TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = todo.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (todo.isCompleted) TextSecondary else TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            todo.dueTime?.let { due ->
                                val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (due < System.currentTimeMillis() && !todo.isCompleted)
                                            ErrorRedLight else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = sdf.format(Date(due)),
                                        fontSize = 12.sp,
                                        color = if (due < System.currentTimeMillis() && !todo.isCompleted)
                                            ErrorRedLight else TextSecondary
                                    )
                                }
                            }

                            if (todo.reminderMinutesBefore.isNotEmpty()) {
                                Text(
                                    text = "提醒${todo.reminderMinutesBefore.size}次",
                                    fontSize = 11.sp,
                                    color = PrimaryBlue.copy(alpha = 0.7f)
                                )
                            }

                            if (todo.totalElapsedSeconds > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    val hours = todo.totalElapsedSeconds / 3600
                                    val mins = (todo.totalElapsedSeconds % 3600) / 60
                                    Text(
                                        text = if (hours > 0) "${hours}h${mins}m" else "${mins}m",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfettiEffect(trigger: Int, onComplete: () -> Unit) {
    val confettiProgress = remember(trigger) { Animatable(0f) }
    val textScale = remember(trigger) { Animatable(0f) }
    val textAlpha = remember(trigger) { Animatable(0f) }
    val flashAlpha = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) {
        List(48) { i ->
            val angle = (i * 7.5f + Random.nextFloat() * 6f) * (Math.PI / 180f)
            val speed = 0.3f + Random.nextFloat() * 0.7f
            val color = Color(
                listOf(
                    0xFF4CAF50, 0xFF2196F3, 0xFFFFC107,
                    0xFFFF5722, 0xFF9C27B0, 0xFFE91E63,
                    0xFF00BCD4, 0xFFFF9800, 0xFF66BB6A,
                    0xFF42A5F5
                ).random()
            )
            val shape = Random.nextInt(4)
            ConfettiParticle(
                dx = cos(angle).toFloat() * speed,
                dy = sin(angle).toFloat() * speed - 0.4f,
                color = color,
                shape = shape,
                delayFactor = Random.nextFloat() * 0.25f,
                size = 3f + Random.nextFloat() * 5f
            )
        }
    }

    LaunchedEffect(trigger) {
        launch {
            flashAlpha.animateTo(0.6f, animationSpec = tween(60))
            flashAlpha.animateTo(0f, animationSpec = tween(250))
        }
        launch {
            textScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 180f)
            )
            delay(200)
            textScale.animateTo(
                targetValue = 1.1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
            )
            textScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(150)
            )
        }
        launch {
            textAlpha.animateTo(1f, animationSpec = tween(100))
            delay(700)
            textAlpha.animateTo(0f, animationSpec = tween(350))
        }
        launch {
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200, easing = LinearEasing)
            )
        }
        delay(1200)
        onComplete()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }

        if (textAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = textScale.value
                        scaleY = textScale.value
                        alpha = textAlpha.value
                    }
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .background(
                        Color.White,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🎉",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "完成！",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = SuccessGreen,
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = confettiProgress.value
            val centerX = size.width / 2
            val centerY = size.height / 2
            particles.forEach { particle ->
                val adjustedP = (p - particle.delayFactor).coerceIn(0f, 1f)
                if (adjustedP <= 0f) return@forEach
                val alpha = (1f - adjustedP * adjustedP).coerceIn(0f, 1f)
                val radius = particle.size * (1f + adjustedP * 0.5f)
                val x = centerX + particle.dx * size.width * adjustedP
                val y = centerY + particle.dy * size.height * adjustedP + 150f * adjustedP * adjustedP

                when (particle.shape) {
                    0 -> drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )
                    1 -> drawRect(
                        color = particle.color.copy(alpha = alpha),
                        topLeft = Offset(x - radius, y - radius * 0.6f),
                        size = Size(radius * 2, radius * 1.2f)
                    )
                    2 -> drawCircle(
                        color = particle.color.copy(alpha = alpha * 0.5f),
                        radius = radius * 2f,
                        center = Offset(x, y)
                    )
                    else -> {
                        val halfSize = radius * 1.2f
                        drawPath(
                            path = Path().apply {
                                moveTo(x, y - halfSize)
                                lineTo(x - halfSize * 0.58f, y + halfSize * 0.38f)
                                lineTo(x + halfSize * 0.58f, y + halfSize * 0.38f)
                                close()
                            },
                            color = particle.color.copy(alpha = alpha)
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val dx: Float,
    val dy: Float,
    val color: Color,
    val shape: Int,
    val delayFactor: Float,
    val size: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoDetailPage(
    todo: TodoEntity,
    subTodos: List<SubTodoEntity>,
    stopwatchState: StopwatchState,
    stopwatchDisplay: String,
    accumulatedDisplay: String,
    soundPool: SoundPool,
    celebrateSoundId: Int,
    currentTimingSubTodoId: Long?,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onToggleComplete: () -> Unit,
    onStartStopwatch: () -> Unit,
    onPauseStopwatch: () -> Unit,
    onResumeStopwatch: () -> Unit,
    onStopStopwatch: () -> Unit,
    onStartSubTodoStopwatch: (Long) -> Unit,
    onAddSubTodo: (String) -> Unit,
    onToggleSubTodoComplete: (SubTodoEntity) -> Unit,
    onDeleteSubTodo: (SubTodoEntity) -> Unit,
    updateSubTodo: (SubTodoEntity) -> Unit,
    updateSubTodoDueTime: (SubTodoEntity, Long?) -> Unit,
    addSubTodoReminder: (SubTodoEntity, Int) -> Unit,
    removeSubTodoReminder: (SubTodoEntity, Int) -> Unit,
    formatTime: (Long) -> String
) {
    var showConfetti by remember { mutableStateOf(false) }
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var newSubTodoTitle by remember { mutableStateOf("") }
    var showAddSubTodo by remember { mutableStateOf(false) }
    var subTodoCelebrationId by remember { mutableStateOf<Long?>(null) }
    var subTodoConfettiTrigger by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text(
                text = "待办详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = todo.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (todo.isCompleted) TextSecondary else TextPrimary
                        )
                        if (todo.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = todo.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DDL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (todo.dueTime != null) {
                            val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
                            val isExpired = todo.dueTime < System.currentTimeMillis() && !todo.isCompleted
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isExpired) ErrorRedLight else PrimaryBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sdf.format(Date(todo.dueTime)),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isExpired) ErrorRedLight else TextPrimary
                                )
                                if (isExpired) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("已过期", fontSize = 12.sp, color = ErrorRedLight, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Text("未设置截止时间", fontSize = 14.sp, color = TextSecondary)
                        }

                        if (todo.reminderMinutesBefore.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "提醒设置",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            todo.reminderMinutesBefore.forEach { minutes ->
                                Text(
                                    text = "• 提前${formatMinutes(minutes)}提醒",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⏱ 待办计时器",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val isParentTiming = currentTimingSubTodoId == null && stopwatchState != StopwatchState.IDLE
                        if (isParentTiming) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "正在计时中...", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Medium)
                        }

                        val totalSubElapsed = subTodos.sumOf { it.totalElapsedSeconds }
                        val totalParentAccumulated = todo.totalElapsedSeconds + totalSubElapsed
                        if (totalParentAccumulated > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PrimaryBlue.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "总累计用时", fontSize = 12.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = viewModel_formatTime(totalParentAccumulated),
                                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentTimingSubTodoId == null) {
                                Text(
                                    text = stopwatchDisplay,
                                    fontSize = 36.sp, fontWeight = FontWeight.Bold,
                                    color = when (stopwatchState) {
                                        StopwatchState.RUNNING -> PrimaryBlue
                                        StopwatchState.PAUSED -> WarningOrange
                                        StopwatchState.IDLE -> TextPrimary
                                    }
                                )
                                if (stopwatchState == StopwatchState.RUNNING) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.size(8.dp).background(SuccessGreen, CircleShape))
                                }
                            } else {
                                Text(
                                    text = "计时器被子待办占用",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            if (currentTimingSubTodoId == null) {
                                when (stopwatchState) {
                                    StopwatchState.IDLE -> {
                                        Button(onClick = onStartStopwatch, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("开始计时")
                                        }
                                    }
                                    StopwatchState.RUNNING -> {
                                        Button(onClick = onPauseStopwatch, colors = ButtonDefaults.buttonColors(containerColor = WarningOrange), shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("暂停")
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        OutlinedButton(onClick = onStopStopwatch, shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("停止")
                                        }
                                    }
                                    StopwatchState.PAUSED -> {
                                        Button(onClick = onResumeStopwatch, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("继续")
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        OutlinedButton(onClick = onStopStopwatch, shape = RoundedCornerShape(10.dp)) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("停止")
                                        }
                                    }
                                }
                            }
                        }

                        if (todo.timingRecords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "计时记录 (${todo.timingRecords.size}次)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                            todo.timingRecords.takeLast(5).forEach { record ->
                                val startSdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                val duration = if (record.endTime != null) (record.endTime - record.startTime) / 1000 else 0L
                                val hours = duration / 3600; val mins = (duration % 3600) / 60; val secs = duration % 60
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = startSdf.format(Date(record.startTime)), fontSize = 12.sp, color = TextSecondary)
                                    Text(text = "${if (hours > 0) "${hours}h " else ""}${mins}m ${secs}s", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PrimaryBlue)
                                }
                            }
                            if (todo.timingRecords.size > 5) {
                                Text(text = "...还有${todo.timingRecords.size - 5}条记录", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📋 子级待办 (${subTodos.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            IconButton(onClick = { showAddSubTodo = !showAddSubTodo }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "添加子待办", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            }
                        }

                        if (showAddSubTodo) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newSubTodoTitle, onValueChange = { newSubTodoTitle = it },
                                    modifier = Modifier.weight(1f), placeholder = { Text("输入子待办标题", fontSize = 14.sp) },
                                    singleLine = true, shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { if (newSubTodoTitle.isNotBlank()) { onAddSubTodo(newSubTodoTitle); newSubTodoTitle = "" } }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Text("添加") }
                            }
                        }

                        subTodos.forEach { subTodo ->
                            Spacer(modifier = Modifier.height(8.dp))
                            SubTodoItem(
                                subTodo = subTodo,
                                isBeingTimed = currentTimingSubTodoId == subTodo.id,
                                stopwatchState = if (currentTimingSubTodoId == subTodo.id) stopwatchState else StopwatchState.IDLE,
                                stopwatchDisplay = if (currentTimingSubTodoId == subTodo.id) stopwatchDisplay else "00:00:00",
                                onToggleComplete = {
                                    if (!subTodo.isCompleted) {
                                        subTodoCelebrationId = subTodo.id
                                        subTodoConfettiTrigger++
                                        soundPool.play(celebrateSoundId, 0.4f, 0.4f, 1, 0, 0.9f)
                                    }
                                    onToggleSubTodoComplete(subTodo)
                                },
                                onDelete = { onDeleteSubTodo(subTodo) },
                                onStartTimer = { onStartSubTodoStopwatch(subTodo.id) },
                                onPauseTimer = onPauseStopwatch,
                                onResumeTimer = onResumeStopwatch,
                                onStopTimer = onStopStopwatch,
                                onUpdateDueTime = { time -> updateSubTodoDueTime(subTodo, time) },
                                onAddReminder = { minutes -> addSubTodoReminder(subTodo, minutes) },
                                onRemoveReminder = { index -> removeSubTodoReminder(subTodo, index) },
                                formatTime = formatTime
                            )
                        }

                        if (subTodos.isEmpty() && !showAddSubTodo) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "暂无子级待办，点击 + 添加", fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            item {
                Box {
                    var isPressed by remember { mutableStateOf(false) }
                    val buttonScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.9f else 1f,
                        animationSpec = tween(150),
                        label = "completeScale"
                    )
                    Button(
                        onClick = {
                            if (!todo.isCompleted) {
                                isPressed = true
                                showConfetti = true
                                confettiTrigger++
                                soundPool.play(celebrateSoundId, 0.4f, 0.4f, 1, 0, 0.9f)
                            }
                            onToggleComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(buttonScale),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (todo.isCompleted) TextSecondary else SuccessGreen
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            if (todo.isCompleted) Icons.Default.Close else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (todo.isCompleted) "取消完成" else "🎉 完成此待办！",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
        }

        if (showConfetti && confettiTrigger > 0) {
            ConfettiEffect(trigger = confettiTrigger) { showConfetti = false }
        }
        if (subTodoCelebrationId != null && subTodoConfettiTrigger > 0) {
            ConfettiEffect(trigger = subTodoConfettiTrigger) {
                subTodoCelebrationId = null
            }
        }
    }
}

private fun viewModel_formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoFormPage(
    formState: TodoFormState,
    stopwatchState: StopwatchState,
    stopwatchDisplay: String,
    accumulatedDisplay: String,
    isAiExtracting: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueTimeChange: (Long?) -> Unit,
    onAddReminder: (Int) -> Unit,
    onRemoveReminder: (Int) -> Unit,
    onUpdateReminder: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onStartStopwatch: () -> Unit,
    onPauseStopwatch: () -> Unit,
    onResumeStopwatch: () -> Unit,
    onStopStopwatch: () -> Unit,
    onShowAiDialog: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(formState.dueTime) }
    var showReminderPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text(
                text = if (formState.isEditing) "编辑待办" else "新建待办",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("保存", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = formState.title,
                    onValueChange = onTitleChange,
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue
                    )
                )
            }

            item {
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue
                    )
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DDL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formState.dueTime?.let {
                                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(it))
                                    } ?: "选择时间",
                                    fontSize = 13.sp
                                )
                            }

                            if (formState.dueTime != null) {
                                TextButton(onClick = { onDueTimeChange(null) }) {
                                    Text("清除", fontSize = 13.sp, color = TextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "提前提醒",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        formState.reminderMinutesBefore.forEachIndexed { index, minutes ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "提前",
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                val presetMinutes = listOf(5, 10, 15, 30, 60, 120, 360, 720, 1440)
                                var expanded by remember { mutableStateOf(false) }

                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = formatMinutes(minutes),
                                            fontSize = 14.sp,
                                            color = PrimaryBlue
                                        )
                                    }

                                    if (expanded) {
                                        AlertDialog(
                                            onDismissRequest = { expanded = false },
                                            title = { Text("选择提前时间") },
                                            text = {
                                                Column {
                                                    presetMinutes.forEach { m ->
                                                        TextButton(
                                                            onClick = {
                                                                onUpdateReminder(index, m)
                                                                expanded = false
                                                            },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = formatMinutes(m),
                                                                color = if (m == minutes) PrimaryBlue else TextPrimary
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { expanded = false }) {
                                                    Text("取消")
                                                }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "提醒",
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(
                                    onClick = { onRemoveReminder(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "移除",
                                        tint = ErrorRedLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showReminderPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("添加提醒", fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⏱ 计时器",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stopwatchDisplay,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (stopwatchState) {
                                StopwatchState.RUNNING -> PrimaryBlue
                                StopwatchState.PAUSED -> WarningOrange
                                StopwatchState.IDLE -> TextPrimary
                            }
                        )

                        if (formState.totalElapsedSeconds > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "累计: $accumulatedDisplay",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (stopwatchState) {
                                StopwatchState.IDLE -> {
                                    Button(
                                        onClick = onStartStopwatch,
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("开始")
                                    }
                                }
                                StopwatchState.RUNNING -> {
                                    Button(
                                        onClick = onPauseStopwatch,
                                        colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("暂停")
                                    }
                                    OutlinedButton(
                                        onClick = onStopStopwatch,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("停止")
                                    }
                                }
                                StopwatchState.PAUSED -> {
                                    Button(
                                        onClick = onResumeStopwatch,
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("继续")
                                    }
                                    OutlinedButton(
                                        onClick = onStopStopwatch,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("停止")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onShowAiDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    enabled = !isAiExtracting
                ) {
                    if (isAiExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI提取中...", fontSize = 15.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🤖 AI自动构建待办", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    AnimatedVisibility(
        visible = showDatePicker,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AnimatedVisibility(
        visible = showTimePicker,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        val existingTime = java.util.Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis ?: System.currentTimeMillis()
        }
        val timePickerState = rememberTimePickerState(
            initialHour = existingTime.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = existingTime.get(java.util.Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = selectedDateMillis ?: System.currentTimeMillis()
                    val calendar = java.util.Calendar.getInstance().apply {
                        timeInMillis = dateMillis
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onDueTimeChange(calendar.timeInMillis)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showReminderPicker) {
        val presetMinutes = listOf(5, 10, 15, 30, 60, 120, 360, 720, 1440)
        var customMinutesText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReminderPicker = false },
            title = { Text("选择提前提醒时间") },
            text = {
                Column {
                    presetMinutes.forEach { minutes ->
                        TextButton(
                            onClick = {
                                onAddReminder(minutes)
                                showReminderPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatMinutes(minutes),
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "自定义时间（分钟）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { customMinutesText = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入分钟数", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue
                            )
                        )
                        Button(
                            onClick = {
                                val minutes = customMinutesText.toIntOrNull()
                                if (minutes != null && minutes > 0) {
                                    onAddReminder(minutes)
                                    showReminderPicker = false
                                }
                            },
                            enabled = customMinutesText.toIntOrNull()?.let { it > 0 } == true,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("确定", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReminderPicker = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatMinutes(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}分钟"
        minutes < 1440 -> "${minutes / 60}小时" + if (minutes % 60 > 0) "${minutes % 60}分钟" else ""
        else -> "${minutes / 1440}天"
    }
}

@Composable
private fun ReminderPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val presetMinutes = listOf(5, 10, 15, 30, 60, 120, 360, 720, 1440)
    var customMinutesText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提前提醒时间") },
        text = {
            Column {
                presetMinutes.forEach { minutes ->
                    TextButton(
                        onClick = { onSelect(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatMinutes(minutes),
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "自定义时间（分钟）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { customMinutesText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入分钟数", fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            focusedLabelColor = PrimaryBlue
                        )
                    )
                    Button(
                        onClick = {
                            val minutes = customMinutesText.toIntOrNull()
                            if (minutes != null && minutes > 0) {
                                onSelect(minutes)
                            }
                        },
                        enabled = customMinutesText.toIntOrNull()?.let { it > 0 } == true,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("确定", fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AiExtractDialog(
    inputText: String,
    onInputChange: (String) -> Unit,
    selectedImageUris: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    onPickImages: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isExtracting: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isExtracting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI自动构建待办", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                Text(
                    text = "粘贴文本、选择图片，或两者混合，AI将自动提取待办信息（包括提醒次数和提前时间）",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("在此粘贴文本...", fontSize = 14.sp, color = TextSecondary) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue
                    ),
                    enabled = !isExtracting
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPickImages,
                    enabled = !isExtracting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("选择图片", fontSize = 14.sp)
                }

                if (selectedImageUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedImageUris.take(4).forEach { uri ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceLight)
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { onRemoveImage(uri) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "移除",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                        if (selectedImageUris.size > 4) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${selectedImageUris.size - 4}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                    Text(
                        text = "已选 ${selectedImageUris.size} 张图片",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            val canConfirm = (inputText.isNotBlank() || selectedImageUris.isNotEmpty()) && !isExtracting
            Button(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (isExtracting) "提取中..." else "开始提取")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExtracting) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubTodoItem(
    subTodo: SubTodoEntity,
    isBeingTimed: Boolean,
    stopwatchState: StopwatchState,
    stopwatchDisplay: String,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onUpdateDueTime: (Long?) -> Unit,
    onAddReminder: (Int) -> Unit,
    onRemoveReminder: (Int) -> Unit,
    formatTime: (Long) -> String
) {
    var isPressed by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh),
        label = "subTodoScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (subTodo.isCompleted) SuccessGreen.copy(alpha = 0.06f)
                else if (isBeingTimed) PrimaryBlue.copy(alpha = 0.06f)
                else BackgroundLight
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .scale(scale)
                        .background(
                            if (subTodo.isCompleted) SuccessGreen else TextSecondary.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                if (!subTodo.isCompleted) {
                                    isPressed = true
                                }
                                onToggleComplete()
                            },
                            onLongClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (subTodo.isCompleted) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = subTodo.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (subTodo.isCompleted) TextSecondary else TextPrimary,
                        textDecoration = if (subTodo.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (subTodo.description.isNotBlank()) {
                        Text(text = subTodo.description, fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (subTodo.dueTime != null) {
                        val dueText = if (subTodo.dueTime > System.currentTimeMillis()) {
                            val diff = subTodo.dueTime - System.currentTimeMillis()
                            val days = diff / (24 * 60 * 60 * 1000)
                            val hours = diff / (60 * 60 * 1000)
                            if (days > 0) "DDL: ${days}天后" else if (hours > 0) "DDL: ${hours}小时后" else "DDL: 即将到期"
                        } else "DDL: 已过期"
                        Text(text = dueText, fontSize = 12.sp, color = if (subTodo.dueTime < System.currentTimeMillis()) ErrorRedLight else WarningOrange, fontWeight = FontWeight.Medium)
                    }
                }

                if (isBeingTimed && stopwatchState != StopwatchState.IDLE) {
                    Box(modifier = Modifier.size(8.dp).background(SuccessGreen, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )

                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrorRedLight.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (subTodo.totalElapsedSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryBlue.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "本项累计用时", fontSize = 11.sp, color = TextSecondary)
                                Text(text = formatTime(subTodo.totalElapsedSeconds), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = subTodo.dueTime?.let {
                                    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(it))
                                } ?: "设置DDL",
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { showReminderPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (subTodo.reminderMinutesBefore.isEmpty()) "设置提醒"
                                       else "${subTodo.reminderMinutesBefore.size}个提醒",
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (subTodo.reminderMinutesBefore.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        subTodo.reminderMinutesBefore.forEachIndexed { idx, mins ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "• 提前${formatMinutes(mins)}提醒", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onRemoveReminder(idx) }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "删除提醒", tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBeingTimed) {
                            Text(
                                text = stopwatchDisplay,
                                fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                color = when (stopwatchState) {
                                    StopwatchState.RUNNING -> PrimaryBlue
                                    StopwatchState.PAUSED -> WarningOrange
                                    StopwatchState.IDLE -> TextPrimary
                                }
                            )
                            if (stopwatchState == StopwatchState.RUNNING) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(6.dp).background(SuccessGreen, CircleShape))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (isBeingTimed) {
                            when (stopwatchState) {
                                StopwatchState.RUNNING -> {
                                    Button(onClick = onPauseTimer, colors = ButtonDefaults.buttonColors(containerColor = WarningOrange), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("暂停", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(onClick = onStopTimer, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("停止", fontSize = 12.sp)
                                    }
                                }
                                StopwatchState.PAUSED -> {
                                    Button(onClick = onResumeTimer, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("继续", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(onClick = onStopTimer, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("停止", fontSize = 12.sp)
                                    }
                                }
                                StopwatchState.IDLE -> {}
                            }
                        } else {
                            Button(onClick = onStartTimer, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("开始计时", fontSize = 12.sp)
                            }
                        }
                    }

                    if (subTodo.timingRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "计时记录 (${subTodo.timingRecords.size}次)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                        subTodo.timingRecords.takeLast(3).forEach { record ->
                            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            val duration = if (record.endTime != null) (record.endTime - record.startTime) / 1000 else 0L
                            val h = duration / 3600; val m = (duration % 3600) / 60; val s = duration % 60
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = sdf.format(Date(record.startTime)), fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
                                Text(text = "${if (h > 0) "${h}h " else ""}${m}m ${s}s", fontSize = 11.sp, color = PrimaryBlue.copy(alpha = 0.8f))
                            }
                        }
                        if (subTodo.timingRecords.size > 3) {
                            Text(text = "...还有${subTodo.timingRecords.size - 3}条记录", fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        SubTodoDateTimePicker(
            initialTime = subTodo.dueTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { selectedTime ->
                onUpdateDueTime(selectedTime)
                showTimePicker = false
            }
        )
    }

    if (showReminderPicker) {
        ReminderPickerDialog(
            onDismiss = { showReminderPicker = false },
            onSelect = { minutes ->
                onAddReminder(minutes)
                showReminderPicker = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除子待办", fontSize = 16.sp) },
            text = { Text("确定要删除「${subTodo.title}」吗？", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("删除", color = ErrorRedLight, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", fontSize = 14.sp)
                }
            }
        )
    }
}

@Composable
private fun SubTodoDateTimePicker(
    initialTime: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    initialTime?.let { calendar.timeInMillis = it }

    LaunchedEffect(Unit) {
        val datePicker = android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                val timePicker = android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onConfirm(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                )
                timePicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }
}

