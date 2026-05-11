package com.aiassistant.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiassistant.data.EncryptedPrefsManager
import com.aiassistant.data.ModelConfigItem
import com.aiassistant.ui.common.EmphasizedDecelerate
import com.aiassistant.ui.theme.BackgroundLight
import com.aiassistant.ui.theme.ErrorRed
import com.aiassistant.ui.theme.PrimaryBlue
import com.aiassistant.ui.theme.SuccessGreen
import com.aiassistant.ui.theme.SurfaceDark
import com.aiassistant.ui.theme.SurfaceLight
import com.aiassistant.ui.theme.TextPrimary
import com.aiassistant.ui.theme.TextSecondary

private data class ProtocolOption(
    val value: String,
    val label: String,
    val description: String
)

private val protocolOptions = listOf(
    ProtocolOption(
        EncryptedPrefsManager.PROTOCOL_OPENAI_COMPATIBLE,
        "OpenAI 兼容模式",
        "支持文件预上传+自动注入"
    ),
    ProtocolOption(
        EncryptedPrefsManager.PROTOCOL_PURE_BASE64,
        "纯 Base64 模式",
        "全部数据转Base64内联发送"
    ),
    ProtocolOption(
        EncryptedPrefsManager.PROTOCOL_LOCAL,
        "本地直连模式",
        "零转换直接传文件"
    )
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val searchApi by viewModel.searchApi.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val enabledNavItems by viewModel.enabledNavItems.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            val modelsList by viewModel.modelsList.collectAsState()
            val mainModelId by viewModel.mainModelId.collectAsState()
            val visionModelId by viewModel.visionModelId.collectAsState()
            val auxModelId by viewModel.auxModelId.collectAsState()
            val sttModelId by viewModel.sttModelId.collectAsState()
            val editingModel by viewModel.editingModel.collectAsState()

            ModelManagerCard(
                models = modelsList,
                mainModelId = mainModelId,
                visionModelId = visionModelId,
                auxModelId = auxModelId,
                sttModelId = sttModelId,
                onAddModel = { viewModel.startNewModel() },
                onEditModel = { viewModel.startEditModel(it) },
                onDeleteModel = { viewModel.deleteModel(it) },
                onMainModelChange = { viewModel.setMainModel(it) },
                onVisionModelChange = { viewModel.setVisionModel(it) },
                onAuxModelChange = { viewModel.setAuxModel(it) },
                onSttModelChange = { viewModel.setSttModel(it) }
            )

            editingModel?.let { model ->
                ModelEditDialog(
                    model = model,
                    onModelChange = { viewModel.updateEditingModel(it) },
                    onSave = { viewModel.saveEditingModel() },
                    onDismiss = { viewModel.cancelEditModel() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val bailianConfigured by viewModel.bailianConfigured.collectAsState()

            BailianConfigCard(
                isConfigured = bailianConfigured,
                presets = viewModel.getBailianPresets(),
                onSetup = { apiKey -> viewModel.setupBailianPresets(apiKey) },
                onUpdateKey = { newKey -> viewModel.updateBailianApiKey(newKey) },
                onRemove = { viewModel.removeBailianPresets() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            val deepseekConfigured by viewModel.deepseekConfigured.collectAsState()

            DeepSeekOfficialConfigCard(
                isConfigured = deepseekConfigured,
                presets = viewModel.getDeepSeekPresets(),
                onSetup = { apiKey -> viewModel.setupDeepSeekPresets(apiKey) },
                onUpdateKey = { newKey -> viewModel.updateDeepSeekApiKey(newKey) },
                onRemove = { viewModel.removeDeepSeekPresets() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SearchApiConfigCard(
                config = searchApi,
                searchMode = searchMode,
                onBaseUrlChange = { viewModel.updateSearchBaseUrl(it) },
                onApiKeyChange = { viewModel.updateSearchApiKey(it) },
                onSave = { viewModel.saveSearchApi() },
                onResetChange = { viewModel.resetSearchConnectivity() },
                onSearchModeChange = { viewModel.saveSearchMode(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            NavigationBarConfigCard(
                allItems = viewModel.allNavItems,
                enabledItems = enabledNavItems,
                onToggle = { viewModel.toggleNavItem(it) }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = SuccessGreen,
                contentColor = Color.White
            )
        }
    }
}

@Composable
private fun ModelConfigCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    config: ModelConfig,
    connectivity: ConnectivityState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onProtocolStyleChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onResetConnectivity: () -> Unit,
    modelPlaceholder: String = "gpt-4o"
) {
    var showApiKey by remember { mutableStateOf(false) }
    var protocolMenuExpanded by remember { mutableStateOf(false) }

    val currentProtocol = protocolOptions.find { it.value == config.protocolStyle }
        ?: protocolOptions.first()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = {
                    onBaseUrlChange(it)
                    onResetConnectivity()
                },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    focusedLabelColor = PrimaryBlue,
                    cursorColor = PrimaryBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = config.apiKey,
                onValueChange = {
                    onApiKeyChange(it)
                    onResetConnectivity()
                },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (showApiKey) "隐藏" else "显示",
                            tint = TextSecondary
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    focusedLabelColor = PrimaryBlue,
                    cursorColor = PrimaryBlue
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = config.modelName,
                onValueChange = {
                    onModelNameChange(it)
                    onResetConnectivity()
                },
                label = { Text("模型名称") },
                placeholder = { Text(modelPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    focusedLabelColor = PrimaryBlue,
                    cursorColor = PrimaryBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box {
                OutlinedTextField(
                    value = currentProtocol.label,
                    onValueChange = {},
                    label = { Text("API 协议风格") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { protocolMenuExpanded = true },
                    trailingIcon = {
                        IconButton(onClick = { protocolMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "选择协议风格",
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    )
                )

                DropdownMenu(
                    expanded = protocolMenuExpanded,
                    onDismissRequest = { protocolMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    protocolOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (option.value == config.protocolStyle)
                                            FontWeight.Bold else FontWeight.Normal,
                                        color = if (option.value == config.protocolStyle)
                                            PrimaryBlue else TextPrimary
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            },
                            onClick = {
                                onProtocolStyleChange(option.value)
                                protocolMenuExpanded = false
                                onResetConnectivity()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Text("保存配置")
                }

                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !connectivity.isTesting &&
                            config.baseUrl.isNotBlank() &&
                            config.apiKey.isNotBlank(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    )
                ) {
                    if (connectivity.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("连通性测试")
                }
            }

            AnimatedVisibility(
                visible = connectivity.result !is ConnectivityResult.Idle,
                enter = slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 300,
                        easing = EmphasizedDecelerate
                    )
                ) + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                val result = connectivity.result
                val (bgColor, iconRes, message) = when (result) {
                    is ConnectivityResult.Success -> Triple(
                        SuccessGreen.copy(alpha = 0.1f),
                        Icons.Default.Check,
                        "✅ ${result.message}"
                    )
                    is ConnectivityResult.Failure -> Triple(
                        ErrorRed.copy(alpha = 0.1f),
                        Icons.Default.Close,
                        "❌ ${result.error}"
                    )
                    is ConnectivityResult.Idle -> Triple(
                        Color.Transparent,
                        Icons.Default.Check,
                        ""
                    )
                }

                if (result !is ConnectivityResult.Idle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = iconRes,
                            contentDescription = null,
                            tint = if (result is ConnectivityResult.Success)
                                SuccessGreen else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result is ConnectivityResult.Success)
                                SuccessGreen else ErrorRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelManagerCard(
    models: List<ModelConfigItem>,
    mainModelId: String,
    visionModelId: String,
    auxModelId: String,
    sttModelId: String,
    onAddModel: () -> Unit,
    onEditModel: (ModelConfigItem) -> Unit,
    onDeleteModel: (String) -> Unit,
    onMainModelChange: (String) -> Unit,
    onVisionModelChange: (String) -> Unit,
    onAuxModelChange: (String) -> Unit,
    onSttModelChange: (String) -> Unit
) {
    var mainExpanded by remember { mutableStateOf(false) }
    var visionExpanded by remember { mutableStateOf(false) }
    var auxExpanded by remember { mutableStateOf(false) }
    var sttExpanded by remember { mutableStateOf(false) }
    val mainSelected = models.find { it.id == mainModelId }
    val visionSelected = models.find { it.id == visionModelId }
    val auxSelected = models.find { it.id == auxModelId }
    val sttSelected = models.find { it.id == sttModelId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "模型管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "配置多个模型，视觉/辅助模型从已配置中选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onAddModel) {
                    Icon(Icons.Default.Add, contentDescription = "添加模型", tint = PrimaryBlue)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (models.isEmpty()) {
                Text(
                    text = "暂无配置的模型，点击右上角 + 添加",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.displayName.ifBlank { model.modelName },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${model.modelName} · ${model.baseUrl.take(30)}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onEditModel(model) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteModel(model.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "默认主模型（对话）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedTextField(
                    value = mainSelected?.displayName?.ifBlank { mainSelected.modelName } ?: "使用列表第一个",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mainExpanded = true },
                    trailingIcon = {
                        IconButton(onClick = { mainExpanded = true }) {
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
                    expanded = mainExpanded,
                    onDismissRequest = { mainExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("使用列表第一个", fontSize = 14.sp) },
                        onClick = { onMainModelChange(""); mainExpanded = false }
                    )
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName.ifBlank { m.modelName }, fontSize = 14.sp) },
                            onClick = { onMainModelChange(m.id); mainExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "视觉模型（OCR/PDF识图）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedTextField(
                    value = visionSelected?.displayName?.ifBlank { visionSelected.modelName } ?: "未选择",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { visionExpanded = true },
                    trailingIcon = {
                        IconButton(onClick = { visionExpanded = true }) {
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
                    expanded = visionExpanded,
                    onDismissRequest = { visionExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("未选择", fontSize = 14.sp) },
                        onClick = { onVisionModelChange(""); visionExpanded = false }
                    )
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName.ifBlank { m.modelName }, fontSize = 14.sp) },
                            onClick = { onVisionModelChange(m.id); visionExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "辅助模型（清洗/修正/提取）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedTextField(
                    value = auxSelected?.displayName?.ifBlank { auxSelected.modelName } ?: "未选择",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { auxExpanded = true },
                    trailingIcon = {
                        IconButton(onClick = { auxExpanded = true }) {
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
                    expanded = auxExpanded,
                    onDismissRequest = { auxExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("未选择", fontSize = 14.sp) },
                        onClick = { onAuxModelChange(""); auxExpanded = false }
                    )
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName.ifBlank { m.modelName }, fontSize = 14.sp) },
                            onClick = { onAuxModelChange(m.id); auxExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "语音转写模型（STT）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedTextField(
                    value = sttSelected?.displayName?.ifBlank { sttSelected.modelName } ?: "未选择",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { sttExpanded = true },
                    trailingIcon = {
                        IconButton(onClick = { sttExpanded = true }) {
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
                    expanded = sttExpanded,
                    onDismissRequest = { sttExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("未选择", fontSize = 14.sp) },
                        onClick = { onSttModelChange(""); sttExpanded = false }
                    )
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName.ifBlank { m.modelName }, fontSize = 14.sp) },
                            onClick = { onSttModelChange(m.id); sttExpanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelEditDialog(
    model: ModelConfigItem,
    onModelChange: (ModelConfigItem) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    var protocolMenuExpanded by remember { mutableStateOf(false) }
    val currentProtocol = protocolOptions.find { it.value == model.protocolStyle } ?: protocolOptions.first()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (model.displayName.isBlank()) "添加模型" else "编辑模型",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = model.displayName,
                    onValueChange = { onModelChange(model.copy(displayName = it)) },
                    label = { Text("显示名称") },
                    placeholder = { Text("如：GPT-4o 主力") },
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
                    value = model.baseUrl,
                    onValueChange = { onModelChange(model.copy(baseUrl = it)) },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com") },
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
                    value = model.apiKey,
                    onValueChange = { onModelChange(model.copy(apiKey = it)) },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = model.modelName,
                    onValueChange = { onModelChange(model.copy(modelName = it)) },
                    label = { Text("模型名称") },
                    placeholder = { Text("gpt-4o") },
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
                Box {
                    OutlinedTextField(
                        value = currentProtocol.label,
                        onValueChange = {},
                        label = { Text("API 协议风格") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { protocolMenuExpanded = true },
                        trailingIcon = {
                            IconButton(onClick = { protocolMenuExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            focusedLabelColor = PrimaryBlue,
                            cursorColor = PrimaryBlue
                        )
                    )
                    DropdownMenu(
                        expanded = protocolMenuExpanded,
                        onDismissRequest = { protocolMenuExpanded = false }
                    ) {
                        protocolOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.label, fontWeight = if (option.value == model.protocolStyle) FontWeight.Bold else FontWeight.Normal)
                                        Text(option.description, fontSize = 12.sp, color = TextSecondary)
                                    }
                                },
                                onClick = {
                                    onModelChange(model.copy(protocolStyle = option.value))
                                    protocolMenuExpanded = false
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
                enabled = model.displayName.isNotBlank() && model.baseUrl.isNotBlank() && model.modelName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SearchApiConfigCard(
    config: SearchApiConfig,
    searchMode: String,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onResetChange: () -> Unit,
    onSearchModeChange: (String) -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "联网搜索配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "选择搜索方式，为主模型提供联网搜索能力",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search mode selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val modes = listOf(
                    EncryptedPrefsManager.SEARCH_MODE_DEEPSEEK to "DeepSeek 搜索",
                    EncryptedPrefsManager.SEARCH_MODE_THIRD_PARTY to "第三方搜索API"
                )
                modes.forEach { (mode, label) ->
                    val isSelected = searchMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryBlue else Color.Transparent)
                            .clickable { onSearchModeChange(mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (searchMode == EncryptedPrefsManager.SEARCH_MODE_DEEPSEEK) {
                Text(
                    text = "使用 DeepSeek V4 Flash 内置联网搜索（需配置百炼 API Key）",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            } else {
                OutlinedTextField(
                    value = config.baseUrl,
                    onValueChange = {
                        onBaseUrlChange(it)
                        onResetChange()
                    },
                    label = { Text("搜索 API Base URL") },
                    placeholder = { Text("https://searxng.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = config.apiKey,
                    onValueChange = {
                        onApiKeyChange(it)
                        onResetChange()
                    },
                    label = { Text("API Key (可选)") },
                    placeholder = { Text("部分搜索API需要Key，无则留空") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = if (showApiKey) "隐藏" else "显示",
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("保存配置")
                }
            }
        }
    }
}

@Composable
private fun BailianConfigCard(
    isConfigured: Boolean,
    presets: List<com.aiassistant.data.preset.BailianPresetManager.BailianModelPreset>,
    onSetup: (String) -> Unit,
    onUpdateKey: (String) -> Unit,
    onRemove: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showUpdateKey by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "阿里云百炼模型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isConfigured) "已配置，共${presets.size}个模型可用" else "输入一个API Key，一键激活10个模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConfigured) SuccessGreen else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isConfigured) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("百炼 API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            onSetup(apiKeyInput.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiKeyInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("一键配置全部模型")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPresets = !showPresets },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (showPresets) "收起模型列表" else "查看模型列表", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { showUpdateKey = !showUpdateKey },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("更新Key", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Text("移除全部百炼模型", fontSize = 13.sp)
                }

                if (showUpdateKey) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            placeholder = { Text("输入新的API Key") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue,
                                cursorColor = PrimaryBlue
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    onUpdateKey(apiKeyInput.trim())
                                    apiKeyInput = ""
                                    showUpdateKey = false
                                }
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("更新")
                        }
                    }
                }

                if (showPresets) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val categories = presets.groupBy { it.category }
                    categories.forEach { (category, models) ->
                        Text(
                            text = when (category) {
                                "qwen" -> "通义千问"
                                "deepseek" -> "DeepSeek"
                                "glm" -> "GLM"
                                "kimi" -> "Kimi"
                                "minimax" -> "MiniMax"
                                else -> category
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        models.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (preset.apiType) {
                                                "responses" -> SuccessGreen
                                                else -> PrimaryBlue
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preset.displayName,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (preset.apiType) {
                                        "responses" -> "Responses"
                                        "stt" -> "STT"
                                        else -> "Chat"
                                    },
                                    fontSize = 10.sp,
                                    color = when (preset.apiType) {
                                        "responses" -> SuccessGreen
                                        "stt" -> PrimaryBlue
                                        else -> PrimaryBlue
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                                if (preset.toolNames.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${preset.toolNames.size}个工具",
                                        fontSize = 10.sp,
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

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("确认移除") },
            text = { Text("将移除所有百炼预设模型，此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showRemoveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DeepSeekOfficialConfigCard(
    isConfigured: Boolean,
    presets: List<com.aiassistant.data.preset.DeepSeekPresetManager.DeepSeekModelPreset>,
    onSetup: (String) -> Unit,
    onUpdateKey: (String) -> Unit,
    onRemove: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showUpdateKey by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DeepSeek 官方模型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isConfigured) "已配置，共${presets.size}个模型可用" else "输入 DeepSeek API Key，直接调用官方API",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConfigured) SuccessGreen else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isConfigured) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("DeepSeek API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedLabelColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            onSetup(apiKeyInput.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiKeyInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("配置 DeepSeek 模型")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPresets = !showPresets },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (showPresets) "收起模型列表" else "查看模型列表", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { showUpdateKey = !showUpdateKey },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("更新Key", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Text("移除 DeepSeek 模型", fontSize = 13.sp)
                }

                if (showUpdateKey) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            placeholder = { Text("输入新的 API Key") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                focusedLabelColor = PrimaryBlue,
                                cursorColor = PrimaryBlue
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    onUpdateKey(apiKeyInput.trim())
                                    apiKeyInput = ""
                                    showUpdateKey = false
                                }
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("更新")
                        }
                    }
                }

                if (showPresets) {
                    Spacer(modifier = Modifier.height(12.dp))
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = preset.displayName,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = preset.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("确认移除") },
            text = { Text("将移除所有 DeepSeek 官方预设模型，此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showRemoveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun NavigationBarConfigCard(
    allItems: List<Pair<String, String>>,
    enabledItems: Set<String>,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "底部导航栏配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "勾选 = 直接显示在底部导航栏，未勾选 = 收进「更多」菜单",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            allItems.forEach { (route, title) ->
                val isEnabled = route in enabledItems
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(route) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled) TextPrimary else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = isEnabled,
                        onCheckedChange = { onToggle(route) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryBlue,
                            uncheckedColor = TextSecondary.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }
    }
}

