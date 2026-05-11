package com.aiassistant.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.data.ModelConfigItem
import com.aiassistant.data.entity.ConversationEntity
import com.aiassistant.data.entity.PersonaEntity
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.DividerLight
import com.aiassistant.ui.theme.ErrorRed
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.TextPrimary
import com.aiassistant.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val personaIcons = listOf(
    "🤖", "👩‍💻", "👨‍🔬", "🎭", "🧠", "📚", "🎨", "🌟",
    "🔮", "🎯", "💡", "🦋", "🌈", "⚡", "🎪", "🦉",
    "🦊", "🐱", "🐶", "🐼", "🐨", "🦁", "🐸", "🌻"
)

@Composable
fun PersonaDrawerPanel(
    personas: List<PersonaEntity>,
    currentPersonaId: Long,
    conversations: List<ConversationEntity>,
    currentConversationId: Long,
    onSelectPersona: (Long) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onAddPersona: () -> Unit,
    onEditPersona: (PersonaEntity) -> Unit,
    onDeletePersona: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = "人格 · 话题管理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(20.dp)
            )

            HorizontalDivider(color = DividerLight)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "人格列表",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                IconButton(onClick = onAddPersona, modifier = Modifier.height(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加人格",
                        tint = PrimaryBlue,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                items(personas, key = { it.id }) { persona ->
                    val isSelected = persona.id == currentPersonaId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectPersona(persona.id) }
                            .background(
                                if (isSelected) PrimaryBlue.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = persona.icon, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = persona.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) PrimaryBlue
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (persona.description.isNotBlank()) {
                                Text(
                                    text = persona.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { onEditPersona(persona) },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = PrimaryBlue,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeletePersona(persona.id) },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = ErrorRed,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = DividerLight, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "历史话题",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                IconButton(onClick = onNewConversation) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新对话",
                        tint = PrimaryBlue
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == currentConversationId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectConversation(conv.id) }
                            .background(
                                if (isSelected) PrimaryBlue.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) PrimaryBlue
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatDrawerTimestamp(conv.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { onDeleteConversation(conv.id) },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = ErrorRed,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDrawerTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun PersonaFormDialog(
    persona: PersonaEntity,
    availableModels: List<ModelConfigItem>,
    onPersonaChange: (PersonaEntity) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showIconPicker by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val selectedModel = availableModels.find { it.id == persona.modelId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (persona.id == 0L) "新建人格" else "编辑人格",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = persona.icon,
                        fontSize = 36.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showIconPicker = true }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "点击更换图标",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                if (showIconPicker) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                        modifier = Modifier.height(120.dp)
                    ) {
                        items(personaIcons.size) { index ->
                            Text(
                                text = personaIcons[index],
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onPersonaChange(persona.copy(icon = personaIcons[index]))
                                        showIconPicker = false
                                    }
                                    .padding(6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = persona.name,
                    onValueChange = { onPersonaChange(persona.copy(name = it)) },
                    label = { Text("人格名称") },
                    placeholder = { Text("如：翻译助手") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = persona.description,
                    onValueChange = { onPersonaChange(persona.copy(description = it)) },
                    label = { Text("简短描述") },
                    placeholder = { Text("一句话描述这个人格") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = persona.systemPrompt,
                    onValueChange = { onPersonaChange(persona.copy(systemPrompt = it)) },
                    label = { Text("System Prompt") },
                    placeholder = { Text("你是一个专业的翻译助手...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "路由模型",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedTextField(
                        value = selectedModel?.displayName?.ifBlank { selectedModel.modelName } ?: "使用默认模型",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelExpanded = true },
                        trailingIcon = {
                            IconButton(onClick = { modelExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            focusedLabelColor = PrimaryBlue
                        )
                    )
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("使用默认模型", fontSize = 14.sp) },
                            onClick = {
                                onPersonaChange(persona.copy(modelId = ""))
                                modelExpanded = false
                            }
                        )
                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.displayName.ifBlank { m.modelName }, fontSize = 14.sp) },
                                onClick = {
                                    onPersonaChange(persona.copy(modelId = m.id))
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = persona.name.isNotBlank() && persona.systemPrompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
