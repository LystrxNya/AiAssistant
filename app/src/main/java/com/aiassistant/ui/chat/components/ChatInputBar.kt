package com.aiassistant.ui.chat.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.ui.chat.SelectedImage
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.SurfaceLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    selectedImages: List<SelectedImage>,
    onRemoveImage: (Uri) -> Unit,
    conversationState: ConversationState,
    onSend: () -> Unit,
    onToggleFineTune: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onImportMarkdown: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isReady = conversationState == ConversationState.IDLE ||
            conversationState == ConversationState.SUCCESS ||
            conversationState == ConversationState.ERROR

    var fullScreenEditorVisible by remember { mutableStateOf(false) }

    if (fullScreenEditorVisible) {
        FullScreenEditor(
            text = inputText,
            onTextChange = onInputChange,
            onDismiss = { fullScreenEditorVisible = false },
            onSend = {
                onSend()
                fullScreenEditorVisible = false
            },
            isReady = isReady,
            hasSelectedImages = selectedImages.isNotEmpty()
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (selectedImages.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    selectedImages.forEach { image ->
                        Box(modifier = Modifier.size(56.dp)) {
                            AsyncImage(
                                model = image.thumbnailFile ?: image.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { onRemoveImage(image.uri) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除图片",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleFineTune,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "\u2699\uFE0F",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                IconButton(
                    onClick = onPickImage,
                    enabled = isReady,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加图片",
                        modifier = Modifier.size(20.dp),
                        tint = if (isReady) PrimaryBlue else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = onTakePhoto,
                    enabled = isReady,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        modifier = Modifier.size(20.dp),
                        tint = if (isReady) PrimaryBlue else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = { fullScreenEditorVisible = true },
                    enabled = isReady,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "全屏编辑",
                        modifier = Modifier.size(20.dp),
                        tint = if (isReady) PrimaryBlue else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = {
                        onNewConversation()
                        focusManager.clearFocus()
                    },
                    enabled = isReady,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = "新建对话",
                        modifier = Modifier.size(20.dp),
                        tint = if (isReady) PrimaryBlue else MaterialTheme.colorScheme.outline
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (isReady) "输入消息..." else "AI 正在思考...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    enabled = isReady,
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() || selectedImages.isNotEmpty()) {
                                onSend()
                                focusManager.clearFocus()
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                val canSend = isReady && (inputText.isNotBlank() || selectedImages.isNotEmpty())

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (canSend) PrimaryBlue
                            else Color(0xFFD0D0D0),
                            shape = CircleShape
                        )
                        .then(
                            if (canSend)
                                Modifier.clickable {
                                    onSend()
                                    focusManager.clearFocus()
                                }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "➤",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) Color.White else Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    isReady: Boolean,
    hasSelectedImages: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundLight
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = PrimaryBlue)
                    }
                    Text(
                        text = "全屏编辑",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF333333)
                    )
                    TextButton(
                        onClick = onSend,
                        enabled = isReady && (text.isNotBlank() || hasSelectedImages)
                    ) {
                        Text("发送", color = if (isReady && (text.isNotBlank() || hasSelectedImages)) PrimaryBlue else Color.Gray)
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = {
                        Text(
                            text = "输入消息...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    enabled = isReady,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = SurfaceLight,
                        unfocusedContainerColor = SurfaceLight
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
