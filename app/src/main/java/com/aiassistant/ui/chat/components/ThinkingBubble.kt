package com.aiassistant.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.TextSecondary

@Composable
fun ThinkingBubble(
    thinkingContent: String = "",
    isLiveStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(isLiveStreaming) }
    // Track whether the user has manually toggled expand/collapse after streaming ended,
    // so we don't override their choice with the auto-collapse.
    var userManuallyToggled by remember { mutableStateOf(false) }
    val hasContent = thinkingContent.isNotBlank()

    LaunchedEffect(isLiveStreaming) {
        if (isLiveStreaming) {
            expanded = true
            userManuallyToggled = false
        } else if (!userManuallyToggled) {
            expanded = false
        }
    }

    val previewText = remember(thinkingContent) {
        if (!hasContent) return@remember ""
        thinkingContent.lineSequence().firstOrNull()?.take(80) ?: thinkingContent.take(80)
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x08000000))
                .clickable(enabled = hasContent) {
                    expanded = !expanded
                    userManuallyToggled = true
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLiveStreaming) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = PrimaryBlue.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (isLiveStreaming) "AI 思考中..." else "思考过程",
                fontSize = 12.sp,
                color = TextSecondary.copy(alpha = if (isLiveStreaming) pulseAlpha else 0.7f),
                fontStyle = if (isLiveStreaming) FontStyle.Italic else FontStyle.Normal
            )

            if (hasContent) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isLiveStreaming) {
            if (expanded) {
                Text(
                    text = if (hasContent) thinkingContent else "正在分析问题...",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    fontStyle = if (hasContent) FontStyle.Normal else FontStyle.Italic,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF888888).copy(alpha = (pulseAlpha * 0.8f).coerceAtMost(1f)),
                    lineHeight = 17.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                )
            } else if (hasContent) {
                Text(
                    text = previewText + if (thinkingContent.length > 80) "..." else "",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF888888).copy(alpha = (pulseAlpha * 0.8f).coerceAtMost(1f)),
                    lineHeight = 17.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp)
                )
            }
        }

        if (!isLiveStreaming && hasContent) {
            if (expanded) {
                Text(
                    text = thinkingContent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF888888),
                    lineHeight = 17.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 4.dp)
                )
            }

            if (!expanded && previewText.isNotEmpty()) {
                Text(
                    text = previewText + if (thinkingContent.length > 80) "..." else "",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF888888),
                    lineHeight = 17.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp)
                )
            }
        }

        if (!hasContent && isLiveStreaming) {
            ThinkingDots(pulseAlpha)
        }
    }
}

@Composable
private fun ThinkingDots(pulseAlpha: Float) {
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 200
            val transition = rememberInfiniteTransition(label = "dot$index")
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = alpha * pulseAlpha))
            )
        }
    }
}
