package com.aiassistant.ui.chat

import android.Manifest
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.data.entity.MessageRole
import com.aiassistant.ui.chat.components.ChatInputBar
import com.aiassistant.ui.chat.components.EmptyState
import com.aiassistant.ui.chat.components.FineTuneBottomSheet
import com.aiassistant.ui.chat.components.MessageBubble
import com.aiassistant.ui.chat.components.PersonaDrawerPanel
import com.aiassistant.ui.chat.components.PersonaFormDialog
import com.aiassistant.ui.chat.components.PersonaSelector
import com.aiassistant.ui.chat.components.ThinkingBubble
import com.aiassistant.ui.chat.components.ToolProgressBubble
import com.aiassistant.ui.common.EmphasizedAccelerate
import com.aiassistant.ui.common.EmphasizedDecelerate
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.ErrorRedLight
import com.aiassistant.ui.theme.ScrimLight
import com.aiassistant.ui.theme.TextSecondary
import com.yalantis.ucrop.UCrop

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val personas by viewModel.personas.collectAsState()
    val currentPersonaId by viewModel.currentPersonaId.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val fineTuneParams by viewModel.fineTuneParams.collectAsState()
    val personaDrawerOpen by viewModel.personaDrawerOpen.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val thinkingContent by viewModel.thinkingContent.collectAsState()
    val toolProgress by viewModel.toolProgress.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val fineTuneVisible by viewModel.fineTuneVisible.collectAsState()
    val editingPersona by viewModel.editingPersona.collectAsState()

    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableStateOf(0) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImageUris(uris)
        }
    }

    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val croppedUri = result.data?.let { UCrop.getOutput(it) }
                if (croppedUri != null) {
                    viewModel.addImageUris(listOf(croppedUri))
                } else {
                    pendingCameraUri?.let { viewModel.addImageUris(listOf(it)) }
                }
            } catch (e: Exception) {
                pendingCameraUri?.let { viewModel.addImageUris(listOf(it)) }
            }
        }
        pendingCameraUri = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri ?: return@rememberLauncherForActivityResult
        if (success) {
            try {
                val cropOptions = viewModel.createCropOptions(uri)
                if (cropOptions != null) {
                    cropLauncher.launch(cropOptions.getIntent(context))
                    return@rememberLauncherForActivityResult
                } else {
                    viewModel.addImageUris(listOf(uri))
                }
            } catch (e: Exception) {
                viewModel.addImageUris(listOf(uri))
            }
        }
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = viewModel.prepareCameraUri()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val currentPersona = personas.firstOrNull { it.id == currentPersonaId }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val drawerProgress by animateFloatAsState(
        targetValue = if (personaDrawerOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EmphasizedDecelerate),
        label = "drawerProgress"
    )

    val isActive = conversationState == ConversationState.STREAMING ||
            conversationState == ConversationState.THINKING
    val activeStreamingMessageId = if (isActive) messages.lastOrNull()?.id ?: 0L else 0L
    val currentThinkingContent = thinkingContent

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(messages.size, streamingContent) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (messages.isNotEmpty() && totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PersonaSelector(
                personas = personas,
                currentPersona = currentPersona,
                onSelectPersona = { viewModel.switchPersona(it) },
                onOpenDrawer = { viewModel.togglePersonaDrawer() }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(
                    targetState = messages.isEmpty() && conversationState == ConversationState.IDLE,
                    label = "contentCrossfade",
                    animationSpec = tween(300, easing = EmphasizedDecelerate)
                ) { isEmpty ->
                    if (isEmpty) {
                        EmptyState(
                            personaName = currentPersona?.name ?: "AI 助手",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 0.dp, end = 0.dp, top = 8.dp,
                                bottom = if (fineTuneVisible) 120.dp else 60.dp
                            )
                        ) {
                            val isThinkingPhase = conversationState == ConversationState.THINKING
                            val activeCachedContent = if (activeStreamingMessageId != 0L)
                                viewModel.getMessageLiveContent(activeStreamingMessageId) else ""
                            val activeDisplayContent = streamingContent.ifEmpty { activeCachedContent }
                            val activeHasContent = activeDisplayContent.isNotEmpty()
                            val showThinkingBubble = fineTuneParams.showThinkingContent &&
                                    currentThinkingContent.isNotEmpty()
                            val showToolProgress = toolProgress.isNotEmpty()

                            items(
                                items = messages,
                                key = { it.id }
                            ) { message ->
                                val isActiveMessage = message.id == activeStreamingMessageId

                                if (isActiveMessage) {
                                    if (showThinkingBubble) {
                                        ThinkingBubble(
                                            thinkingContent = currentThinkingContent,
                                            isLiveStreaming = isThinkingPhase || !activeHasContent
                                        )
                                    }
                                    if (showToolProgress && !isThinkingPhase) {
                                        ToolProgressBubble(message = toolProgress)
                                    }

                                    if (isThinkingPhase && !activeHasContent) {
                                        if (message.role == MessageRole.USER ||
                                            (message.role == MessageRole.ASSISTANT && message.content.isNotBlank())
                                        ) {
                                            MessageBubble(
                                                message = message,
                                                liveContent = "",
                                                onImageClick = if (message.imagePaths.isNotEmpty()) { idx ->
                                                    previewImages = message.imagePaths
                                                    previewIndex = idx
                                                } else null
                                            )
                                        }
                                    } else {
                                        MessageBubble(
                                            message = message,
                                            liveContent = activeDisplayContent,
                                            onImageClick = if (message.imagePaths.isNotEmpty()) { idx ->
                                                previewImages = message.imagePaths
                                                previewIndex = idx
                                            } else null
                                        )
                                    }
                                } else {
                                    if (fineTuneParams.showThinkingContent &&
                                        message.role == MessageRole.ASSISTANT &&
                                        message.thinkingContent.isNotEmpty()
                                    ) {
                                        ThinkingBubble(thinkingContent = message.thinkingContent)
                                    }
                                    MessageBubble(
                                        message = message,
                                        liveContent = "",
                                        onRetry = if (message.role == MessageRole.ASSISTANT) {
                                            { viewModel.retryMessage(message.id) }
                                        } else null,
                                        onDelete = if (message.role == MessageRole.ASSISTANT) {
                                            { viewModel.deleteMessage(message.id) }
                                        } else null,
                                        onEdit = { viewModel.editMessage(message) },
                                        onImageClick = if (message.imagePaths.isNotEmpty()) { idx ->
                                            previewImages = message.imagePaths
                                            previewIndex = idx
                                        } else null
                                    )
                                }
                            }

                        }
                    }
                }

                if (fineTuneVisible) {
                    FineTuneBottomSheet(
                        params = fineTuneParams,
                        onParamsChange = { viewModel.updateFineTuneParams(it) },
                        availableBailianTools = viewModel.getAvailableBailianTools(),
                        isResponsesApi = viewModel.isCurrentModelResponsesApi(),
                        thinkingCapability = viewModel.getThinkingCapability(),
                        onDismiss = { viewModel.toggleFineTune() }
                    )
                }
            }

            ChatInputBar(
                inputText = inputText,
                onInputChange = { viewModel.updateInputText(it) },
                selectedImages = selectedImages,
                onRemoveImage = { uri -> viewModel.removeImage(uri) },
                conversationState = conversationState,
                onSend = { viewModel.sendMessage() },
                onToggleFineTune = { viewModel.toggleFineTune() },
                onPickImage = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhoto = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onImportMarkdown = {},
                onNewConversation = { viewModel.createNewConversation() }
            )
        }

        if (drawerProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = drawerProgress }
                    .background(ScrimLight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.closePersonaDrawer() }
            )
        }

        if (drawerProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer { translationX = size.width * (1f - drawerProgress) }
            ) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                ) {
                    PersonaDrawerPanel(
                        personas = personas,
                        currentPersonaId = currentPersonaId,
                        conversations = conversations,
                        currentConversationId = currentConversationId,
                        onSelectPersona = { viewModel.switchPersona(it) },
                        onSelectConversation = { viewModel.selectConversation(it) },
                        onNewConversation = { viewModel.createNewConversation() },
                        onDeleteConversation = { viewModel.deleteConversation(it) },
                        onAddPersona = { viewModel.startNewPersona() },
                        onEditPersona = { viewModel.startEditPersona(it) },
                        onDeletePersona = { viewModel.deletePersona(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = { viewModel.closePersonaDrawer() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ErrorRedLight,
                contentColor = Color.White
            )
        }

        editingPersona?.let { persona ->
            PersonaFormDialog(
                persona = persona,
                availableModels = viewModel.getAvailableModels(),
                onPersonaChange = { viewModel.updateEditingPersona(it) },
                onSave = { viewModel.saveEditingPersona() },
                onDismiss = { viewModel.cancelEditPersona() }
            )
        }

        if (previewImages.isNotEmpty()) {
            ImagePreviewOverlay(
                images = previewImages,
                initialIndex = previewIndex,
                onDismiss = { previewImages = emptyList() }
            )
        }
    }
}

@Composable
private fun ImagePreviewOverlay(
    images: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        val safeIndex = currentIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
        val imageBytes = remember(images, safeIndex) {
            if (images.isEmpty()) return@remember null
            val base64 = images[safeIndex]
            val raw = if (base64.startsWith("data:")) base64.substringAfter("base64,") else base64
            try { Base64.decode(raw, Base64.DEFAULT) } catch (_: Exception) { null }
        }

        if (imageBytes != null) {
            AsyncImage(
                model = imageBytes,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            )
        } else {
            Text(
                text = "图片加载失败",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White.copy(alpha = 0.8f)
            )
        }

        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 8.dp else 6.dp)
                            .background(
                                color = if (index == currentIndex) Color.White
                                else Color.White.copy(alpha = 0.4f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .clickable { currentIndex = index }
                    )
                }
            }

            if (currentIndex > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(40.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { currentIndex-- },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        color = Color.White,
                        fontSize = 24.sp
                    )
                }
            }

            if (currentIndex < images.size - 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { currentIndex++ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "›",
                        color = Color.White,
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}
