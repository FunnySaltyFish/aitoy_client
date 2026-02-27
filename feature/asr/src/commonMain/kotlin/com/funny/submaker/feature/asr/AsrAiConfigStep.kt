package com.funny.submaker.feature.asr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.asr.components.AsrFieldLabel
import com.funny.submaker.feature.asr.components.AsrInfoCard
import com.funny.submaker.feature.asr.components.AsrWizardBottomActions

private enum class AsrModelType {
    Standard,
    HighPrecision,
}

@Composable
fun AsrAiConfigStep(
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    var modelType by rememberSaveable { mutableStateOf(AsrModelType.HighPrecision) }
    var timestampLevel by rememberSaveable { mutableFloatStateOf(1f) }
    var enableEnhancement by rememberSaveable { mutableStateOf(true) }
    var enableAutoFix by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 122.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "智能生成配置",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.titleText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "配置 AI 模型参数以获得最佳字幕效果。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedText,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsrFieldLabel(text = "模型选择")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModelCard(
                        title = "标准速度",
                        desc = "适合日常对话，生成速度快，消耗Token少。",
                        icon = Icons.Rounded.Bolt,
                        selected = modelType == AsrModelType.Standard,
                        onClick = { modelType = AsrModelType.Standard },
                        modifier = Modifier.weight(1f),
                    )
                    ModelCard(
                        title = "高精度模型",
                        desc = "适合专业术语、复杂语境，识别率极高。",
                        icon = Icons.Rounded.Psychology,
                        selected = modelType == AsrModelType.HighPrecision,
                        onClick = { modelType = AsrModelType.HighPrecision },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsrFieldLabel(
                    text = "术语库配置",
                    trailing = {
                        Row(
                            modifier = Modifier.clickable { },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = tokens.sectionLabel,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "新建",
                                color = tokens.sectionLabel,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.inputCardContainer)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Book,
                        contentDescription = null,
                        tint = tokens.mutedText,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "通用科技类术语",
                            color = tokens.bodyText,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "包含 450+ 个关键词",
                            color = tokens.mutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = tokens.mutedText,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.panelContainer)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = tokens.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "时间戳粒度",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.bodyText
                        )
                    }
                    Text(
                        text = if (timestampLevel >= 1.5f) "句子级（Sentence）" else "单词级（Word）",
                        color = tokens.sectionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(tokens.chipContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Slider(
                    value = timestampLevel,
                    onValueChange = { timestampLevel = it },
                    valueRange = 0f..2f,
                    steps = 1,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = tokens.primaryButtonContainer,
                        activeTrackColor = tokens.primaryButtonContainer,
                        inactiveTrackColor = tokens.sliderTrack,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "单词级",
                        color = tokens.mutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "句子级",
                        color = tokens.mutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "段落级",
                        color = tokens.mutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsrFieldLabel(text = "高级处理")
                AdvancedSwitchRow(
                    title = "语音增强",
                    desc = "消除背景噪音，提升人声清晰度。",
                    icon = Icons.Rounded.GraphicEq,
                    checked = enableEnhancement,
                    onCheckedChange = { enableEnhancement = it },
                )
                AdvancedSwitchRow(
                    title = "AI 智能纠错",
                    desc = "自动修正语法错误和语气词。",
                    icon = Icons.Rounded.AutoFixHigh,
                    checked = enableAutoFix,
                    onCheckedChange = { enableAutoFix = it },
                )
            }

            AsrInfoCard(
                title = "预计消耗: ~150 Tokens",
                content = "本次生成大约需要 2-3 分钟，生成过程中您可以将应用切换至后台。",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = tokens.sectionLabel,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        AsrWizardBottomActions(
            primaryLabel = "确认并生成",
            primaryIcon = Icons.Rounded.SmartToy,
            onPrimaryClick = onConfirm,
            secondaryLabel = "上一步",
            secondaryIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            onSecondaryClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter),
            elevatedStyle = true,
        )
    }
}

@Composable
private fun ModelCard(
    title: String,
    desc: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) tokens.modelSelectedContainer else tokens.inputCardContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) tokens.modelSelectedText else tokens.mutedText,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(AsrStepShape)
                    .background(if (selected) tokens.primaryButtonContainer else tokens.inputLeadingContainer),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) tokens.modelSelectedText else tokens.bodyText,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) tokens.modelSelectedText.copy(alpha = 0.85f) else tokens.mutedText,
            )
        }
    }
}

@Composable
private fun AdvancedSwitchRow(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.mutedText,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.bodyText
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.mutedText
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.switchThumb,
                checkedTrackColor = tokens.primaryButtonContainer,
                uncheckedTrackColor = tokens.switchTrack,
                uncheckedThumbColor = tokens.switchThumb,
            ),
        )
    }
}
