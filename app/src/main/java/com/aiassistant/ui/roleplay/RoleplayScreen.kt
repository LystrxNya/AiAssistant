package com.aiassistant.ui.roleplay

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.data.entity.MemoryEntity
import com.aiassistant.data.entity.MessageEntity
import com.aiassistant.data.entity.MessageRole
import com.aiassistant.data.entity.PersonaEntity
import com.aiassistant.ui.chat.components.MessageBubble
import com.aiassistant.ui.chat.components.ThinkingBubble
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.ErrorRed
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.SurfaceDark
import com.aiassistant.ui.theme.SurfaceLight
import com.aiassistant.ui.theme.TextPrimary
import com.aiassistant.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayScreen(
    viewModel: RoleplayViewModel = hiltViewModel()
) {
    val personas by viewModel.personas.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val thinkingContent by viewModel.thinkingContent.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val thinkingMode by viewModel.thinkingMode.collectAsState()
    val showThinkingContent by viewModel.showThinkingContent.collectAsState()
    val currentPersonaId by viewModel.currentPersonaId.collectAsState()
    val setupMessage by viewModel.setupMessage.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showThinkingModeSelector by remember { mutableStateOf(false) }
    var showCreatePersonaDialog by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }
    var showMemoryDialog by remember { mutableStateOf(false) }

    val currentPersona = personas.find { it.id == currentPersonaId }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = SurfaceLight
            ) {
                RoleplayDrawerContent(
                    personas = personas,
                    conversations = conversations,
                    currentPersonaId = currentPersonaId,
                    onSelectPersona = { viewModel.selectPersona(it) },
                    onSelectConversation = {
                        viewModel.selectConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onCreatePersona = { showCreatePersonaDialog = true },
                    onEditPersona = { editingPersona = it },
                    onDeletePersona = { viewModel.deletePersona(it) },
                    onDeleteConversation = { viewModel.deleteConversation(it.id) },
                    onNewConversation = {
                        viewModel.createNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onManageMemory = {
                        viewModel.loadMemories()
                        showMemoryDialog = true
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                RoleplayTopBar(
                    persona = currentPersona,
                    thinkingMode = thinkingMode,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onThinkingModeClick = { showThinkingModeSelector = true }
                )

                setupMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissSetupMessage() }) {
                                Text("知道了", color = ErrorRed)
                            }
                        }
                    }
                }

                Crossfade(
                    modifier = Modifier.weight(1f),
                    targetState = messages.isEmpty() && streamingContent.isEmpty(),
                    label = "content"
                ) { isEmpty ->
                    if (isEmpty) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🎭", fontSize = 64.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "开始角色扮演",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "选择或创建一个角色，开始沉浸式对话",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(messages.size, streamingContent.length, thinkingContent.length, conversationState) {
                            val hasActiveMessage = conversationState == ConversationState.STREAMING ||
                                    conversationState == ConversationState.THINKING
                            if (messages.isNotEmpty() || hasActiveMessage) {
                                listState.animateScrollToItem(
                                    (messages.size + if (hasActiveMessage) 1 else 0) - 1
                                )
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val isThinkingPhase = conversationState == ConversationState.THINKING
                            val activeHasContent = streamingContent.isNotEmpty()

                            items(messages) { message ->
                                if (showThinkingContent &&
                                    message.role == MessageRole.ASSISTANT &&
                                    message.thinkingContent.isNotEmpty()
                                ) {
                                    ThinkingBubble(thinkingContent = message.thinkingContent)
                                }
                                MessageBubble(
                                    message = message,
                                    onEdit = { viewModel.editMessage(message) },
                                    onDelete = {
                                        viewModel.deleteConversation(message.conversationId)
                                    }
                                )
                            }

                            if (conversationState == ConversationState.STREAMING ||
                                conversationState == ConversationState.THINKING
                            ) {
                                item {
                                    // Thinking phase: only show ThinkingBubble, no MessageBubble
                                    if (isThinkingPhase && !activeHasContent) {
                                        if (showThinkingContent) {
                                            ThinkingBubble(
                                                thinkingContent = thinkingContent,
                                                isLiveStreaming = true
                                            )
                                        }
                                    } else {
                                        // Streaming phase: ThinkingBubble above message
                                        if (showThinkingContent && thinkingContent.isNotEmpty()) {
                                            ThinkingBubble(
                                                thinkingContent = thinkingContent,
                                                isLiveStreaming = false
                                            )
                                        }
                                        val streamingMessage = MessageEntity(
                                            conversationId = 0,
                                            role = MessageRole.ASSISTANT,
                                            content = "",
                                            thinkingContent = thinkingContent,
                                            state = ConversationState.STREAMING
                                        )
                                        MessageBubble(
                                            message = streamingMessage,
                                            liveContent = streamingContent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                RoleplayInputBar(
                    text = inputText,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() },
                    enabled = conversationState == ConversationState.IDLE ||
                            conversationState == ConversationState.SUCCESS ||
                            conversationState == ConversationState.ERROR
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showThinkingModeSelector) {
        ThinkingModeDialog(
            currentMode = thinkingMode,
            onSelect = {
                viewModel.setThinkingMode(it)
                showThinkingModeSelector = false
            },
            onDismiss = { showThinkingModeSelector = false }
        )
    }

    if (showCreatePersonaDialog) {
        CreateRoleplayPersonaDialog(
            availableModels = viewModel.getAvailableModels(),
            onConfirm = { name, prompt, desc, icon, modelId ->
                viewModel.createPersona(name, prompt, desc, icon, modelId)
                showCreatePersonaDialog = false
            },
            onDismiss = { showCreatePersonaDialog = false }
        )
    }

    editingPersona?.let { persona ->
        EditRoleplayPersonaDialog(
            persona = persona,
            availableModels = viewModel.getAvailableModels(),
            onConfirm = { updatedPersona ->
                viewModel.updatePersona(updatedPersona)
                editingPersona = null
            },
            onDismiss = { editingPersona = null }
        )
    }

    if (showMemoryDialog) {
        val memories by viewModel.memories.collectAsState()
        MemoryManagementDialog(
            memories = memories,
            onDelete = { viewModel.deleteMemory(it) },
            onDismiss = { showMemoryDialog = false }
        )
    }
}

@Composable
private fun RoleplayTopBar(
    persona: PersonaEntity?,
    thinkingMode: RoleplayThinkingMode,
    onMenuClick: () -> Unit,
    onThinkingModeClick: () -> Unit
) {
    Surface(
        color = SurfaceLight,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "菜单", tint = TextPrimary)
            }

            Text(
                text = persona?.icon ?: "🎭",
                fontSize = 24.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persona?.name ?: "角色扮演",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (persona != null) {
                    Text(
                        text = persona.description.ifBlank { "沉浸式对话" },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            IconButton(onClick = onThinkingModeClick) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = "思考模式",
                    tint = when (thinkingMode) {
                        RoleplayThinkingMode.INNER_OS -> PrimaryBlue
                        RoleplayThinkingMode.ANALYSIS -> TextSecondary
                        RoleplayThinkingMode.DEFAULT -> TextSecondary
                    }
                )
            }
        }
    }
}

@Composable
private fun RoleplayInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = SurfaceLight,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp),
                placeholder = { Text("输入角色对话...", color = TextSecondary, fontSize = 14.sp) },
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = SurfaceDark,
                    cursorColor = PrimaryBlue
                ),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && text.isNotBlank()) PrimaryBlue
                        else TextSecondary.copy(alpha = 0.3f)
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RoleplayDrawerContent(
    personas: List<PersonaEntity>,
    conversations: List<com.aiassistant.data.entity.ConversationEntity>,
    currentPersonaId: Long,
    onSelectPersona: (Long) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onCreatePersona: () -> Unit,
    onEditPersona: (PersonaEntity) -> Unit,
    onDeletePersona: (PersonaEntity) -> Unit,
    onDeleteConversation: (com.aiassistant.data.entity.ConversationEntity) -> Unit,
    onNewConversation: () -> Unit,
    onManageMemory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "角色列表",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Personas
        personas.forEach { persona ->
            val isSelected = persona.id == currentPersonaId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onSelectPersona(persona.id) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = persona.icon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) PrimaryBlue else TextPrimary
                    )
                    if (persona.description.isNotBlank()) {
                        Text(
                            text = persona.description,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
                IconButton(
                    onClick = { onEditPersona(persona) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onDeletePersona(persona) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onCreatePersona,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建角色", tint = PrimaryBlue)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "对话历史",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        TextButton(
            onClick = onNewConversation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("新对话")
        }

        val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
        conversations.forEach { conv ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectConversation(conv.id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conv.title,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = dateFormat.format(Date(conv.updatedAt)),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = { onDeleteConversation(conv) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onManageMemory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("记忆管理")
        }
    }
}

@Composable
private fun ThinkingModeDialog(
    currentMode: RoleplayThinkingMode,
    onSelect: (RoleplayThinkingMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("思考模式") },
        text = {
            Column {
                RoleplayThinkingMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(mode) }
                            .background(if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.displayName,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PrimaryBlue else TextPrimary
                            )
                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CreateRoleplayPersonaDialog(
    availableModels: List<com.aiassistant.data.ModelConfigItem>,
    onConfirm: (name: String, systemPrompt: String, description: String, icon: String, modelId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🎭") }
    var selectedModelIndex by remember { mutableStateOf(0) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建角色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("图标 (emoji)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("角色名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("简短描述") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("角色设定 (System Prompt)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("描述角色的背景、性格、说话方式...") }
                )
                Text(
                    text = "留空将使用默认角色扮演提示词",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                if (availableModels.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (availableModels.isNotEmpty()) availableModels[selectedModelIndex].displayName else "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("模型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            availableModels.forEachIndexed { index, model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName)
                                            Text(
                                                model.modelName,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedModelIndex = index
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "默认使用列表中第一个模型，支持百炼和 DeepSeek 官方",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val modelId = if (availableModels.isNotEmpty()) availableModels[selectedModelIndex].id else ""
                        onConfirm(name, systemPrompt, description, icon, modelId)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRoleplayPersonaDialog(
    persona: PersonaEntity,
    availableModels: List<com.aiassistant.data.ModelConfigItem>,
    onConfirm: (PersonaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(persona.name) }
    var systemPrompt by remember { mutableStateOf(persona.systemPrompt) }
    var description by remember { mutableStateOf(persona.description) }
    var icon by remember { mutableStateOf(persona.icon) }
    val initialModelIndex = availableModels.indexOfFirst { it.id == persona.modelId }.coerceAtLeast(0)
    var selectedModelIndex by remember { mutableStateOf(initialModelIndex) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑角色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("图标 (emoji)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("角色名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("简短描述") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("角色设定 (System Prompt)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("描述角色的背景、性格、说话方式...") }
                )

                if (availableModels.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (availableModels.isNotEmpty()) availableModels[selectedModelIndex].displayName else "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("模型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            availableModels.forEachIndexed { index, model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName)
                                            Text(
                                                model.modelName,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedModelIndex = index
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val modelId = if (availableModels.isNotEmpty()) availableModels[selectedModelIndex].id else persona.modelId
                        onConfirm(
                            persona.copy(
                                name = name,
                                systemPrompt = systemPrompt,
                                description = description,
                                icon = icon,
                                modelId = modelId
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MemoryManagementDialog(
    memories: List<MemoryEntity>,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记忆管理") },
        text = {
            if (memories.isEmpty()) {
                Text(
                    text = "暂无记忆记录。对话过程中会自动提取重要记忆。",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "重要度: ${String.format("%.1f", memory.importance)}",
                                        fontSize = 11.sp,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { onDelete(memory.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = ErrorRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = memory.personaSummary,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                if (memory.topics.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "主题: ${memory.topics}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
