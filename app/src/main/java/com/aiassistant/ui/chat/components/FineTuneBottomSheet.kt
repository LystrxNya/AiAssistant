package com.aiassistant.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.ui.chat.FineTuneParams
import com.aiassistant.ui.theme.*

enum class DeepSeekThinkingLevel(
    val modeName: String,
    val displayName: String,
    val description: String,
    val reasoningEffort: String
) {
    NONE("none", "不思考", "直接回答，速度最快", "none"),
    HIGH("high", "思考", "常规推理，适合大多数场景", "high"),
    MAX("max", "深度思考", "极限推理，数学/代码/难题", "max")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FineTuneBottomSheet(
    params: FineTuneParams,
    onParamsChange: (FineTuneParams) -> Unit,
    availableBailianTools: List<String>,
    isResponsesApi: Boolean,
    thinkingCapability: String = "binary",
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLight,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "模型微调设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (availableBailianTools.isNotEmpty()) {
                SectionCard {
                    SectionTitle(
                        icon = Icons.Default.Build,
                        title = if (isResponsesApi) "百炼官方工具 (Responses API)" else "百炼官方工具 (Chat API)"
                    )

                    if (isResponsesApi) {
                        if ("web_search" in availableBailianTools) {
                            SwitchSettingItem(
                                title = "联网搜索",
                                subtitle = "通过阿里云官方搜索服务联网",
                                checked = params.bailianWebSearch,
                                onCheckedChange = { onParamsChange(params.copy(bailianWebSearch = it)) }
                            )
                        }
                        if ("web_extractor" in availableBailianTools) {
                            SwitchSettingItem(
                                title = "网页抓取",
                                subtitle = "直接访问并读取指定URL内容",
                                checked = params.bailianWebExtractor,
                                onCheckedChange = { onParamsChange(params.copy(bailianWebExtractor = it)) }
                            )
                        }
                        if ("code_interpreter" in availableBailianTools) {
                            SwitchSettingItem(
                                title = "代码解释器",
                                subtitle = "在阿里云沙箱中运行Python代码",
                                checked = params.bailianCodeInterpreter,
                                onCheckedChange = { onParamsChange(params.copy(bailianCodeInterpreter = it)) }
                            )
                        }
                        if ("web_search_image" in availableBailianTools) {
                            SwitchSettingItem(
                                title = "文搜图",
                                subtitle = "根据文字描述搜索网络图片",
                                checked = params.bailianWebSearchImage,
                                onCheckedChange = { onParamsChange(params.copy(bailianWebSearchImage = it)) }
                            )
                        }
                        if ("image_search" in availableBailianTools) {
                            SwitchSettingItem(
                                title = "图搜图",
                                subtitle = "以图搜图，基于视觉相似度搜索",
                                checked = params.bailianImageSearch,
                                onCheckedChange = { onParamsChange(params.copy(bailianImageSearch = it)) }
                            )
                        }
                    } else {
                        if ("web_search" in availableBailianTools) {
                            SwitchSettingItem(
                                title = "联网搜索",
                                subtitle = "通过阿里云官方搜索服务联网",
                                checked = params.bailianWebSearch,
                                onCheckedChange = { onParamsChange(params.copy(bailianWebSearch = it)) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            SectionCard {
                SectionTitle(icon = Icons.Default.Search, title = "联网搜索（通用）")
                SwitchSettingItem(
                    title = "联网搜索",
                    subtitle = "通过 DeepSeek 或配置的搜索服务联网",
                    checked = params.webSearchEnabled,
                    onCheckedChange = { onParamsChange(params.copy(webSearchEnabled = it)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (thinkingCapability != "none") {
                SectionCard {
                    SectionTitle(icon = Icons.Default.Psychology, title = "深度思考")

                    if (thinkingCapability == "reasoning_effort") {
                        DeepSeekThinkingSection(params, onParamsChange)
                    } else {
                        BinaryThinkingSection(params, onParamsChange)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = DividerColor
                    )

                    SwitchSettingItem(
                        title = "显示思考内容",
                        subtitle = "在回复前展示AI的思考过程",
                        checked = params.showThinkingContent,
                        onCheckedChange = { onParamsChange(params.copy(showThinkingContent = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            SectionCard {
                SectionTitle(icon = Icons.Default.Stream, title = "流式输出")
                SwitchSettingItem(
                    title = "流式输出",
                    subtitle = "实时显示回复内容",
                    checked = params.showStreamingEnabled,
                    onCheckedChange = { onParamsChange(params.copy(showStreamingEnabled = it)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SectionCard {
                SectionTitle(icon = Icons.AutoMirrored.Filled.List, title = "对话上下文")

                EditableSliderSettingItem(
                    title = "上下文轮数",
                    value = params.contextTurns.toFloat(),
                    valueRange = 4f..50f,
                    onValueChange = { onParamsChange(params.copy(contextTurns = it.toInt())) }
                )

                Text(
                    text = "保留最近 ${params.contextTurns} 轮对话作为上下文",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = DividerColor
                )

                SwitchSettingItem(
                    title = "智能上下文压缩",
                    subtitle = "用辅助小模型总结已丢弃的上下文，保持记忆连续",
                    checked = params.summarizeDiscardedContext,
                    onCheckedChange = { onParamsChange(params.copy(summarizeDiscardedContext = it)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SectionCard {
                var showTemperatureInfo by remember { mutableStateOf(false) }

                SectionTitle(icon = Icons.Default.Tune, title = "参数微调")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTemperatureInfo = !showTemperatureInfo }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = InfoBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showTemperatureInfo) "收起参数说明" else "查看参数说明",
                        fontSize = 12.sp,
                        color = InfoBlue
                    )
                }

                AnimatedVisibility(
                    visible = showTemperatureInfo,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(InfoBlue.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Temperature（温度）",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "控制回复的随机性。越低越确定（如0.1），越高越随机有创意（如1.5）。默认0.7",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Top-P（核采样）",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "控制词汇选择范围。0.1只考虑最可能的词，1.0考虑所有词。默认0.9",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "建议：只调一个参数，另一个关闭",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WarningOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SwitchSettingItem(
                    title = "Temperature",
                    subtitle = "控制回复随机性",
                    checked = params.temperatureEnabled,
                    onCheckedChange = {
                        onParamsChange(
                            params.copy(
                                temperatureEnabled = it,
                                topPEnabled = if (it) false else params.topPEnabled
                            )
                        )
                    }
                )

                if (params.temperatureEnabled) {
                    EditableSliderSettingItem(
                        title = "Temperature 值",
                        value = params.temperature,
                        valueRange = 0f..2f,
                        onValueChange = { onParamsChange(params.copy(temperature = it)) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = DividerColor
                )

                SwitchSettingItem(
                    title = "Top-P",
                    subtitle = "控制词汇选择范围",
                    checked = params.topPEnabled,
                    onCheckedChange = {
                        onParamsChange(
                            params.copy(
                                topPEnabled = it,
                                temperatureEnabled = if (it) false else params.temperatureEnabled
                            )
                        )
                    }
                )

                if (params.topPEnabled) {
                    EditableSliderSettingItem(
                        title = "Top-P 值",
                        value = params.topP,
                        valueRange = 0f..1f,
                        onValueChange = { onParamsChange(params.copy(topP = it)) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SectionCard {
                SectionTitle(icon = Icons.Default.Code, title = "显示设置")

                SwitchSettingItem(
                    title = "Markdown 渲染",
                    subtitle = "关闭后显示纯文本",
                    checked = params.mdRenderingEnabled,
                    onCheckedChange = { onParamsChange(params.copy(mdRenderingEnabled = it)) }
                )
            }
        }
    }
}

@Composable
private fun DeepSeekThinkingSection(
    params: FineTuneParams,
    onParamsChange: (FineTuneParams) -> Unit
) {
    Column {
        Text(
            text = "DeepSeek 支持多档思考深度控制",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val effectiveMode = if (DeepSeekThinkingLevel.entries.any { it.modeName == params.thinkingMode }) {
            params.thinkingMode
        } else {
            DeepSeekThinkingLevel.MAX.modeName
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DeepSeekThinkingLevel.entries.forEach { level ->
                val isSelected = effectiveMode == level.modeName
                val effectiveEnabled = level.modeName != "none"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) PrimaryBlue else Color.Transparent
                        )
                        .clickable {
                            onParamsChange(
                                params.copy(
                                    thinkingEnabled = effectiveEnabled,
                                    thinkingMode = level.modeName
                                )
                            )
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = level.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextSecondary
                    )
                }
            }
        }
        val selectedLevel = DeepSeekThinkingLevel.entries.find { it.modeName == effectiveMode }
        if (selectedLevel != null && selectedLevel.description.isNotEmpty()) {
            Text(
                text = selectedLevel.description,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun BinaryThinkingSection(
    params: FineTuneParams,
    onParamsChange: (FineTuneParams) -> Unit
) {
    SwitchSettingItem(
        title = "启用深度思考",
        subtitle = "模型将进行更深入的推理，响应时间可能更长",
        checked = params.thinkingEnabled,
        onCheckedChange = { enabled ->
            onParamsChange(
                params.copy(
                    thinkingEnabled = enabled,
                    thinkingMode = if (enabled) "standard" else "none"
                )
            )
        }
    )
}
