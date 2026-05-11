package com.aiassistant.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.data.entity.ConversationState
import com.aiassistant.data.entity.MessageEntity
import com.aiassistant.data.entity.MessageRole
import android.util.Base64
import coil.compose.AsyncImage
import com.aiassistant.ui.theme.ErrorRedLight
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.SurfaceLight
import com.aiassistant.ui.theme.TextSecondary

@Composable
fun MessageBubble(
    message: MessageEntity,
    liveContent: String = "",
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onImageClick: ((Int) -> Unit)? = null
) {
    if (message.role == MessageRole.SYSTEM) return

    val isUser = message.role == MessageRole.USER
    val hasLiveContent = liveContent.isNotEmpty()
    val hasSavedContent = message.content.isNotBlank()
    val isLoading = message.state == ConversationState.THINKING && liveContent.isEmpty() && !hasSavedContent
    // Messages with content are never treated as errors
    val isError = message.state == ConversationState.ERROR && !hasSavedContent
    val hasImages = message.imagePaths.isNotEmpty()
    val isEmpty = !hasLiveContent && !hasSavedContent && !isLoading && !hasImages

    if (isEmpty) return

    val displayContent = when {
        hasLiveContent -> liveContent
        hasSavedContent -> message.content
        else -> ""
    }

    val showCursor = hasLiveContent
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            if (message.imagePaths.isNotEmpty()) {
                UserImageThumbnails(
                    imagePaths = message.imagePaths,
                    onImageClick = onImageClick
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            UserBubble(displayContent)
            if (!hasLiveContent && hasSavedContent && (onEdit != null || onDelete != null)) {
                ActionBar(
                    content = displayContent,
                    isError = false,
                    onCopy = { copyToClipboard(context, displayContent) },
                    onRetry = null,
                    onDelete = onDelete,
                    onEdit = onEdit
                )
            }
        } else {
            AssistantBubble(
                content = displayContent,
                isStreaming = showCursor,
                isError = isError,
                isLoading = isLoading,
                mdRendering = true,
                messageId = message.id
            )

            if (!hasLiveContent && !isLoading && hasSavedContent) {
                ActionBar(
                    content = displayContent,
                    isError = isError,
                    onCopy = { copyToClipboard(context, displayContent) },
                    onRetry = onRetry,
                    onDelete = onDelete,
                    onEdit = onEdit
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    content: String,
    isError: Boolean,
    onCopy: () -> Unit,
    onRetry: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(15.dp),
                tint = TextSecondary
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(15.dp),
                    tint = TextSecondary
                )
            }
        }
        if (isError && onRetry != null) {
            IconButton(onClick = onRetry, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重试",
                    modifier = Modifier.size(15.dp),
                    tint = PrimaryBlue
                )
            }
        } else if (!isError && onRetry != null) {
            IconButton(onClick = onRetry, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重试",
                    modifier = Modifier.size(15.dp),
                    tint = TextSecondary
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(15.dp),
                    tint = TextSecondary
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI回复", text))
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

@Composable
private fun UserBubble(content: String) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
            .background(PrimaryBlue)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = content,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun AssistantBubble(
    content: String,
    isStreaming: Boolean,
    isError: Boolean,
    isLoading: Boolean,
    mdRendering: Boolean,
    messageId: Long = 0L
) {
    val backgroundColor = when {
        isError -> ErrorRedLight.copy(alpha = 0.1f)
        else -> SurfaceLight
    }
    val textColor = when {
        isError -> ErrorRedLight
        else -> Color.Unspecified
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f),
        shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isLoading) {
                LoadingDots()
            } else if (content.isNotEmpty()) {
                if (mdRendering) {
                    MarkdownText(
                        content = content,
                        fontSize = 15f,
                        fontColor = textColor,
                        messageId = messageId
                    )
                } else {
                    Text(
                        text = content,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }

                if (isStreaming) {
                    StreamingCursor()
                }
            }
        }
    }
}

@Composable
private fun LoadingDots() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(PrimaryBlue.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Text(
        text = "▌",
        color = PrimaryBlue.copy(alpha = alpha),
        fontSize = 15.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun UserImageThumbnails(
    imagePaths: List<String>,
    onImageClick: ((Int) -> Unit)? = null
) {
    val imageBytesList = remember(imagePaths) {
        imagePaths.map { base64 ->
            val raw = if (base64.startsWith("data:")) {
                base64.substringAfter("base64,")
            } else base64
            try { Base64.decode(raw, Base64.DEFAULT) } catch (_: Exception) { null }
        }
    }
    if (imageBytesList.all { it == null }) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .widthIn(max = 280.dp)
            .padding(bottom = 2.dp)
    ) {
        items(imageBytesList.size) { index ->
            val bytes = imageBytesList[index] ?: return@items
            AsyncImage(
                model = bytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onImageClick != null) Modifier.clickable { onImageClick(index) }
                        else Modifier
                    )
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
